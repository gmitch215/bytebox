package dev.gmitch215.bytebox.io;

import static dev.gmitch215.bytebox.io.Conformance.assertFieldsMatch;
import static dev.gmitch215.bytebox.io.Conformance.assertMatches;
import static dev.gmitch215.bytebox.io.Conformance.jvm;
import static dev.gmitch215.bytebox.io.Conformance.uid;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.gmitch215.bytebox.io.Wires.Blobs;
import dev.gmitch215.bytebox.io.Wires.Boxed;
import dev.gmitch215.bytebox.io.Wires.Node;
import dev.gmitch215.bytebox.io.Wires.Order;
import dev.gmitch215.bytebox.io.Wires.Scalars;
import dev.gmitch215.bytebox.io.Wires.Status;
import dev.gmitch215.bytebox.io.Wires.Tagged;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The bytes, compared against the runtime that defines them.
 *
 * <p>The claim these codecs make is that their bytes are the bytes a JVM writes, and this test lane
 * runs on a JVM, so the claim is checkable rather than assertable: every fixture is written both ways
 * and the two arrays are compared. Whether the two can read each other is a separate claim, in
 * {@link SerialInteropTest}.
 */
class SerialFormatTest {

	@BeforeAll
	static void registerCodecs() {
		Wires.register();
	}

	// #region the borrowed constants

	@Test
	void everyBorrowedIdentifierMatchesTheRuntime() {
		assertEquals(uid(Number.class), Wire.NUMBER.serialVersionUID());
		assertEquals(uid(Enum.class), Wire.ENUM.serialVersionUID());

		assertEquals(uid(Byte.class), Wire.BYTE.get(0).serialVersionUID());
		assertEquals(uid(Short.class), Wire.SHORT.get(0).serialVersionUID());
		assertEquals(uid(Integer.class), Wire.INTEGER.get(0).serialVersionUID());
		assertEquals(uid(Long.class), Wire.LONG.get(0).serialVersionUID());
		assertEquals(uid(Float.class), Wire.FLOAT.get(0).serialVersionUID());
		assertEquals(uid(Double.class), Wire.DOUBLE.get(0).serialVersionUID());
		assertEquals(uid(Character.class), Wire.CHARACTER.get(0).serialVersionUID());
		assertEquals(uid(Boolean.class), Wire.BOOLEAN.get(0).serialVersionUID());

		assertEquals(uid(byte[].class), Wire.BYTE_ARRAY.serialVersionUID());
		assertEquals(uid(boolean[].class), Wire.BOOLEAN_ARRAY.serialVersionUID());
		assertEquals(uid(char[].class), Wire.CHAR_ARRAY.serialVersionUID());
		assertEquals(uid(short[].class), Wire.SHORT_ARRAY.serialVersionUID());
		assertEquals(uid(int[].class), Wire.INT_ARRAY.serialVersionUID());
		assertEquals(uid(long[].class), Wire.LONG_ARRAY.serialVersionUID());
		assertEquals(uid(float[].class), Wire.FLOAT_ARRAY.serialVersionUID());
		assertEquals(uid(double[].class), Wire.DOUBLE_ARRAY.serialVersionUID());
		assertEquals(uid(String[].class), Wire.STRING_ARRAY.serialVersionUID());
	}

	@Test
	void aBoxedPrimitiveExtendsNumberExceptWhereItDoesNot() {
		assertEquals(2, Wire.INTEGER.size());
		assertEquals("java.lang.Number", Wire.INTEGER.get(1).className());
		assertEquals(1, Wire.BOOLEAN.size());
		assertEquals(1, Wire.CHARACTER.size());
	}

	@Test
	void everyFixtureListsItsFieldsTheWayTheRuntimeSortsThem() {
		assertFieldsMatch(Order.class, Wires.ORDER);
		assertFieldsMatch(Scalars.class, Wires.SCALARS);
		assertFieldsMatch(Boxed.class, Wires.BOXED);
		assertFieldsMatch(Blobs.class, Wires.BLOBS);
		assertFieldsMatch(Tagged.class, Wires.TAGGED);
		assertFieldsMatch(Node.class, Wires.NODE);
	}

	// #endregion

	// #region byte for byte

	@Test
	void aRecordOfMixedFieldsMatches() {
		assertMatches(new Order("abc", 2, 9_007_199_254_740_993L));
	}

	@Test
	void everyPrimitiveMatches() {
		assertMatches(
			new Scalars(
				true,
				(byte) -7,
				'Z',
				(short) -30_000,
				-123_456,
				Long.MIN_VALUE,
				1.5f,
				-0.25
			)
		);
	}

	@Test
	void aBoxedFieldMatches() {
		assertMatches(new Boxed(42, 43L, true, 'q'));
	}

	@Test
	void anArrayFieldMatches() {
		assertMatches(
			new Blobs(
				new byte[] { 0, 127, -128, 1 },
				new int[] { 1, -1, Integer.MAX_VALUE },
				new String[] { "one", "two" }
			)
		);
	}

	@Test
	void anEnumFieldMatches() {
		assertMatches(new Tagged(Status.CLOSED, "shut"));
	}

	@Test
	void aClassWithADeclaredIdentifierMatches() {
		assertMatches(new Node("head", new Node("tail", null)));
	}

	@Test
	void aNullReferenceMatches() {
		assertMatches(new Order(null, 0, 0L));
	}

	@Test
	void anEmptyArrayMatches() {
		assertMatches(new Blobs(new byte[0], new int[0], new String[0]));
	}

	@Test
	void aCycleMatchesTheRuntimeByteForByte() {
		Node first = new Node("first", null);
		Node second = new Node("second", first);
		first.next = second;
		assertMatches(first);
	}

	@Test
	void aRepeatedStringIsWrittenOnceAndReferencedAfter() {
		String shared = "same";
		Blobs blobs = new Blobs(new byte[0], new int[0], new String[] { shared, shared });
		assertArrayEquals(jvm(blobs), Serial.encode(blobs));
	}

	/**
	 * Two bytes for a Latin-1 character, three for one in the basic plane, and six for one outside it,
	 * because modified UTF-8 writes a character above the basic plane as its two surrogates at three
	 * bytes each rather than as one four-byte sequence.
	 *
	 * <p>Built from code points rather than written as a literal, because the Java formatter's parser
	 * reads neither a non-ASCII literal nor its escape. The characters are the same ones.
	 */
	@Test
	void modifiedUtf8Matches() {
		String text =
			"a b" +
			Character.toString(0xE9) +
			Character.toString(0x4E2D) +
			Character.toString(0x1F600);
		assertMatches(new Order(text, 1, 1L));
	}

	@Test
	void aStringLongerThanTwoBytesOfLengthMatches() {
		assertMatches(new Order("x".repeat(70_000), 1, 1L));
	}

	@Test
	void everyFloatingEdgeMatches() {
		assertMatches(new Scalars(false, (byte) 0, ' ', (short) 0, 0, 0L, Float.NaN, Double.NaN));
		assertMatches(
			new Scalars(
				false,
				(byte) 0,
				' ',
				(short) 0,
				0,
				0L,
				Float.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY
			)
		);
		// negative zero, which equals positive zero and does not share its bits
		assertMatches(new Scalars(false, (byte) 0, ' ', (short) 0, 0, 0L, -0.0f, -0.0));
	}

	// #endregion
}
