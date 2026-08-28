package dev.gmitch215.bytebox.socket;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBodyImport;

/**
 * Raw outbound TCP, from {@code cloudflare:sockets}.
 *
 * <p>What this cannot reach is worth knowing before reaching for it. Cloudflare refuses a connection
 * to its own IP ranges, to localhost and to private network addresses, blocks port 25 outright, and
 * guards against a Worker connecting back to itself. The refusal reads
 * {@code proxy request failed, cannot connect to the specified address}.
 *
 * <p>One consequence catches people out: {@code smtp.mx.cloudflare.net} is a Cloudflare address, so
 * a Worker cannot SMTP to Cloudflare Email Sending even with valid credentials. Use the
 * {@link dev.gmitch215.bytebox.binding.EmailSender} binding or the REST API. External SMTP, IMAP and
 * POP3 hosts are reachable; only Cloudflare's own relay is not.
 *
 * {@snippet lang = "java":
 * try (Socket socket = Sockets.connect("example.com", 443)) {
 * 	socket.startTLS();
 * 	socket.write("GET / HTTP/1.0\r\nHost: example.com\r\n\r\n");
 * 	System.out.println(socket.readAll());
 * }
 *}
 *
 * @since 1.0.0
 */
public final class Sockets {

	private Sockets() {}

	/**
	 * Opens a connection.
	 *
	 * <p>Returns without waiting for the handshake. The first read or write is what surfaces a
	 * connection failure.
	 *
	 * @param hostname the host
	 * @param port the port, not 25
	 * @return the socket
	 */
	public static Socket connect(String hostname, int port) {
		return new Socket(open(hostname + ":" + port, null));
	}

	/**
	 * Opens a connection that upgrades to TLS later.
	 *
	 * <p>Needed before {@link Socket#startTLS()}, because a socket opened without it cannot be
	 * upgraded.
	 *
	 * @param hostname the host
	 * @param port the port
	 * @return the socket
	 */
	public static Socket connectStartTLS(String hostname, int port) {
		TSObject options = TSObject.object();
		options.set("secureTransport", TSObject.of("starttls"));
		return new Socket(open(hostname + ":" + port, options));
	}

	/**
	 * Opens a connection that is TLS from the first byte.
	 *
	 * @param hostname the host
	 * @param port the port
	 * @return the socket
	 */
	public static Socket connectTLS(String hostname, int port) {
		TSObject options = TSObject.object();
		options.set("secureTransport", TSObject.of("on"));
		return new Socket(open(hostname + ":" + port, options));
	}

	@JSBody(
		params = { "address", "options" },
		imports = @JSBodyImport(alias = "sockets", fromModule = "cloudflare:sockets"),
		script = "return options === null" +
			" ? sockets.connect(address)" +
			" : sockets.connect(address, options);"
	)
	private static native JSSocket open(String address, TSObject options);
}
