package fixture;

public class Queued {
	public static void main(String[] args) throws Exception {
		System.out.println("start");
		Thread t = new Thread(() -> System.out.println("thread ran"));
		t.start();
		System.out.println("started");
	}
}
