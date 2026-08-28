package dev.gmitch215.bytebox.socket;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

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

	private final JSSocket socket;

	Socket(JSSocket socket) {
		this.socket = socket;
	}

	/**
	 * Writes text, UTF-8 encoded.
	 *
	 * @param text the text
	 */
	public void write(String text) {
		Async.awaitVoid(Streams.write(socket, text));
	}

	/**
	 * Reads whatever has arrived, up to the next chunk boundary.
	 *
	 * @return the text, or {@code null} at end of stream
	 */
	public String read() {
		JSString chunk = Async.await(Streams.read(socket));
		return chunk == null ? null : chunk.stringValue();
	}

	/**
	 * Reads until the peer closes the connection.
	 *
	 * @return everything that arrived
	 */
	public String readAll() {
		return Async.await(Streams.readAll(socket)).stringValue();
	}

	/**
	 * Reads until a delimiter appears, which is how a line-oriented protocol is framed.
	 *
	 * @param delimiter the delimiter, such as {@code "\r\n"}
	 * @return the text up to but not including the delimiter, or {@code null} at end of stream
	 */
	public String readUntil(String delimiter) {
		JSString found = Async.await(Streams.readUntil(socket, delimiter));
		return found == null ? null : found.stringValue();
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
		return new Socket(socket.startTLS());
	}

	/** {@return a promise that settles when the connection closes, for {@code ctx.waitUntil}} */
	public JSPromise<JSObject> closed() {
		return socket.getClosed();
	}

	/** {@return the platform's own socket object, for anything this surface does not cover} */
	public JSObject unwrap() {
		return socket;
	}

	@Override
	public void close() {
		Async.awaitVoid(socket.close());
	}
}
