package dev.gmitch215.bytebox;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSString;

/**
 * Query string parsing, done by the platform's own URL parser.
 *
 * <p>Splitting a query string by hand gets percent-decoding, {@code +} for space, repeated keys and
 * empty values wrong in four separate ways, and the runtime already has a parser that does not.
 */
final class Queries {

	private Queries() {}

	@JSBody(params = { "url", "name" }, script = "return new URL(url).searchParams.get(name);")
	static native JSString get(String url, String name);

	@JSBody(params = "url", script = "return new URL(url).pathname;")
	static native String path(String url);

	@JSBody(params = "url", script = "return new URL(url).host;")
	static native String host(String url);

	@JSBody(params = "url", script = "return new URL(url).origin;")
	static native String origin(String url);
}
