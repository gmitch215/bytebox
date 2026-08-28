package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * The JavaScript option objects the bindings pass through.
 *
 * <p>These live in a class because Java forbids {@code native} on an interface method, and every
 * binding surface in this package is an interface.
 */
final class Options {

	private Options() {}

	@JSBody(script = "return {};")
	static native TSObject empty();

	@JSBody(params = "seconds", script = "return { expirationTtl: seconds };")
	static native JSObject expirationTtl(int seconds);

	@JSBody(params = "metadata", script = "return { metadata: metadata };")
	static native JSObject metadata(TSObject metadata);

	@JSBody(params = "prefix", script = "return { prefix: prefix };")
	static native JSObject prefix(String prefix);

	@JSBody(
		params = { "prefix", "cursor", "limit" },
		script = "const options = { limit: limit };" +
			" if (prefix !== null) options.prefix = prefix;" +
			" if (cursor !== null) options.cursor = cursor;" +
			" return options;"
	)
	static native JSObject listing(String prefix, String cursor, int limit);

	@JSBody(params = "seconds", script = "return { delaySeconds: seconds };")
	static native JSObject delaySeconds(int seconds);

	@JSBody(params = "type", script = "return { type: type };")
	static native JSObject type(String type);

	@JSBody(
		params = { "onlyIf", "range" },
		script = "const options = {};" +
			" if (onlyIf !== null) options.onlyIf = onlyIf;" +
			" if (range !== null) options.range = range;" +
			" return options;"
	)
	static native JSObject r2Get(JSObject onlyIf, JSObject range);

	@JSBody(params = { "offset", "length" }, script = "return { offset: offset, length: length };")
	static native JSObject range(int offset, int length);

	@JSBody(
		params = { "prefix", "cursor", "limit", "delimiter" },
		script = "const options = { limit: limit };" +
			" if (prefix !== null) options.prefix = prefix;" +
			" if (cursor !== null) options.cursor = cursor;" +
			" if (delimiter !== null) options.delimiter = delimiter;" +
			" return options;"
	)
	static native JSObject r2List(String prefix, String cursor, int limit, String delimiter);

	@JSBody(
		params = { "contentType", "meta" },
		script = "const options = {};" +
			" if (contentType !== null) options.httpMetadata = { contentType: contentType };" +
			" if (meta !== null) options.customMetadata = meta;" +
			" return options;"
	)
	static native JSObject r2Put(String contentType, TSObject meta);

	/**
	 * D1's {@code bind} is variadic rather than array-taking, so the values have to be spread.
	 * Passing the array binds one value that happens to be an array, and D1 answers
	 * {@code D1_TYPE_ERROR: Type 'object' not supported}.
	 */
	@JSBody(
		params = { "statement", "values" },
		script = "return statement.bind.apply(statement, values);"
	)
	static native D1Database.D1Statement bind(
		D1Database.D1Statement statement,
		JSArray<JSObject> values
	);

	@JSBody(
		params = { "from", "to", "raw" },
		imports = @org.teavm.jso.JSBodyImport(alias = "email", fromModule = "cloudflare:email"),
		script = "return new email.EmailMessage(from, to, raw);"
	)
	static native JSObject mail(String from, String to, String raw);

	@JSBody(script = "return crypto.randomUUID();")
	static native String uuid();
}
