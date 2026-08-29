package dev.gmitch215.bytebox.js;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.teavm.jso.JSIndexer;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;

/**
 * Any JavaScript value, reachable by name.
 *
 * <p>This is the floor of the generated npm bindings: a member the generator could not type, a
 * declaration written as {@code any}, or a package with no type information at all binds as a
 * {@code TSObject}, so nothing is ever unreachable from Java. It is also the body type of a Queue
 * message, which arrives as an arbitrary structured-clone value.
 *
 * <p>Nothing is copied. This is the JavaScript value itself, and the accessors read through to it.
 *
 * {@snippet lang = "java":
 * TSObject config = module.get("config");
 * int port = config.get("port").asInt();
 * String host = config.get("host").asString();
 *}
 *
 * <h2>Numbers</h2>
 *
 * <p>JavaScript has two numeric types and Java has six. Everything except {@code long} maps to
 * {@code Number}, which holds every {@code int}, {@code short}, {@code byte}, {@code float} and
 * {@code double} exactly. {@code long} maps to {@code BigInt}, because a {@code Number} loses
 * precision above 2^53 and a Java {@code long} goes well past it.
 *
 * <p>So {@link #of(long)} produces a {@code BigInt} and {@link #asLong()} reads one, while
 * {@link #asInt()} and its siblings read a {@code Number}. Each reader accepts either kind and
 * converts, so a value that arrived as a {@code Number} still reads correctly through
 * {@link #asLong()}.
 *
 * <p>Out-of-range conversions follow Java's own rules: {@link #asLong()} and {@link #asInt()}
 * saturate at the type's bounds, and {@link #asShort()}, {@link #asByte()} and {@link #asChar()}
 * truncate the low bits, which is what a Java cast does.
 *
 * @since 1.0.0
 */
public interface TSObject extends JSObject {
	/**
	 * Reads one property.
	 *
	 * @param name the property name
	 * @return the value, which is never {@code null} for a property that is present and holds
	 *     {@code null}; use {@link #isNull()} to tell those apart
	 */
	@JSIndexer
	TSObject get(String name);

	/**
	 * Writes one property.
	 *
	 * @param name the property name
	 * @param value the value
	 */
	@JSIndexer
	void set(String name, TSObject value);

	/**
	 * Reads one element of an array.
	 *
	 * @param index the index
	 * @return the element
	 */
	@JSIndexer
	TSObject at(int index);

	// #region kind

	/** {@return the result of JavaScript's {@code typeof} on this value} */
	default String typeOf() {
		return JS.typeOf(this);
	}

	/** {@return whether this value is {@code null} or {@code undefined}} */
	default boolean isNull() {
		return JS.isNullish(this);
	}

	/** {@return whether this value is {@code undefined} specifically} */
	default boolean isUndefined() {
		return JS.isUndefined(this);
	}

	/** {@return whether this value is a JavaScript array} */
	default boolean isArray() {
		return JS.isArray(this);
	}

	/** {@return whether this value is a {@code Number}, which is every Java numeric but long} */
	default boolean isNumber() {
		return JS.isNumber(this);
	}

	/** {@return whether this value is a {@code BigInt}, which is how a Java long crosses over} */
	default boolean isBigInt() {
		return JS.isBigInt(this);
	}

	/** {@return whether this value is a string} */
	default boolean isString() {
		return JS.isString(this);
	}

	/** {@return whether this value is a boolean} */
	default boolean isBoolean() {
		return JS.isBoolean(this);
	}

	/** {@return whether this value is callable} */
	default boolean isFunction() {
		return JS.isFunction(this);
	}

	/** {@return whether this value is a JavaScript {@code Map}, not a plain object} */
	default boolean isMap() {
		return JS.isMap(this);
	}

	/** {@return whether this value is a JavaScript {@code Set}} */
	default boolean isSet() {
		return JS.isSet(this);
	}

	// #endregion

	// #region scalars

	/** {@return this value coerced to a string, the way {@code String(value)} would} */
	default String asString() {
		return JS.asString(this);
	}

