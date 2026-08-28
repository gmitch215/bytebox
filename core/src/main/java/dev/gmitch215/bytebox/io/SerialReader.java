package dev.gmitch215.bytebox.io;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the stream.
 *
 * <p>The handle table is a list rather than a map: handles are handed out in order from a known base,
 * so the handle a reference names is an index. A forward reference cannot happen, because the format
 * assigns a handle before writing anything that could refer back to it.
 */
final class SerialReader implements SerialSource {

	private final byte[] in;
	private int at;
	private final List<Object> handles = new ArrayList<>();
	private int claiming = -1;

	SerialReader(byte[] in) {
		this.in = in;
	}

	<T> T read(Class<T> type) {
		int magic = u2();
		int version = u2();
		if (magic != Wire.MAGIC || version != Wire.VERSION) {
			throw new SerialException(
				"not a serialization stream: expected the magic ACED0005 and read " +
					Integer.toHexString(magic).toUpperCase() +
					Integer.toHexString(version).toUpperCase()
			);
		}
		T value = readObject(type);
		if (at != in.length) {
			throw new SerialException(
				in.length - at + " byte(s) follow the object, so the stream holds more than one"
			);
		}
		return value;
	}

	// #region values

	@Override
	public boolean readBoolean() {
		return u1() != 0;
	}

	@Override
	public byte readByte() {
		return (byte) u1();
	}

	@Override
	public char readChar() {
		return (char) u2();
	}

	@Override
	public short readShort() {
		return (short) u2();
	}

	@Override
	public int readInt() {
		return u4();
	}

	@Override
	public long readLong() {
		return ((long) u4() << 32) | (u4() & 0xFFFFFFFFL);
	}

	@Override
	public float readFloat() {
		return Float.intBitsToFloat(u4());
	}

	@Override
	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	@Override
	public <T> T readObject(Class<T> type) {
		byte tag = (byte) u1();
		return switch (tag) {
			case Wire.TC_NULL -> null;
			case Wire.TC_REFERENCE -> cast(type, handles.get(u4() - Wire.BASE_HANDLE));
			case Wire.TC_STRING -> cast(type, string(u2()));
			case Wire.TC_LONGSTRING -> cast(type, longString());
			case Wire.TC_ARRAY -> cast(type, array());
			case Wire.TC_ENUM -> cast(type, constant());
			case Wire.TC_OBJECT -> cast(type, object());
			default -> throw new SerialException(
				"unexpected tag 0x" + Integer.toHexString(tag & 0xFF) + " at byte " + (at - 1)
			);
		};
	}

	// #endregion

	// #region objects

	private String string(int utf) {
		String text = utf(utf);
		handles.add(text);
		return text;
	}

	private String longString() {
		long high = u4() & 0xFFFFFFFFL;
		long utf = (high << 32) | (u4() & 0xFFFFFFFFL);
		if (utf > Integer.MAX_VALUE) {
			throw new SerialException(
				"a string of " + utf + " bytes is longer than an array can be"
			);
		}
		return string((int) utf);
	}

	private Object object() {
		List<SerialDescriptor> chain = chain();
		SerialDescriptor descriptor = chain.get(0);

		List<SerialDescriptor> boxed = boxedChain(descriptor.className());
		if (boxed != null) {
			checkIdentifier(descriptor, boxed.get(0));
			int slot = handles.size();
			handles.add(null);
			Object value = boxedValue(descriptor.className());
			handles.set(slot, value);
			return value;
		}

		SerialCodec<Object> codec = Serial.codecFor(descriptor.className());
		if (codec == null) {
			throw new SerialException(
				"the stream holds a " +
					descriptor.className() +
					", which has no codec. Annotate that type @SerialType."
			);
		}
		checkIdentifier(descriptor, codec.descriptors().get(0));
		return build(codec);
	}

