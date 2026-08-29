package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.Value;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;

/**
 * The timestamp is absent here: it reads a JavaScript {@code Date} through a body with no Java in it,
 * so the workerd lane is where that one is checked.
 */
@DisplayName("a delivered queue batch")
class MessageBatchTest {

	@Test
	@DisplayName("names the queue it came from")
	void queue() {
		assertEquals("orders", new MessageBatchImpl(batch("orders")).queue());
	}

	@Test
	@DisplayName("wraps every delivered message, in the order they arrived")
	void messages() {
		StubMessage first = new StubMessage("a");
		StubMessage second = new StubMessage("b");

		List<Message<TSObject>> messages = new MessageBatchImpl(
			batch("orders", first, second)
		).messages();

		assertEquals(2, messages.size());
		assertEquals("a", messages.get(0).id());
		assertEquals("b", messages.get(1).id());
	}

	@Test
	@DisplayName("hands back a list nothing can add to, because the batch is what was delivered")
	void unmodifiable() {
		MessageBatchImpl batch = new MessageBatchImpl(batch("orders", new StubMessage("a")));

		assertThrows(UnsupportedOperationException.class, () -> batch.messages().clear());
	}

	@Test
	@DisplayName("converts the batch once rather than on every read")
	void convertsOnce() {
		MessageBatchImpl batch = new MessageBatchImpl(batch("orders", new StubMessage("a")));

		assertSame(batch.messages(), batch.messages());
		assertSame(batch.messages().get(0), batch.messages().get(0));
	}

	@Test
	@DisplayName("reads each message's own fields")
	void fields() {
		StubMessage message = new StubMessage("a");
		message.attempts = 3;
		message.body = Value.of("payload");

		Message<TSObject> wrapped = new MessageBatchImpl(batch("orders", message))
			.messages()
			.get(0);

		assertEquals("a", wrapped.id());
		assertEquals(3, wrapped.attempts());
		assertEquals("payload", wrapped.body().asString());
	}

	@Test
	@DisplayName("passes an acknowledgement through to the message it belongs to")
	void acknowledges() {
		StubMessage first = new StubMessage("a");
		StubMessage second = new StubMessage("b");
		MessageBatchImpl batch = new MessageBatchImpl(batch("orders", first, second));

		batch.messages().get(0).ack();
		batch.messages().get(1).retry();

		assertEquals(List.of("ack"), first.calls);
		assertEquals(List.of("retry"), second.calls);
	}

	@Test
	@DisplayName("carries an empty batch rather than refusing one")
	void empty() {
		assertTrue(new MessageBatchImpl(batch("orders")).messages().isEmpty());
	}

	@Test
	@DisplayName("acknowledges or retries the whole batch in one call")
	void wholeBatch() {
		StubBatch delivered = batch("orders", new StubMessage("a"));
		MessageBatchImpl batch = new MessageBatchImpl(delivered);

		batch.ackAll();
		batch.retryAll();

		assertEquals(List.of("ackAll", "retryAll"), delivered.calls);
	}

	private static StubBatch batch(String queue, StubMessage... messages) {
		return new StubBatch(queue, List.of(messages));
	}

	private static final class StubBatch implements QueueBatch {

		private final String queue;
		private final List<StubMessage> messages;
		final List<String> calls = new ArrayList<>();

		StubBatch(String queue, List<StubMessage> messages) {
			this.queue = queue;
			this.messages = messages;
		}

		@Override
		public String getQueue() {
			return queue;
		}

		@Override
		public JSArrayReader<QueueMessage> getMessages() {
			return new JSArrayReader<>() {
				@Override
				public int getLength() {
					return messages.size();
				}

				@Override
				public QueueMessage get(int index) {
					return messages.get(index);
				}
			};
		}

		@Override
		public void ackAll() {
			calls.add("ackAll");
		}

		@Override
		public void retryAll() {
			calls.add("retryAll");
		}
	}

	private static final class StubMessage implements QueueBatch.QueueMessage {

		private final String id;
		int attempts = 1;
		TSObject body = Value.object();
		final List<String> calls = new ArrayList<>();

		StubMessage(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public JSObject getTimestamp() {
			return null;
		}

		@Override
		public int getAttempts() {
			return attempts;
		}

		@Override
		public TSObject getBody() {
			return body;
		}

		@Override
		public void ack() {
			calls.add("ack");
		}

		@Override
		public void retry() {
			calls.add("retry");
		}

		@Override
		public void retry(JSObject options) {
			throw new UnsupportedOperationException("a delay needs a real options object");
		}
	}
}
