package dev.gmitch215.bytebox.io;

/**
 * Where a codec writes one object's field values.
 *
 * <p>Implemented by the writer and called by generated code. The order of the calls is the order of
 * the bytes, and the generator emits them in the order the format fixes, so nothing here decides
 * layout.
 *
 * @since 1.0.0
 */
public interface SerialSink {
	/**
	 * @param value the value
	 */
	void writeBoolean(boolean value);

	/**
	 * @param value the value
	 */
	void writeByte(byte value);

	/**
	 * @param value the value
	 */
	void writeChar(char value);

	/**
	 * @param value the value
	 */
	void writeShort(short value);

	/**
	 * @param value the value
	 */
	void writeInt(int value);

	/**
	 * @param value the value
	 */
	void writeLong(long value);

	/**
	 * @param value the value
	 */
	void writeFloat(float value);

	/**
	 * @param value the value
	 */
	void writeDouble(double value);

	/**
	 * Writes a reference: {@code null}, a string, a boxed primitive, an array, an enum constant, or
	 * another type with a codec.
	 *
	 * <p>An object already written in this stream is written as a reference to it, so a graph that
	 * shares or cycles comes back sharing and cycling.
	 *
	 * @param value the value, which may be {@code null}
	 */
	void writeObject(Object value);
}
