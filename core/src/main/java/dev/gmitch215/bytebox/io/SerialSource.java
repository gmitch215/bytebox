package dev.gmitch215.bytebox.io;

/**
 * Where a codec reads one object's field values back.
 *
 * <p>The mirror of {@link SerialSink}: the same calls in the same order read what they wrote.
 *
 * @since 1.0.0
 */
public interface SerialSource {
	/** {@return the next value} */
	boolean readBoolean();

	/** {@return the next value} */
	byte readByte();

	/** {@return the next value} */
	char readChar();

	/** {@return the next value} */
	short readShort();

	/** {@return the next value} */
	int readInt();

	/** {@return the next value} */
	long readLong();

	/** {@return the next value} */
	float readFloat();

	/** {@return the next value} */
	double readDouble();

	/**
	 * Reads a reference.
	 *
	 * @param type what the field is declared as, which is what the value is checked against
	 * @param <T> the type
	 * @return the value, or {@code null}
	 */
	<T> T readObject(Class<T> type);

	/**
	 * Registers the instance being built, before any of its fields are read.
	 *
	 * <p>What makes a cycle work. A field that points back at the object holding it reads as a
	 * reference to a handle, and that handle has to already name the instance. A codec that builds its
	 * object from field values cannot do that, so it calls this the moment it has an object at all and
	 * before the first {@link #readObject}.
	 *
	 * <p>A record never needs it: its components are final and set by the constructor, so a record
	 * cannot hold a reference to itself in the first place.
	 *
	 * @param instance the object being built
	 */
	void claim(Object instance);
}
