package com.example;

public class PlainGate {

	private int permits;

	public PlainGate(int permits) {
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
