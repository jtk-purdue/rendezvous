import java.util.Observable;
import java.util.Observer;

/**
 * Created by jtk on 3/15/14.
 */
public class Simulator extends Thread implements Observer {
    String name;
    Connector connector;
    boolean running = true;

    public Simulator(String name, String host, int port) {
        this.name = name;
        this.connector = new Connector(host, port, "server", this);
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
            connector.writeLine("server beat " + String.valueOf(c));
        }
        connector.writeLine("exit");
        connector.close();
    }

    @Override
    public void update(Observable o, Object arg) {
        String line = (String) arg;
        System.out.printf("SIM: %s\n", line);
        String[] fields = line.split(" ");
        if (fields[1].equals("connect"))
            connector.writeLine(String.format("=%s=Hello %s", fields[0], fields[2].toUpperCase()));
        else if (fields[0].equals("server") && fields[1].equals("beat") && fields[2].equals("5"))
            running = false;
    }
}
