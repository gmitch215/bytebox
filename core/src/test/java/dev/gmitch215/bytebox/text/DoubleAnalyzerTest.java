package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the digits Double.toString is written from")
class DoubleAnalyzerTest {

	/** The digits without trailing zeros and the power of ten of the first, as the JDK writes them. */
	private static String expected(double value) {
		BigDecimal decimal = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
		int exponent = decimal.precision() - decimal.scale() - 1;
		return decimal.unscaledValue() + "E" + exponent;
	}

	private static String analysed(double value) {
		return analysed(value, false);
	}

	private static String analysed(double value, boolean exactOnly) {
		DoubleAnalyzer.Result result = new DoubleAnalyzer.Result();
		DoubleAnalyzer.analyze(value, result, exactOnly);
		String digits = Long.toString(result.mantissa).replaceAll("0+$", "");
		return digits + "E" + result.exponent;
	}

	private static void assertShortest(double value) {
		assertShortest(value, false);
	}

	/** Through the table arithmetic with its fallback, or through the exact route alone. */
	private static void assertShortest(double value, boolean exactOnly) {
		assertEquals(
			expected(value),
			analysed(value, exactOnly),
			"bits: " + Long.toHexString(Double.doubleToRawLongBits(value))
		);
	}

	/** Bits of doubles whose digits did not read back as the double in the class library's analyzer. */
	private static final long[] MISREAD = {
		0x1dcb2e81a65dee3fL,
		0xc382cbf215d7dc7bL,
		0x435938feb499fc57L,
		0x435422eebaa4b775L,
		0x438cc76ebdbbea4bL,
		0xd0f08a8968a661cbL,
		0x236d2d5461ce568aL,
		0x435417f2c488ec4fL,
		0x43556e735dac4e49L,
		0x436ac4ff63098fe5L,
		0x4367ded23b8d32bbL,
		0x43829b15b87e5d91L,
		0x436d4c9a9869cd1fL,
		0x436852a0fc647039L,
		0x43613ad8f0432a4dL,
		0x4360a32db069385bL,
		0x4354eb1a88df8289L,
		0x435e16d53bd94287L,
		0x4352e036889cdffbL,
		0x4388fddcd2be7337L,
		0x43605366f2182a67L,
		0x4365c0ac57a1b3c3L,
		0x43581d2215346c13L,
		0x4368fd5f6401e659L,
		0x436004d05e46c66bL,
		0x43b30b81e3d9cdb3L,
		0x435620e8fc078f3bL,
		0x43836b2e4a9859b9L,
		0x43bbae72aabaeb83L,
		0x43868f300838e349L,
		0x435f475cbe70c941L,
		0x4369d367e925352dL,
		0x4360625bc0eff637L,
		0x436a08b5dbca9c7dL,
		0x4360ea63da4caeb9L,
		0x43523293851f6b51L,
		0x436feeb8d5f07e05L,
		0x436767a04ebce8adL,
		0x43640340854c2595L,
		0x43734e0d68cad233L,
		0x436577ee3e9ac4b1L,
		0x436c4cf25ff8ebd5L,
		0x436f166e08100113L,
		0x438848667665cc1fL,
		0x436ed014408e8e51L,
		0x43665ffca3b55045L,
		0x435b216620bb0213L,
		0x439679120eb7bafdL,
		0x436f05a2b6a64999L,
		0x436cd3f6705ce121L,
		0x436627fe5b695d21L,
		0x436ec1b2db531391L,
		0x4367956c4f2fb5f5L,
		0x43691d4000dbcd91L,
		0x43655910458c32b1L,
		0x43b5c29c1d117765L,
		0x4380ecdc838167b1L,
		0x4370f66991d43753L,
		0x43680e34d07c7d5fL,
		0x4358c3ba7631778dL,
		0x437585ce8a219075L,
		0x4363bdb3d58d972dL,
		0x43a2059ba4f0f361L,
		0x436ecdcfd5d6b7a5L,
		0x43669def072e4575L,
		0x4374a8b22460356fL,
		0x4374bb43a6a28bc9L,
		0x439fd86dea352241L,
		0x3d2b6384ab8ab4dfL,
		0x436abf63169a7d2dL,
		0x4373252229cef2adL,
		0x435d0ddd46d1333bL
	};

	private static final double[] EDGES = {
		1.0,
		0.1,
		0.35,
		1.005,
		1e22,
		1e23,
		9.999999999999999E22,
		9007199254740993.0,
		123456789.0E7,
		2.2250738585072014E-308,
		Double.MIN_NORMAL,
		Double.MAX_VALUE,
		Math.nextDown(Double.MAX_VALUE),
		Math.nextUp(Double.MIN_NORMAL)
	};

