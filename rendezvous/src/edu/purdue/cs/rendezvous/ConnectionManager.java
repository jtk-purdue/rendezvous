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

    static int counter = 0;

    private void processSelectorEvents(ServerSocketChannel serverSocketChannel) {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            logger.info(String.format("@1 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
            keyIterator.remove();
            logger.info(String.format("@2 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
            if (key.isAcceptable())
                processAccept(serverSocketChannel);
            if (key.isConnectable())
                logger.severe("CONNECT: why?\n");
            if (key.isReadable())
                processRead(key);
            if (key.isValid() && key.isWritable())
                processWrite(key);
            if (key.isValid())
                logger.info(String.format("@3a %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
            else
                logger.info(String.format("@3b %d key (%s) status: no longer valid", counter++, key.toString()));

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
                    if (connection != null)  // ensure connection is a client socket, not server socket
                        stageOneOutgoingMessage(message, connection);
                }
            else {
                Connection connection = connections.get(remote);
                stageOneOutgoingMessage(message, connection);
            }
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
            connection.channel.keyFor(selector).cancel();
            connections.remove(connection.remote);
            incomingMessages.add(new Message(connection.remote, null));
        } catch (CancelledKeyException e) {
            logger.info(String.format("CANCELLED KEY exception caught: %s", e.toString()));
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
            logger.severe(String.format("ACCEPT FAILURE: %s", e.toString()));
        }
    }

    private void processWrite(SelectionKey key) {
        // Copy data from string buffer to connection buffer...
        Connection connection = (Connection) key.attachment();
        logger.info(String.format("@4 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
        while (connection.outgoingBuffer.position() < connection.outgoingBuffer.limit() && connection.outgoingString.length() > 0) {
            connection.outgoingBuffer.put((byte) connection.outgoingString.charAt(0));
            connection.outgoingString.deleteCharAt(0);
        }

        // Flip buffer from writing to reading...
        connection.outgoingBuffer.flip();

        // Write data from connection buffer to network channel...
        if (connection.outgoingBuffer.hasRemaining()) {
            try {
                connection.channel.write(connection.outgoingBuffer);
                connection.outgoingBuffer.compact();
            } catch (IOException e) {
                logger.warning(String.format("WRITE ERROR '%s' to %s", e.getMessage(), connection.remote));
                key.cancel();
                connections.remove(connection.remote);
                incomingMessages.add(new Message(connection.remote, null));
                try {
                    connection.channel.close();
                } catch (IOException e1) {
                    logger.severe(String.format("CLOSE ERROR '%s' of %s", e.getMessage(), connection.remote));
                }
                return;
            }
        }

        // If there is no more to be written, turn off write selection...
        logger.info(String.format("@5 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
        if (connection.outgoingString.length() == 0 && !connection.outgoingBuffer.hasRemaining()) {
            logger.info(String.format("@7 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
            logger.info(String.format("NO MORE TO WRITE to %s: string length = %d, hasRemaining = %b", connection.remote, connection.outgoingString.length(), connection.outgoingBuffer.hasRemaining()));
            try {
                connection.channel.register(selector, SelectionKey.OP_READ, connection);
            } catch (ClosedChannelException e) {
                logger.warning(String.format("CLOSE ERROR '%s' to %s", e.getMessage(), connection.remote));
                key.cancel();
                connections.remove(connection.remote);
                incomingMessages.add(new Message(connection.remote, null));
            }
            logger.info(String.format("@6 %d key (%s) status: interestOps = %o, readyOps = %o", counter++, key.toString(), key.interestOps(), key.readyOps()));
        }
    }

    private void processRead(SelectionKey key) {
        logger.info("process read");
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[500]);
        SocketChannel socketChannel = (SocketChannel) key.channel();
        buffer.clear();
        int charsRead = 0;
        Connection connection = (Connection) key.attachment();

        // Read data from network channel into buffer...
        try {
            charsRead = socketChannel.read(buffer);
        } catch (IOException e) {
            logger.warning(String.format("READ ERROR '%s' from %s", e.getMessage(), connection.remote));
            key.cancel();
            connections.remove(connection.remote);
            incomingMessages.add(new Message(connection.remote, null));
            try {
                socketChannel.close();
            } catch (IOException e1) {
                logger.severe(String.format("CLOSE ERROR '%s' of %s", e.getMessage(), connection.remote));
            }
            return;
        }

        // Check for end of file...
        if (charsRead == -1) {
            try {
                logger.info(String.format("end of file on %s", connection.remote));
                socketChannel.shutdownInput();
                key.cancel();
                connections.remove(connection.remote);
                incomingMessages.add(new Message(connection.remote, null));
            } catch (IOException e) {
                logger.severe(String.format("CLOSE ERROR '%s' of %s", e.getMessage(), connection.remote));
            }
            return;
        }

        // Flip buffer from writing to reading...
        buffer.flip();

        // Copy data from buffer to incoming message...
        StringBuffer sb = connection.incomingString;
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get(); // TODO: This is wrong, since get returns a byte.
            if (c == '\r') // ignore carriage returns
                ;
            else if (c == '\n') {
                incomingMessages.add(new Message(connection.remote, sb.toString()));
                logger.info(String.format("READ: '%s' from %s", sb.toString(), socketChannel));
                sb.setLength(0);
            } else
                sb.append(c);
        }
    }
}
