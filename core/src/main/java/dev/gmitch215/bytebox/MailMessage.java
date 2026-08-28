package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

/**
 * Cloudflare's {@code ForwardableEmailMessage}.
 *
 * <p>{@link InboundMail} is what a handler sees. This is the JavaScript object behind it, and it is
 * the surface to reach for when something about a message is not exposed there.
 *
 * @since 1.0.0
 */
public interface MailMessage extends JSObject {
	/** {@return the envelope sender} */
	@JSProperty
	String getFrom();

	/** {@return the envelope recipient} */
	@JSProperty
	String getTo();

	/** {@return the message headers} */
	@JSProperty
	Headers getHeaders();

	/** {@return the raw MIME message, as a stream} */
	@JSProperty
	JSObject getRaw();

	/** {@return the size of the raw message in bytes} */
	@JSProperty
	int getRawSize();

	/**
	 * Refuses the message with a permanent SMTP error.
	 *
	 * @param reason returned to the sending server
	 */
	void setReject(String reason);

	/**
	 * Sends the message on to a verified destination address.
	 *
	 * @param address the destination
	 * @return settles once Cloudflare has accepted the forward
	 */
	JSPromise<JSObject> forward(String address);

	/**
	 * Answers the message in the same SMTP session.
	 *
	 * @param message the reply
	 * @return settles once Cloudflare has accepted the reply
	 */
	JSPromise<JSObject> reply(JSObject message);
}
