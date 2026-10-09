package com.example.sub;

public class Semaphore {

	private int permits;

	public Semaphore(int permits) {
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
