package com.example;

import com.google.gson.Gson;
import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

public class LibWorker implements Worker {

	private static final String ANSWER = answer();

	static {
		System.out.println("answer=" + ANSWER);
	}

	private static String answer() {
		return new Gson().toJson(new int[] { 1, 2, 3 });
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response(ANSWER);
	}
}
