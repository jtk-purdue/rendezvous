import edu.purdue.cs.rendezvous.ConnectionManager;
import edu.purdue.cs.rendezvous.Message;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.logging.*;

/**
 * Created by jtk on 2/10/14.
 */
public class CMTestServer {
    public static void main(String args[]) {
        new CMTestServer().run();
    }

    ConnectionManager connectionManager;

    public void run() {
        /**
         * Set up logging...
         */
        LogManager.getLogManager().reset();
        Logger logger = Logger.getLogger(CMTestServer.class.getName());
        Logger loggerParent = logger.getParent();  // get root or global logger (why is not clear to me)
        loggerParent.setLevel(Level.INFO);
        Handler handler = new ConsoleHandler();
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
        connectionManager = new ConnectionManager(1111);

        /**
         * Start broadcaster to generate broadcast traffic...
         */
        Runnable broadcaster = new Runnable() {
            @Override
            public void run() {
                int n = 0;

                while (true) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    connectionManager.broadcast(String.format("broadcast %d", ++n));
                }
            }
        };
        new Thread(broadcaster).start();

        /**
         * Process incoming traffic from clients; generate outgoing traffic...
         */
        while (true) {
            try {
                Message message = connectionManager.getNextRawMessage();

                logger.info("CMTestServer received: " + message.toString());

                // message format indicating nth of m messages with c additional non-blank characters...
                // message n m c xxx...

                if (message.getString() != null) {
                    String remote = message.getRemote();
                    String[] fields = message.getString().split(" ");
                    String command = fields[0];
                    int n = Integer.parseInt(fields[1]);
                    int m = Integer.parseInt(fields[2]);
                    int c = Integer.parseInt(fields[3]);
                    String text = fields[4];

                    ConnectionData cd = null;
                    if (info.containsKey(remote))
                        cd = info.get(remote);
                    else {
                        cd = new ConnectionData(1, m);
                        info.put(remote, cd);
                    }

                    assert command.equals("message");
                    assert cd.next == n;
                    assert cd.last == m;
                    assert c == text.length();

                    cd.next++;

                    connectionManager.send(remote, String.format("received %d %s", c, text));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
