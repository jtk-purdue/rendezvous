import java.util.Observable;
import java.util.Observer;

/**
 * Created by jtk on 3/14/14.
 */
public class RSTest4 implements Observer {
    public static void main(String[] args) {
        new RSTest4().run(args[0], Integer.parseInt(args[1]));
    }

    private void run(String host, int port) {
        Connector connector = new Connector(host, port, null, this);
        connector.writeLine("hello");
        connector.writeLine("here is a test");
        connector.writeLine("last line");
        sleep(1000);
        connector.close();
    }

    private void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        String line = (String) arg;
        System.out.printf("RECEIVED: '%s'\n", line);
        System.out.flush();
    }
}
