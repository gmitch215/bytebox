package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** Does the runtime carry real monitors, or does synchronized compile to nothing? */
public class MonitorWorker implements Worker {

	private static final Object LOCK = new Object();

	static {
		System.out.println("thread=" + Thread.currentThread().getName());

		synchronized (LOCK) {
			System.out.println("entered");
			synchronized (LOCK) {
				System.out.println("reentered");
			}
		}

		Thread waiter = new Thread(() -> {
			synchronized (LOCK) {
				System.out.println("waiter has the monitor");
				try {
					LOCK.wait(1000);
					System.out.println("waiter woke");
				} catch (InterruptedException e) {
					System.out.println("waiter interrupted");
				}
			}
		});
		waiter.start();

		synchronized (LOCK) {
			System.out.println("main got the monitor back");
			LOCK.notifyAll();
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
