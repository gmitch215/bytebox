package dev.gmitch215.bytebox.net;

import java.io.IOException;

/**
 * A socket failed, standing in for {@code java.net.SocketException}.
 *
 * <p>Absent from the class library, and supplied because a library that opens a socket catches it.
 *
 * @since 1.0.0
 */
public class SocketException extends IOException {

	private static final long serialVersionUID = -5935874303556886934L;

	/** With no message. */
	public SocketException() {}

	/**
	 * @param message what failed
	 */
	public SocketException(String message) {
		super(message);
	}
}
