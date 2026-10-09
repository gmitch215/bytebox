package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

public class SocketWriteWorker implements Worker {

	@JSBody(script = "return { writable: new WritableStream({ write: function () {} }) };")
	private static native JSObject fakeSocket();

	@JSBody(
		params = { "socket", "text" },
		script = "var writer = socket.writable.getWriter();" +
			" return writer.write(new TextEncoder().encode(text))" +
			"   .finally(function () { writer.releaseLock(); });"
	)
	private static native JSPromise<JSObject> writeEach(JSObject socket, String text);

	@JSBody(params = "socket", script = "return socket.writable.getWriter();")
	private static native JSObject hoist(JSObject socket);

	@JSBody(
		params = { "writer", "text" },
		script = "return writer.write(new TextEncoder().encode(text));"
	)
	private static native JSPromise<JSObject> writeHoisted(JSObject writer, String text);

	@JSBody(params = "writer", script = "writer.releaseLock();")
	private static native void release(JSObject writer);

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		String path = request.path();
		int n = Integer.parseInt(request.query("n", "2000"));
		JSObject socket = fakeSocket();
		if (path.equals("/a")) {
			for (int i = 0; i < n; i++) Async.awaitVoid(writeEach(socket, "MAIL FROM:<a@b.c>\r\n"));
		} else if (path.equals("/b")) {
			JSObject writer = hoist(socket);
			for (int i = 0; i < n; i++) Async.awaitVoid(
				writeHoisted(writer, "MAIL FROM:<a@b.c>\r\n")
			);
			release(writer);
		} else if (path.equals("/c")) {
			StringBuilder buffer = new StringBuilder();
			for (int i = 0; i < n; i++) buffer.append("MAIL FROM:<a@b.c>\r\n");
			Async.awaitVoid(writeEach(socket, buffer.toString()));
		} else if (path.equals("/smtp")) {
			try (
				dev.gmitch215.bytebox.socket.Socket smtp =
					dev.gmitch215.bytebox.socket.Sockets.connect(
						request.query("host", "127.0.0.1"),
						Integer.parseInt(request.query("port", "3025"))
					)
			) {
				String greeting = smtp.readUntil("\r\n");
				smtp.write("EHLO bb103\r\n");
				smtp.write("NOOP\r\n");
				smtp.write("QUIT\r\n");
				return Bytebox.response(greeting + "\n" + smtp.readAll());
			}
		} else {
			return Bytebox.response("ok\n");
		}
		return Bytebox.response(path.substring(1) + " " + n + "\n");
	}
}
