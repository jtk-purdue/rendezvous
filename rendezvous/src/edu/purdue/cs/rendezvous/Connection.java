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
    private SocketChannel socketChannel;
    private Selector selector;
    private Logger logger;
    private SelectionKey key;
    private StringBuffer incomingString = new StringBuffer();
    private StringBuffer outgoingString = new StringBuffer();
    private ByteBuffer outgoingBuffer = ByteBuffer.wrap(new byte[500]);
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
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[500]);
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
//        outgoingBuffer.clear();
//        logger.info(String.format("POSTCLEAR: position = %d and limit = %d", outgoingBuffer.position(), outgoingBuffer.limit()));
        while (outgoingBuffer.position() < outgoingBuffer.limit() && outgoingString.length() > 0) {
            outgoingBuffer.put((byte) outgoingString.charAt(0));
            outgoingString.deleteCharAt(0);
        }

        // Flip buffer from writing to reading...
        logger.info(String.format("PREFLIP: position = %d and limit = %d", outgoingBuffer.position(), outgoingBuffer.limit()));
        outgoingBuffer.flip();
        logger.info(String.format("POSTFLIP: position = %d and limit = %d", outgoingBuffer.position(), outgoingBuffer.limit()));

        // Write data from connection buffer to network channel...
        if (outgoingBuffer.hasRemaining()) {
            logger.info(String.format("WRITE: '%s'", outgoingBuffer.toString()));
            socketChannel.write(outgoingBuffer);
            outgoingBuffer.compact();
            logger.info(String.format("POSTCOMPACT: position = %d and limit = %d", outgoingBuffer.position(), outgoingBuffer.limit()));
        }

        // If there is no more to be written, turn off write selection...
        if (outgoingString.length() == 0 && !outgoingBuffer.hasRemaining()) {
            outgoingBuffer.clear();
            logger.info("NO MORE DATA to write on this channel; selecting for read only");
            socketChannel.register(selector, SelectionKey.OP_READ, this);
        } else {
            logger.info(String.format("MORE DATA: %d in outgoingString and hasRemaining is %b", outgoingString.length(), outgoingBuffer.hasRemaining()));
        }
    }
}
