import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Created by jtk on 2/22/14.
 */
public class ServerConnection {
    Socket socket;
    OutputStreamWriter outputStreamWriter;
    BufferedReader bufferedReader;

    ServerConnection(String serverLocation, int portLocation) {
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
