package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.StringJoiner;

public class ProbeWorker implements Worker {

	static {
		System.out.println("start");
		StringJoiner j = new StringJoiner(",");
		j.add("a").add("b");
		System.out.println("joined=" + j);
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