	/** {@return this value as a double} */
	default double asDouble() {
		return JS.asDouble(this);
	}

	/** {@return this value as a float, narrowed the way a Java cast narrows} */
	default float asFloat() {
		return (float) JS.asDouble(this);
	}

	/** {@return this value as an int, saturating at the bounds like a Java cast} */
	default int asInt() {
		return (int) JS.asDouble(this);
	}

	/**
	 * {@return this value as a long}
	 *
	 * <p>Reads a {@code BigInt} exactly, and converts a {@code Number} by truncating toward zero.
	 * Out of range saturates rather than wrapping.
	 */
	default long asLong() {
		return JS.asLong(this);
	}

	/** {@return this value as a short, truncating the low 16 bits like a Java cast} */
	default short asShort() {
		return (short) asInt();
	}

	/** {@return this value as a byte, truncating the low 8 bits like a Java cast} */
	default byte asByte() {
		return (byte) asInt();
	}

	/**
	 * {@return this value as a char}
	 *
	 * <p>A string reads as its first character, which is what a caller means by asking a string for
	 * a char. Anything else reads as a UTF-16 code unit, which is how a Java {@code char} crosses
	 * into JavaScript in the first place.
	 */
	default char asChar() {
		if (isString()) {
			String text = asString();
			return text.isEmpty() ? '\0' : text.charAt(0);
		}
		return (char) asInt();
	}

	/** {@return this value's truthiness} */
	default boolean asBoolean() {
		return JS.asBoolean(this);
	}

	// #endregion

	// #region collections

	/**
	 * {@return how many elements this holds}
	 *
	 * <p>Reads {@code size} for a {@code Map} or {@code Set} and {@code length} for everything else,
	 * because JavaScript spells the same question two ways.
	 */
	default int length() {
		return isMap() || isSet() ? JS.size(this) : JS.length(this);
	}

	/**
	 * {@return the elements of this array, {@code Set} or other iterable}
	 *
	 * <p>The list is a Java copy. The elements are not: each is the JavaScript value itself.
	 */
	default List<TSObject> asList() {
		JSArray<TSObject> items = JS.iterate(this);
		List<TSObject> values = new ArrayList<>(items.getLength());
		for (int i = 0; i < items.getLength(); i++) values.add(items.get(i));
		return values;
	}

	/** {@return the elements of this array or {@code Set}, in encounter order, without duplicates} */
	default Set<TSObject> asSet() {
		return new LinkedHashSet<>(asList());
	}

	/** {@return the elements of this array or {@code Set} as a stream} */
	default Stream<TSObject> stream() {
		return asList().stream();
	}

	/** {@return the elements of this array read as strings} */
	default List<String> asStringList() {
		List<TSObject> items = asList();
		List<String> values = new ArrayList<>(items.size());
		for (TSObject item : items) values.add(item.asString());
		return values;
	}

	/** {@return the elements of this array read as ints} */
	default List<Integer> asIntList() {
		List<TSObject> items = asList();
		List<Integer> values = new ArrayList<>(items.size());
		for (TSObject item : items) values.add(item.asInt());
		return values;
	}

	/** {@return the elements of this array read as longs} */
	default List<Long> asLongList() {
		List<TSObject> items = asList();
		List<Long> values = new ArrayList<>(items.size());
		for (TSObject item : items) values.add(item.asLong());
		return values;
	}

	/** {@return the elements of this array read as doubles} */
	default List<Double> asDoubleList() {
		List<TSObject> items = asList();
		List<Double> values = new ArrayList<>(items.size());
		for (TSObject item : items) values.add(item.asDouble());
		return values;
	}

	/**
	 * {@return this object's own enumerable property names, or a {@code Map}'s keys}
	 *
	 * <p>A {@code Map} key that is not a string reads as its string form, because a Java
	 * {@code Map<String, ?>} is what the result has to fit.
	 */
	default List<String> keys() {
		JSArray<JSObject> names = isMap() ? JS.mapKeys(this) : JS.keys(this);
		List<String> result = new ArrayList<>(names.getLength());
		for (int i = 0; i < names.getLength(); i++) result.add(JS.asString(names.get(i)));
		return result;
	}

