package dev.gmitch215.bytebox.net;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.socket.Sockets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.teavm.jso.JSObject;

/**
 * A TCP socket, standing in for {@code java.net.Socket}.
 *
 * <p>The compiler substitutes {@code java.net.Socket} for this one, so a library that opens a socket
 * the ordinary way works unchanged. Underneath it is {@code cloudflare:sockets}, and its limits are the
 * platform's: no connection to Cloudflare's own address ranges, to localhost or to a private network
 * address, port 25 blocked, and six simultaneous outgoing connections.
 *
 * <p>Reads block the way they do on a JVM, which here means suspending the fiber rather than an OS
 * thread. Nothing else runs during a read either way.
 *
 * {@snippet lang = "java":
 * try (Socket socket = new Socket("example.com", 80)) {
 * 	socket.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes());
 * 	socket.getInputStream().transferTo(System.out);
 * }
 *}
 *
 * <p>What is not here, because the platform has none of it: binding a local address, a server socket,
 * datagrams, and the socket options that describe an OS buffer. A program reaching for one of those
 * fails to compile, which is where it should fail.
 *
 * @since 1.0.0
 */
public class Socket implements java.io.Closeable {

	private final dev.gmitch215.bytebox.socket.Socket socket;
	private final String host;
	private final int port;

	private SocketInput input;
	private SocketOutput output;
	private boolean closed;
	private int timeout;

	/**
	 * Opens a connection.
	 *
	 * <p>Returns without waiting for the handshake, which is what the platform's own API does. The
	 * first read or write surfaces a connection failure.
	 *
	 * @param host the host
	 * @param port the port
	 */
	public Socket(String host, int port) {
		this.host = host;
		this.port = port;
		this.socket = Sockets.connect(host, port);
	}

	/**
	 * Opens a connection, with or without TLS.
	 *
	 * @param host the host
	 * @param port the port
	 * @param secure whether to start TLS straight away
	 */
	public Socket(String host, int port, boolean secure) {
		this.host = host;
		this.port = port;
		this.socket = secure ? Sockets.connectTLS(host, port) : Sockets.connect(host, port);
	}

	/**
	 * {@return the stream to read from}
	 *
	 * <p>The same stream every time: a socket has one, and handing out a second would split the bytes
	 * between them.
	 *
	 * @throws IOException if the socket is closed
	 */
	public InputStream getInputStream() throws IOException {
		if (closed) throw new IOException("the socket is closed");
		if (input == null) input = new SocketInput(Bytes.reader(raw()));
		return input;
	}

	/**
	 * {@return the stream to write to}
	 *
	 * @throws IOException if the socket is closed
	 */
	public OutputStream getOutputStream() throws IOException {
		if (closed) throw new IOException("the socket is closed");
		if (output == null) output = new SocketOutput(Bytes.writer(raw()));
		return output;
	}

	/** {@return the host this socket was opened to} */
	public String getHost() {
		return host;
	}

	/**
	 * {@return the address this socket was opened to}
	 *
	 * <p>Built from the host rather than resolved: the platform connects by name and never hands back
	 * a peer address, so a reverse lookup would have nothing to look up.
	 */
	public InetAddress getInetAddress() {
		try {
			return InetAddress.getByName(host);
		} catch (UnknownHostException unresolvable) {
			return null;
		}
	}

	/** {@return the port this socket was opened to} */
	public int getPort() {
		return port;
	}

	/** {@return whether the socket has been closed} */
	public boolean isClosed() {
		return closed;
	}

	/** {@return whether the socket was opened and has not been closed} */
	public boolean isConnected() {
		return !closed;
	}

	/**
	 * Records a read timeout.
	 *
	 * <p>Recorded and reported, not enforced. A read here waits on a promise the runtime settles, and
	 * there is no readable clock inside a request to time it against, so a timeout that appeared to
	 * work would be a lie. {@link #getSoTimeout} answers what was set so a library that reads its own
	 * setting back keeps working.
	 *
	 * @param milliseconds the timeout
	 */
	public void setSoTimeout(int milliseconds) {
		timeout = milliseconds;
	}

	/** {@return the timeout that was set, which is not enforced} */
	public int getSoTimeout() {
		return timeout;
	}

	/**
	 * Starts TLS on this connection.
	 *
	 * <p>Not a {@code java.net.Socket} method. A JVM program would wrap the socket in an
	 * {@code SSLSocket}; here the platform upgrades the connection in place.
	 *
	 * @return a socket speaking TLS
	 */
	public Socket startTLS() {
		return new Wrapped(host, port, socket.startTLS());
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		if (output != null) output.finish();
		socket.close();
	}

	private JSObject raw() {
		return socket.unwrap();
	}

	/** A socket that already exists, which is what {@link #startTLS} produces. */
	private static final class Wrapped extends Socket {

		private Wrapped(String host, int port, dev.gmitch215.bytebox.socket.Socket upgraded) {
			super(host, port, upgraded);
		}
	}

	/**
	 * @param host the host
	 * @param port the port
	 * @param existing an already-open platform socket
	 */
	protected Socket(String host, int port, dev.gmitch215.bytebox.socket.Socket existing) {
		this.host = host;
		this.port = port;
		this.socket = existing;
	}

	/** Reads bytes off the socket, buffering whatever a chunk delivered past what was asked for. */
	private static final class SocketInput extends InputStream {

		private final JSObject reader;
		private byte[] pending = new byte[0];
		private int at;
		private boolean ended;

		private SocketInput(JSObject reader) {
			this.reader = reader;
		}

		@Override
		public int read() throws IOException {
			if (!fill()) return -1;
			return pending[at++] & 0xFF;
		}

		@Override
		public int read(byte[] into, int offset, int length) throws IOException {
			if (length == 0) return 0;
			if (!fill()) return -1;
			int taken = Math.min(length, pending.length - at);
			System.arraycopy(pending, at, into, offset, taken);
			at += taken;
			return taken;
		}

		@Override
		public int available() {
			return pending.length - at;
		}

		/** Whether a byte is ready, waiting for the next chunk when the buffer is spent. */
		private boolean fill() {
			while (at >= pending.length) {
				if (ended) return false;
				org.teavm.jso.typedarrays.Uint8Array chunk = Async.await(Bytes.read(reader));
				if (chunk == null) {
					ended = true;
					return false;
				}
				pending = dev.gmitch215.bytebox.js.Bytes.fromView(chunk);
				at = 0;
			}
			return true;
		}
	}

	/** Writes bytes to the socket, one chunk per call. */
	private static final class SocketOutput extends OutputStream {

		private final JSObject writer;

		private SocketOutput(JSObject writer) {
			this.writer = writer;
		}

		@Override
		public void write(int one) {
			write(new byte[] { (byte) one }, 0, 1);
		}

		@Override
		public void write(byte[] from, int offset, int length) {
			byte[] chunk = new byte[length];
			System.arraycopy(from, offset, chunk, 0, length);
			Async.awaitVoid(
				Bytes.write(writer, dev.gmitch215.bytebox.js.Bytes.toUnsignedView(chunk))
			);
		}

		private void finish() {
			Async.awaitVoid(Bytes.close(writer));
		}
	}
}
