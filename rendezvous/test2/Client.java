import java.util.Observable;
import java.util.Observer;

/**
 * Created by jtk on 3/15/14.
 */
public class Client extends Thread implements Observer {
    String name;
    Connector connector;
    boolean running = true;

    public Client(String name, String host, int port) {
        this.name = name;
        this.connector = new Connector(host, port, String.format("connect %s", name), this);
    }

    public void run() {
        int c = 0;



        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            c++;
            connector.writeLine(String.format("client %s beat %d", name, c));
        }
        connector.close();
    }

    @Override
    public void update(Observable o, Object arg) {
        String line = (String) arg;
        System.out.printf("CLIENT %s received: %s\n", name, line);
        String[] fields = line.split(" ");
        if (fields[0].equals("exit"))
            running = false;
    }
}
