package fixture;

public class Sleeper {
	public static void main(String[] args) {
		System.out.println("before");
		new Thread(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("after sleep");
		}).start();
		System.out.println("returned");
	}
}
