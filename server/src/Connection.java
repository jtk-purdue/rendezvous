import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by jtk on 2/9/14.
 */
public class Connection {
    SocketChannel channel;
    StringBuffer incomingString = new StringBuffer();
    StringBuffer outgoingString = new StringBuffer();
    ByteBuffer outgoingBuffer = ByteBuffer.allocate(500);
    String remote;

    Connection(SocketChannel channel) {
        this.channel = channel;
        try {
            this.remote = channel.getRemoteAddress().toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toString() {
        return channel.toString();
    }
}
