package dev.gmitch215.bytebox;

import java.util.List;

/**
 * A batch of Queue messages delivered to a {@link Consumer}.
 *
 * @param <T> the decoded body type
 * @since 1.0.0
 */
public interface MessageBatch<T> {
	/** {@return the name of the queue this batch came from} */
	String queue();

	/** {@return the messages, in best-effort publication order} */
	List<Message<T>> messages();

	/** Marks every message delivered regardless of how the handler ends. */
	void ackAll();

	/** Marks every message for redelivery in a later batch. */
	void retryAll();
}
