import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by jtk on 3/14/14.
 */
public class Connector extends Observable implements Runnable, Observer {
    private String host;
    private int port;

    private boolean closing = false;
    private Socket socket = null;
    private OutputStreamWriter outputStreamWriter;
    private BufferedReader bufferedReader;

    public Connector(String host, int port) {
        this(host, port, null);
    }

    public Connector(String host, int port, Observer observer) {
        this.host = host;
        this.port = port;

        if (observer == null)
            addObserver(this);
        else
            addObserver(observer);

        new Thread(this).start();
    }

    private synchronized void open() {
        if (socket != null)
            return;
        try {
            socket = new Socket(host, port);
            outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            socket = null;
        }
    }

    public void writeLine(String s) {
        open();
        try {
            outputStreamWriter.write(s);
            outputStreamWriter.write("\n");
            outputStreamWriter.flush();
        } catch (Exception e) {
            socket = null;
        }
    }

    public void run() {
        while (true) {
            open();
            try {
                String line = bufferedReader.readLine();
                setChanged();
                notifyObservers(line);
            } catch (Exception e) {
                socket = null;
                System.err.printf("closing = %b\n", closing);
                if (closing)
                    return;
                System.err.printf("READ FAILED: sleeping for 5 seconds\n");
                sleep(5000);
            }
        }
    }

    void close() {
        closing = true;
        try {
            outputStreamWriter.close();
            bufferedReader.close();
            socket.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test section...
     */
    public static void main(String args[]) {
        Connector c = new Connector("localhost", 1337);
        c.writeLine("hello there");
        c.writeLine("here is the second message");
        c.writeLine("last message, then closing");
        c.sleep(10000);
        c.close();
    }

    @Override
    public void update(Observable o, Object arg) {
        String line = (String) arg;
        System.out.printf("RECEIVED: '%s'\n", line);
        System.out.flush();
    }
}
