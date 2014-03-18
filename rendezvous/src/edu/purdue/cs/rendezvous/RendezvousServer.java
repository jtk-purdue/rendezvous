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
        if (args.length != 2)
            System.err.printf("Usage: Rendezvous PORT-NUMBER [console|file]\n");
        else {
            int port = Integer.parseInt(args[0]);
            boolean console = args[1].equals("console");
            new RendezvousServer().run(port, console);
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
                handler = new FileHandler("trace.log", 10000000, 20);
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

                // If the message string is empty, the connection is closed...
                if (message.getString() == null) {
                    logger.info(String.format("Connection to %s closed", message.getRemote()));
                    if (message.getRemote().equals(server)) {
                        server = null;
                        connectionManager.broadcast("reset");
                    } else if (server != null)
                        connectionManager.send(server, message.getRemote() + " reset");
                }
                // else if we don't have a server yet, check for it...
                else if (server == null) {
                    if (message.getString().equals("server"))
                        server = message.getRemote();
                }
                // else it is a regular packet...
                else {
                    if (message.getRemote().equals(server)) {
                        // packet from server, is it directed or broadcast?
                        String s = message.getString();
                        if (s.startsWith("=")) { // directed message
                            String[] fields = s.split("=");
                            connectionManager.send(fields[1], fields[2]);
                        } else // else broadcast
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
