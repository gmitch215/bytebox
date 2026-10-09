package dev.gmitch215.bytebox.sync;

public final class Waits {

	private Waits() {}

	public static void acquire(int[] permits) throws InterruptedException {
		while (permits[0] <= 0) Thread.sleep(1);
		permits[0]--;
	}

	public static void release(int[] permits) {
		permits[0]++;
	}
}
