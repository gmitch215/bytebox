package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-Java stands-in for the JavaScript objects the surfaces wrap.
 *
 * <p>Every surface in the main source set is an interface whose convenience methods are
 * {@code default}, which is what makes them reachable here: the compiler turns the abstract members
 * into JavaScript calls, and a plain implementation answers them in Java instead. So that logic is
 * testable on an ordinary JVM, with no compiler and no runtime.
 *
 * <p>Two things are out of reach and are covered in the workerd lane instead. A method backed by
 * {@code @JSBody} has no Java body at all. And {@code JSString} and {@code JSPromise} are classes
 * rather than interfaces, so anything returning one cannot be stood in for, which rules out every
 * method that waits on a promise.
 */
final class Stubs {

	private Stubs() {}

	/** A {@link TSObject} over a Java value. */
	static final class Value implements TSObject {

		private final Object value;
		private final boolean present;
		private final Map<String, TSObject> fields = new LinkedHashMap<>();
		private final List<TSObject> elements = new ArrayList<>();

		private Value(Object value, boolean present) {
			this.value = value;
			this.present = present;
		}

		static Value of(Object value) {
			return new Value(value, true);
		}

		static Value object() {
			return new Value(null, true);
		}

		/** A value that is present and holds null, which reads as absent. */
		static Value absent() {
			return new Value(null, false);
		}

		Value with(String name, TSObject field) {
			fields.put(name, field);
			return this;
		}

		Value with(String name, String field) {
			return with(name, of(field));
		}

		Value element(TSObject item) {
			elements.add(item);
			return this;
		}

		@Override
		public TSObject get(String name) {
			return fields.get(name);
		}

		@Override
		public void set(String name, TSObject field) {
			fields.put(name, field);
		}

		@Override
		public TSObject at(int index) {
			return elements.get(index);
		}

		@Override
		public String typeOf() {
			if (!present) return "undefined";
			if (value instanceof String) return "string";
			if (value instanceof Number) return "number";
			if (value instanceof Boolean) return "boolean";
			return "object";
		}

		@Override
		public boolean isNull() {
			return !present;
		}

		@Override
		public boolean isUndefined() {
			return !present;
		}

		@Override
		public boolean isArray() {
			return !elements.isEmpty();
		}

		@Override
		public boolean isString() {
			return value instanceof String;
		}

		@Override
		public boolean isNumber() {
			return value instanceof Number;
		}

		@Override
		public boolean isBoolean() {
			return value instanceof Boolean;
		}

		@Override
		public boolean isBigInt() {
			return value instanceof Long;
		}

		@Override
		public boolean isFunction() {
			return false;
		}

		/** A plain object rather than a real {@code Map}, which is the shape JSON uses. */
		@Override
		public boolean isMap() {
			return false;
		}

		@Override
		public boolean isSet() {
			return false;
		}

		@Override
		public String asString() {
			return String.valueOf(value);
		}

		@Override
		public int asInt() {
			return (int) asDouble();
		}

		@Override
		public long asLong() {
			return (long) asDouble();
		}

		@Override
		public double asDouble() {
			return value instanceof Number number ? number.doubleValue() : Double.NaN;
		}

		@Override
		public boolean asBoolean() {
			return value instanceof Boolean flag ? flag : value != null;
		}

		@Override
		public int length() {
			return elements.size();
		}

		@Override
		public List<TSObject> asList() {
			return List.copyOf(elements);
		}

		@Override
		public List<String> keys() {
			return List.copyOf(fields.keySet());
		}

		@Override
		public String toJson() {
			throw new UnsupportedOperationException("a stub does not serialise");
		}

		@Override
		public TSObject call(String method, TSObject... args) {
			throw new UnsupportedOperationException("no method " + method + " on a stub");
		}
	}

	/** Headers backed by a map, matching the case-insensitive comparison the platform uses. */
	static class StubHeaders implements Headers {

		private final Map<String, String> values = new LinkedHashMap<>();

		@Override
		public String get(String name) {
			return values.get(name.toLowerCase());
		}

		@Override
		public void set(String name, String value) {
			values.put(name.toLowerCase(), value);
		}

		@Override
		public void append(String name, String value) {
			String existing = get(name);
			set(name, existing == null ? value : existing + ", " + value);
		}

		@Override
		public void delete(String name) {
			values.remove(name.toLowerCase());
		}

		@Override
		public boolean has(String name) {
			return values.containsKey(name.toLowerCase());
		}

		@Override
		public org.teavm.jso.core.JSArrayReader<org.teavm.jso.core.JSString> getSetCookie() {
			throw new UnsupportedOperationException("Set-Cookie needs a real JSString");
		}
	}

	/** A request that answers from Java, for the members that do not read the body. */
	static final class StubRequest implements Request {

		private final String url;
		private final String method;
		private final Headers headers;
		private final Map<String, String> query = new LinkedHashMap<>();
		private TSObject cf = Value.object();

		StubRequest(String url) {
			this(url, "GET", new StubHeaders());
		}

		StubRequest(String url, String method, Headers headers) {
			this.url = url;
			this.method = method;
			this.headers = headers;
		}

		/** Cloudflare's own request properties, which the platform attaches rather than the URL. */
		StubRequest withCf(TSObject value) {
			cf = value;
			return this;
		}

		StubRequest withQuery(String name, String value) {
			query.put(name, value);
			return this;
		}

		@Override
		public String getUrl() {
			return url;
		}

		@Override
		public String getMethod() {
			return method;
		}

		@Override
		public Headers getHeaders() {
			return headers;
		}

		@Override
		public boolean isBodyUsed() {
			return false;
		}

		@Override
		public TSObject getCf() {
			return cf;
		}

		@Override
		public org.teavm.jso.core.JSPromise<org.teavm.jso.core.JSString> readText() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}

		@Override
		public org.teavm.jso.core.JSPromise<TSObject> readJson() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}

		@Override
		public org.teavm.jso.core.JSPromise<org.teavm.jso.typedarrays.ArrayBuffer> readBytes() {
			throw new UnsupportedOperationException("reading a body needs a real promise");
		}

		/** The platform's own URL parser does this, so the stub answers from what it was given. */
		@Override
		public String query(String name) {
			return query.get(name);
		}
	}

	/** An environment over a map of bindings. */
	static final class StubEnv implements Env {

		private final Map<String, TSObject> bindings = new LinkedHashMap<>();

		StubEnv with(String name, TSObject binding) {
			bindings.put(name, binding);
			return this;
		}

		StubEnv with(String name, String value) {
			return with(name, Value.of(value));
		}

		@Override
		public TSObject get(String name) {
			return bindings.get(name);
		}
	}
}
