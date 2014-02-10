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

    Connection(SocketChannel channel) {
        this.channel = channel;
    }

    public String toString() {
        return channel.toString();
    }
}
