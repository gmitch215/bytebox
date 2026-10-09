package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.DurableObjectNamespace;

/**
 * Routes to a {@link Counter} Durable Object, which is where state that has to be exact belongs.
 *
 * <p>One instance per id, in one place, so two requests for the same id reach the same instance and
 * see each other's writes. That is the difference from the kv-counter sample, where two regions can
 * both read the same value and both write the next one.
 *
 * <p>Both classes are Java: the plugin exports {@code Counter} to the platform from the
 * {@code durableObjects} declaration and writes the migration that creates it.
 */
public class CounterWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		DurableObjectNamespace counters = env.durableObject("DO_COUNTER");

		// the id is derived from the path, so every path gets its own instance
		String count = counters.byName(request.path()).fetch("https://counter.internal/").text();

		return Bytebox.response(request.path() + " is at " + count + "\n");
	}
}
