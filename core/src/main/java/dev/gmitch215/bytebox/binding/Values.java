package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSObject;

/**
 * Turns ordinary Java values into the JavaScript ones a binding takes.
 *
 * <p>{@link TSObject#from(Object)} does the general conversion. This adds the two rules D1 imposes on
 * top of it: a boolean is a number, because SQLite has no boolean type, and a {@code byte[]} is a
 * blob rather than an array of numbers.
 */
final class Values {

	private Values() {}

	static JSObject toJs(Object value) {
		if (value == null) return null;
		if (value instanceof Boolean flag) return TSObject.of(flag ? 1 : 0);
		if (value instanceof byte[] bytes) return blob(bytes);
		return TSObject.from(value);
	}

	/** SQLite blobs arrive as an array of unsigned bytes. */
	private static TSObject blob(byte[] bytes) {
		TSObject array = TSObject.array();
		for (byte value : bytes) array.push(TSObject.of(value & 0xFF));
		return array;
	}
}
