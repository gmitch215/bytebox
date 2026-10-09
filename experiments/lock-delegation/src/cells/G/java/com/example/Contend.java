package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** Does Throwable.addSuppressed work on this runtime. */
public class Contend implements Worker {

	static {
		try {
			IllegalArgumentException a = new IllegalArgumentException("src");
			a.addSuppressed(new IllegalStateException("action"));
			System.out.println("suppressed " + a.getSuppressed().length);
		} catch (Throwable t) {
			System.out.println("threw " + t);
		}
		try {
			RuntimeException b = new RuntimeException(new IllegalStateException("c"));
			b.addSuppressed(new IllegalStateException("action"));
			System.out.println("suppressed " + b.getSuppressed().length);
		} catch (Throwable t) {
			System.out.println("threw " + t);
		}
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
