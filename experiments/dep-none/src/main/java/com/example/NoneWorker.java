package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** The baseline every dependency arm is measured against: same shape, no library. */
public class NoneWorker implements Worker {

	private static final String ANSWER = answer();

	static {
		System.out.println("answer=" + ANSWER);
	}

	private static String answer() {
		return "a,b,c";
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response(ANSWER);
	}
}
