package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.StubHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * Forwarding and replying are absent here: both wait on a promise, which needs a runtime. What the
 * handler can get wrong before either is reached is the disposition and the two limits Cloudflare
 * enforces on a reply, and those are checked before anything is sent.
 */
@DisplayName("an inbound message")
class InboundMailTest {

	@Test
	@DisplayName("reads what the platform delivered")
	void fields() {
		StubMessage message = new StubMessage();
		message.headers.set("Subject", "hello");
		InboundMailImpl mail = new InboundMailImpl(message);

		assertEquals("sender@example.com", mail.from());
		assertEquals("inbox@example.com", mail.to());
		assertEquals(2048, mail.rawSize());
		assertEquals("hello", mail.headers().get("Subject"));
		assertSame(message.headers, mail.headers());
	}

	@Test
	@DisplayName("records nothing until the handler acts, which is what raises a dropped message")
	void startsUnacted() {
		assertEquals(MailAction.NONE, new InboundMailImpl(new StubMessage()).disposition());
	}

	@Test
	@DisplayName("records a rejection, and passes the reason to the platform")
	void rejects() {
		StubMessage message = new StubMessage();
		InboundMailImpl mail = new InboundMailImpl(message);

		mail.reject("no thanks");

		assertEquals(MailAction.REJECTED, mail.disposition());
		assertEquals("no thanks", message.rejected);
	}

	@Test
	@DisplayName("records a deliberate drop, which is how a handler says the silence was meant")
	void drops() {
		InboundMailImpl mail = new InboundMailImpl(new StubMessage());

		mail.drop();

		assertEquals(MailAction.DROPPED, mail.disposition());
	}

	@Test
	@DisplayName("refuses a reply past a hundred References entries, which Cloudflare refuses too")
	void referencesLimit() {
		StubMessage message = new StubMessage();
		message.headers.set("References", chain(100));
		InboundMailImpl mail = new InboundMailImpl(message);

		IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
			mail.reply("From: a@example.com")
		);

		assertTrue(failure.getMessage().contains("100 entries"), failure.getMessage());
	}

	@Test
	@DisplayName("counts the entries rather than the header's length")
	void underTheLimit() {
		StubMessage message = new StubMessage();
		message.headers.set("References", chain(99));

		// under the limit the reply goes on to be sent, which is what needs a runtime; what matters
		// here is that the refusal is not what stopped it
		Throwable failure = assertThrows(Throwable.class, () ->
			new InboundMailImpl(message).reply("From: a@b.com")
		);

		assertFalse(failure instanceof IllegalStateException, String.valueOf(failure));
	}

	@Test
	@DisplayName("lets a reply through when the message carries no References at all")
	void noReferences() {
		Throwable failure = assertThrows(Throwable.class, () ->
			new InboundMailImpl(new StubMessage()).reply("From: a@b.com")
		);

		assertFalse(failure instanceof IllegalStateException, String.valueOf(failure));
	}

	private static String chain(int count) {
		StringBuilder references = new StringBuilder();
		for (int i = 0; i < count; i++) references.append("<message-").append(i).append("@x> ");
		return references.toString();
	}

	private static final class StubMessage implements MailMessage {

		final StubHeaders headers = new StubHeaders();
		String rejected;

		@Override
		public String getFrom() {
			return "sender@example.com";
		}

		@Override
		public String getTo() {
			return "inbox@example.com";
		}

		@Override
		public Headers getHeaders() {
			return headers;
		}

		@Override
		public JSObject getRaw() {
			return null;
		}

		@Override
		public int getRawSize() {
			return 2048;
		}

		@Override
		public void setReject(String reason) {
			rejected = reason;
		}

		@Override
		public JSPromise<JSObject> forward(String address) {
			throw new UnsupportedOperationException("forwarding needs a real promise");
		}

		@Override
		public JSPromise<JSObject> reply(JSObject message) {
			throw new UnsupportedOperationException("replying needs a real promise");
		}
	}
}
