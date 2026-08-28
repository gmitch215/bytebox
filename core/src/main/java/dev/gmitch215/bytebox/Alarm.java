package dev.gmitch215.bytebox;

/**
 * Handles a Durable Object alarm. An alarm invocation carries the same CPU limit as a request
 * rather than a larger one.
 *
 * @since 1.0.0
 */
public interface Alarm {
	/**
	 * Runs one alarm.
	 *
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 */
	void alarm(Env env, ExecutionCtx ctx);
}
