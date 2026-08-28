package dev.gmitch215.bytebox.net;

import java.io.InterruptedIOException;

/**
 * A read ran out of time, standing in for {@code java.net.SocketTimeoutException}.
 *
 * <p>Absent from the class library, and supplied because a library that reads from a socket catches
 * it. Nothing here throws it from a socket read: there is no readable clock inside a request to time a
 * read against, which {@link Socket#setSoTimeout} records. An HTTP request does have a deadline, and
 * that one the platform enforces.
 *
 * @since 1.0.0
 */
public class SocketTimeoutException extends InterruptedIOException {

	private static final long serialVersionUID = -8846654841826352300L;

	/** With no message. */
	public SocketTimeoutException() {}

	/**
	 * @param message what ran out of time
	 */
	public SocketTimeoutException(String message) {
		super(message);
	}
}
