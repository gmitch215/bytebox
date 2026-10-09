package dev.gmitch215.bytebox.security;

import java.security.GeneralSecurityException;

/**
 * {@code java.security.DigestException}, which the class library does not carry.
 *
 * @since 1.0.3
 */
public class DigestException extends GeneralSecurityException {

	public DigestException() {}

	/**
	 * @param message what went wrong
	 */
	public DigestException(String message) {
		super(message);
	}

	/**
	 * @param message what went wrong
	 * @param cause what caused it
	 */
	public DigestException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause what caused it
	 */
	public DigestException(Throwable cause) {
		super(cause);
	}
}
