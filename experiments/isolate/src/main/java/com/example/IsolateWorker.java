package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import java.util.ArrayList;
import java.util.List;

/**
 * Allocates Java heap until the isolate refuses, to find where 128 MB falls for a compiled program.
 *
 * <p>A Java array on this target is a WebAssembly GC array, so it lives in the host engine's
 * collected heap rather than in linear memory. That is the heap the isolate limit applies to.
 */
public class IsolateWorker implements Worker {

	private static final int CHUNK = 1024 * 1024;

	/** Survives between requests, which is how the cumulative ceiling is reached. */
	private static final List<byte[]> RETAINED = new ArrayList<>();

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		int mb = Integer.parseInt(request.query("mb", "1"));
		boolean retain = "retain".equals(request.query("mode", "transient"));

		List<byte[]> held = retain ? RETAINED : new ArrayList<>();
		long before = held.size();
		long sum = 0;
		boolean touchPages = "page".equals(request.query("touch", "ends"));
		for (int i = 0; i < mb; i++) {
			byte[] chunk = new byte[CHUNK];
			if (touchPages) {
				// one write per 4 KiB forces the engine to commit the page without the cost of
				// writing all of it, which the CPU budget would not allow
				for (int at = 0; at < CHUNK; at += 4096) chunk[at] = (byte) i;
				sum += chunk[0];
			} else {
				chunk[0] = (byte) i;
				chunk[CHUNK - 1] = (byte) i;
				sum += chunk[0] + chunk[CHUNK - 1];
			}
			held.add(chunk);
		}
		String answer =
			"asked=" +
			mb +
			"MiB mode=" +
			(retain ? "retain" : "transient") +
			" touch=" +
			(touchPages ? "page" : "ends") +
			" heldBefore=" +
			before +
			" heldNow=" +
			held.size() +
			" totalRetained=" +
			RETAINED.size() +
			"MiB sum=" +
			sum;
		if (!retain) held.clear();
		return Bytebox.response(answer + "\n");
	}
}
