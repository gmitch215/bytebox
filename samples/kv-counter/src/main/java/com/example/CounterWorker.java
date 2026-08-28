package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.KVNamespace;

/**
 * A counter in Workers KV.
 *
 * <p>The reads and writes look synchronous and suspend underneath. Nothing here returns a future,
 * because a blocking call compiles to a continuation the host resumes.
 *
 * <p>KV is eventually consistent, so two requests in different regions can both read the same value
 * and both write the next one. A counter that has to be exact belongs in a Durable Object, which the
 * durable-object sample shows.
 */
public class CounterWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		KVNamespace kv = env.kv();
		String key = "visits" + request.path();

		String current = kv.get(key);
		long next = current == null ? 1 : Long.parseLong(current) + 1;
		kv.put(key, String.valueOf(next));

		return Bytebox.response(request.path() + " has been visited " + next + " time(s)\n");
	}
}
