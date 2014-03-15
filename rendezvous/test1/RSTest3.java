import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jtk on 2/8/14.
 */
public class RSTest3 {
    public final static int numClients = 10;

    final static String serverLocation = "localhost";
    final static int portLocation = 1337;

    public static AtomicInteger totalCount = new AtomicInteger(0);

    public static void main(String args[]) {
        new RSTest3().testServerMode();
    }

    private static void testServerMode() {
        Thread server = new Thread(new TestServer(serverLocation, portLocation));
        server.start();

        try {
            Thread.sleep(1000);  // give the server a chance to get started and connected to the RendezvousServer
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Thread[] clients = new Thread[numClients];

        for (int i = 0; i < numClients; i++) {
            clients[i] = new Thread(new TestClient(serverLocation, portLocation));
            clients[i].start();
        }

        for (int i = 0; i < numClients; i++)
            try {
                clients[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        try {
            server.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.printf("Test complete\n");
    }
}

class TestServer implements Runnable {
    ServerConnection connection;
    HashMap<String, String> clients = new HashMap<String, String>();

    TestServer(String serverLocation, int portLocation) {
        connection = new ServerConnection(serverLocation, portLocation);
        connection.writeLine("server");
    }

    public void run() {
        int clients = 0;

        while (clients < RSTest3.numClients) {
            String message = connection.readLine();
            System.out.printf("server read: %s\n", message);

            // Format: remote command parameters...
            String[] fields = message.split(" ");
            String remote = fields[0];
            String command = fields[1];

            if (command.equals("hello")) {
                connection.writeLine("to " + remote + " " + command.toUpperCase());
                clients++;
            }
        }

        connection.close();
    }
}

class TestClient implements Runnable {
    ServerConnection connection;

    TestClient(String serverLocation, int portLocation) {
        connection = new ServerConnection(serverLocation, portLocation);
    }

    public void run() {
        connection.writeLine(String.format("hello from %s", connection.toString()));

        while (true) {
            String message = connection.readLine();
            System.out.printf("client read: %s\n", message);

            // Format: remote command parameters...
            String[] fields = message.split(" ");
            String command = fields[0];

            if (command.equals("server") && fields[1].equals("gone"))
                break;
        }

        connection.close();
    }
}
