package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** Runs the future scenarios on a fiber and prints one line each. */
public class Contend implements Worker {

	static {
		new Thread(() -> {
			for (String line : CompletableFutureScenarios.run()) System.out.println(line);
		}).start();
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
