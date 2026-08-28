package dev.gmitch215.bytebox.io;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes objects in Java's own serialization format.
 *
 * <p>The bytes are the ones a JVM writes. A stream from here is readable by
 * {@code ObjectInputStream} and a stream from {@code ObjectOutputStream} is readable here, which is
 * what makes this usable for talking to something that already speaks the format rather than only for
 * talking to itself.
 *
 * {@snippet lang = "java":
 * @SerialType
 * public record Order(String sku, int quantity, long total) implements Serializable {}
 *
 * byte[] wire = Serial.encode(new Order("abc", 2, 9_007_199_254_740_993L));
 * Order back = Serial.decode(wire, Order.class);
 *}
 *
 * <p>Compare {@link dev.gmitch215.bytebox.json.JSON}, which is the right choice when both ends are
 * yours: JSON is smaller to read, and nothing about it has to agree with a JVM.
 *
 * @since 1.0.0
 */
public final class Serial {

	private static final Map<Class<?>, SerialCodec<?>> BY_TYPE = new HashMap<>();
	private static final Map<String, SerialCodec<?>> BY_NAME = new HashMap<>();

	private Serial() {}

	/**
	 * Registers a codec.
	 *
	 * <p>Called from the generated registration class during module evaluation. Registering the same
	 * type twice replaces the earlier codec, so a hand-written one can override a generated one.
	 *
	 * @param type the type
	 * @param codec the codec
	 * @param <T> the type
	 */
	public static <T> void register(Class<T> type, SerialCodec<T> codec) {
		BY_TYPE.put(type, codec);
		BY_NAME.put(codec.descriptors().get(0).className(), codec);
	}

	/**
	 * Whether a type can be read and written.
	 *
	 * @param type the type
	 * @return whether it has a codec
	 */
	public static boolean handles(Class<?> type) {
		return BY_TYPE.containsKey(type);
	}

	/**
	 * Writes an object.
	 *
	 * @param value the object
	 * @return the stream
	 * @throws SerialException if the object, or anything it holds, has no codec
	 */
	public static byte[] encode(Object value) {
		return new SerialWriter().write(value);
	}

	/**
	 * Reads an object.
	 *
	 * @param bytes the stream
	 * @param type what to read
	 * @param <T> the type
	 * @return the object
	 * @throws SerialException if the stream is malformed, holds a type with no codec, or names a
	 *     {@code serialVersionUID} this build does not agree with
	 */
	public static <T> T decode(byte[] bytes, Class<T> type) {
		return new SerialReader(bytes).read(type);
	}

	@SuppressWarnings("unchecked")
	static SerialCodec<Object> codecFor(Class<?> type) {
		return (SerialCodec<Object>) BY_TYPE.get(type);
	}

	@SuppressWarnings("unchecked")
	static SerialCodec<Object> codecFor(String className) {
		return (SerialCodec<Object>) BY_NAME.get(className);
	}
}
