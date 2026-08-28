package dev.gmitch215.bytebox.socket;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

/**
 * Reading and writing a socket's streams.
 *
 * <p>A socket's reader and writer have to be locked, used and released, and a reader left locked
 * makes every later read fail. Each of these acquires and releases in one expression so that cannot
 * be got wrong from Java.
 *
 * <p>The scripts avoid destructuring and argument spread, which the compiler's own script parser
 * refuses. A {@code @JSBody} is parsed at build time by that parser rather than by the runtime, so
 * what the platform supports is not the constraint — what the compiler reads is.
 */
final class Streams {

	private Streams() {}

	@JSBody(
		params = { "socket", "text" },
		script = "var writer = socket.writable.getWriter();" +
			" return writer.write(new TextEncoder().encode(text))" +
			"   .finally(function () { writer.releaseLock(); });"
	)
	static native JSPromise<org.teavm.jso.JSObject> write(JSSocket socket, String text);

	@JSBody(
		params = "socket",
		script = "var reader = socket.readable.getReader();" +
			" return reader.read()" +
			"   .then(function (chunk) {" +
			"     return chunk.done ? null : new TextDecoder().decode(chunk.value);" +
			"   })" +
			"   .finally(function () { reader.releaseLock(); });"
	)
	static native JSPromise<JSString> read(JSSocket socket);

	@JSBody(
		params = "socket",
		script = "var reader = socket.readable.getReader();" +
			" var decoder = new TextDecoder();" +
			" var all = '';" +
			" var pump = function () {" +
			"   return reader.read().then(function (chunk) {" +
			"     if (chunk.done) return all;" +
			"     all += decoder.decode(chunk.value, { stream: true });" +
			"     return pump();" +
			"   });" +
			" };" +
			" return pump().finally(function () { reader.releaseLock(); });"
	)
	static native JSPromise<JSString> readAll(JSSocket socket);

	@JSBody(
		params = { "socket", "delimiter" },
		script = "var reader = socket.readable.getReader();" +
			" var decoder = new TextDecoder();" +
			" var buffered = '';" +
			" var pump = function () {" +
			"   return reader.read().then(function (chunk) {" +
			"     if (chunk.done) return buffered.length > 0 ? buffered : null;" +
			"     buffered += decoder.decode(chunk.value, { stream: true });" +
			"     var at = buffered.indexOf(delimiter);" +
			"     return at < 0 ? pump() : buffered.slice(0, at);" +
			"   });" +
			" };" +
			" return pump().finally(function () { reader.releaseLock(); });"
	)
	static native JSPromise<JSString> readUntil(JSSocket socket, String delimiter);
}
