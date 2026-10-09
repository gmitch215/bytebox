package dev.gmitch215.bytebox.sync;

public final class Waits {

	private Waits() {}

	public static void acquire(int[] permits) throws InterruptedException {
		synchronized (permits) {
			while (permits[0] <= 0) permits.wait();
			permits[0]--;
		}
	}

	public static void release(int[] permits) {
		synchronized (permits) {
			permits[0]++;
			permits.notifyAll();
		}
	}
}
