package dev.gmitch215.bytebox.sync;

public final class Gate {

	private int permits;

	public Gate(int permits) {
		this.permits = permits;
	}

	public void acquire() throws InterruptedException {
		synchronized (this) {
			while (permits <= 0) wait();
			permits--;
		}
	}

	public void release() {
		synchronized (this) {
			permits++;
			notifyAll();
		}
	}
}
