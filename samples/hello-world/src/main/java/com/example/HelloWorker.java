package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

/** The smallest Worker: one handler, no bindings. */
public class HelloWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("hello world!");
	}
}
