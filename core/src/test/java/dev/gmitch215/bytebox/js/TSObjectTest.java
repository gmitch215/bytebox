package dev.gmitch215.bytebox.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The conversions layered over the JavaScript reads, which is the half of this surface written in
 * Java. Everything that reads or builds a JavaScript value itself is in the workerd lane, because
 * there is no Java in it to run.
 */
@DisplayName("a JavaScript value")
class TSObjectTest {

	@Test
	@DisplayName("reads an array as a list, a set and a stream")
	void collections() {
		Stub repeated = value("a");
		TSObject array = array(repeated, value("b"), repeated);

		assertEquals(3, array.asList().size());
		assertEquals(2, array.asSet().size());
		assertEquals(3, array.stream().count());
	}

	@Test
	@DisplayName("reads an array's elements as the type asked for")
	void typedLists() {
		assertEquals(List.of("a", "b"), array(value("a"), value("b")).asStringList());
		assertEquals(List.of(1, 2), array(value(1), value(2)).asIntList());
		assertEquals(List.of(1L, 2L), array(value(1), value(2)).asLongList());
		assertEquals(List.of(1.5, 2.5), array(value(1.5), value(2.5)).asDoubleList());
	}

	@Test
	@DisplayName("reads an object's own properties as a map, in the order they were written")
	void asMap() {
		Stub object = new Stub(null).with("b", value("second")).with("a", value("first"));

		Map<String, TSObject> entries = object.asMap();

		assertEquals(List.of("b", "a"), List.copyOf(entries.keySet()));
		assertEquals("second", entries.get("b").asString());
	}

	@Test
	@DisplayName("narrows the way a Java cast narrows")
	void narrowing() {
		assertEquals((short) 70000, value(70000).asShort());
		assertEquals((byte) 300, value(300).asByte());
	}

	@Test
	@DisplayName("reads a string's first character, and anything else as a code unit")
	void chars() {
		assertEquals('a', value("abc").asChar());
		assertEquals('\0', value("").asChar());
		assertEquals('A', value(65).asChar());
	}

	@Test
	@DisplayName("refuses a long a JavaScript number cannot hold exactly")
	void numberRange() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
			TSObject.ofNumber(9007199254740993L)
		);

		assertTrue(failure.getMessage().contains("cannot be held exactly"), failure.getMessage());
		assertThrows(IllegalArgumentException.class, () -> TSObject.ofNumber(-9007199254740993L));
	}

	@Test
	@DisplayName("names the Java type it has no counterpart for, rather than converting it wrongly")
	void unconvertible() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
			TSObject.from(new StringBuilder())
		);

		assertTrue(failure.getMessage().contains("StringBuilder"), failure.getMessage());
		assertTrue(failure.getMessage().contains("hand over a JSObject"), failure.getMessage());
	}

	private static Stub value(Object held) {
		return new Stub(held);
	}

	private static Stub array(Stub... elements) {
		Stub array = new Stub(null);
		for (Stub element : elements) array.elements.add(element);
		return array;
	}

	/**
	 * A value that answers from Java.
	 *
	 * <p>Written here rather than borrowed from the other package's stubs, because what is under test
	 * is the default methods and a shared stub would have to override the very ones being exercised.
	 */
	private static final class Stub implements TSObject {

		private final Object held;
		private final Map<String, TSObject> fields = new LinkedHashMap<>();
		private final List<TSObject> elements = new ArrayList<>();

		Stub(Object held) {
			this.held = held;
		}

		Stub with(String name, TSObject field) {
			fields.put(name, field);
			return this;
		}

		@Override
		public TSObject get(String name) {
			return fields.get(name);
		}

		@Override
		public void set(String name, TSObject value) {
			fields.put(name, value);
		}

		@Override
		public TSObject at(int index) {
			return elements.get(index);
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
		public boolean isMap() {
			return false;
		}

		@Override
		public boolean isSet() {
			return false;
		}

		@Override
		public boolean isString() {
			return held instanceof String;
		}

		@Override
		public String asString() {
			return String.valueOf(held);
		}

		@Override
		public int asInt() {
			return held instanceof Number number ? number.intValue() : 0;
		}

		@Override
		public long asLong() {
			return held instanceof Number number ? number.longValue() : 0L;
		}

		@Override
		public double asDouble() {
			return held instanceof Number number ? number.doubleValue() : Double.NaN;
		}
	}
}
