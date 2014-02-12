import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by jtk on 2/10/14.
 */
public class Main {
    public static void main(String args[]) {
        new Main().run();
    }

    ConnectionManager connectionManager;

    public void run() {
        connectionManager = new ConnectionManager(1111);
        new Thread(new EventManager()).start();

        class ConnectionData {
            int next; // next message number expected from this connection
            int last; // last message number expected from this connection

            public ConnectionData(int next, int last) {
                this.next = next;
                this.last = last;
            }
        }

        HashMap<Connection, ConnectionData> info = new HashMap<Connection, ConnectionData>();

        while (true) {
            try {
                Message message = connectionManager.getNextMessage();

                // message format indicating nth of m messages with c additional non-blank characters...
                // message n m c xxx...

                String[] fields = message.string.split(" ");
                // System.out.printf("RECEIVED: %s (%d fields)\n", message.string, fields.length);
                int n = Integer.parseInt(fields[1]);
                int m = Integer.parseInt(fields[2]);
                int c = Integer.parseInt(fields[3]);

                ConnectionData cd = null;
                if (info.containsKey(message.connection))
                    cd = info.get(message.connection);
                else {
                    cd = new ConnectionData(1, m);
                    info.put(message.connection, cd);
                }

                assert fields[0].equals("message");
                assert cd.next == n;
                assert cd.last == m;
                assert c == fields[4].length();

                cd.next++;

                connectionManager.send(message.connection, String.format("received %d %s", c, fields[4]));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class EventManager implements Runnable {
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
    }
}
