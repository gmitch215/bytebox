package dev.gmitch215.bytebox.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("an array of references")
class AtomicReferenceArrayTest {

	@Test
	@DisplayName("starts empty at the length asked for")
	void empty() {
		AtomicReferenceArray<String> array = new AtomicReferenceArray<>(3);

		assertEquals(3, array.length());
		assertNull(array.get(0));
		assertEquals("[null, null, null]", array.toString());
	}

	@Test
	@DisplayName("copies the array it was given rather than aliasing it")
	void copies() {
		String[] source = { "a", "b" };
		AtomicReferenceArray<String> array = new AtomicReferenceArray<>(source);
		source[0] = "changed";

		assertEquals("a", array.get(0));
	}

	@Test
	@DisplayName("reads back what was set, and reports what was displaced")
	void setAndGet() {
		AtomicReferenceArray<String> array = new AtomicReferenceArray<>(2);
		array.set(0, "first");

		assertEquals("first", array.get(0));
		assertEquals("first", array.getAndSet(0, "second"));
		assertEquals("second", array.get(0));

		array.lazySet(1, "other");
		assertEquals("other", array.get(1));
	}

	@Test
	@DisplayName("swaps only when the element is the one expected, by identity")
	void compareAndSet() {
		AtomicReferenceArray<String> array = new AtomicReferenceArray<>(1);
		String held = "held";
		array.set(0, held);

		assertFalse(array.compareAndSet(0, "other", "new"));
		assertEquals(held, array.get(0));
		assertTrue(array.compareAndSet(0, held, "new"));
		assertEquals("new", array.get(0));
		assertTrue(array.weakCompareAndSet(0, "new", "last"));
		assertEquals("last", array.get(0));
	}
}
