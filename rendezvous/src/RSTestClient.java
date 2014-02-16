import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jtk on 2/8/14.
 */
public class RSTestClient implements Runnable {
    private final static int numClients = 10;
    private final static int numPackets = 500;

    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    int id;
    int nPackets;

    public static AtomicInteger totalCount = new AtomicInteger(0);

    public static void main(String args[]) {
        new RSTestClient().run();
    }

    public void run() {
        testSingleConnection();
        testBroadcastMode();
//        testServerMode();
    }

    private class Connection {
        Socket socket;
        OutputStreamWriter outputStreamWriter;
        BufferedReader bufferedReader;

        Connection() {
            try {
                socket = new Socket(serverLocation, portLocation);
                outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void writeLine(String s) {
            try {
                outputStreamWriter.write(s);
                outputStreamWriter.write("\n");
                outputStreamWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String readLine() {
            try {
                return bufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        void close() {
            try {
                outputStreamWriter.close();
                bufferedReader.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void testSingleConnection() {
        System.out.println("BEGIN: SINGLE CONNECTION TEST");
        Connection c = new Connection();
        c.writeLine("hello");
        String response = c.readLine();
        System.out.println(response);
        assert response.contains("hello");
        c.close();
        System.out.println("END: SINGLE CONNECTION TEST");
    }

    private void testBroadcastMode() {
        System.out.println("BEGIN: BROADCAST TEST");

        final int numConnections = 10;
        final int numMessages = 10;

        Connection[] connections = new Connection[numConnections];

        for (int i = 0; i < numConnections; i++)
            connections[i] = new Connection();

        for (int i = 0; i < numConnections; i++)
            for (int j = 0; j < numMessages; j++) {
                String message = String.format("message %d of %d from connection %d of %d", j + 1, numMessages, i + 1, numConnections);
                System.out.println(message);
                connections[i].writeLine(message);
            }

        for (int i = 0; i < numConnections; i++) {
            System.out.printf("%d: STARTING\n", i + 1);
            int j = 0;
            while (j < numMessages * numConnections) {
                String response = connections[i].readLine();
                if (response == null) {
                    System.err.printf("%d: null response on connection\n", i + 1);
                    break;
                }
                String[] fields = response.split(" ");
                if (fields.length > 0 && fields[1].equals("message")) {
                    j++;
                    System.out.printf("%d: received %d of %d: %s\n", i + 1, j, numMessages * numConnections, response);
                } else
                    System.out.printf("%d: IGNORING: %s\n", i + 1, response);
            }
            System.out.printf("%d: COMPLETED\n", i + 1);
        }

        System.out.println("END: BROADCAST TEST");
    }

    private static void testServerMode() {
        Thread[] clients = new Thread[numClients];

        for (int i = 0; i < numClients; i++) {
//            clients[i] = new Thread(new RSTestClient(i + 1, numPackets));
            clients[i].start();
        }

        for (int i = 0; i < numClients; i++)
            try {
                clients[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        System.out.printf("Test complete: %d packets expected, %d packets received\n",
                numClients * numPackets, totalCount.get());
    }

    public void run2() {

        try {
            Socket socket = new Socket(serverLocation, portLocation);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());

            Watcher watcher = new Watcher(socket, nPackets);
            watcher.start();

            Thread.sleep(1000);

            for (int i = 0; i < nPackets; i++) {
                outputStreamWriter.write(String.format("message %d %d 5 xxxxx\n", i + 1, nPackets));
                outputStreamWriter.flush();
            }

            watcher.join();
            socket.close();
        } catch (IOException e) {
            System.err.printf("CLIENT %d FAILED TO START\n", id, nPackets);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Created by jtk on 2/10/14.
     */
    public static class Watcher extends Thread {
        private final Socket socket;
        private final int numPackets;
        BufferedReader bufferedReader;
        private int countPackets = 0;

        public Watcher(Socket socket, int numPackets) {
            this.socket = socket;
            this.numPackets = numPackets;

            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            while (!socket.isOutputShutdown()) {
                try {
                    String line = bufferedReader.readLine();
                    System.out.printf("RECEIVED: %s\n", line);
                    System.out.flush();
                    String[] fields = line.split(" ");
                    // remote message %d %d 5 xxxxx
                    // 0      1       2  3  4 5
                    if (fields.length >= 2 && fields[1].equals("message")) {
                        totalCount.incrementAndGet();
                        int c = Integer.parseInt(fields[4]);
                        assert c == fields[5].length();
                        if (++countPackets == numPackets) {
                            System.out.printf("RECEIVED all %d packets\n", numPackets);
                            return;
                        }
                    }
                } catch (IOException e) {
                    assert SocketException.class.isInstance(e);  // assert underlying socket is closed
                    return;
                }
            }
        }
    }
}
