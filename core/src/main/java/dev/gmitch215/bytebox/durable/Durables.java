package dev.gmitch215.bytebox.durable;

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSString;

/** The two reads the platform does not shape the way a JSO interface can describe. */
final class Durables {

	private Durables() {}

	/**
	 * The keys of a listing.
	 *
	 * <p>{@code storage.list()} resolves to a {@code Map}, not an object, so {@code Object.keys} would
	 * answer an empty list rather than fail.
	 */
	static List<String> keys(JSObject listing) {
		if (listing == null) return List.of();
		JSArrayReader<JSString> names = names(listing);
		List<String> keys = new ArrayList<>(names.getLength());
		for (int i = 0; i < names.getLength(); i++) keys.add(names.get(i).stringValue());
		return keys;
	}

	@JSBody(
		params = "listing",
		script = "if (listing instanceof Map) return Array.from(listing.keys());" +
			" return Object.keys(listing);"
	)
	private static native JSArrayReader<JSString> names(JSObject listing);

	/** The instance's identifier, which is an object with a {@code toString} rather than a string. */
	@JSBody(params = "state", script = "return state.id.toString();")
	static native String identifier(JSObject state);

	/**
	 * A query against the instance's own database.
	 *
	 * <p>{@code exec} takes its bindings as separate arguments rather than an array, and a generated
	 * script may not spread one, so the call is applied instead.
	 */
	@JSBody(
		params = { "storage", "query", "bindings" },
		script = "return storage.sql.exec.apply(storage.sql, [query].concat(bindings));"
	)
	static native SQL exec(JSObject storage, String query, JSObject bindings);
}
