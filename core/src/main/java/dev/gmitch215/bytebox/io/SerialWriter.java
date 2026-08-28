package dev.gmitch215.bytebox.io;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the stream.
 *
 * <p>Every object, string, array and class descriptor takes the next handle from one shared counter,
 * and a repeat is written as a reference to the handle it already has. The table is keyed by reference
 * identity rather than by equality, which is what {@code ObjectOutputStream} does: two equal but
 * distinct strings get two handles, and a stream that did otherwise would not be byte-identical.
 */
final class SerialWriter implements SerialSink {

	private byte[] out = new byte[256];
	private int length;

	private final Map<Object, Integer> handles = new IdentityHashMap<>();
	private final Map<String, Integer> descriptors = new HashMap<>();
	private int next = Wire.BASE_HANDLE;

	byte[] write(Object root) {
		u2(Wire.MAGIC);
		u2(Wire.VERSION);
		writeObject(root);
		byte[] bytes = new byte[length];
		System.arraycopy(out, 0, bytes, 0, length);
		return bytes;
	}

	// #region values

	@Override
	public void writeBoolean(boolean value) {
		u1(value ? 1 : 0);
	}

	@Override
	public void writeByte(byte value) {
		u1(value);
	}

	@Override
	public void writeChar(char value) {
		u2(value);
	}

	@Override
	public void writeShort(short value) {
		u2(value);
	}

	@Override
	public void writeInt(int value) {
		u4(value);
	}

	@Override
	public void writeLong(long value) {
		u4((int) (value >>> 32));
		u4((int) value);
	}

	@Override
	public void writeFloat(float value) {
		u4(Float.floatToIntBits(value));
	}

	@Override
	public void writeDouble(double value) {
		writeLong(Double.doubleToLongBits(value));
	}

	@Override
	public void writeObject(Object value) {
		if (value == null) {
			u1(Wire.TC_NULL);
			return;
		}
		Integer handle = handles.get(value);
		if (handle != null) {
			u1(Wire.TC_REFERENCE);
			u4(handle);
			return;
		}
		if (value instanceof String text) {
			string(text);
			return;
		}
		if (isArray(value)) {
			array(value);
			return;
		}
		if (value instanceof Enum<?> constant) {
			constant(constant);
			return;
		}
		List<SerialDescriptor> boxed = box(value);
		if (boxed != null) {
			boxedValue(value, boxed);
			return;
		}
		ordinary(value);
	}

	// #endregion

	// #region objects

	private void string(String text) {
		int utf = utfLength(text);
		if (utf > Wire.LONG_STRING) {
			u1(Wire.TC_LONGSTRING);
			u4(0);
			u4(utf);
		} else {
			u1(Wire.TC_STRING);
			u2(utf);
		}
		handles.put(text, next++);
		utf(text);
	}

	private void ordinary(Object value) {
		SerialCodec<Object> codec = Serial.codecFor(value.getClass());
		if (codec == null) {
			throw new SerialException(
				value.getClass().getName() +
					" has no codec, so it cannot be written. Annotate it @SerialType."
			);
		}
		u1(Wire.TC_OBJECT);
		chain(codec.descriptors());
		handles.put(value, next++);
		codec.writeData(value, this);
	}

	private void boxedValue(Object value, List<SerialDescriptor> chain) {
		u1(Wire.TC_OBJECT);
		chain(chain);
		handles.put(value, next++);
		if (value instanceof Byte number) writeByte(number);
		else if (value instanceof Short number) writeShort(number);
		else if (value instanceof Integer number) writeInt(number);
		else if (value instanceof Long number) writeLong(number);
		else if (value instanceof Float number) writeFloat(number);
		else if (value instanceof Double number) writeDouble(number);
		else if (value instanceof Character character) writeChar(character);
		else writeBoolean((Boolean) value);
	}

	/**
	 * An enum constant is its name, and its descriptor carries no fields.
	 *
	 * <p>The class name comes from the generated codec rather than from {@code getClass().getName()},
	 * because a class name read at run time is only present if the compiler kept it, and a wire format
	 * that depends on what dead-code elimination decided to keep is not a wire format.
	 */
	private void constant(Enum<?> value) {
		Class<?> declaring = value.getDeclaringClass();
		SerialCodec<Object> codec = Serial.codecFor(declaring);
		if (codec == null) {
			throw new SerialException(
				declaring.getName() +
					" has no codec, so the constant " +
					value.name() +
					" cannot be written. Annotate the enum @SerialType."
			);
		}
		u1(Wire.TC_ENUM);
		chain(codec.descriptors());
		handles.put(value, next++);
		writeObject(value.name());
	}

