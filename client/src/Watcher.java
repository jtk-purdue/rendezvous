import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by jtk on 2/10/14.
 */
public class Watcher implements Runnable {
    Socket socket;
    BufferedReader bufferedReader;

    public Watcher(Socket socket) {
        this.socket = socket;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        new Thread(this).start();
    }

    public void run() {
        while (!socket.isOutputShutdown()) {
            try {
                String line = bufferedReader.readLine();
                System.out.printf("RECEIVED: %s\n", line);
            } catch (IOException e) {
                assert SocketException.class.isInstance(e);  // assert underlying socket is closed
                return;
            }
        }
    }
}