	/**
	 * {@return this object's own properties, or a {@code Map}'s entries}
	 *
	 * <p>Insertion order is preserved, which is the order JavaScript enumerates string keys in.
	 */
	default Map<String, TSObject> asMap() {
		Map<String, TSObject> entries = new LinkedHashMap<>();
		if (isMap()) {
			for (String key : keys()) entries.put(key, JS.mapGet(this, of(key)));
			return entries;
		}
		for (String key : keys()) entries.put(key, get(key));
		return entries;
	}

	/**
	 * Appends to this array.
	 *
	 * @param value the element
	 */
	default void push(TSObject value) {
		JS.push(this, value);
	}

	/**
	 * Adds to this {@code Set}.
	 *
	 * @param value the element
	 */
	default void add(TSObject value) {
		JS.add(this, value);
	}

	/**
	 * Writes into this {@code Map}, which takes any key rather than only a string.
	 *
	 * @param key the key
	 * @param value the value
	 */
	default void put(TSObject key, TSObject value) {
		JS.put(this, key, value);
	}

	// #endregion

	/**
	 * {@return this value serialised as JSON}
	 *
	 * <p>A {@code BigInt} is written as a string, because {@code JSON.stringify} refuses one outright
	 * and writing it as a number would lose precision above 2^53 — the range a {@code long} exists
	 * for. {@link #asLong()} reads a string of digits back exactly, so the round trip is lossless.
	 */
	default String toJson() {
		return JS.stringify(this);
	}

	/**
	 * Calls one of this object's methods.
	 *
	 * @param method the method name
	 * @param args the arguments
	 * @return what the method returned
	 */
	default TSObject call(String method, TSObject... args) {
		return JS.apply(this, method, JSArrays.of(args));
	}

	// #region factories

