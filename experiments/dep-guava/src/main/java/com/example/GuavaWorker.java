package com.example;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

public class GuavaWorker implements Worker {

	private static final String ANSWER = answer();

	static {
		System.out.println("answer=" + ANSWER);
	}

	private static String answer() {
		ImmutableList<String> parts = ImmutableList.of("a", "b", "c");
		return Joiner.on(",").join(parts);
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response(ANSWER);
	}
}
