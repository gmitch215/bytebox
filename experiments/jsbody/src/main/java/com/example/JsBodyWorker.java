package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

public class JsBodyWorker implements Worker {

	@JSBody(script = "return 1;")
	private static native int other();

	@JSBody(script = "var n = 1n; return Number(n);")
	private static native int probe();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("probe=" + probe() + other());
	}
}
