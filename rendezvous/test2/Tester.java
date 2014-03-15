/**
 * Created by jtk on 3/15/14.
 */
public class Tester {
    public static void main(String[] args) throws InterruptedException {
        Simulator simulator = new Simulator("safewalk", args[0], Integer.parseInt(args[1]));
        simulator.start();

        Client client1 = new Client("alice", args[0], Integer.parseInt(args[1]));
        client1.start();

        Client client2 = new Client("bob", args[0], Integer.parseInt(args[1]));
        client2.start();

        simulator.join();
        client1.join();
        client2.join();
        System.out.printf("Tester exiting.\n");
    }

    private static void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
