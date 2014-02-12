import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jtk on 2/8/14.
 */
public class Client implements Runnable {
    private final static int numClients = 1000;
    private final static int numPackets = 1000;

    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    int id;
    int nPackets;

    public static AtomicInteger totalCount = new AtomicInteger(0);

    public static void main(String args[]) {
//        Socket[] sockets = new Socket[numClients];
//        for (int i = 0; i < numClients; i++) {
////            System.out.printf("opening %d\n", i);
//            try {
//                sockets[i] = new Socket(serverLocation, portLocation);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        System.out.printf("all sockets open; you have 5 seconds\n");
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }


        Thread[] clients = new Thread[numClients];

        for (int i = 0; i < numClients; i++) {
            clients[i] = new Thread(new Client(i+1, numPackets));
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

    public Client(int id, int nPackets) {
        this.id = id;
        this.nPackets = nPackets;
    }

    public void run() {
//        System.out.printf("client %d starting: generating %d packets\n", id, nPackets);

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
//            e.printStackTrace();
//            System.exit(id);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
