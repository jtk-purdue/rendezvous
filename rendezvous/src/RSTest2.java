/**
 * Created by jtk on 2/8/14.
 */
public class RSTest2 {
    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    public static void main(String args[]) {
        new RSTest2().testBroadcastMode();
    }

    private void testBroadcastMode() {
        System.out.println("BEGIN: BROADCAST TEST");

        final int numConnections = 10;
        final int numMessages = 100;

        ServerConnection[] connections = new ServerConnection[numConnections];

        for (int i = 0; i < numConnections; i++)
            connections[i] = new ServerConnection(serverLocation, portLocation);

        try {
            Thread.sleep(2000);  // ensure all the server connections have been established
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < numConnections; i++)
            for (int j = 0; j < numMessages; j++) {
                String message = String.format("message %d of %d from connection %d of %d", j + 1, numMessages, i + 1, numConnections);
                System.out.println(message);
                connections[i].writeLine(message);
            }

        for (int i = 0; i < numConnections; i++) {
            System.out.printf("%d: STARTING\n", i + 1);
            int j = 0;
            while (j < numMessages * numConnections) {
                String response = connections[i].readLine();
                if (response == null) {
                    System.err.printf("%d: null response on connection\n", i + 1);
                    break;
                }
                String[] fields = response.split(" ");
                if (fields.length > 0 && fields[1].equals("message")) {
                    j++;
                    System.out.printf("%d: received %d of %d: %s\n", i + 1, j, numMessages * numConnections, response);
                } else
                    System.out.printf("%d: IGNORING: %s\n", i + 1, response);
            }
            System.out.printf("%d: COMPLETED\n", i + 1);
        }

        System.out.println("END: BROADCAST TEST");
    }
}