	/**
	 * Wraps any JavaScript value.
	 *
	 * @param value the value
	 * @return the same value, typed as a {@code TSObject}
	 */
	static TSObject of(JSObject value) {
		return JS.wrap(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript string
	 */
	static TSObject of(String value) {
		return JS.string(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript string of one character, which is what a caller means by a char
	 */
	static TSObject of(char value) {
		return JS.string(String.valueOf(value));
	}

	/**
	 * @param value the value
	 * @return a JavaScript number
	 */
	static TSObject of(double value) {
		return JS.number(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript number
	 */
	static TSObject of(float value) {
		return JS.number(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript number
	 */
	static TSObject of(int value) {
		return JS.number(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript number
	 */
	static TSObject of(short value) {
		return JS.number(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript number
	 */
	static TSObject of(byte value) {
		return JS.number(value);
	}

	/**
	 * A {@code BigInt}, because a {@code Number} cannot hold every long.
	 *
	 * @param value the value
	 * @return a JavaScript BigInt
	 */
	static TSObject of(long value) {
		return JS.bigint(value);
	}

	/**
	 * A long as a {@code Number}, for a JavaScript API that will not take a {@code BigInt}.
	 *
	 * <p>Refuses a value a {@code Number} cannot hold exactly rather than silently rounding it.
	 *
	 * @param value the value, within +/-2^53
	 * @return a JavaScript number
	 */
	static TSObject ofNumber(long value) {
		if (value > 9007199254740992L || value < -9007199254740992L) {
			throw new IllegalArgumentException(
				value + " cannot be held exactly by a JavaScript number; use of(long) for a BigInt"
			);
		}
		return JS.number(value);
	}

	/**
	 * @param value the value
	 * @return a JavaScript boolean
	 */
	static TSObject of(boolean value) {
		return JS.bool(value);
	}

	/** {@return JavaScript {@code null}} */
	static TSObject nullValue() {
		return JS.nullValue();
	}

	/** {@return JavaScript {@code undefined}, which an absent property reads as} */
	static TSObject undefined() {
		return JS.undefinedValue();
	}

	/** {@return a new empty object} */
	static TSObject object() {
		return JS.object();
	}

	/**
	 * A plain object from a map, which is the shape JSON and a structured clone both use.
	 *
	 * @param values the entries, converted by {@link #from(Object)}
	 * @return the object
	 */
	static TSObject object(Map<String, ?> values) {
		TSObject result = JS.object();
		for (Map.Entry<String, ?> entry : values.entrySet()) {
			result.set(entry.getKey(), from(entry.getValue()));
		}
		return result;
	}

	/** {@return a new empty array} */
	static TSObject array() {
		return JS.array();
	}

	/**
	 * An array from any collection.
	 *
	 * @param values the elements, converted by {@link #from(Object)}
	 * @return the array
	 */
	static TSObject array(Collection<?> values) {
		TSObject result = JS.array();
		for (Object value : values) JS.push(result, from(value));
		return result;
	}

	/**
	 * A real JavaScript {@code Set}, for an API that wants one. {@link #array(Collection)} is what a
	 * JSON-shaped value wants.
	 *
	 * @param values the elements, converted by {@link #from(Object)}
	 * @return the set
	 */
	static TSObject set(Collection<?> values) {
		TSObject result = JS.newSet();
		for (Object value : values) JS.add(result, from(value));
		return result;
	}

	/**
	 * A real JavaScript {@code Map}, for an API that wants one. {@link #object(Map)} is what a
	 * JSON-shaped value wants.
	 *
	 * @param values the entries, converted by {@link #from(Object)}
	 * @return the map
	 */
	static TSObject map(Map<String, ?> values) {
		TSObject result = JS.newMap();
		for (Map.Entry<String, ?> entry : values.entrySet()) {
			JS.put(result, of(entry.getKey()), from(entry.getValue()));
		}
		return result;
	}

	/**
	 * Converts any Java value, deciding by its runtime type.
	 *
	 * <p>Nested collections and maps convert all the way down, so a
	 * {@code Map<String, List<Integer>>} becomes an object of arrays of numbers. A collection
	 * becomes an array rather than a {@code Set}, and a map becomes a plain object rather than a
	 * {@code Map}, because those are the shapes JSON and the structured clone algorithm use — reach
	 * for {@link #set(Collection)} or {@link #map(Map)} to say otherwise.
	 *
	 * @param value the value
	 * @return the JavaScript value
	 * @throws IllegalArgumentException for a type with no JavaScript counterpart, rather than
	 *     producing something that looks converted and is not
	 */
	static TSObject from(Object value) {
		if (value == null) return nullValue();
		if (value instanceof JSObject already) return JS.wrap(already);
		if (value instanceof String text) return of(text);
		if (value instanceof Boolean flag) return of(flag.booleanValue());
		if (value instanceof Character letter) return of(letter.charValue());
		if (value instanceof Long number) return of(number.longValue());
		if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
			return JS.number(((Number) value).intValue());
		}
		if (value instanceof Double || value instanceof Float) {
			return JS.number(((Number) value).doubleValue());
		}
		if (value instanceof Map<?, ?> entries) return object(stringKeyed(entries));
		if (value instanceof Collection<?> items) return array(items);
		if (value instanceof Object[] items) return array(List.of(items));
		if (value instanceof Enum<?> constant) return of(constant.name());
		throw new IllegalArgumentException(
			"no JavaScript counterpart for " +
				value.getClass().getName() +
				"; convert it yourself, or hand over a JSObject"
		);
	}

	// #endregion

	private static Map<String, Object> stringKeyed(Map<?, ?> entries) {
		Map<String, Object> keyed = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : entries.entrySet()) {
			keyed.put(String.valueOf(entry.getKey()), entry.getValue());
		}
		return keyed;
	}

	/**
	 * Parses JSON.
	 *
	 * @param json the JSON text
	 * @return the parsed value
	 */
	static TSObject fromJson(String json) {
		return JS.parse(json);
	}
}
