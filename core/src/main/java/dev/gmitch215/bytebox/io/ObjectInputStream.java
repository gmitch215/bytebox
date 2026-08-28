package dev.gmitch215.bytebox.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads objects in Java's serialization format, standing in for {@code java.io.ObjectInputStream}.
 *
 * <p>Reads what a JVM wrote. {@link #readObject} takes the whole stream, because the format's handle
 * table belongs to one stream and a partial read cannot know where the object ends.
 *
 * {@snippet lang = "java":
 * try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(wire))) {
 * 	Order order = in.readObject(Order.class);
 * }
 *}
 *
 * <p>{@link #readObject()} with no type is here for a caller that has to look like a JVM's. It answers
 * {@code Object}, which a caller then casts, and the cast is unchecked on this platform because there
 * is no class metadata behind it to check against. {@link #readObject(Class)} is the one to use.
 *
 * @since 1.0.0
 */
public class ObjectInputStream implements AutoCloseable {

	private final InputStream in;

	/**
	 * @param in where the bytes come from
	 */
	public ObjectInputStream(InputStream in) {
		this.in = in;
	}

	/**
	 * Reads the object.
	 *
	 * @param type what to read
	 * @param <T> the type
	 * @return the object
	 * @throws IOException if the underlying stream cannot be read
	 * @throws SerialException if the stream is malformed or names a type with no codec
	 */
	public <T> T readObject(Class<T> type) throws IOException {
		return Serial.decode(in.readAllBytes(), type);
	}

	/**
	 * Reads the object without naming its type.
	 *
	 * @return the object
	 * @throws IOException if the underlying stream cannot be read
	 * @throws SerialException if the stream is malformed or names a type with no codec
	 */
	public Object readObject() throws IOException {
		return readObject(Object.class);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
