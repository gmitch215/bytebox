package dev.gmitch215.bytebox.io;

import java.util.List;

/**
 * Reads and writes one type in Java's serialization format.
 *
 * <p>Written by the Gradle plugin for every type annotated {@link SerialType}. A hand-written one
 * registered for the same type replaces it, which is the escape hatch for a shape the generator
 * refuses.
 *
 * @param <T> the type
 * @since 1.0.0
 */
public interface SerialCodec<T> {
	/**
	 * {@return the class hierarchy's descriptors, most derived first}
	 *
	 * <p>The format writes the chain so a reader knows which fields belong to which level. A class
	 * whose superclass is not serializable has one entry.
	 */
	List<SerialDescriptor> descriptors();

	/**
	 * Writes every field value.
	 *
	 * <p>Most super class first, and within each class the order of its {@link SerialDescriptor#fields}.
	 * That is the order the format fixes, so the generator emits the calls and this does not choose.
	 *
	 * @param value the object
	 * @param sink where the values go
	 */
	void writeData(T value, SerialSink sink);

	/**
	 * Reads every field value back and builds the object.
	 *
	 * <p>The instance is built from the values rather than allocated and filled, because there is no
	 * {@code Unsafe} on this platform to allocate one without running a constructor. A record is built
	 * through its canonical constructor and anything else through its no-argument constructor.
	 *
	 * @param source where the values come from
	 * @return the object
	 */
	T readData(SerialSource source);
}
