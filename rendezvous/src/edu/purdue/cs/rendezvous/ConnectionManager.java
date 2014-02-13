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
        logger = Logger.getLogger(ConnectionManager.class.getName());
        logger.info("Logger created in edu.purdue.cs.rendezvous.ConnectionManager");
        this.port = port;
        new Thread(this).start();
    }

    public void send(String remote, String string) {
        outgoingMessages.add(new Message(remote, string));
        selector.wakeup();
    }

    public void broadcast(String string) {
        outgoingMessages.add(new Message(null, string)); // null indicates broadcast message
    }

    public String getNextMessage() throws InterruptedException {
        Message message = incomingMessages.take();
        return message.getRemote() + " " + message.getString();
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
                processSelectorEvents(serverSocketChannel);
                stageOutgoingMessages();
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.severe("SHOULDN'T GET HERE: server shutting down");
        }
    }

    private void stageOutgoingMessages() {
        while (!outgoingMessages.isEmpty()) {
            Message message = outgoingMessages.remove();
            Connection connection = connections.get(message.getRemote());
            if (connection == null) // handle broadcast message
                for (SelectionKey key : selector.keys()) {
                    connection = (Connection) key.attachment();
                    if (connection != null)  // ensure connection is a client socket, not server socket
                        stageOneOutgoingMessage(message, connection);
                }
            else
                stageOneOutgoingMessage(message, connection);
        }
    }

    private void stageOneOutgoingMessage(Message message, Connection connection) {
        connection.outgoingString.append(message.getString());
        connection.outgoingString.append("\n");
        logger.info(String.format("SENDING: '%s'", message.getString()));
        try {
            connection.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, connection);
        } catch (ClosedChannelException e) {
            logger.warning(String.format("CLOSED channel to %s", connection.remote));
            connections.remove(connection.remote);
        }
    }

    private void processSelectorEvents(ServerSocketChannel serverSocketChannel) {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isValid() && key.isAcceptable()) // TODO: Are these isValid() calls really needed?
                processAccept(serverSocketChannel);
            if (key.isValid() && key.isConnectable())
                logger.severe("CONNECT: why?\n");
            if (key.isValid() && key.isReadable())
                processRead(key);
            if (key.isValid() && key.isWritable())
                processWrite(key);
            keyIterator.remove();
        }
    }

    private void processWrite(SelectionKey key) {
        Connection connection = (Connection) key.attachment();
        while (connection.outgoingBuffer.position() < connection.outgoingBuffer.limit() && connection.outgoingString.length() > 0) {
            connection.outgoingBuffer.put((byte) connection.outgoingString.charAt(0));
            connection.outgoingString.deleteCharAt(0);
        }
        connection.outgoingBuffer.flip();
        boolean fConnectionClosed = false; // TODO: Hack to avoid registering on a closed channel (below)
        if (connection.outgoingBuffer.hasRemaining()) {
            try {
                connection.channel.write(connection.outgoingBuffer);
            } catch (IOException e) {
                logger.warning(String.format("WRITE ERROR '%s' to %s", e.getMessage(), connection.remote));
                fConnectionClosed = true;
                try {
                    connection.channel.close();
                    connections.remove(connection.remote);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        connection.outgoingBuffer.compact();
        if (!fConnectionClosed && !connection.outgoingBuffer.hasRemaining())
            try {
                connection.channel.register(selector, SelectionKey.OP_READ, connection);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            }
    }

    private void processRead(SelectionKey key) {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[500]);
        SocketChannel socketChannel = (SocketChannel) key.channel();
        buffer.clear();
        int charsRead = 0;
        boolean fConnectionClosed = false; // TODO: Another sort of hack (below)
        Connection connection = (Connection) key.attachment();

        try {
            charsRead = socketChannel.read(buffer);
        } catch (IOException e) {
            logger.warning(String.format("READ ERROR '%s' from %s", e.getMessage(), connection.remote));
            fConnectionClosed = true;
            try {
                socketChannel.close();
                connections.remove(connection.remote);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        buffer.flip();

        if (charsRead == -1) {
            if (!fConnectionClosed) {
                try {
                    socketChannel.shutdownInput();  // TODO: Not clear whether this is needed or should be just close()
                    connections.remove(connection.remote);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Connection cd = (Connection) key.attachment();
            StringBuffer sb = cd.incomingString;

            while (buffer.hasRemaining()) {
                char c = (char) buffer.get(); // TODO: This is wrong, since get returns a byte.
                if (c == '\r') // ignore carriage returns
                    ;
                else if (c == '\n') {
                    incomingMessages.add(new Message(cd.remote, sb.toString()));
                    logger.info(String.format("READ: '%s' from %s", sb.toString(), socketChannel));
                    sb.setLength(0);
                } else
                    sb.append(c);
            }
        }
    }

    private void processAccept(ServerSocketChannel serverSocketChannel) {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            Connection connection = new Connection(socketChannel);
            connections.put(connection.remote, connection);
            socketChannel.register(selector, SelectionKey.OP_READ, connection);
            logger.info(String.format("ACCEPT: %s (socket size = %d)", socketChannel.toString(), socketChannel.socket().getSendBufferSize()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
