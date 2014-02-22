/**
 * Created by jtk on 2/22/14.
 */
public class RSTest1 {
    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    public static void main(String args[]) {
        new RSTest1().testSingleConnection();
    }

    private void testSingleConnection() {
        System.out.println("BEGIN: SINGLE CONNECTION TEST");
        ServerConnection c = new ServerConnection(serverLocation, portLocation);
        c.writeLine("hello");
        String response = c.readLine();
        System.out.println(response);
        assert response.contains("hello");
        c.close();
        System.out.println("END: SINGLE CONNECTION TEST");
    }
}
