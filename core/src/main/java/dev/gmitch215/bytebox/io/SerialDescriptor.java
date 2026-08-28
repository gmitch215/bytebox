package dev.gmitch215.bytebox.io;

import java.util.List;

/**
 * One class's entry in a stream's class descriptor chain.
 *
 * <p>A stream describes the whole hierarchy, not just the class being written, because a reader has to
 * know which fields belong to which level. {@link SerialCodec#descriptors()} carries the chain.
 *
 * @param className the binary name, with dots and a {@code $} before a nested class's name
 * @param serialVersionUID the declared value, or the one computed from the class's shape
 * @param flags the descriptor flags: {@code 0x02} for serializable, plus {@code 0x10} for an enum
 * @param fields the fields this level declares, primitives first and then references, each group in
 *     alphabetical order, which is the order the format fixes
 * @since 1.0.0
 */
public record SerialDescriptor(
	String className,
	long serialVersionUID,
	byte flags,
	List<SerialField> fields
) {
	/** A class that takes part in serialization. */
	public static final byte SERIALIZABLE = 0x02;

	/** An enum class, whose constants are written by name. */
	public static final byte ENUM = 0x10;

	/**
	 * The descriptor every enum's chain ends with.
	 *
	 * <p>Written by generated code, so it is here rather than kept private: a wrong copy of it would
	 * produce a stream a JVM misreads, and {@code SerialConformanceTest} checks this one.
	 */
	public static final SerialDescriptor JAVA_LANG_ENUM = new SerialDescriptor(
		"java.lang.Enum",
		0L,
		(byte) (SERIALIZABLE | ENUM),
		List.of()
	);

	/**
	 * @param className the binary name
	 * @param serialVersionUID the identifier
	 * @param flags the descriptor flags
	 * @param fields the fields, which are copied
	 */
	public SerialDescriptor {
		fields = List.copyOf(fields);
	}
}
