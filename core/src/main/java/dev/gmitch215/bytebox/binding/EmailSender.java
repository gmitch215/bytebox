package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * An email sending binding. Declared with {@code email()}, named {@code EMAIL} by default.
 *
 * <p>Separate from the {@link dev.gmitch215.bytebox.Mail} handler: that receives, this sends. The
 * destination has to be verified in the Cloudflare dashboard, and the sending domain has to be one
 * the account controls.
 *
 * <p>SMTP is not an alternative. Cloudflare's own relay sits on a Cloudflare IP, and
 * {@code cloudflare:sockets} refuses to connect to those, so a Worker cannot SMTP to Cloudflare
 * Email Sending even with valid credentials. This binding and the REST API are the two ways out.
 *
 * @since 1.0.0
 */
public interface EmailSender extends JSObject {
	/**
	 * Sends a message.
	 *
	 * @param from the sender, on a domain the account controls
	 * @param to the recipient, verified in the dashboard
	 * @param mime the message as raw MIME
	 */
	default void send(String from, String to, String mime) {
		Async.awaitVoid(sendMessage(Options.mail(from, to, mime)));
	}

	/**
	 * Sends a plain-text message, assembling the MIME.
	 *
	 * @param from the sender
	 * @param to the recipient
	 * @param subject the subject
	 * @param body the body
	 */
	default void send(String from, String to, String subject, String body) {
		send(from, to, mime(from, to, subject, body));
	}

	@JSMethod("send")
	JSPromise<JSObject> sendMessage(JSObject message);

	/**
	 * The smallest MIME message a mail server will accept, which is five headers and a body.
	 *
	 * <p>A {@code Message-ID} is required and its domain has to match the sender's, so it is derived
	 * from the sender. Its unique part comes from the platform's random source rather than a clock,
	 * because the clock is pinned between I/O and two messages in one request would collide.
	 */
	private static String mime(String from, String to, String subject, String body) {
		String domain = from.substring(from.indexOf('@') + 1);
		return (
			"From: " +
			from +
			"\r\n" +
			"To: " +
			to +
			"\r\n" +
			"Subject: " +
			subject +
			"\r\n" +
			"Message-ID: <" +
			Options.uuid() +
			"@" +
			domain +
			">\r\n" +
			"Content-Type: text/plain; charset=utf-8\r\n" +
			"MIME-Version: 1.0\r\n" +
			"\r\n" +
			body +
			"\r\n"
		);
	}
}
