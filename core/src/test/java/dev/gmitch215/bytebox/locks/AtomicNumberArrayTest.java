package dev.gmitch215.bytebox.locks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("an array of numbers")
class AtomicNumberArrayTest {

	@Test
	@DisplayName("starts at zero and reads back what was set")
	void longBasics() {
		AtomicLongArray array = new AtomicLongArray(2);

		assertEquals(2, array.length());
		assertEquals(0, array.get(0));
		array.set(0, 7);
		assertEquals(7, array.get(0));
		assertEquals(7, array.getAndSet(0, 9));
		assertEquals(9, array.get(0));
		array.lazySet(1, 3);
		assertEquals("[9, 3]", array.toString());
	}

	@Test
	@DisplayName("adds and counts in both directions")
	void longArithmetic() {
		AtomicLongArray array = new AtomicLongArray(1);

		assertEquals(0, array.getAndAdd(0, 5));
		assertEquals(7, array.addAndGet(0, 2));
		assertEquals(7, array.getAndIncrement(0));
		assertEquals(8, array.get(0));
		assertEquals(9, array.incrementAndGet(0));
		assertEquals(9, array.getAndDecrement(0));
		assertEquals(7, array.decrementAndGet(0));
	}

	@Test
	@DisplayName("swaps a long only when the value is the one expected")
	void longCompareAndSet() {
		AtomicLongArray array = new AtomicLongArray(1);
		array.set(0, 4);

		assertFalse(array.compareAndSet(0, 5, 6));
		assertEquals(4, array.get(0));
		assertTrue(array.compareAndSet(0, 4, 6));
		assertEquals(6, array.get(0));
		assertTrue(array.weakCompareAndSet(0, 6, 8));
		assertEquals(8, array.get(0));
	}

	@Test
	@DisplayName("copies the long array it was given rather than aliasing it")
	void longCopies() {
		long[] source = { 1, 2 };
		AtomicLongArray array = new AtomicLongArray(source);
		source[0] = 99;

		assertEquals(1, array.get(0));
	}

	@Test
	@DisplayName("carries the same surface for ints")
	void intBasics() {
		AtomicIntegerArray array = new AtomicIntegerArray(2);

		assertEquals(2, array.length());
		array.set(0, 7);
		assertEquals(7, array.getAndSet(0, 9));
		assertEquals(0, array.getAndAdd(1, 5));
		assertEquals(6, array.incrementAndGet(1));
		assertEquals(5, array.decrementAndGet(1));
		assertTrue(array.compareAndSet(0, 9, 2));
		assertFalse(array.compareAndSet(0, 9, 3));
		assertEquals("[2, 5]", array.toString());
	}

	@Test
	@DisplayName("copies the int array it was given rather than aliasing it")
	void intCopies() {
		int[] source = { 1, 2 };
		AtomicIntegerArray array = new AtomicIntegerArray(source);
		source[0] = 99;

		assertEquals(1, array.get(0));
	}
}
