package dev.gmitch215.bytebox;

/**
 * Handles Cron Triggers. Cloudflare waits up to fifteen minutes for a scheduled invocation to
 * finish, which is a far larger budget than the CPU limit a request gets, and an uncaught exception
 * is logged without a retry.
 *
 * <p>Cron Triggers are limited per account rather than per Worker: five on the free plan, two
 * hundred and fifty on paid.
 *
 * @since 1.0.0
 */
public interface Scheduled {
	/**
	 * Runs one scheduled invocation.
	 *
	 * @param cron the trigger that fired
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 */
	void scheduled(Cron cron, Env env, ExecutionCtx ctx);
}
