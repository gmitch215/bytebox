package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;

public class ConformanceWorker implements Worker {

	static {
		Conformance.transcript();
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		if (request.path().equals("/backtrack")) {
			Conformance.backtrack(Integer.parseInt(request.query("n", "20")));
		}
		return Bytebox.response("ok");
	}
}
