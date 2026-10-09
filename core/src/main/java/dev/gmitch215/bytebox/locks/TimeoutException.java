package dev.gmitch215.bytebox.locks;

/**
 * {@code java.util.concurrent.TimeoutException}, which the class library does not carry.
 *
 * @since 1.0.3
 */
public class TimeoutException extends Exception {

	/** No message. */
	public TimeoutException() {}

	/**
	 * @param message what timed out
	 */
	public TimeoutException(String message) {
		super(message);
	}
}
