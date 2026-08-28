package dev.gmitch215.bytebox;

/**
 * Handles incoming email.
 *
 * <p>A message that reaches the end of this method with no disposition recorded is dropped by the
 * platform, so {@link InboundMail} tracks its own. Call {@link InboundMail#forward},
 * {@link InboundMail#reply} or {@link InboundMail#reject} at any point and carry on working;
 * {@link InboundMail#drop()} states that dropping the message was the intent. Returning without any
 * of them raises rather than discarding the message silently.
 *
 * @since 1.0.0
 */
public interface Mail {
	/**
	 * Handles one inbound message.
	 *
	 * @param mail the message, which records what was done to it
	 * @param env the bindings declared in the Wrangler configuration
	 * @param ctx the invocation context
	 */
	void email(InboundMail mail, Env env, ExecutionCtx ctx);
}
