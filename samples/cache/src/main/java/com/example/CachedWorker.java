package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.cache.Cache;
import dev.gmitch215.bytebox.cache.Caches;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * Answers from the Workers cache when it can.
 *
 * <p>The first request for a URL computes the answer and returns {@code X-Cache: MISS}. The write
 * goes through {@code ctx.waitUntil}, so the response does not wait for it. A later request for the
 * same URL, in the same data center, returns {@code X-Cache: HIT} with the stored body.
 *
 * <p>The cache needs no binding and stores a response only for a {@code GET} request. It is local to
 * one data center, so a request routed elsewhere is a miss again.
 */
public class CachedWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		Cache cache = Caches.defaultCache();

		Response stored = cache.match(request);
		if (stored != null) return answer(stored.text(), "HIT");

		Response fresh = answer(compute(request.path()), "MISS");
		fresh.getHeaders().set("Cache-Control", "public, max-age=60");
		ctx.waitUntil(cache.putAsync(request, fresh.copy()));
		return fresh;
	}

	private static Response answer(String body, String cache) {
		TSObject headers = TSObject.object();
		headers.set("X-Cache", TSObject.of(cache));
		return Bytebox.response(body, 200, headers);
	}

	private static String compute(String path) {
		long sum = 0;
		for (int i = 1; i <= 10_000; i++) sum += (long) i * path.length();
		return path + " computed at " + System.currentTimeMillis() + ", sum " + sum + "\n";
	}
}
