package com.example.sub;

import dev.gmitch215.bytebox.sync.Gate;

public class Semaphore {

	private final Gate gate;

	public Semaphore(int permits) {
		this.gate = new Gate(permits);
	}

	public void acquire() throws InterruptedException {
		gate.acquire();
	}

	public void release() {
		gate.release();
	}
}
