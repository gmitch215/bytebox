package dev.gmitch215.bytebox;

import java.util.List;

/**
 * Receives trace events from another Worker. A Tail Worker is deployed separately and named in the
 * producing Worker's {@code tail_consumers}.
 *
 * @since 1.0.0
 */
public interface Tail {
	/**
	 * Handles one batch of trace events.
	 *
	 * @param events the traces
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 */
	void tail(List<TraceItem> events, Env env, ExecutionCtx ctx);
}
