import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Created by jtk on 2/8/14.
 */
public class Client {
    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    public static void main(String args[]) {

        new Client().run();
    }

    public void run() {
        System.out.println("client starting");

        try {
            Socket socket = new Socket(serverLocation, portLocation);
            Watcher watcher = new Watcher(socket);

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
            outputStreamWriter.write("first message from client\n"); outputStreamWriter.flush();

            Thread.sleep(5000);

            outputStreamWriter.write("last message from client\n"); outputStreamWriter.flush();

            Thread.sleep(10000);

            outputStreamWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
