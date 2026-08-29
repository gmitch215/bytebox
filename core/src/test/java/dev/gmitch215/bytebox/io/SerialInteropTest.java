package dev.gmitch215.bytebox.io;

import static dev.gmitch215.bytebox.io.Conformance.deserialise;
import static dev.gmitch215.bytebox.io.Conformance.indexOfIdentifier;
import static dev.gmitch215.bytebox.io.Conformance.jvm;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.io.Wires.Blobs;
import dev.gmitch215.bytebox.io.Wires.Boxed;
import dev.gmitch215.bytebox.io.Wires.Node;
import dev.gmitch215.bytebox.io.Wires.Order;
import dev.gmitch215.bytebox.io.Wires.Scalars;
import dev.gmitch215.bytebox.io.Wires.Status;
import dev.gmitch215.bytebox.io.Wires.Tagged;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Whether the two can read each other, and what happens when a stream is wrong.
 *
 * <p>Equal bytes and mutual readability are different claims, and a format is only interoperable if
 * both hold. {@link SerialFormatTest} covers the bytes.
 */
class SerialInteropTest {

	@BeforeAll
	static void registerCodecs() {
		Wires.register();
	}

	// #region reading each other

	@Test
	void theRuntimeReadsWhatWeWrite() throws Exception {
		Order order = new Order("abc", 2, 9_007_199_254_740_993L);
		assertEquals(order, deserialise(Serial.encode(order)));
	}

	@Test
	void weReadWhatTheRuntimeWrites() {
		Order order = new Order("abc", 2, -1L);
		assertEquals(order, Serial.decode(jvm(order), Order.class));
	}

	@Test
	void weReadTheRuntimesEnumsAndBoxes() {
		Tagged tagged = new Tagged(Status.OPEN, "note");
		assertEquals(tagged, Serial.decode(jvm(tagged), Tagged.class));

		Boxed boxed = new Boxed(1, 2L, false, 'c');
		assertEquals(boxed, Serial.decode(jvm(boxed), Boxed.class));
	}

	@Test
	void anEnumConstantComesBackAsTheConstant() {
		Tagged read = Serial.decode(Serial.encode(new Tagged(Status.CLOSED, "x")), Tagged.class);
		assertSame(Status.CLOSED, read.status());
	}

	@Test
	void anArrayComesBackWithItsContents() {
		Blobs blobs = new Blobs(new byte[] { 1, 2, 3 }, new int[] { 4, 5 }, new String[] { "six" });
		Blobs read = Serial.decode(Serial.encode(blobs), Blobs.class);
		assertArrayEquals(blobs.raw(), read.raw());
		assertArrayEquals(blobs.counts(), read.counts());
		assertArrayEquals(blobs.tags(), read.tags());
	}

	@Test
	void aSharedObjectComesBackShared() {
		Node tail = new Node("tail", null);
		Node head = new Node("head", tail);
		Node read = Serial.decode(Serial.encode(new Node("root", head)), Node.class);
		assertEquals("head", read.next.name);
		assertEquals("tail", read.next.next.name);
		assertNull(read.next.next.next);
	}

	@Test
	void aCycleComesBackAsACycle() {
		Node first = new Node("first", null);
		Node second = new Node("second", first);
		first.next = second;

		Node read = Serial.decode(Serial.encode(first), Node.class);
		assertEquals("first", read.name);
		assertEquals("second", read.next.name);
		assertSame(read, read.next.next);
	}

	@Test
	void aNullRootIsAStreamOfItsOwn() {
		byte[] wire = Serial.encode(null);
		assertArrayEquals(jvm(null), wire);
		assertNull(Serial.decode(wire, Order.class));
	}

	@Test
	void aStreamGoesThroughTheJavaIoNamesToo() throws Exception {
		Order order = new Order("abc", 2, 3L);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try (ObjectOutputStream stream = new ObjectOutputStream(out)) {
			stream.writeObject(order);
		}
		byte[] wire = out.toByteArray();
		assertArrayEquals(jvm(order), wire);

		try (
			ObjectInputStream stream = new ObjectInputStream(new java.io.ByteArrayInputStream(wire))
		) {
			assertEquals(order, stream.readObject(Order.class));
		}
	}