	private void array(Object value) {
		SerialDescriptor descriptor = arrayDescriptor(value);
		u1(Wire.TC_ARRAY);
		chain(List.of(descriptor));
		handles.put(value, next++);

		if (value instanceof byte[] bytes) {
			u4(bytes.length);
			for (byte element : bytes) writeByte(element);
		} else if (value instanceof boolean[] booleans) {
			u4(booleans.length);
			for (boolean element : booleans) writeBoolean(element);
		} else if (value instanceof char[] chars) {
			u4(chars.length);
			for (char element : chars) writeChar(element);
		} else if (value instanceof short[] shorts) {
			u4(shorts.length);
			for (short element : shorts) writeShort(element);
		} else if (value instanceof int[] ints) {
			u4(ints.length);
			for (int element : ints) writeInt(element);
		} else if (value instanceof long[] longs) {
			u4(longs.length);
			for (long element : longs) writeLong(element);
		} else if (value instanceof float[] floats) {
			u4(floats.length);
			for (float element : floats) writeFloat(element);
		} else if (value instanceof double[] doubles) {
			u4(doubles.length);
			for (double element : doubles) writeDouble(element);
		} else {
			String[] strings = (String[]) value;
			u4(strings.length);
			for (String element : strings) writeObject(element);
		}
	}

	/** By instanceof rather than {@code Class.isArray}, so nothing here needs class metadata kept. */
	private static boolean isArray(Object value) {
		return (
			value instanceof byte[] ||
			value instanceof boolean[] ||
			value instanceof char[] ||
			value instanceof short[] ||
			value instanceof int[] ||
			value instanceof long[] ||
			value instanceof float[] ||
			value instanceof double[] ||
			value instanceof Object[]
		);
	}

	private static SerialDescriptor arrayDescriptor(Object value) {
		if (value instanceof byte[]) return Wire.BYTE_ARRAY;
		if (value instanceof boolean[]) return Wire.BOOLEAN_ARRAY;
		if (value instanceof char[]) return Wire.CHAR_ARRAY;
		if (value instanceof short[]) return Wire.SHORT_ARRAY;
		if (value instanceof int[]) return Wire.INT_ARRAY;
		if (value instanceof long[]) return Wire.LONG_ARRAY;
		if (value instanceof float[]) return Wire.FLOAT_ARRAY;
		if (value instanceof double[]) return Wire.DOUBLE_ARRAY;
		if (value instanceof String[]) return Wire.STRING_ARRAY;
		throw new SerialException(
			"an array of " +
				value.getClass().getName() +
				" cannot be written: only arrays of primitives and of String are supported"
		);
	}

	static List<SerialDescriptor> box(Object value) {
		if (value instanceof Byte) return Wire.BYTE;
		if (value instanceof Short) return Wire.SHORT;
		if (value instanceof Integer) return Wire.INTEGER;
		if (value instanceof Long) return Wire.LONG;
		if (value instanceof Float) return Wire.FLOAT;
		if (value instanceof Double) return Wire.DOUBLE;
		if (value instanceof Character) return Wire.CHARACTER;
		if (value instanceof Boolean) return Wire.BOOLEAN;
		return null;
	}

	// #endregion

	// #region descriptors

	/** The chain, most derived first, each one's superclass following it and {@code null} at the end. */
	private void chain(List<SerialDescriptor> chain) {
		for (SerialDescriptor descriptor : chain) {
			Integer handle = descriptors.get(descriptor.className());
			if (handle != null) {
				u1(Wire.TC_REFERENCE);
				u4(handle);
				return;
			}
			descriptor(descriptor);
		}
		u1(Wire.TC_NULL);
	}

	private void descriptor(SerialDescriptor descriptor) {
		u1(Wire.TC_CLASSDESC);
		u2(utfLength(descriptor.className()));
		utf(descriptor.className());
		writeLong(descriptor.serialVersionUID());
		descriptors.put(descriptor.className(), next++);

		u1(descriptor.flags());
		u2(descriptor.fields().size());
		for (SerialField field : descriptor.fields()) {
			u1(field.typeCode());
			u2(utfLength(field.name()));
			utf(field.name());
			if (!field.primitive()) writeObject(field.signature());
		}
		u1(Wire.TC_ENDBLOCKDATA);
	}

	// #endregion

	// #region bytes

	/**
	 * Modified UTF-8, which is not UTF-8: a zero character is two bytes rather than one, and a
	 * character outside the basic plane is written as its two surrogates at three bytes each rather
	 * than as one four-byte sequence.
	 */
	static int utfLength(String text) {
		int total = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0x0001 && c <= 0x007F) total += 1;
			else if (c <= 0x07FF) total += 2;
			else total += 3;
		}
		return total;
	}

	private void utf(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0x0001 && c <= 0x007F) {
				u1(c);
			} else if (c <= 0x07FF) {
				u1(0xC0 | (c >> 6));
				u1(0x80 | (c & 0x3F));
			} else {
				u1(0xE0 | (c >> 12));
				u1(0x80 | ((c >> 6) & 0x3F));
				u1(0x80 | (c & 0x3F));
			}
		}
	}

	private void u1(int value) {
		if (length == out.length) {
			byte[] grown = new byte[out.length * 2];
			System.arraycopy(out, 0, grown, 0, length);
			out = grown;
		}
		out[length++] = (byte) value;
	}

	private void u2(int value) {
		u1(value >> 8);
		u1(value);
	}

	private void u4(int value) {
		u1(value >> 24);
		u1(value >> 16);
		u1(value >> 8);
		u1(value);
	}

	// #endregion
}
