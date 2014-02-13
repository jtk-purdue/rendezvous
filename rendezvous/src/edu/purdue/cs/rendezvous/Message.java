package edu.purdue.cs.rendezvous;

/**
 * Created by jtk on 2/9/14.
 */

public class Message {
    private Connection connection;
    private String string;

    public Message(String s, Connection connection) {
        this.string = s;
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getString() {
        return string;
    }
}
