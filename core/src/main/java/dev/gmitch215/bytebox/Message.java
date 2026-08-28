package dev.gmitch215.bytebox;

/**
 * One message from a Queue.
 *
 * @param <T> the decoded body type
 * @since 1.0.0
 */
public interface Message<T> {
	/** {@return the system-generated message id} */
	String id();

	/** {@return when the message was sent, in milliseconds since the epoch} */
	long timestamp();

	/** {@return how many times delivery has been attempted, starting at one} */
	int attempts();

	/** {@return the decoded body} */
	T body();

	/** Marks this message delivered regardless of how the handler ends. */
	void ack();

	/** Marks this message for redelivery in a later batch. */
	void retry();

	/**
	 * Marks this message for redelivery after a delay.
	 *
	 * @param delaySeconds how long to wait before redelivering
	 */
	void retry(int delaySeconds);
}
