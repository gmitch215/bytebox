package dev.gmitch215.bytebox.net.http;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * Request building, asked of the platform directly.
 *
 * <p>Separate from {@link HttpClient} because that class is a substitute - the compiler renames it to
 * the one it stands in for - and a renamed class loses the annotations that turn a {@code native}
 * method into a call into JavaScript.
 */
final class Fetches {

	private Fetches() {}

	/**
	 * A request initialiser {@code fetch} accepts.
	 *
	 * @param method the method
	 * @param body the body, or null for none
	 * @param timeoutMillis how long to allow, or zero for no limit
	 * @param follow whether to follow redirects rather than return them
	 */
	@JSBody(
		params = { "method", "body", "timeoutMillis", "follow" },
		script = "var init = { method: method, headers: {}," +
			" redirect: follow ? 'follow' : 'manual' };" +
			"if (body !== null) init.body = body;" +
			"if (timeoutMillis > 0 && typeof AbortSignal.timeout === 'function') {" +
			"  init.signal = AbortSignal.timeout(timeoutMillis);" +
			"}" +
			"return init;"
	)
	static native TSObject init(String method, ArrayBuffer body, int timeoutMillis, boolean follow);

	/** Sets one request header on an initialiser. */
	@JSBody(params = { "init", "name", "value" }, script = "init.headers[name] = value;")
	static native void header(TSObject init, String name, String value);

	/** Every response header name, comma separated, so the Java side can walk them. */
	@JSBody(
		params = "headers",
		script = "var names = []; headers.forEach(function (v, k) { names.push(k); });" +
			"return names.join(',');"
	)
	static native String names(TSObject headers);
}
