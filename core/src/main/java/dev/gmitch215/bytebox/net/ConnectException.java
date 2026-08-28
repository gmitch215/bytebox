package dev.gmitch215.bytebox.net;

/**
 * A connection was refused, standing in for {@code java.net.ConnectException}.
 *
 * <p>Absent from the class library, and supplied because a library that opens a socket catches it. The
 * platform refuses a connection to its own address ranges, to localhost, to a private network address
 * and to port 25, and those are the refusals a program here will see.
 *
 * @since 1.0.0
 */
public class ConnectException extends SocketException {

	private static final long serialVersionUID = 3831404271622369812L;

	/** With no message. */
	public ConnectException() {}

	/**
	 * @param message what was refused
	 */
	public ConnectException(String message) {
		super(message);
	}
}
