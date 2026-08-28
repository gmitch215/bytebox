package dev.gmitch215.bytebox.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes objects in Java's serialization format, standing in for
 * {@code java.io.ObjectOutputStream}.
 *
 * <p>The compiler substitutes the class library's for this one, which the class library does not have
 * at all, so a library that serialises the ordinary way works unchanged.
 *
 * {@snippet lang = "java":
 * ByteArrayOutputStream bytes = new ByteArrayOutputStream();
 * try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
 * 	out.writeObject(new Order("abc", 2, 3L));
 * }
 *}
 *
 * <p>One object per stream. A JVM writes a header once and then any number of objects, sharing the
 * handle table between them; here each {@link #writeObject} is a complete stream, so writing twice
 * throws rather than producing bytes a reader would misread.
 *
 * <p>Every type written needs a codec, which the build writes for anything annotated
 * {@link SerialType}. {@link Serial} is the same thing without a stream in the way.
 *
 * @since 1.0.0
 */
public class ObjectOutputStream implements AutoCloseable {

	private final OutputStream out;
	private boolean written;

	/**
	 * @param out where the bytes go
	 */
	public ObjectOutputStream(OutputStream out) {
		this.out = out;
	}

	/**
	 * Writes an object.
	 *
	 * @param value the object, which may be {@code null}
	 * @throws IOException if the underlying stream refuses the bytes
	 * @throws SerialException if the object, or anything it holds, has no codec
	 */
	public void writeObject(Object value) throws IOException {
		if (written) {
			throw new SerialException(
				"this stream already holds an object. A stream here carries one object, because the" +
					" handle table is not shared across a second."
			);
		}
		written = true;
		out.write(Serial.encode(value));
	}

	/**
	 * Flushes the underlying stream.
	 *
	 * @throws IOException if the underlying stream refuses
	 */
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}
}
