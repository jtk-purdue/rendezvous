package edu.purdue.cs.rendezvous;

/**
 * Created by jtk on 2/8/14.
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;

public class ConnectionManager implements Runnable {
    private Logger logger;
    private int port;
    private Selector selector;
    private LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<Message>();
    private ConcurrentLinkedQueue<Message> outgoingMessages = new ConcurrentLinkedQueue<Message>();
    private HashMap<String, Connection> connections = new HashMap<String, Connection>();

    public ConnectionManager(int port) {
        logger = Logger.getLogger(this.getClass().getSimpleName());
        logger.info("Logger created");
        this.port = port;
        new Thread(this).start();
    }

    public void send(String remote, String string) {
        outgoingMessages.add(new Message(remote, string));
        selector.wakeup();
    }

    public void broadcast(String string) {
        outgoingMessages.add(new Message(null, string)); // null indicates broadcast message
        selector.wakeup();
    }

    public String getNextMessage() throws InterruptedException {
        Message message = incomingMessages.take();
        return message.getRemote() + " " + message.getString();
    }

    public Message getNextRawMessage() throws InterruptedException {
        return incomingMessages.take();
    }

    public void run() {
        logger.info("server starting");

        ServerSocketChannel serverSocketChannel;

        try {
            selector = Selector.open();

            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // ignoring SelectionKey returned
            serverSocketChannel.socket().bind(new InetSocketAddress(port), 1000);  // TODO: Limit likely being ignored.

            while (true) {
                selector.select();
                logger.info("selector wakeup");
                processSelectorEvents(serverSocketChannel);
                stageOutgoingMessages();
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.severe("SHOULDN'T GET HERE: server shutting down");
        }
    }

    private void processSelectorEvents(ServerSocketChannel serverSocketChannel) {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            logger.info(String.format("key event: %s", key.toString()));
            if (key.isAcceptable())
                processAccept(serverSocketChannel);
            if (key.isConnectable()) {
                logger.severe("CONNECT: why?\n");
                throw new RuntimeException("Unexpected key.isConnectable()");
            }
            if (key.isReadable())
                processRead(key);
            if (key.isValid() && key.isWritable())
                processWrite(key);
            keyIterator.remove();
        }
    }

    private void processAccept(ServerSocketChannel serverSocketChannel) {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            Connection connection = new Connection(socketChannel, selector);
            connections.put(connection.getRemote(), connection);
            logger.info(String.format("ACCEPT: %s (socket size = %d)", socketChannel.toString(), socketChannel.socket().getSendBufferSize()));
        } catch (IOException e) {
            logger.severe(String.format("ACCEPT FAILURE: %s", e.toString()));
            throw new RuntimeException(e);
        }
    }

    private void processWrite(SelectionKey key) {
        logger.info("process write");
        Connection connection = (Connection) key.attachment();
        try {
            connection.writeMessage();
        } catch (IOException e) {
            incomingMessages.add(new Message(connection.getRemote(), null));
            connections.remove(connection.getRemote());
            connection.close();
        }
    }

    private void processRead(SelectionKey key) {
        logger.info("process read");
        Connection connection = (Connection) key.attachment();
        try {
            connection.readMessages(incomingMessages);
        } catch (IOException e) {
            incomingMessages.add(new Message(connection.getRemote(), null));
            connections.remove(connection.getRemote());
            connection.close();
        }
    }

    private void stageOutgoingMessages() {
        logger.info("staging outgoing messages");
        while (!outgoingMessages.isEmpty()) {
            Message message = outgoingMessages.remove();
            String remote = message.getRemote();
            if (remote == null) // handle broadcast message
                for (SelectionKey key : selector.keys()) {
                    Connection connection = (Connection) key.attachment();
                    if (connection != null && connection.isOpen())  // ensure connection is a client socket, not server socket, and is open
                        stageOneOutgoingMessage(message, connection);
                }
            else {
                Connection connection = connections.get(remote);
                if (connection != null && connection.isOpen())
                    stageOneOutgoingMessage(message, connection);
            }
        }
    }

    private void stageOneOutgoingMessage(Message message, Connection connection) {
        try {
            connection.appendLine(message.getString());
        } catch (IOException e) {
            incomingMessages.add(new Message(connection.getRemote(), null));
            connections.remove(connection.getRemote());
            connection.close();
        }
    }
}