	/**
	 * Reserves this object's handle, then reads it.
	 *
	 * <p>The handle belongs to the object before its fields do, so a codec that has an instance early
	 * calls {@link #claim} and a cycle resolves. One that builds from values fills the slot on the way
	 * out, which is all a record can do and all a record needs.
	 */
	private Object build(SerialCodec<Object> codec) {
		int slot = handles.size();
		handles.add(null);
		int enclosing = claiming;
		claiming = slot;
		Object value;
		try {
			value = codec.readData(this);
		} finally {
			claiming = enclosing;
		}
		if (handles.get(slot) == null) handles.set(slot, value);
		return value;
	}

	@Override
	public void claim(Object instance) {
		if (claiming < 0) {
			throw new SerialException("claim was called with no object being read");
		}
		handles.set(claiming, instance);
	}

	/**
	 * An enum constant, whose payload is its name.
	 *
	 * <p>The generated codec turns the name back into the constant, which keeps
	 * {@code Enum.valueOf} and its reflection out of the compiled program.
	 */
	private Object constant() {
		SerialDescriptor descriptor = chain().get(0);
		SerialCodec<Object> codec = Serial.codecFor(descriptor.className());
		if (codec == null) {
			throw new SerialException(
				"the stream holds a constant of " +
					descriptor.className() +
					", which has no codec. Annotate that enum @SerialType."
			);
		}
		return build(codec);
	}

	private Object boxedValue(String className) {
		return switch (className) {
			case "java.lang.Byte" -> readByte();
			case "java.lang.Short" -> readShort();
			case "java.lang.Integer" -> readInt();
			case "java.lang.Long" -> readLong();
			case "java.lang.Float" -> readFloat();
			case "java.lang.Double" -> readDouble();
			case "java.lang.Character" -> readChar();
			default -> readBoolean();
		};
	}

	private static List<SerialDescriptor> boxedChain(String className) {
		return switch (className) {
			case "java.lang.Byte" -> Wire.BYTE;
			case "java.lang.Short" -> Wire.SHORT;
			case "java.lang.Integer" -> Wire.INTEGER;
			case "java.lang.Long" -> Wire.LONG;
			case "java.lang.Float" -> Wire.FLOAT;
			case "java.lang.Double" -> Wire.DOUBLE;
			case "java.lang.Character" -> Wire.CHARACTER;
			case "java.lang.Boolean" -> Wire.BOOLEAN;
			default -> null;
		};
	}

	private Object array() {
		List<SerialDescriptor> chain = chain();
		String className = chain.get(0).className();
		int slot = handles.size();
		handles.add(null);
		int size = u4();
		if (size < 0) throw new SerialException("an array of " + size + " elements");

		Object value = switch (className) {
			case "[B" -> bytes(size);
			case "[Z" -> booleans(size);
			case "[C" -> chars(size);
			case "[S" -> shorts(size);
			case "[I" -> ints(size);
			case "[J" -> longs(size);
			case "[F" -> floats(size);
			case "[D" -> doubles(size);
			case "[Ljava.lang.String;" -> strings(size);
			default -> throw new SerialException(
				"the stream holds an array of type " +
					className +
					", and only arrays of primitives and of String are supported"
			);
		};
		handles.set(slot, value);
		return value;
	}

	private byte[] bytes(int size) {
		byte[] value = new byte[size];
		for (int i = 0; i < size; i++) value[i] = readByte();
		return value;
	}

	private boolean[] booleans(int size) {
		boolean[] value = new boolean[size];
		for (int i = 0; i < size; i++) value[i] = readBoolean();
		return value;
	}

	private char[] chars(int size) {
		char[] value = new char[size];
		for (int i = 0; i < size; i++) value[i] = readChar();
		return value;
	}

	private short[] shorts(int size) {
		short[] value = new short[size];
		for (int i = 0; i < size; i++) value[i] = readShort();
		return value;
	}

	private int[] ints(int size) {
		int[] value = new int[size];
		for (int i = 0; i < size; i++) value[i] = readInt();
		return value;
	}

	private long[] longs(int size) {
		long[] value = new long[size];
		for (int i = 0; i < size; i++) value[i] = readLong();
		return value;
	}

