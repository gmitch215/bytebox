package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.Semaphore;

/** An uncontended acquire in a static initializer, so nothing ever waits. */
public class Contend implements Worker {

	static {
		try {
			Semaphore sem = new Semaphore(1);
			sem.acquire();
			System.out.println("acquired in clinit");
			sem.release();
			System.out.println("released in clinit");
		} catch (Throwable e) {
			System.out.println("clinit threw " + e);
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
