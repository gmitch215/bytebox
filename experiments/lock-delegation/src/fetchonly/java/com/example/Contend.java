package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.Semaphore;

/** The lock is reachable from fetch only; nothing runs at module start. */
public class Contend implements Worker {

	static {
		System.out.println("clinit ran");
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		try {
			Semaphore sem = new Semaphore(1);
			sem.acquire();
			sem.release();
		} catch (InterruptedException e) {
			return Bytebox.response("interrupted");
		}
		return Bytebox.response("ok");
	}
}
