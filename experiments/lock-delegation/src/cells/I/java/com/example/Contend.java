package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.locks.ReentrantLock;

/** The shipped lock taken, uncontended, in a static initializer. */
public class Contend implements Worker {

	static {
		ReentrantLock lock = new ReentrantLock();
		lock.lock();
		System.out.println("locked in clinit");
		lock.unlock();
		System.out.println("unlocked in clinit");
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