	@Test
	void aSecondObjectOnOneStreamIsRefused() throws Exception {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try (ObjectOutputStream stream = new ObjectOutputStream(out)) {
			stream.writeObject(new Order("a", 1, 1L));

			SerialException refused = assertThrows(SerialException.class, () ->
				stream.writeObject(new Order("b", 2, 2L))
			);
			assertTrue(refused.getMessage().contains("already holds an object"));
		}
	}

	// #endregion

	// #region refusals

	@Test
	void aStreamThatIsNotAStreamSays() {
		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(new byte[] { 1, 2, 3, 4, 5 }, Order.class)
		);
		assertTrue(refused.getMessage().contains("not a serialization stream"));
	}

	@Test
	void aTruncatedStreamSays() {
		byte[] wire = Serial.encode(new Order("abc", 2, 3L));
		byte[] cut = new byte[wire.length - 4];
		System.arraycopy(wire, 0, cut, 0, cut.length);

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(cut, Order.class)
		);
		assertTrue(refused.getMessage().contains("ends before"));
	}

	@Test
	void trailingBytesSay() {
		byte[] wire = Serial.encode(new Order("abc", 2, 3L));
		byte[] extra = new byte[wire.length + 2];
		System.arraycopy(wire, 0, extra, 0, wire.length);

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(extra, Order.class)
		);
		assertTrue(refused.getMessage().contains("follow the object"));
	}

	@Test
	void aTypeWithNoCodecSays() {
		List<String> unsupported = new ArrayList<>(List.of("a"));
		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.encode(unsupported)
		);
		assertTrue(refused.getMessage().contains("has no codec"));
	}

	@Test
	void anArrayOfSomethingUnsupportedSays() {
		Order[] array = { new Order("a", 1, 1L) };
		SerialException refused = assertThrows(SerialException.class, () -> Serial.encode(array));
		assertTrue(refused.getMessage().contains("only arrays of primitives and of String"));
	}

	/**
	 * A stream whose identifier disagrees is refused rather than read as though it agreed, which is the
	 * whole reason the identifier is on the wire.
	 */
	@Test
	void anIdentifierThatDisagreesSays() {
		byte[] wire = Serial.encode(new Node("a", null));
		int at = indexOfIdentifier(wire, 7L);
		assertTrue(at > 0, "the declared identifier should be in the stream");
		wire[at + 7] = 8;

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(wire, Node.class)
		);
		assertTrue(refused.getMessage().contains("do not agree on its shape"));
	}

	@Test
	void handlesReportsWhatIsRegistered() {
		assertTrue(Serial.handles(Order.class));
		assertTrue(Serial.handles(Status.class));
		assertFalse(Serial.handles(StringBuilder.class));
	}

	// #endregion

	// #region every shape the format carries

	@Test
	void everyPrimitiveComesBackAsItself() {
		Scalars scalars = new Scalars(
			true,
			(byte) -7,
			'z',
			(short) 1234,
			-9,
			1L << 40,
			1.5f,
			-2.25
		);

		assertEquals(scalars, Serial.decode(Serial.encode(scalars), Scalars.class));
		assertEquals(scalars, Serial.decode(jvm(scalars), Scalars.class));
	}

	@Test
	void everyBoxComesBackAsItself() {
		assertEquals((byte) 7, Serial.decode(jvm((byte) 7), Byte.class));
		assertEquals((short) 7, Serial.decode(jvm((short) 7), Short.class));
		assertEquals(7, Serial.decode(jvm(7), Integer.class));
		assertEquals(7L, Serial.decode(jvm(7L), Long.class));
		assertEquals(1.5f, Serial.decode(jvm(1.5f), Float.class));
		assertEquals(1.5, Serial.decode(jvm(1.5), Double.class));
		assertEquals('c', Serial.decode(jvm('c'), Character.class));
		assertEquals(true, Serial.decode(jvm(true), Boolean.class));
	}

	@Test
	void everyArrayOfPrimitivesComesBackAsItself() {
		assertArrayEquals(new byte[] { 1, -1 }, roundTrip(new byte[] { 1, -1 }, byte[].class));
		assertArrayEquals(
			new boolean[] { true, false },
			roundTrip(new boolean[] { true, false }, boolean[].class)
		);
		assertArrayEquals(
			new char[] { 'a', 'z' },
			roundTrip(new char[] { 'a', 'z' }, char[].class)
		);
		assertArrayEquals(new short[] { 1, -2 }, roundTrip(new short[] { 1, -2 }, short[].class));
		assertArrayEquals(new int[] { 1, -2 }, roundTrip(new int[] { 1, -2 }, int[].class));
		assertArrayEquals(new long[] { 1L, -2L }, roundTrip(new long[] { 1L, -2L }, long[].class));
		assertArrayEquals(
			new float[] { 1.5f, -2.5f },
			roundTrip(new float[] { 1.5f, -2.5f }, float[].class)
		);
		assertArrayEquals(
			new double[] { 1.5, -2.5 },
			roundTrip(new double[] { 1.5, -2.5 }, double[].class)
		);
		assertArrayEquals(
			new String[] { "a", null },
			roundTrip(new String[] { "a", null }, String[].class)
		);
	}

	/**
	 * A string is written in the modified UTF-8 the format uses, so a character outside ASCII takes
	 * two or three bytes and the length prefix counts bytes rather than characters.
	 */
	@Test
	void aStringOutsideAsciiComesBackWhole() {
		Order order = new Order("café 日本語", 1, 1L);

		assertArrayEquals(jvm(order), Serial.encode(order));
		assertEquals(order, Serial.decode(Serial.encode(order), Order.class));
		assertEquals(order, Serial.decode(jvm(order), Order.class));
	}

	/** Past 65535 bytes the format switches to a second string tag with a 64-bit length. */
	@Test
	void aStringPastTheShortLimitComesBackWhole() {
		Order order = new Order("x".repeat(70_000), 1, 1L);

		assertArrayEquals(jvm(order), Serial.encode(order));
		assertEquals(order, Serial.decode(Serial.encode(order), Order.class));
		assertEquals(order, Serial.decode(jvm(order), Order.class));
	}

	// #endregion

	// #region streams that are wrong

	@Test
	void aTagThatIsNotAnObjectSays() {
		byte[] wire = Serial.encode(new Order("abc", 2, 3L));
		wire[4] = 0x7F;

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(wire, Order.class)
		);
		assertTrue(refused.getMessage().contains("unexpected tag 0x7f"), refused.getMessage());
	}

	@Test
	void somethingOtherThanAClassDescriptorSays() {
		byte[] wire = Serial.encode(new Order("abc", 2, 3L));
		wire[5] = 0x7F;

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(wire, Order.class)
		);
		assertTrue(
			refused.getMessage().contains("expected a class descriptor"),
			refused.getMessage()
		);
	}

	@Test
	void aByteThatStartsNoUtfSequenceSays() {
		byte[] wire = Serial.encode(new Order("abcdefgh", 2, 3L));
		// the payload is the last thing written, so the final byte is inside the string
		wire[wire.length - 1] = (byte) 0xF8;

		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(wire, Order.class)
		);
		assertTrue(
			refused.getMessage().contains("does not start a UTF-8 sequence"),
			refused.getMessage()
		);
	}

	@Test
	void aClassTheBuildNeverSawSays() {
		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(jvm(new Unregistered("a")), Unregistered.class)
		);

		assertTrue(refused.getMessage().contains("has no codec"), refused.getMessage());
		assertTrue(refused.getMessage().contains("@SerialType"), refused.getMessage());
	}

	@Test
	void anEnumTheBuildNeverSawSays() {
		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.decode(jvm(Unregistered.Kind.ONE), Unregistered.Kind.class)
		);

		assertTrue(refused.getMessage().contains("constant of"), refused.getMessage());
	}

	@Test
	void anEnumWithNoCodecCannotBeWrittenEither() {
		SerialException refused = assertThrows(SerialException.class, () ->
			Serial.encode(Unregistered.Kind.ONE)
		);

		assertTrue(refused.getMessage().contains("cannot be written"), refused.getMessage());
	}

	@Test
	void claimingWithNothingBeingReadSays() {
		SerialException refused = assertThrows(SerialException.class, () ->
			new SerialReader(new byte[0]).claim("anything")
		);

		assertTrue(refused.getMessage().contains("no object being read"), refused.getMessage());
	}

	// #endregion

	private static <T> T roundTrip(T value, Class<T> type) {
		assertArrayEquals(jvm(value), Serial.encode(value), type.getSimpleName());
		assertEquals(
			type.cast(Serial.decode(jvm(value), type)).getClass(),
			type,
			"the runtime's own bytes read back as the same type"
		);
		return Serial.decode(Serial.encode(value), type);
	}

	private record Unregistered(String value) implements java.io.Serializable {
		enum Kind {
			ONE
		}
	}
}
