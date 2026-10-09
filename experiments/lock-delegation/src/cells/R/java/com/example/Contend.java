package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** The shipped lock: contention, a timed tryLock, and a condition handshake. */
public class Contend implements Worker {

	static boolean ready;

	static {
		new Thread(Contend::run).start();
	}

	static void run() {
		try {
			ReentrantLock lock = new ReentrantLock();
			lock.lock();
			System.out.println("t1 acquired");
			Thread t = new Thread(() -> {
				try {
					System.out.println("t2 requesting");
					lock.lock();
					System.out.println("t2 acquired");
					lock.unlock();
					System.out.println("t2 released");
				} catch (Throwable e) {
					System.out.println("t2 threw " + e);
				}
			});
			t.start();
			System.out.println("t1 started t2");
			Thread.sleep(20);
			System.out.println("t1 releasing");
			lock.unlock();
			t.join();

			Lock guard = new ReentrantLock();
			Condition cond = guard.newCondition();
			Thread waiter = new Thread(() -> {
				guard.lock();
				try {
					System.out.println("waiter awaiting");
					while (!ready) cond.await();
					System.out.println("waiter saw ready");
				} catch (InterruptedException e) {
					System.out.println("waiter interrupted");
				} finally {
					guard.unlock();
				}
			});
			waiter.start();
			Thread.sleep(20);
			guard.lock();
			ready = true;
			System.out.println("t1 signalling");
			cond.signal();
			guard.unlock();
			waiter.join();

			ReentrantLock timed = new ReentrantLock();
			timed.lock();
			Thread t3 = new Thread(() -> {
				try {
					System.out.println(
						"t3 timed 30ms: " + timed.tryLock(30, TimeUnit.MILLISECONDS)
					);
				} catch (InterruptedException e) {
					System.out.println("t3 interrupted");
				}
			});
			t3.start();
			t3.join();
			timed.unlock();

			guard.lock();
			System.out.println("await 20ms: " + cond.await(20, TimeUnit.MILLISECONDS));
			guard.unlock();
			System.out.println("t1 done");
		} catch (Throwable e) {
			System.out.println("t1 threw " + e);
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
