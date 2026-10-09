package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Does nanoTime move across a sleep and across a timed wait while another fiber's timer is pending. */
public class Contend implements Worker {

	static {
		new Thread(Contend::run).start();
	}

	static void run() {
		try {
			long n0 = System.nanoTime();
			long m0 = System.currentTimeMillis();
			Thread.sleep(20);
			System.out.println(
				"after sleep(20): nano " +
					(System.nanoTime() - n0) / 1_000_000 +
					" ms, millis " +
					(System.currentTimeMillis() - m0)
			);

			ReentrantLock lock = new ReentrantLock();
			Thread holder = new Thread(() -> {
				lock.lock();
				try {
					Thread.sleep(80);
				} catch (InterruptedException e) {
					System.out.println("holder interrupted");
				}
				System.out.println("holder releasing");
				lock.unlock();
			});
			holder.start();
			Thread.sleep(20);
			long n1 = System.nanoTime();
			long m1 = System.currentTimeMillis();
			boolean got = lock.tryLock(10, TimeUnit.MILLISECONDS);
			System.out.println(
				"tryLock(10ms) while held for 80: " +
					got +
					", nano " +
					(System.nanoTime() - n1) / 1_000_000 +
					" ms, millis " +
					(System.currentTimeMillis() - m1)
			);
			if (got) lock.unlock();
			holder.join();
		} catch (Throwable e) {
			System.out.println("threw " + e);
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
