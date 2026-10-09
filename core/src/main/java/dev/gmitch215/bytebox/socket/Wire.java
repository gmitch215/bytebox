package dev.gmitch215.bytebox.socket;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

/**
 * What {@link Socket} asks of the connection, so its buffering is testable without one.
 */
interface Wire {
	void write(String text);

	/** {@return the chunk, or {@code null} at end of stream} */
	String read();

	String readAll();

	/** {@return the text before the delimiter, or {@code null} at end of stream} */
	String readUntil(String delimiter);

	JSSocket startTls();

	JSPromise<JSObject> closed();

	void close();

	static Wire of(JSSocket socket) {
		return new Wire() {
			@Override
			public void write(String text) {
				Async.awaitVoid(Streams.write(socket, text));
			}

			@Override
			public String read() {
				JSString chunk = Async.await(Streams.read(socket));
				return chunk == null ? null : chunk.stringValue();
			}

			@Override
			public String readAll() {
				return Async.await(Streams.readAll(socket)).stringValue();
			}

			@Override
			public String readUntil(String delimiter) {
				JSString found = Async.await(Streams.readUntil(socket, delimiter));
				return found == null ? null : found.stringValue();
			}

			@Override
			public JSSocket startTls() {
				return socket.startTls();
			}

			@Override
			public JSPromise<JSObject> closed() {
				return socket.getClosed();
			}

			@Override
			public void close() {
				Async.awaitVoid(socket.close());
			}
		};
	}
}
