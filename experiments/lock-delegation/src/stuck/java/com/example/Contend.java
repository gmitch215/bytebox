package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.concurrent.CompletableFuture;

/** A constructor that waits for a future nothing completes, so module start never finishes. */
public class Contend implements Worker {

	public Contend() {
		new CompletableFuture<String>().join();
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("unreachable");
	}
}
