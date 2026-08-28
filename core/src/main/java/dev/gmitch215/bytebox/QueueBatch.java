package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArrayReader;

/**
 * Cloudflare's {@code MessageBatch}.
 *
 * <p>{@link MessageBatch} is what a handler sees. This is the JavaScript object behind it.
 *
 * @since 1.0.0
 */
public interface QueueBatch extends JSObject {
	/** {@return the name of the queue this batch came from} */
	@JSProperty
	String getQueue();

	/** {@return the messages} */
	@JSProperty
	JSArrayReader<QueueMessage> getMessages();

	/** Marks every message delivered. */
	void ackAll();

	/** Marks every message for redelivery. */
	void retryAll();

	/**
	 * One message from a Queue, as JavaScript delivers it.
	 *
	 * @since 1.0.0
	 */
	interface QueueMessage extends JSObject {
		/** {@return the system-generated message id} */
		@JSProperty
		String getId();

		/** {@return when the message was sent, as a {@code Date}} */
		@JSProperty
		JSObject getTimestamp();

		/** {@return how many times delivery has been attempted} */
		@JSProperty
		int getAttempts();

		/** {@return the body, which is any structured-clone value} */
		@JSProperty
		TSObject getBody();

		/** Marks this message delivered. */
		void ack();

		/** Marks this message for redelivery. */
		void retry();

		/**
		 * Marks this message for redelivery after a delay.
		 *
		 * @param options {@code { delaySeconds }}
		 */
		void retry(JSObject options);
	}
}
