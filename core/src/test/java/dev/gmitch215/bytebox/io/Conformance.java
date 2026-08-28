package dev.gmitch215.bytebox.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.util.List;

/** What both halves of the conformance suite need: a real JVM's answer, to compare against. */
final class Conformance {

	private Conformance() {}

	/** Written both ways and compared byte for byte, then written again to check it is stable. */
	static void assertMatches(Object value) {
		byte[] ours = Serial.encode(value);
		assertArrayEquals(jvm(value), ours, "the stream differs from the runtime's");
		assertEquals(ours.length, Serial.encode(value).length, "encoding is not stable");
	}

	/** What a real {@code ObjectOutputStream} writes for the same object. */
	static byte[] jvm(Object value) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (java.io.ObjectOutputStream stream = new java.io.ObjectOutputStream(out)) {
			stream.writeObject(value);
		} catch (IOException impossible) {
			throw new AssertionError("a byte array stream cannot fail", impossible);
		}
		return out.toByteArray();
	}

	/** What a real {@code ObjectInputStream} reads back from a stream we wrote. */
	static Object deserialise(byte[] wire) throws IOException, ClassNotFoundException {
		try (
			java.io.ObjectInputStream stream = new java.io.ObjectInputStream(
				new ByteArrayInputStream(wire)
			)
		) {
			return stream.readObject();
		}
	}

	static long uid(Class<?> type) {
		ObjectStreamClass descriptor = ObjectStreamClass.lookup(type);
		assertNotNull(descriptor, type.getName() + " is not serializable");
		return descriptor.getSerialVersionUID();
	}

	/**
	 * That a codec lists its fields the way the runtime sorts them.
	 *
	 * <p>The order is not ours: the format puts primitives before references and sorts each group by
	 * name, and a codec listing them any other way writes a stream a JVM misreads.
	 */
	static void assertFieldsMatch(Class<?> type, SerialCodec<?> codec) {
		ObjectStreamField[] expected = ObjectStreamClass.lookup(type).getFields();
		List<SerialField> actual = codec.descriptors().get(0).fields();
		assertEquals(expected.length, actual.size(), type.getName() + " field count");
		for (int i = 0; i < expected.length; i++) {
			assertEquals(
				expected[i].getName(),
				actual.get(i).name(),
				type.getName() + " field " + i
			);
			assertEquals(
				expected[i].getTypeCode(),
				actual.get(i).typeCode(),
				type.getName() + " type code " + i
			);
			assertEquals(
				expected[i].getTypeString(),
				actual.get(i).signature(),
				type.getName() + " signature " + i
			);
		}
	}

	/** Where an eight-byte big-endian value sits, so a test can corrupt exactly it. */
	static int indexOfIdentifier(byte[] wire, long identifier) {
		for (int at = 0; at + 8 <= wire.length; at++) {
			long read = 0;
			for (int i = 0; i < 8; i++) read = (read << 8) | (wire[at + i] & 0xFF);
			if (read == identifier) return at;
		}
		return -1;
	}
}
