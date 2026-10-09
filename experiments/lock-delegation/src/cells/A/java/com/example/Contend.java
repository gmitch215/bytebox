package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.locks.ReentrantLock;

/** Control: the shipped ReentrantLock under the same two-fiber contention. */
public class Contend implements Worker {

	static {
		new Thread(Contend::run).start();
	}

	static void run() {
		try {
			ReentrantLock lock = new ReentrantLock();
			lock.lock();
			System.out.println("main acquired");
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
			System.out.println("main started t2");
			Thread.sleep(20);
			System.out.println("main releasing");
			lock.unlock();
			t.join();
			System.out.println("main done");
		} catch (Throwable e) {
			System.out.println("main threw " + e);
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
