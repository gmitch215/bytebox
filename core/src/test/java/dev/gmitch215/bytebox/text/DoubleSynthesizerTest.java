package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the values Double.parseDouble and Float.parseFloat are made from")
class DoubleSynthesizerTest {

	/** What the class library's lexer collects: up to 19 digits, the first at 10^18, and where it sits. */
	private static String handedOver(String decimal) {
		BigDecimal value = new BigDecimal(decimal);
		String digits = value.unscaledValue().abs().toString();
		int exponent = digits.length() - value.scale() - 1;
		String first = (digits + "0".repeat(19)).substring(0, 19);
		return DoubleSynthesizer.text(Long.parseUnsignedLong(first), exponent);
	}

	@Test
	@DisplayName(
		"writes the digits and the power of ten the platform reads back as the same number"
	)
	void writesTheDecimal() {
		assertEquals("1000000000000000000e5", DoubleSynthesizer.text(1000000000000000000L, 23));
		assertEquals(1e23, Double.parseDouble(handedOver("1e23")));
		assertEquals(Double.MIN_VALUE, Double.parseDouble(handedOver("4.9E-324")));
		assertEquals(
			Double.longBitsToDouble(0x1dcb2e81a65dee3fL),
			Double.parseDouble(handedOver("3.6876394947873562E-165"))
		);
		assertEquals(
			Double.MAX_VALUE,
			Double.parseDouble(handedOver(Double.toString(Double.MAX_VALUE)))
		);
	}

	@Test
	@DisplayName("keeps a full 19 digits whole, though they take the sign bit of a long")
	void keepsTheLastDigits() {
		assertEquals("9999999999999999999e-18", DoubleSynthesizer.text(-8446744073709551617L, 0));
		assertTrue(Double.parseDouble(DoubleSynthesizer.text(-8446744073709551617L, 0)) > 9.99);
	}

	@Test
	@DisplayName("reads back what Double.toString wrote, for subnormals and the rest")
	void readsBackWhatWasWritten() {
		long state = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 20000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			long bits = i % 4 == 0 ? (state >>> 12) | 1 : state & Long.MAX_VALUE;
			double value = Double.longBitsToDouble(bits);
			if (Double.isNaN(value) || Double.isInfinite(value) || value == 0) continue;
			assertEquals(
				bits,
				Double.doubleToRawLongBits(Double.parseDouble(handedOver(Double.toString(value)))),
				Double.toString(value)
			);
		}
	}

	@Test
	@DisplayName("answers a zero without asking the platform, and keeps its sign")
	void answersZero() {
		assertEquals(
			0L,
			Double.doubleToRawLongBits(DoubleSynthesizer.synthesizeDouble(0, 5, false))
		);
		assertEquals(
			Double.doubleToRawLongBits(-0.0),
			Double.doubleToRawLongBits(DoubleSynthesizer.synthesizeDouble(0, 5, true))
		);
	}

	/** The float a JVM reads, from the digits the class library's lexer would keep of a decimal. */
	private static void assertFloat(String decimal) {
		BigDecimal value = new BigDecimal(decimal);
		String digits = value.unscaledValue().abs().toString();
		int exponent = digits.length() - value.scale() - 1;
		long mantissa = Long.parseUnsignedLong((digits + "0".repeat(19)).substring(0, 19));
		// the same decimal, with the digits the lexer kept and no others
		String kept = DoubleSynthesizer.text(mantissa, exponent);
		float expected = Float.parseFloat(kept);
		double nearest = Double.parseDouble(kept);
		assertEquals(
			Float.floatToRawIntBits(expected),
			Float.floatToRawIntBits(Parse.round(nearest, Long.toString(mantissa), exponent - 18)),
			kept
		);
	}

	@Test
	@DisplayName("reads a float once where the double sits exactly halfway between two floats")
	void roundsOnce() {
		// 1 + 3 * 2^-24 is the double halfway between two floats; a decimal a hair under it is below
		assertFloat("1.000000178813934326");
		assertFloat("1.000000178813934327");
		assertFloat("1.000000178813934328");
		assertFloat("1.0000000596046447754");
		assertFloat("1.0000000596046447752");
		assertFloat("1.0000000596046447756");
		// the same either side of the halfway point between the largest float and 2^128
		assertFloat("3.4028235677973366E38");
		assertFloat("3.4028235677973365E38");
		assertFloat("3.4028235677973367E38");
		assertFloat("7.0064923216240854E-46");
		assertFloat("7.0064923216240853E-46");
		assertFloat("7.0064923216240855E-46");
	}

	@Test
	@DisplayName("reads the floats a JVM reads, for decimals of up to nine digits")
	void readsFloats() {
		long state = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 20000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			long digits = ((state >>> 20) % 900000000L) + 100000000L;
			int exponent = (int) ((state & 0xFFFF) % 90) - 45;
			assertFloat(digits + "e" + exponent);
		}
		for (String decimal : new String[] {
			"16777217",
			"16777219",
			"0.1",
			"1e-45",
			"3.4028235e38",
			"1.4e-45"
		}) {
			assertFloat(decimal);
		}
	}

	@Test
	@DisplayName("compares a decimal with a double without rounding either")
	void comparesExactly() {
		long state = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 20000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			double value = Double.longBitsToDouble(state & Long.MAX_VALUE);
			if (Double.isNaN(value) || Double.isInfinite(value) || value == 0) continue;
			long mantissa = ((state >>> 11) % 100000000L) + 1;
			int power = (int) Math.floor(Math.log10(value)) - 7 + (int) ((state >>> 5) % 3) - 1;
			BigDecimal decimal = BigDecimal.valueOf(mantissa).scaleByPowerOfTen(power);
			assertEquals(
				decimal.compareTo(new BigDecimal(value)),
				Exact.compare(Long.toString(mantissa), power, value),
				mantissa + "e" + power + " against " + value
			);
		}
		assertEquals(0, Exact.compare("5", -1, 0.5));
		assertEquals(1, Exact.compare("51", -2, 0.5));
		assertEquals(-1, Exact.compare("49", -2, 0.5));
		assertEquals(1, Exact.compare("1", 23, 1e23));
		// more digits than a long holds, and a tail that only a full comparison sees
		assertEquals(0, Exact.compare("100000000000000000000000", -23, 1.0));
		assertEquals(1, Exact.compare("100000000000000000000001", -23, 1.0));
		assertEquals(-1, Exact.compare("99999999999999999999999", -23, 1.0));
	}
}
