package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.DurableObjectNamespace;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * Routes to a Durable Object, which is where state that has to be exact belongs.
 *
 * <p>One instance per id, in one place, so two requests for the same id reach the same instance and
 * see each other's writes. That is the difference from the kv-counter sample, where two regions can
 * both read the same value and both write the next one.
 *
 * <p>The Durable Object class itself is JavaScript: it extends Cloudflare's own base class, which is
 * a JavaScript class the runtime instantiates. What Java owns is the routing and the calls.
 */
public class CounterWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		DurableObjectNamespace counters = env.durableObject("DO_COUNTER");

		// the id is derived from the path, so every path gets its own instance
		var counter = counters.byName(request.path());
		TSObject count = counter.rpc("increment");

		return Bytebox.response(request.path() + " is at " + count.asInt() + "\n");
	}
}
