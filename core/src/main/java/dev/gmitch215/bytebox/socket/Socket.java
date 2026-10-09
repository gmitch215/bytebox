package dev.gmitch215.bytebox.socket;

import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * One TCP connection.
 *
 * <p>Reads and writes block. Closing is what releases the connection, and the six-simultaneous-
 * connection limit every plan has makes that matter: a socket left open holds a sixth of the
 * invocation's budget. This is {@link AutoCloseable} so try-with-resources can do it.
 *
 * {@snippet lang = "java":
 * try (Socket socket = Sockets.connectTLS("example.com", 443)) {
 * 	socket.write("GET / HTTP/1.0\r\nHost: example.com\r\n\r\n");
 * 	System.out.println(socket.readAll());
 * }
 *}
 *
 * @since 1.0.0
 */
public final class Socket implements AutoCloseable {

	/** Characters held before a write is sent (64 Ki, a stream chunk, so memory stays bounded). */
	static final int MAX_PENDING = 64 * 1024;

	private final JSSocket socket;
	private final Wire wire;
	private final StringBuilder pending = new StringBuilder();

	Socket(JSSocket socket) {
		this(socket, Wire.of(socket));
	}

	Socket(JSSocket socket, Wire wire) {
		this.socket = socket;
		this.wire = wire;
	}

	/**
	 * Writes text, UTF-8 encoded.
	 *
	 * <p>The text is held and sent with the next call that reads, upgrades, closes or waits on the
	 * socket, with {@link #flush()}, or once 64 Ki characters are held, so a dialogue of short lines
	 * costs one write per exchange rather than one per line and a large upload never sits whole in
	 * memory. A write that fails is reported by that later call (or by this one, at the cap).
	 *
	 * @param text the text
	 */
	public void write(String text) {
		pending.append(text);
		if (pending.length() >= MAX_PENDING) flush();
	}

	/** Sends whatever {@link #write(String)} is holding. */
	public void flush() {
		if (pending.length() == 0) return;
		String text = pending.toString();
		pending.setLength(0);
		wire.write(text);
	}

	/**
	 * Reads whatever has arrived, up to the next chunk boundary.
	 *
	 * @return the text, or {@code null} at end of stream
	 */
	public String read() {
		flush();
		return wire.read();
	}

	/**
	 * Reads until the peer closes the connection.
	 *
	 * @return everything that arrived
	 */
	public String readAll() {
		flush();
		return wire.readAll();
	}

	/**
	 * Reads until a delimiter appears, which is how a line-oriented protocol is framed.
	 *
	 * @param delimiter the delimiter, such as {@code "\r\n"}
	 * @return the text up to but not including the delimiter, or {@code null} at end of stream
	 */
	public String readUntil(String delimiter) {
		flush();
		return wire.readUntil(delimiter);
	}

	/**
	 * Upgrades a connection opened with {@link Sockets#connectStartTLS(String, int)} to TLS.
	 *
	 * <p>Returns a new socket. The original is unusable afterwards, which is why the result has to be
	 * kept rather than discarded.
	 *
	 * @return the encrypted socket
	 */
	public Socket startTLS() {
		flush();
		return new Socket(wire.startTls());
	}

	/** {@return a promise that settles when the connection closes, for {@code ctx.waitUntil}} */
	public JSPromise<JSObject> closed() {
		flush();
		return wire.closed();
	}

	/**
	 * {@return the platform's own socket object, for anything this surface does not cover}
	 *
	 * <p>Held writes are sent first, so what is written through the object follows them.
	 */
	public JSObject unwrap() {
		flush();
		return socket;
	}

	@Override
	public void close() {
		try {
			flush();
		} finally {
			wire.close();
		}
	}
}