	/** Doubles drawn over every exponent, and over the 53-bit fractions at each decade. */
	private static double[] drawn(int count) {
		long state = 0x9E3779B97F4A7C15L;
		double[] all = new double[count];
		for (int i = 0; i < count; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			double value =
				(i & 1) == 0
					? Double.longBitsToDouble(state & Long.MAX_VALUE)
					: (state >>> 11) * 0x1.0p-53 * Math.pow(10, (int) ((state & 0xFFFF) % 40) - 20);
			all[i] = Double.isNaN(value) || Double.isInfinite(value) || value == 0 ? 1.0 : value;
		}
		return all;
	}

	private static void assertAllShortest(boolean exactOnly) {
		for (long bits : MISREAD) assertShortest(Double.longBitsToDouble(bits), exactOnly);
		for (double value : EDGES) assertShortest(value, exactOnly);
		for (int exponent = 1; exponent < 2047; exponent++) {
			long power = (long) exponent << 52;
			for (long fraction : new long[] {
				0,
				1,
				2,
				0x8000000000000L,
				0xFFFFFFFFFFFFEL,
				0xFFFFFFFFFFFFFL
			}) {
				assertShortest(Double.longBitsToDouble(power | fraction), exactOnly);
			}
		}
		for (double value : drawn(20000)) assertShortest(value, exactOnly);
	}

	@Test
	@DisplayName("writes Double.MIN_VALUE as 4.9E-324, the form a modern JDK gives")
	void writesTheSmallestDouble() {
		assertEquals("49E-324", analysed(Double.MIN_VALUE));
		assertEquals("99E-324", analysed(Double.longBitsToDouble(2L)));
		assertEquals(-323, DoubleAnalyzer.fastIntLog10(Double.MIN_VALUE));
	}

	@Test
	@DisplayName("finds the shortest digits of every power-of-two subnormal and its neighbours")
	void findsTheDigitsOfPowersOfTwo() {
		for (int bit = 0; bit < 52; bit++) {
			long bits = 1L << bit;
			assertShortest(Double.longBitsToDouble(bits));
			if (bits > 1) assertShortest(Double.longBitsToDouble(bits - 1));
			assertShortest(Double.longBitsToDouble(bits + 1));
		}
		assertShortest(Double.longBitsToDouble(0x000fffffffffffffL));
		assertShortest(Double.longBitsToDouble(0x000ffffffffffffeL));
	}

	@Test
	@DisplayName("finds the shortest digits of the first few hundred subnormals")
	void findsTheDigitsOfTheFirstSubnormals() {
		for (long bits = 1; bits <= 600; bits++) assertShortest(Double.longBitsToDouble(bits));
	}

	@Test
	@DisplayName("finds the shortest digits of a few thousand drawn subnormals")
	void findsTheDigitsOfDrawnSubnormals() {
		long state = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 4000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			long bits = (state >>> (12 + (i % 52))) & 0xfffffffffffffL;
			if (bits != 0) assertShortest(Double.longBitsToDouble(bits));
		}
	}

	@Test
	@DisplayName("keeps the sign of a subnormal apart from its digits")
	void keepsTheSign() {
		DoubleAnalyzer.Result result = new DoubleAnalyzer.Result();
		DoubleAnalyzer.analyze(-Double.MIN_VALUE, result);
		assertTrue(result.sign);
		assertEquals("49E-324", analysed(-Double.MIN_VALUE));

		DoubleAnalyzer.analyze(Double.MIN_VALUE, result);
		assertFalse(result.sign);
	}

	@Test
	@DisplayName("leaves a normal double and the zeros as the class library has them")
	void leavesNormalDoublesAlone() {
		assertEquals("1E0", analysed(1.0));
		assertEquals("1E-1", analysed(0.1));
		assertEquals("22250738585072014E-308", analysed(Double.MIN_NORMAL));
		assertEquals("17976931348623157E308", analysed(Double.MAX_VALUE));

		DoubleAnalyzer.Result result = new DoubleAnalyzer.Result();
		DoubleAnalyzer.analyze(0.0, result);
		assertEquals(0, result.mantissa);
		DoubleAnalyzer.analyze(-0.0, result);
		assertEquals(0, result.mantissa);
		assertTrue(result.sign);
	}

	@Test
	@DisplayName(
		"writes the shortest digits of a normal double, the ones it once got wrong included"
	)
	void writesTheShortestDigitsOfNormals() {
		assertEquals("1E23", analysed(1e23));
		assertEquals(
			"36876394947873562E-165",
			analysed(Double.longBitsToDouble(0x1dcb2e81a65dee3fL))
		);
		assertAllShortest(false);
	}

	@Test
	@DisplayName("writes the same digits when only the exact route is taken")
	void writesTheSameDigitsFromTheExactRoute() {
		assertEquals("1E23", analysed(1e23, true));
		assertAllShortest(true);
	}
}
