package dev.gmitch215.bytebox.io;

/**
 * One field, as the serialization format describes it.
 *
 * @param name the field's name
 * @param typeCode the JVM type code: one of {@code BCDFIJSZ} for a primitive, {@code L} for a
 *     reference, {@code [} for an array
 * @param signature the JVM type signature for a reference or array, and {@code null} for a primitive
 * @since 1.0.0
 */
public record SerialField(String name, char typeCode, String signature) {
	/** {@return whether this field is a primitive, which decides where it sorts and how it is written} */
	public boolean primitive() {
		return typeCode != 'L' && typeCode != '[';
	}
}
