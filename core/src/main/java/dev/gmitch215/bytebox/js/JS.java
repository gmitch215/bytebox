package dev.gmitch215.bytebox.js;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * The JavaScript expressions {@link TSObject}'s default methods are built from.
 *
 * <p>These live in a class because Java forbids {@code native} on an interface method, and
 * {@code @JSBody} needs one.
 *
 * <p>One rule governs everything numeric here. A Java {@code long} crosses the boundary as a
 * {@code BigInt}, and every other numeric type crosses as a {@code Number} — {@code char} included,
 * which arrives as its UTF-16 code unit rather than as a string. So a body declared to return
 * {@code long} has to produce a {@code BigInt}: returning a plain number from one raises
 * {@code TypeError: Cannot convert 42 to a BigInt}, which is why the coercions below are explicit.
 */
final class JS {

	private JS() {}

	@JSBody(params = "value", script = "return typeof value;")
	static native String typeOf(JSObject value);

	@JSBody(params = "value", script = "return value === null || value === undefined;")
	static native boolean isNullish(JSObject value);

	@JSBody(params = "value", script = "return value === undefined;")
	static native boolean isUndefined(JSObject value);

	@JSBody(params = "value", script = "return Array.isArray(value);")
	static native boolean isArray(JSObject value);

	@JSBody(params = "value", script = "return typeof value === 'number';")
	static native boolean isNumber(JSObject value);

	@JSBody(params = "value", script = "return typeof value === 'bigint';")
	static native boolean isBigInt(JSObject value);

	@JSBody(params = "value", script = "return typeof value === 'string';")
	static native boolean isString(JSObject value);

	@JSBody(params = "value", script = "return typeof value === 'boolean';")
	static native boolean isBoolean(JSObject value);

	@JSBody(params = "value", script = "return typeof value === 'function';")
	static native boolean isFunction(JSObject value);

	@JSBody(params = "value", script = "return value instanceof Map;")
	static native boolean isMap(JSObject value);

	@JSBody(params = "value", script = "return value instanceof Set;")
	static native boolean isSet(JSObject value);

	@JSBody(params = "value", script = "return String(value);")
	static native String asString(JSObject value);

	@JSBody(params = "value", script = "return Number(value);")
	static native double asDouble(JSObject value);

	/**
	 * Reads any numeric value as a {@code long}, saturating rather than wrapping.
	 *
	 * <p>Saturation is what a Java cast from {@code double} does, and applying the same rule to a
	 * {@code BigInt} too keeps one story for both: out of range clamps to the nearest bound.
	 *
	 * <p>A string of digits is read exactly rather than through a {@code Number}, which is what
	 * closes the round trip with {@link #stringify}: a long is written as a string, so it has to
	 * come back as one without passing through a type that cannot hold it.
	 *
	 * <p>The bounds are constructed rather than written as {@code BigInt} literals, which the
	 * compiler's own script parser refuses.
	 */
	@JSBody(
		params = "value",
		script = "var min = BigInt('-9223372036854775808');" +
			" var max = BigInt('9223372036854775807');" +
			" var clamp = function (v) { return v < min ? min : v > max ? max : v; };" +
			" if (typeof value === 'bigint') return clamp(value);" +
			" if (typeof value === 'string' && /^\\s*-?\\d+\\s*$/.test(value))" +
			"   return clamp(BigInt(value.trim()));" +
			" var number = Number(value);" +
			" if (!Number.isFinite(number)) return BigInt(0);" +
			" return clamp(BigInt(Math.trunc(number)));"
	)
	static native long asLong(JSObject value);

	@JSBody(params = "value", script = "return !!value;")
	static native boolean asBoolean(JSObject value);

	@JSBody(params = "value", script = "return Object.keys(value);")
	static native JSArray<JSObject> keys(JSObject value);

	@JSBody(params = "value", script = "return value.length;")
	static native int length(JSObject value);

	/** The size of a {@code Map} or {@code Set}, which carry no {@code length}. */
	@JSBody(params = "value", script = "return value.size;")
	static native int size(JSObject value);

	/** Every element of anything iterable, so one reader serves an array, a {@code Set} and a string. */
	@JSBody(params = "value", script = "return Array.from(value);")
	static native JSArray<TSObject> iterate(JSObject value);

	@JSBody(params = "value", script = "return Array.from(value.keys());")
	static native JSArray<JSObject> mapKeys(JSObject value);

	@JSBody(params = { "map", "key" }, script = "return map.get(key);")
	static native TSObject mapGet(JSObject map, JSObject key);

	/** {@code apply} rather than argument spread, which the compiler's script parser refuses. */
	@JSBody(
		params = { "target", "method", "args" },
		script = "return target[method].apply(target, args);"
	)
	static native TSObject apply(JSObject target, String method, JSArray<TSObject> args);

	@JSBody(params = { "array", "value" }, script = "array.push(value);")
	static native void push(JSObject array, TSObject value);

	@JSBody(params = { "set", "value" }, script = "set.add(value);")
	static native void add(JSObject set, TSObject value);

	@JSBody(params = { "map", "key", "value" }, script = "map.set(key, value);")
	static native void put(JSObject map, TSObject key, TSObject value);

	@JSBody(params = "value", script = "return value;")
	static native TSObject wrap(JSObject value);

	@JSBody(params = "value", script = "return value;")
	static native TSObject string(String value);

	@JSBody(params = "value", script = "return value;")
	static native TSObject number(double value);

	/** The parameter arrives as a {@code BigInt} already, which is the whole point of taking a long. */
	@JSBody(params = "value", script = "return value;")
	static native TSObject bigint(long value);

	@JSBody(params = "value", script = "return value;")
	static native TSObject bool(boolean value);

	@JSBody(script = "return null;")
	static native TSObject nullValue();

	@JSBody(script = "return undefined;")
	static native TSObject undefinedValue();

	@JSBody(script = "return {};")
	static native TSObject object();

	@JSBody(script = "return [];")
	static native TSObject array();

	@JSBody(script = "return new Set();")
	static native TSObject newSet();

	@JSBody(script = "return new Map();")
	static native TSObject newMap();

	@JSBody(params = "json", script = "return JSON.parse(json);")
	static native TSObject parse(String json);

	/**
	 * Serialises to JSON, writing a {@code BigInt} as a string.
	 *
	 * <p>{@code JSON.stringify} refuses a {@code BigInt} outright with
	 * {@code TypeError: Do not know how to serialize a BigInt}, so a long would make any object
	 * holding one unserialisable. Writing it as a number instead would silently lose precision above
	 * 2^53, which is the range a long exists for. A string is exact, {@link #asLong} reads one back
	 * exactly, and it is what every JSON API carrying 64-bit identifiers already does.
	 */
	@JSBody(
		params = "value",
		script = "return JSON.stringify(value, (key, held) =>" +
			" typeof held === 'bigint' ? held.toString() : held);"
	)
	static native String stringify(JSObject value);
}
