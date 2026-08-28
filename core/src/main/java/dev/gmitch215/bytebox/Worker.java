package dev.gmitch215.bytebox;

/**
 * Handles HTTP requests. Implement this on the class named by the plugin's {@code handlerClass},
 * and the generated scaffold exports a {@code fetch} handler that routes to it.
 *
 * <p>The method looks synchronous and suspends underneath. Binding calls such as
 * {@code env.kv().get(key)} return a value rather than a future, because TeaVM rewrites a blocking
 * call into a continuation the host resumes. There is no parallelism on this platform: Cloudflare
 * Workers run in a single thread and the Web Worker API is unavailable, so a Java thread is a fiber
 * on the host's timer queue.
 *
 * @since 1.0.0
 */
public interface Worker {
	/**
	 * Answers one HTTP request.
	 *
	 * @param request the incoming request
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 * @return the response to send
	 */
	Response fetch(Request request, Env env, ExecutionCtx ctx);
}
