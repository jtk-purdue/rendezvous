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

        while (true) {
            try {
                Message message = connectionManager.getNextMessage();
                System.out.printf("MESSAGE RECEIVED from connection %s: '%s'\n", message.connection, message.string);
                connectionManager.send(message.connection, "Thank you!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class EventManager implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                connectionManager.broadcast("TIMER: tick");
            }
        }
    }
}
