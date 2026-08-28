package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Holds the disposition the JavaScript message does not track. */
final class InboundMailImpl implements InboundMail {

	private final MailMessage message;
	private MailAction disposition = MailAction.NONE;

	InboundMailImpl(MailMessage message) {
		this.message = message;
	}

	@Override
	public String from() {
		return message.getFrom();
	}

	@Override
	public String to() {
		return message.getTo();
	}

	@Override
	public Headers headers() {
		return message.getHeaders();
	}

	@Override
	public int rawSize() {
		return message.getRawSize();
	}

	@Override
	public String raw() {
		return Async.await(readStream(message.getRaw())).stringValue();
	}

	@Override
	public MailAction disposition() {
		return disposition;
	}

	@Override
	public void forward(String address) {
		Async.awaitVoid(message.forward(address));
		disposition = MailAction.FORWARDED;
	}

	@Override
	public void reply(String mime) {
		requireReplyable();
		// a reply is sent from the address that received the original, back to whoever sent it;
		// Cloudflare refuses one whose sender does not match the receiving domain
		Async.awaitVoid(message.reply(emailMessage(to(), from(), mime)));
		disposition = MailAction.REPLIED;
	}

	@Override
	public void reject(String reason) {
		message.setReject(reason);
		disposition = MailAction.REJECTED;
	}

	@Override
	public void drop() {
		disposition = MailAction.DROPPED;
	}

	/**
	 * Cloudflare refuses a second reply and refuses one past a hundred {@code References} entries,
	 * so both are checked here rather than surfacing as an opaque platform failure.
	 */
	private void requireReplyable() {
		if (disposition == MailAction.REPLIED) {
			throw new IllegalStateException("a message can only be replied to once");
		}
		String references = headers().get("References");
		if (references != null && countReferences(references) >= 100) {
			throw new IllegalStateException(
				"the References header already holds 100 entries, which is the reply limit"
			);
		}
	}

	private static int countReferences(String references) {
		int count = 0;
		for (int i = 0; i < references.length(); i++) {
			if (references.charAt(i) == '<') count++;
		}
		return count;
	}

	@JSBody(params = "stream", script = "return new Response(stream).text();")
	private static native org.teavm.jso.core.JSPromise<org.teavm.jso.core.JSString> readStream(
		JSObject stream
	);

	@JSBody(
		params = { "from", "to", "raw" },
		imports = @org.teavm.jso.JSBodyImport(alias = "email", fromModule = "cloudflare:email"),
		script = "return new email.EmailMessage(from, to, raw);"
	)
	private static native JSObject emailMessage(String from, String to, String raw);
}
