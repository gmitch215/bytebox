package dev.gmitch215.bytebox.net;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Byte-level reads and writes over a platform socket.
 *
 * <p>The reader and writer are taken once and held for the socket's life. A stream can only be locked
 * to one reader, so taking a new one per call throws, and releasing between calls would drop whatever
 * had been buffered.
 */
final class Bytes {

	private Bytes() {}

	@JSBody(params = "socket", script = "return socket.readable.getReader();")
	static native JSObject reader(JSObject socket);

	@JSBody(params = "socket", script = "return socket.writable.getWriter();")
	static native JSObject writer(JSObject socket);

	/** The next chunk, or {@code null} at the end of the stream. */
	@JSBody(
		params = "reader",
		script = "return reader.read().then(function (chunk) {" +
			" return chunk.done ? null : chunk.value;" +
			" });"
	)
	static native JSPromise<Uint8Array> read(JSObject reader);

	@JSBody(params = { "writer", "chunk" }, script = "return writer.write(chunk);")
	static native JSPromise<JSObject> write(JSObject writer, Uint8Array chunk);

	@JSBody(params = "writer", script = "return writer.close();")
	static native JSPromise<JSObject> close(JSObject writer);
}
