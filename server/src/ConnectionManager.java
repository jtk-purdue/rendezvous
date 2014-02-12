/**
 * Created by jtk on 2/8/14.
 */

import java.io.Console;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.text.DateFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.*;

public class ConnectionManager implements Runnable {
    private static Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private int port;
    private Selector selector;
    private LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<Message>();
    private ConcurrentLinkedQueue<Message> outgoingMessages = new ConcurrentLinkedQueue<Message>();

    public ConnectionManager(int port) {
        LogManager.getLogManager().reset();
        LOGGER.setLevel(Level.INFO);
        Handler handler = new ConsoleHandler();
        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                final StringBuffer sb = new StringBuffer();
                sb.setLength(0);
                sb.append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(logRecord.getMillis()));
                sb.append(": ");
                sb.append(logRecord.getLevel().toString());
                sb.append(" - ");
                sb.append(logRecord.getMessage());
                sb.append("\n");
                return sb.toString();
            }
        };
        handler.setFormatter(formatter);
        LOGGER.addHandler(handler);
        this.port = port;
        new Thread(this).start();
    }

    public void send(Connection connection, String s) {
        outgoingMessages.add(new Message(s, connection));
        selector.wakeup();
    }

    public void broadcast(String s) {  // TODO: this method is not running on the CM thread; check races
        for (SelectionKey key : selector.keys()) {
            Connection c = (Connection) key.attachment();
            if (c != null) { // the server socket has no Connection attachment
                outgoingMessages.add(new Message(s, c));
                LOGGER.info(String.format("STILL ALIVE: %s", c.remote));
            }
        }
        selector.wakeup();
    }

    public Message getNextMessage() throws InterruptedException {
        return incomingMessages.take();
    }

    public void run() {
        ServerSocketChannel serverSocketChannel;

        LOGGER.info("server starting");

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
            LOGGER.severe("SHOULDN'T GET HERE: server shutting down");
        }
    }

    private void stageOutgoingMessages() {
        while (!outgoingMessages.isEmpty()) {
            Message m = outgoingMessages.remove();
            m.connection.outgoingString.append(m.string);
            m.connection.outgoingString.append("\n");
            LOGGER.info(String.format("SENDING: '%s'", m.string));
            try {
                m.connection.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, m.connection);
            } catch (ClosedChannelException e) {
                LOGGER.warning(String.format("CLOSED channel to %s", m.connection.remote));
            }
        }
    }

    private void processSelectorEvents(ServerSocketChannel serverSocketChannel) {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isValid() && key.isAcceptable())
                processAccept(serverSocketChannel);
            if (key.isValid() && key.isConnectable())
                LOGGER.severe("CONNECT: why?\n");
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
                LOGGER.warning(String.format("WRITE ERROR '%s' to %s", e.getMessage(), connection.remote));
                fConnectionClosed = true;
                try {
                    connection.channel.close();
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
        try {
            charsRead = socketChannel.read(buffer);
        } catch (IOException e) {
            Connection connection = (Connection) key.attachment();
            LOGGER.warning(String.format("READ ERROR '%s' from %s", e.getMessage(), connection.remote));
            fConnectionClosed = true;
            try {
                socketChannel.close(); // TODO: Cleanup
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        buffer.flip();

        if (charsRead == -1) {
//            System.out.printf("EOF: closing channel\n");
            if (!fConnectionClosed) {
                try {
                    socketChannel.shutdownInput();  // TODO: Not clear whether this is needed or should be just close()
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
                    incomingMessages.add(new Message(sb.toString(), cd));
                    LOGGER.info(String.format("READ: '%s' from %s", sb.toString(), socketChannel));
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
            socketChannel.register(selector, SelectionKey.OP_READ, new Connection(socketChannel));
            LOGGER.info(String.format("ACCEPT: %s (socket size = %d)", socketChannel.toString(), socketChannel.socket().getSendBufferSize()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
