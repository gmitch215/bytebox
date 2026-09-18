package dev.gmitch215.bytebox.locks;

/**
 * {@code java.util.concurrent.atomic.AtomicReferenceArray}, which the class library does not carry
 * even though it carries {@code AtomicReference}.
 *
 * <p>One fiber runs at a time and none of these operations suspends, so each is already indivisible
 * and no lock is needed to make it so.
 *
 * @param <E> the element type
 * @since 1.0.2
 */
public class AtomicReferenceArray<E> {

	private final Object[] values;

	/**
	 * @param length how many elements, each starting null
	 */
	public AtomicReferenceArray(int length) {
		values = new Object[length];
	}

	/**
	 * @param array the elements to copy
	 */
	public AtomicReferenceArray(E[] array) {
		values = new Object[array.length];
		System.arraycopy(array, 0, values, 0, array.length);
	}

	/** {@return how many elements} */
	public final int length() {
		return values.length;
	}

	/**
	 * @param i the index
	 * @return the element
	 */
	@SuppressWarnings("unchecked")
	public final E get(int i) {
		return (E) values[i];
	}

	/**
	 * @param i the index
	 * @param newValue the element to store
	 */
	public final void set(int i, E newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the element to store
	 */
	public final void lazySet(int i, E newValue) {
		values[i] = newValue;
	}

	/**
	 * @param i the index
	 * @param newValue the element to store
	 * @return the element that was there
	 */
	@SuppressWarnings("unchecked")
	public final E getAndSet(int i, E newValue) {
		E old = (E) values[i];
		values[i] = newValue;
		return old;
	}

	/**
	 * @param i the index
	 * @param expect the element that must be there
	 * @param update the element to store
	 * @return whether it was stored
	 */
	public final boolean compareAndSet(int i, E expect, E update) {
		if (values[i] != expect) return false;
		values[i] = update;
		return true;
	}

	/**
	 * @param i the index
	 * @param expect the element that must be there
	 * @param update the element to store
	 * @return whether it was stored
	 */
	public final boolean weakCompareAndSet(int i, E expect, E update) {
		return compareAndSet(i, expect, update);
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
