package dev.gmitch215.bytebox.locks;

/**
 * {@code java.util.concurrent.atomic.AtomicIntegerArray}, which the class library does not carry even
 * though it carries {@code AtomicInteger}.
 *
 * <p>Indivisible for the same reason as {@link AtomicReferenceArray}: one fiber runs at a time and
 * none of these operations suspends, so no two of them interleave.
 *
 * @since 1.0.2
 */
public class AtomicIntegerArray {

	private final int[] values;

	/**
	 * @param length how many elements, each starting zero
	 */
	public AtomicIntegerArray(int length) {
		values = new int[length];
	}

	/**
	 * @param array the elements to copy
	 */
	public AtomicIntegerArray(int[] array) {
		values = array.clone();
	}

	/** {@return how many elements} */
	public final int length() {
		return values.length;
	}

	/**
	 * @param i the index
	 * @return the element
	 */
	public final int get(int i) {
		return values[i];
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 */
	public final void set(int i, int newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 */
	public final void lazySet(int i, int newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 * @return the value that was there
	 */
	public final int getAndSet(int i, int newValue) {
		int old = values[i];
		values[i] = newValue;
		return old;
	}

	/**
	 * @param i the index
	 * @param expect the value that must be there
	 * @param update the value to store
	 * @return whether it was stored
	 */
	public final boolean compareAndSet(int i, int expect, int update) {
		if (values[i] != expect) return false;
		values[i] = update;
		return true;
	}

	/**
	 * @param i the index
	 * @param expect the value that must be there
	 * @param update the value to store
	 * @return whether it was stored
	 */
	public final boolean weakCompareAndSet(int i, int expect, int update) {
		return compareAndSet(i, expect, update);
	}

	/**
	 * @param i the index
	 * @param delta how much to add
	 * @return the value before adding
	 */
	public final int getAndAdd(int i, int delta) {
		int old = values[i];
		values[i] = old + delta;
		return old;
	}

	/**
	 * @param i the index
	 * @param delta how much to add
	 * @return the value after adding
	 */
	public final int addAndGet(int i, int delta) {
		return (values[i] += delta);
	}

	/**
	 * @param i the index
	 * @return the value before incrementing
	 */
	public final int getAndIncrement(int i) {
		return getAndAdd(i, 1);
	}

	/**
	 * @param i the index
	 * @return the value before decrementing
	 */
	public final int getAndDecrement(int i) {
		return getAndAdd(i, -1);
	}

	/**
	 * @param i the index
	 * @return the value after incrementing
	 */
	public final int incrementAndGet(int i) {
		return addAndGet(i, 1);
	}

	/**
	 * @param i the index
	 * @return the value after decrementing
	 */
	public final int decrementAndGet(int i) {
		return addAndGet(i, -1);
	}

	@Override
	public String toString() {
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) out.append(", ");
			out.append(values[i]);
		}
		return out.append(']').toString();
	}
}
