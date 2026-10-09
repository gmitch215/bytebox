package com.example.sub;

import dev.gmitch215.bytebox.sync.Waits;

public class Semaphore {

	private final int[] permits;

	public Semaphore(int permits) {
		this.permits = new int[] { permits };
	}

	public void acquire() throws InterruptedException {
		Waits.acquire(permits);
	}

	public void release() {
		Waits.release(permits);
	}
}
