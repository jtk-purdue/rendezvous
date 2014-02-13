package edu.purdue.cs.rendezvous;

/**
 * Created by jtk on 2/9/14.
 */

public class Message {
    private String remote;
    private String string;

    public Message(String remote, String string) {
        this.remote = remote;
        this.string = string;
    }

    public String getRemote() {
        return remote;
    }

    public String getString() {
        return string;
    }
}
