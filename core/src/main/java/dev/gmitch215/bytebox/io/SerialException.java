package dev.gmitch215.bytebox.io;

/**
 * A stream that could not be read or written.
 *
 * <p>Unchecked, unlike {@code java.io}'s. Nothing here wraps a real stream, so there is no I/O to
 * fail: the failures are a malformed stream, a type with no codec, or a class identifier that does
 * not match. None of those are worth a {@code try} block in a request handler.
 *
 * @since 1.0.0
 */
public class SerialException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param message what went wrong
	 */
	public SerialException(String message) {
		super(message);
	}

	/**
	 * @param message what went wrong
	 * @param cause what caused it
	 */
	public SerialException(String message, Throwable cause) {
		super(message, cause);
	}
}
