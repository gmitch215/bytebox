package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.Semaphore;

/** Two fibers contend for one permit; the second must wait until the first releases. */
public class Contend implements Worker {

	static {
		new Thread(Contend::run).start();
	}

	static void run() {
		try {
			Semaphore sem = new Semaphore(1);
			sem.acquire();
			System.out.println("main acquired");
			Thread t = new Thread(() -> {
				try {
					System.out.println("t2 requesting");
					sem.acquire();
					System.out.println("t2 acquired");
					sem.release();
					System.out.println("t2 released");
				} catch (InterruptedException e) {
					System.out.println("t2 interrupted");
				}
			});
			t.start();
			System.out.println("main started t2");
			Thread.sleep(20);
			System.out.println("main releasing");
			sem.release();
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
