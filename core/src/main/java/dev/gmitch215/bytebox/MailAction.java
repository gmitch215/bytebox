package dev.gmitch215.bytebox;

/**
 * What a {@link Mail} handler did with a message.
 *
 * @since 1.0.0
 */
public enum MailAction {
	/** Nothing yet. A handler that returns in this state raises rather than dropping silently. */
	NONE,
	/** Sent on to a verified destination address. */
	FORWARDED,
	/** Answered in the same SMTP session. */
	REPLIED,
	/** Refused with a permanent SMTP error. */
	REJECTED,
	/** Discarded on purpose. */
	DROPPED
}
