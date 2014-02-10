import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by jtk on 2/10/14.
 */
public class Watcher extends Thread {
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
                Client.totalCount.incrementAndGet(); // includes Tick packets
                if (line.startsWith("Thank")  && ++countPackets >= numPackets)
                    return;
            } catch (IOException e) {
                assert SocketException.class.isInstance(e);  // assert underlying socket is closed
                return;
            }
        }
    }
}
