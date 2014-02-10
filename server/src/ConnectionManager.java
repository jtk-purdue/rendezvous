/**
 * Created by jtk on 2/8/14.
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ConnectionManager implements Runnable {
    private int port;
    private Selector selector;
    private LinkedBlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<Message>();
    private ConcurrentLinkedQueue<Message> outgoingMessages = new ConcurrentLinkedQueue<Message>();

    public ConnectionManager(int port) {
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
            if (c != null) // the server socket has no Connection attachment
                send(c, s);
        }
    }

    public Message getNextMessage() throws InterruptedException {
        return incomingMessages.take();
    }

    public void run() {
        ServerSocketChannel serverSocketChannel;

        System.out.println("server starting");

        try {
            selector = Selector.open();

            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // ignoring SelectionKey returned
            serverSocketChannel.socket().bind(new InetSocketAddress(port));

            while (true) {
                selector.select();
                processSelectorEvents(serverSocketChannel);
                stageOutgoingMessages();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.print("shouldn't get here\n");
    }

    private void stageOutgoingMessages() {
        while (!outgoingMessages.isEmpty()) {
            Message m = outgoingMessages.remove();
            m.connection.outgoingString.append(m.string);
            m.connection.outgoingString.append("\n");
            System.err.printf("SENDING: '%s'\n", m.string);
            try {
                m.connection.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, m.connection);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
                System.err.printf("CLOSED channel %s\n", m.connection.channel);
            }
        }
    }

    private void processSelectorEvents(ServerSocketChannel serverSocketChannel) throws IOException {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isAcceptable())
                processAccept(serverSocketChannel);
            else if (key.isConnectable())
                System.err.printf("CONNECT: why?\n");
            else if (key.isReadable())
                processRead(key);
            else if (key.isWritable())
                processWrite(key);
            keyIterator.remove();
        }
    }

    private void processWrite(SelectionKey key) throws IOException {
        Connection cd = (Connection) key.attachment();
        while (cd.outgoingBuffer.position() < cd.outgoingBuffer.limit() && cd.outgoingString.length() > 0) {
            cd.outgoingBuffer.put((byte) cd.outgoingString.charAt(0));
            cd.outgoingString.deleteCharAt(0);
        }
        cd.outgoingBuffer.flip();
        if (cd.outgoingBuffer.hasRemaining()) {
            cd.channel.write(cd.outgoingBuffer);
        }
        cd.outgoingBuffer.compact();
        if (!cd.outgoingBuffer.hasRemaining())
            cd.channel.register(selector, SelectionKey.OP_READ, cd);
    }

    private void processRead(SelectionKey key) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[500]);
        SocketChannel socketChannel = (SocketChannel) key.channel();
        buffer.clear();
        int charsRead = socketChannel.read(buffer);
        buffer.flip();

        if (charsRead == -1) {
            System.err.printf("EOF: closing channel\n");
            socketChannel.close();
        } else {
            System.err.printf("BYTES: %d\n", charsRead);

            Connection cd = (Connection) key.attachment();
            StringBuffer sb = cd.incomingString;

            while (buffer.hasRemaining()) {
                char c = (char) buffer.get(); // TODO: This is wrong, since get returns a byte.
                if (c == '\r') // ignore carriage returns
                    ;
                else if (c == '\n') {
                    incomingMessages.add(new Message(sb.toString(), cd));
                    System.err.printf("READ: '%s' from %s\n", sb.toString(), socketChannel);
                    sb.setLength(0);
                } else
                    sb.append(c);
            }
        }
    }

    private void processAccept(ServerSocketChannel serverSocketChannel) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ, new Connection(socketChannel));
        System.err.printf("ACCEPT: %s (socket size = %d)\n", socketChannel.toString(), socketChannel.socket().getSendBufferSize());
    }
}
