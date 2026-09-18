package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.js.Bytes;

/** Stages a byte[] through the interop heap, which is what minDirectBuffersSize sizes. */
public class MemoryWorker implements Worker {

	static {
		int size = Integer.getInteger("bytebox.crossing", 4096);
		byte[] payload = new byte[size];
		for (int i = 0; i < size; i++) payload[i] = (byte) i;
		System.out.println("crossed " + Bytes.toView(payload).getLength() + " of " + size);
	}

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("ok");
	}
}
