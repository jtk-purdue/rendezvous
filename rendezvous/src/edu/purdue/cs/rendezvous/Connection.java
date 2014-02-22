package edu.purdue.cs.rendezvous;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * Created by jtk on 2/9/14.
 */
public class Connection {
    private final static int BUFFER_SIZE = 500;
    private SocketChannel socketChannel;
    private Selector selector;
    private Logger logger;
    private SelectionKey key;
    private StringBuffer incomingString = new StringBuffer();
    private StringBuffer outgoingString = new StringBuffer();
    private ByteBuffer outgoingBuffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]);
    private String remote;

    Connection(SocketChannel socketChannel, Selector selector) {
        this.socketChannel = socketChannel;
        this.selector = selector;
        logger = Logger.getLogger(this.getClass().getSimpleName());
        try {
            this.remote = socketChannel.getRemoteAddress().toString();
            key = socketChannel.register(selector, SelectionKey.OP_READ, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        return socketChannel.toString();
    }

    public String getRemote() {
        return remote;
    }

    public void close() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            throw new RuntimeException("unexpected channel close failure");
        }
        key.cancel();
    }

    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    public void appendLine(String string) throws ClosedChannelException {
        outgoingString.append(string);
        outgoingString.append("\n");
        socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
    }

    public void readMessages(LinkedBlockingQueue<Message> incomingMessages) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]);
        logger.info(String.format("readMessage: position = %d, limit = %d, hasRemaining = %b", buffer.position(), buffer.limit(), buffer.hasRemaining()));
        int charsRead = 0;

        // Read data from network channel into buffer...
        charsRead = socketChannel.read(buffer);

        // Check for end of file...
        if (charsRead == -1)
            throw new IOException("end of file");

        // Flip buffer from writing to reading...
        buffer.flip();

        // Copy data from buffer to incoming message...
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get(); // TODO: This is wrong, since get returns a byte.
            if (c == '\r') // ignore carriage returns
                ;
            else if (c == '\n') {
                Message message = new Message(remote, incomingString.toString());
                incomingMessages.add(message);
                logger.info(String.format("READ: '%s' from %s", message.getString(), message.getRemote()));
                incomingString.setLength(0);
            } else
                incomingString.append(c);
        }
    }

    public void writeMessage() throws IOException {
        // Copy data from string buffer to connection buffer...
        if (outgoingBuffer.limit() == 0)
            throw new RuntimeException("limit is 0");
        logger.info(String.format("writeMessage: position = %d, limit = %d, hasRemaining = %b", outgoingBuffer.position(), outgoingBuffer.limit(), outgoingBuffer.hasRemaining()));
        while (outgoingBuffer.position() < outgoingBuffer.limit() && outgoingString.length() > 0) {
            outgoingBuffer.put((byte) outgoingString.charAt(0));
            outgoingString.deleteCharAt(0);
        }

        // Flip buffer from writing to reading...
        outgoingBuffer.flip();

        // Write data from connection buffer to network channel...
        if (outgoingBuffer.hasRemaining())
            socketChannel.write(outgoingBuffer);

        // Prepare the buffer for next time: either compact if data left, else clear it
        if (outgoingBuffer.hasRemaining())
            outgoingBuffer.compact();
        else
            outgoingBuffer.clear();

        // If there is no more to be written, turn off write selection...
        if (outgoingBuffer.position() == 0 && outgoingString.length() == 0)
            socketChannel.register(selector, SelectionKey.OP_READ, this);
    }
}