	private float[] floats(int size) {
		float[] value = new float[size];
		for (int i = 0; i < size; i++) value[i] = readFloat();
		return value;
	}

	private double[] doubles(int size) {
		double[] value = new double[size];
		for (int i = 0; i < size; i++) value[i] = readDouble();
		return value;
	}

	private String[] strings(int size) {
		String[] value = new String[size];
		for (int i = 0; i < size; i++) value[i] = readObject(String.class);
		return value;
	}

	// #endregion

	// #region descriptors

	/** The chain, most derived first. A reference stands for a descriptor and everything above it. */
	private List<SerialDescriptor> chain() {
		List<SerialDescriptor> chain = new ArrayList<>();
		while (true) {
			byte tag = (byte) u1();
			if (tag == Wire.TC_NULL) return chain;
			if (tag == Wire.TC_REFERENCE) {
				Object referenced = handles.get(u4() - Wire.BASE_HANDLE);
				if (!(referenced instanceof SerialDescriptor descriptor)) {
					throw new SerialException("a class descriptor reference names something else");
				}
				chain.add(descriptor);
				return chain;
			}
			if (tag != Wire.TC_CLASSDESC) {
				throw new SerialException(
					"expected a class descriptor and read tag 0x" + Integer.toHexString(tag & 0xFF)
				);
			}
			chain.add(descriptor());
		}
	}

	private SerialDescriptor descriptor() {
		String className = utf(u2());
		long identifier = readLong();
		int slot = handles.size();
		handles.add(null);

		byte flags = (byte) u1();
		int count = u2();
		List<SerialField> fields = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			char typeCode = (char) u1();
			String name = utf(u2());
			String signature = typeCode == 'L' || typeCode == '[' ? readObject(String.class) : null;
			fields.add(new SerialField(name, typeCode, signature));
		}
		byte end = (byte) u1();
		if (end != Wire.TC_ENDBLOCKDATA) {
			throw new SerialException(
				className +
					" carries a class annotation, which means it has a custom writeObject." +
					" Streams written by one are not supported."
			);
		}

		SerialDescriptor descriptor = new SerialDescriptor(className, identifier, flags, fields);
		handles.set(slot, descriptor);
		return descriptor;
	}

	private static void checkIdentifier(SerialDescriptor stream, SerialDescriptor local) {
		if (stream.serialVersionUID() == local.serialVersionUID()) return;
		throw new SerialException(
			stream.className() +
				" was written with serialVersionUID " +
				stream.serialVersionUID() +
				" and this build has " +
				local.serialVersionUID() +
				", so the two do not agree on its shape"
		);
	}

	// #endregion

	// #region bytes

	@SuppressWarnings("unchecked")
	private <T> T cast(Class<T> type, Object value) {
		// no Class.cast: it needs class metadata the compiler is free to drop, and the generated
		// codec already knows what it asked for
		return (T) value;
	}

	private String utf(int utf) {
		char[] chars = new char[utf];
		int written = 0;
		int end = at + utf;
		while (at < end) {
			int first = u1();
			if ((first & 0x80) == 0) {
				chars[written++] = (char) first;
			} else if ((first & 0xE0) == 0xC0) {
				chars[written++] = (char) (((first & 0x1F) << 6) | (u1() & 0x3F));
			} else if ((first & 0xF0) == 0xE0) {
				int second = u1();
				int third = u1();
				chars[written++] = (char) (((first & 0x0F) << 12) |
					((second & 0x3F) << 6) |
					(third & 0x3F));
			} else {
				throw new SerialException(
					"byte 0x" + Integer.toHexString(first) + " does not start a UTF-8 sequence"
				);
			}
		}
		return new String(chars, 0, written);
	}

	private int u1() {
		if (at >= in.length) {
			throw new SerialException("the stream ends before the object does");
		}
		return in[at++] & 0xFF;
	}

	private int u2() {
		return (u1() << 8) | u1();
	}

	private int u4() {
		return (u1() << 24) | (u1() << 16) | (u1() << 8) | u1();
	}

	// #endregion
}
