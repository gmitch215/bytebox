package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;

/** Turns the delivered batch into Java collections once, rather than on every read. */
final class MessageBatchImpl implements MessageBatch<TSObject> {

	private final QueueBatch batch;
	private final List<Message<TSObject>> messages;

	MessageBatchImpl(QueueBatch batch) {
		this.batch = batch;
		JSArrayReader<QueueBatch.QueueMessage> delivered = batch.getMessages();
		List<Message<TSObject>> wrapped = new ArrayList<>(delivered.getLength());
		for (int i = 0; i < delivered.getLength(); i++) {
			wrapped.add(new MessageImpl(delivered.get(i)));
		}
		messages = Collections.unmodifiableList(wrapped);
	}

	@Override
	public String queue() {
		return batch.getQueue();
	}

	@Override
	public List<Message<TSObject>> messages() {
		return messages;
	}

	@Override
	public void ackAll() {
		batch.ackAll();
	}

	@Override
	public void retryAll() {
		batch.retryAll();
	}

	private static final class MessageImpl implements Message<TSObject> {

		private final QueueBatch.QueueMessage message;

		MessageImpl(QueueBatch.QueueMessage message) {
			this.message = message;
		}

		@Override
		public String id() {
			return message.getId();
		}

		@Override
		public long timestamp() {
			return (long) millis(message.getTimestamp());
		}

		@Override
		public int attempts() {
			return message.getAttempts();
		}

		@Override
		public TSObject body() {
			return message.getBody();
		}

		@Override
		public void ack() {
			message.ack();
		}

		@Override
		public void retry() {
			message.retry();
		}

		@Override
		public void retry(int delaySeconds) {
			message.retry(retryOptions(delaySeconds));
		}

		@JSBody(
			params = "date",
			script = "return date instanceof Date ? date.getTime() : Number(date);"
		)
		private static native double millis(JSObject date);

		@JSBody(params = "seconds", script = "return { delaySeconds: seconds };")
		private static native JSObject retryOptions(int seconds);
	}
}
