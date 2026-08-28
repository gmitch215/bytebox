package dev.gmitch215.bytebox.json;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts between Java types and JSON, through codecs rather than reflection.
 *
 * <p>The Gradle plugin generates and registers a codec for every type annotated
 * {@link JSONType}, so the common case needs nothing but the annotation:
 *
 * {@snippet lang = "java":
 * @JSONType
 * public record Settings(String host, int port) {}
 *
 * Settings settings = request.json(Settings.class);
 *}
 *
 * <p>Nothing here reflects. A reflective decoder would have to keep field metadata for every type
 * that could arrive through an {@code Object}-typed field, which is the whole-program closure dead
 * code elimination exists to prune — so the size of the binary would depend on how many types were
 * serialisable rather than on how many were used.
 *
 * <p>{@link dev.gmitch215.bytebox.Request#json(java.util.function.Function)} is the escape hatch for a
 * type with no codec, and costs nothing at build time.
 *
 * @since 1.0.0
 */
public final class JSON {

	private static final Map<Class<?>, Codec<?>> CODECS = new HashMap<>();

	private JSON() {}

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
	public static <T> void register(Class<T> type, Codec<T> codec) {
		CODECS.put(type, codec);
	}

	/**
	 * Whether a type has a codec.
	 *
	 * @param type the type
	 * @return whether it can be converted
	 */
	public static boolean handles(Class<?> type) {
		return CODECS.containsKey(type);
	}

	/**
	 * Reads a JavaScript value as a Java type.
	 *
	 * @param value the JavaScript value
	 * @param type the type
	 * @param <T> the type
	 * @return the Java value, or {@code null} when the JavaScript value was null
	 */
	public static <T> T decode(TSObject value, Class<T> type) {
		if (value == null || value.isNull()) return null;
		return codec(type).decode(value);
	}

	/**
	 * Writes a Java value as a JavaScript value.
	 *
	 * @param value the Java value
	 * @param type the type
	 * @param <T> the type
	 * @return the JavaScript value
	 */
	public static <T> TSObject encode(T value, Class<T> type) {
		if (value == null) return TSObject.nullValue();
		return codec(type).encode(value);
	}

	/**
	 * Parses JSON text as a Java type.
	 *
	 * @param json the JSON
	 * @param type the type
	 * @param <T> the type
	 * @return the Java value
	 */
	public static <T> T parse(String json, Class<T> type) {
		return decode(TSObject.fromJson(json), type);
	}

	/**
	 * Serialises a Java value as JSON text.
	 *
	 * @param value the Java value
	 * @param type the type
	 * @param <T> the type
	 * @return the JSON
	 */
	public static <T> String stringify(T value, Class<T> type) {
		return encode(value, type).toJson();
	}

	@SuppressWarnings("unchecked")
	private static <T> Codec<T> codec(Class<T> type) {
		Codec<?> codec = CODECS.get(type);
		if (codec == null) {
			throw new IllegalStateException(
				"no JSON codec is registered for " +
					type.getName() +
					". Annotate it @JSONType so the plugin generates one, register a Codec by hand, or" +
					" read the body with json(mapper) instead."
			);
		}
		return (Codec<T>) codec;
	}
}
