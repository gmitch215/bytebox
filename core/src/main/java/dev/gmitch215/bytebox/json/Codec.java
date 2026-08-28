package dev.gmitch215.bytebox.json;

import dev.gmitch215.bytebox.js.TSObject;

/**
 * Converts one type to and from a JavaScript value.
 *
 * <p>Written by the Gradle plugin for every type it was told to generate one for, and registrable by
 * hand for anything it cannot see. There is no reflective fallback: the compiler emits only what a
 * program reaches, so a decoder discovered at runtime would need metadata for every field of every
 * type that might arrive, which is the closure dead-code elimination exists to avoid.
 *
 * @param <T> the type
 * @since 1.0.0
 */
public interface Codec<T> {
	/**
	 * Reads a value.
	 *
	 * @param value the JavaScript value
	 * @return the Java value
	 */
	T decode(TSObject value);

	/**
	 * Writes a value.
	 *
	 * @param value the Java value
	 * @return the JavaScript value
	 */
	TSObject encode(T value);
}
