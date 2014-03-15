package edu.purdue.cs.rendezvous;

import java.io.IOException;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.logging.*;

/**
 * Created by jtk on 2/15/14.
 */
public class RendezvousServer {
    public static void main(String[] args) {
        if (args.length != 1)
            System.err.printf("Usage: Rendezvous PORT-NUMBER\n");
        else {
            int port = Integer.parseInt(args[0]);
            new RendezvousServer().run(port, true);
        }
    }

    ConnectionManager connectionManager;
    String server = null;

    public void run(int port, boolean console) {
        /**
         * Set up logging...
         */
        LogManager.getLogManager().reset();
        Logger logger = Logger.getLogger(RendezvousServer.class.getName());
        Logger loggerParent = logger.getParent();  // get root or global logger (why is not clear to me)
        loggerParent.setLevel(Level.INFO);

        Handler handler = null;
        if (console)
            handler = new ConsoleHandler();
        else {
            try {
                handler = new FileHandler("trace.log", 1000, 10);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Formatter formatter = new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                final StringBuffer sb = new StringBuffer();
                sb.setLength(0);
                sb.append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(logRecord.getMillis()));
                sb.append(": ");
                sb.append(logRecord.getLevel().toString());
                sb.append(" - ");
                sb.append(logRecord.getMessage());
                sb.append(" (");
                sb.append(logRecord.getSourceClassName());
                sb.append(".");
                sb.append(logRecord.getSourceMethodName());
                sb.append(")\n");
                return sb.toString();
            }
        };
        handler.setFormatter(formatter);
        loggerParent.addHandler(handler);
        logger.info("Logging configuration complete");

        /**
         * Create a ConnectionData structure and hash to keep track of connections...
         */
        class ConnectionData {

            int next; // next message number expected from this connection
            int last; // last message number expected from this connection

            public ConnectionData(int next, int last) {
                this.next = next;
                this.last = last;
            }
        }
        HashMap<String, ConnectionData> info = new HashMap<String, ConnectionData>();

        /**
         * Create ConnectionManager...
         */
        connectionManager = new ConnectionManager(port);

        /**
         * Process incoming traffic from clients; generate outgoing traffic...
         */
        while (true) {
            try {
                Message message = connectionManager.getNextRawMessage();

                // TODO: Deal with new connections and closed connections in hashmap

                if (message.getString() == null) { // connection closed
                    logger.info(String.format("Connection to %s closed", message.getRemote()));
                    if (message.getRemote().equals(server)) {
                        server = null;
                        connectionManager.broadcast("server gone");
                    } else if (server != null)
                        connectionManager.send(server, String.format("server %s closed", message.getRemote()));
                } else if (server == null) { // don't have a server yet, check for server
                    if (message.getString().equals("server"))
                        server = message.getRemote();
                } else {
                    if (message.getRemote().equals(server)) {
                        connectionManager.broadcast(message.getString());
                    } else {
                        connectionManager.send(server, message.getRemote() + " " + message.getString());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
