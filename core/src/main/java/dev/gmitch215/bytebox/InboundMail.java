package dev.gmitch215.bytebox;

/**
 * An incoming email message, which records what a handler did with it.
 *
 * <p>Cloudflare drops a message that a handler returns without acting on, so this type keeps the
 * disposition rather than making the handler return one. Act at any point and carry on:
 *
 * {@snippet lang = "java":
 * public void email(InboundMail mail, Env env, ExecutionCtx ctx) {
 * 	mail.reply(acknowledgement(mail));
 * 	audit(env, mail.from());
 * 	mail.forward("inbox@example.com");
 * }
 *}
 *
 * <p>Inbound messages are capped at 25 MiB. A reply is refused unless the original passed DMARC,
 * the recipient matches the original sender, the sending domain matches the receiving domain, only
 * one reply is sent per message, and the {@code References} header holds fewer than 100 entries.
 *
 * @since 1.0.0
 */
public interface InboundMail {
	/** {@return the envelope sender} */
	String from();

	/** {@return the envelope recipient} */
	String to();

	/** {@return the message headers} */
	Headers headers();

	/** {@return the size of the raw message in bytes} */
	int rawSize();

	/** {@return the raw MIME message} */
	String raw();

	/** {@return what has been done with this message so far} */
	MailAction disposition();

	/**
	 * Sends the message on to a verified destination address.
	 *
	 * @param address the destination
	 */
	void forward(String address);

	/**
	 * Answers the message in the same SMTP session.
	 *
	 * @param mime the reply, as a raw MIME message
	 */
	void reply(String mime);

	/**
	 * Refuses the message with a permanent SMTP error.
	 *
	 * @param reason the reason, returned to the sending server
	 */
	void reject(String reason);

	/** Discards the message on purpose. */
	void drop();
}
