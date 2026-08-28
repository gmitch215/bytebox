package dev.gmitch215.bytebox.net;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * URL parsing and request building, asked of the platform directly.
 *
 * <p>Separate from {@link URL} and {@link HttpURLConnection} because those two are substitutes - the
 * compiler renames them to the classes they stand in for - and a renamed class loses the annotations
 * that turn a {@code native} method into a call into JavaScript.
 */
final class Urls {

	private Urls() {}

	/** Whether the platform can parse a spec, optionally against a base. */
	@JSBody(
		params = { "spec", "base" },
		script = "try { new URL(spec, base === null ? undefined : base); return true; }" +
			" catch (e) { return false; }"
	)
	static native boolean parses(String spec, String base);

	/**
	 * One field of a parsed URL, named as the platform names it.
	 *
	 * <p>One call per field rather than one call returning an object, because a string is what crosses
	 * the boundary cheapest and parsing again costs less than a wrapper would.
	 */
	@JSBody(
		params = { "spec", "base", "field" },
		script = "var url = new URL(spec, base === null ? undefined : base);" +
			"return String(url[field]);"
	)
	static native String field(String spec, String base, String field);

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
