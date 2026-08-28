package dev.gmitch215.bytebox.net;

import java.io.IOException;

/**
 * No address for a name, standing in for {@code java.net.UnknownHostException}.
 *
 * <p>Absent from the class library, and supplied because {@link InetAddress} throws it and because a
 * library that resolves a name catches it.
 *
 * @since 1.0.0
 */
public class UnknownHostException extends IOException {

	private static final long serialVersionUID = -6642436674601710697L;

	/** With no name. */
	public UnknownHostException() {}

	/**
	 * @param host the name that did not resolve
	 */
	public UnknownHostException(String host) {
		super(host);
	}
}
