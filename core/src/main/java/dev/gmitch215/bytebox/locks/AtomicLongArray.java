package dev.gmitch215.bytebox.locks;

/**
 * {@code java.util.concurrent.atomic.AtomicLongArray}, which the class library does not carry even
 * though it carries {@code AtomicLong}.
 *
 * <p>Indivisible for the same reason as {@link AtomicReferenceArray}: one fiber runs at a time and
 * none of these operations suspends, so no two of them interleave.
 *
 * @since 1.0.2
 */
public class AtomicLongArray {

	private final long[] values;

	/**
	 * @param length how many elements, each starting zero
	 */
	public AtomicLongArray(int length) {
		values = new long[length];
	}

	/**
	 * @param array the elements to copy
	 */
	public AtomicLongArray(long[] array) {
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
	public final long get(int i) {
		return values[i];
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 */
	public final void set(int i, long newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 */
	public final void lazySet(int i, long newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the value to store
	 * @return the value that was there
	 */
	public final long getAndSet(int i, long newValue) {
		long old = values[i];
		values[i] = newValue;
		return old;
	}

	/**
	 * @param i the index
	 * @param expect the value that must be there
	 * @param update the value to store
	 * @return whether it was stored
	 */
	public final boolean compareAndSet(int i, long expect, long update) {
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
	public final boolean weakCompareAndSet(int i, long expect, long update) {
		return compareAndSet(i, expect, update);
	}

	/**
	 * @param i the index
	 * @param delta how much to add
	 * @return the value before adding
	 */
	public final long getAndAdd(int i, long delta) {
		long old = values[i];
		values[i] = old + delta;
		return old;
	}

	/**
	 * @param i the index
	 * @param delta how much to add
	 * @return the value after adding
	 */
	public final long addAndGet(int i, long delta) {
		return (values[i] += delta);
	}

	/**
	 * @param i the index
	 * @return the value before incrementing
	 */
	public final long getAndIncrement(int i) {
		return getAndAdd(i, 1);
	}

	/**
	 * @param i the index
	 * @return the value before decrementing
	 */
	public final long getAndDecrement(int i) {
		return getAndAdd(i, -1);
	}

	/**
	 * @param i the index
	 * @return the value after incrementing
	 */
	public final long incrementAndGet(int i) {
		return addAndGet(i, 1);
	}

	/**
	 * @param i the index
	 * @return the value after decrementing
	 */
	public final long decrementAndGet(int i) {
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
