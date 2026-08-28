package dev.gmitch215.bytebox;

/**
 * Consumes messages from a Queue. An uncaught exception retries the whole batch; acknowledging or
 * retrying individual messages is done through {@link MessageBatch}.
 *
 * <p>A message body travels through the structured clone algorithm and is capped at 128 KB.
 *
 * @param <T> the decoded body type
 * @since 1.0.0
 */
public interface Consumer<T> {
	/**
	 * Handles one batch.
	 *
	 * @param batch the delivered messages
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 */
	void queue(MessageBatch<T> batch, Env env, ExecutionCtx ctx);
}
