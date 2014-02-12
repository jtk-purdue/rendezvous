import java.io.IOException;

/**
 * Created by jtk on 2/9/14.
 */

public class Message {
    Connection connection;
    String string;

    public Message(String s, Connection connection) {
        this.string = s;
        this.connection = connection;
    }
}
