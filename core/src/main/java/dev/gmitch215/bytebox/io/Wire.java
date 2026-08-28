package dev.gmitch215.bytebox.io;

import java.util.List;

/**
 * The constants the Java Object Serialization Specification fixes, and the descriptors for the class
 * library types a field can hold.
 *
 * <p>None of these are ours to choose. Every value here is either a tag the specification names or a
 * {@code serialVersionUID} read off a real JVM, and {@code SerialConformanceTest} asserts each one
 * against {@code ObjectStreamClass.lookup} so a wrong copy fails rather than corrupting a stream.
 */
final class Wire {

	static final int MAGIC = 0xACED;
	static final int VERSION = 5;

	/** The first handle a stream hands out. Every object, string, array and descriptor takes one. */
	static final int BASE_HANDLE = 0x7E0000;

	static final byte TC_NULL = 0x70;
	static final byte TC_REFERENCE = 0x71;
	static final byte TC_CLASSDESC = 0x72;
	static final byte TC_OBJECT = 0x73;
	static final byte TC_STRING = 0x74;
	static final byte TC_ARRAY = 0x75;
	static final byte TC_ENDBLOCKDATA = 0x78;
	static final byte TC_LONGSTRING = 0x7C;
	static final byte TC_ENUM = 0x7E;

	/** Beyond this many bytes a string is written with an eight-byte length instead of two. */
	static final int LONG_STRING = 0xFFFF;

	static final SerialDescriptor NUMBER = descriptor("java.lang.Number", -8742448824652078965L);
	static final SerialDescriptor ENUM = SerialDescriptor.JAVA_LANG_ENUM;

	private Wire() {}

	/** A class library type with no fields of its own: an array, a string, or {@code Number}. */
	static SerialDescriptor descriptor(String className, long serialVersionUID) {
		return new SerialDescriptor(
			className,
			serialVersionUID,
			SerialDescriptor.SERIALIZABLE,
			List.of()
		);
	}

	/** A boxed primitive, which carries one field named {@code value} and extends {@code Number}. */
	static List<SerialDescriptor> boxed(String className, long serialVersionUID, char typeCode) {
		SerialDescriptor own = new SerialDescriptor(
			className,
			serialVersionUID,
			SerialDescriptor.SERIALIZABLE,
			List.of(new SerialField("value", typeCode, null))
		);
		boolean number =
			!className.equals("java.lang.Boolean") && !className.equals("java.lang.Character");
		return number ? List.of(own, NUMBER) : List.of(own);
	}

	static final List<SerialDescriptor> BYTE = boxed("java.lang.Byte", -7183698231559129828L, 'B');
	static final List<SerialDescriptor> SHORT = boxed("java.lang.Short", 7515723908773894738L, 'S');
	static final List<SerialDescriptor> INTEGER = boxed(
		"java.lang.Integer",
		1360826667806852920L,
		'I'
	);
	static final List<SerialDescriptor> LONG = boxed("java.lang.Long", 4290774380558885855L, 'J');
	static final List<SerialDescriptor> FLOAT = boxed(
		"java.lang.Float",
		-2671257302660747028L,
		'F'
	);
	static final List<SerialDescriptor> DOUBLE = boxed(
		"java.lang.Double",
		-9172774392245257468L,
		'D'
	);
	static final List<SerialDescriptor> CHARACTER = boxed(
		"java.lang.Character",
		3786198910865385080L,
		'C'
	);
	static final List<SerialDescriptor> BOOLEAN = boxed(
		"java.lang.Boolean",
		-3665804199014368530L,
		'Z'
	);

	static final SerialDescriptor BYTE_ARRAY = descriptor("[B", -5984413125824719648L);
	static final SerialDescriptor BOOLEAN_ARRAY = descriptor("[Z", 6309297032502205922L);
	static final SerialDescriptor CHAR_ARRAY = descriptor("[C", -5753798564021173076L);
	static final SerialDescriptor SHORT_ARRAY = descriptor("[S", -1188055269542874886L);
	static final SerialDescriptor INT_ARRAY = descriptor("[I", 5600894804908749477L);
	static final SerialDescriptor LONG_ARRAY = descriptor("[J", 8655923659555304851L);
	static final SerialDescriptor FLOAT_ARRAY = descriptor("[F", 836686056779680834L);
	static final SerialDescriptor DOUBLE_ARRAY = descriptor("[D", 4514449696888150558L);
	static final SerialDescriptor STRING_ARRAY = descriptor(
		"[Ljava.lang.String;",
		-5921575005990323385L
	);
}
