package dev.gmitch215.bytebox.locks;

/**
 * {@code java.util.concurrent.CompletionException}, which the class library does not carry.
 *
 * @since 1.0.3
 */
public class CompletionException extends RuntimeException {

	/** No message and no cause. */
	public CompletionException() {}

	/**
	 * @param message the detail
	 */
	public CompletionException(String message) {
		super(message);
	}

	/**
	 * @param message the detail
	 * @param cause the failure that ended the stage
	 */
	public CompletionException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param cause the failure that ended the stage
	 */
	public CompletionException(Throwable cause) {
		super(cause);
	}
}
