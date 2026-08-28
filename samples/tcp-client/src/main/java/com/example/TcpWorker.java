package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.socket.Socket;
import dev.gmitch215.bytebox.socket.Sockets;

/**
 * A raw TCP client over {@code cloudflare:sockets}.
 *
 * <p>What this cannot reach decides whether it is the right tool. Cloudflare refuses a connection to
 * its own IP ranges, to localhost and to private addresses, and blocks port 25. So a Worker cannot
 * SMTP to Cloudflare Email Sending even with valid credentials; the email binding is the way out.
 *
 * <p>Every plan allows six simultaneous outgoing connections, so the socket is closed rather than
 * left to the runtime. Try-with-resources is why {@code Socket} is {@code AutoCloseable}.
 */
public class TcpWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		String host = request.query("host", "example.com");

		try (Socket socket = Sockets.connectTLS(host, 443)) {
			socket.write("HEAD / HTTP/1.0\r\nHost: " + host + "\r\nConnection: close\r\n\r\n");
			String status = socket.readUntil("\r\n");
			return Bytebox.response(host + " answered " + status + "\n");
		} catch (RuntimeException refused) {
			// the refusal reads "proxy request failed, cannot connect to the specified address"
			return Bytebox.response("could not reach " + host + ": " + refused.getMessage(), 502);
		}
	}
}
