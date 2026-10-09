package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the digits Float.toString is written from")
class FloatAnalyzerTest {

	/** The digits without trailing zeros and the power of ten of the first, as the JDK writes them. */
	private static String expected(float value) {
		BigDecimal decimal = new BigDecimal(Float.toString(Math.abs(value))).stripTrailingZeros();
		int exponent = decimal.precision() - decimal.scale() - 1;
		return decimal.unscaledValue() + "E" + exponent;
	}

	private static String analysed(float value) {
		FloatAnalyzer.Result result = new FloatAnalyzer.Result();
		FloatAnalyzer.analyze(value, result);
		String digits = Integer.toString(result.mantissa).replaceAll("0+$", "");
		return digits + "E" + result.exponent;
	}

	private static void assertShortest(float value) {
		assertEquals(
			expected(value),
			analysed(value),
			"bits: " + Integer.toHexString(Float.floatToRawIntBits(value))
		);
	}

	private static final float[] EDGES = {
		1.0f,
		0.1f,
		0.3f,
		1.005f,
		1e10f,
		1e23f,
		8.5e9f,
		16777216f,
		16777217f,
		33554432f,
		Float.MIN_VALUE,
		Float.MIN_NORMAL,
		Math.nextUp(Float.MIN_NORMAL),
		Math.nextDown(Float.MIN_NORMAL),
		Float.MAX_VALUE,
		Math.nextDown(Float.MAX_VALUE)
	};

	@Test
	@DisplayName("writes the shortest digits at the edges of the range")
	void writesTheEdges() {
		assertEquals("14E-45", analysed(Float.MIN_VALUE));
		assertEquals("34028235E38", analysed(Float.MAX_VALUE));
		assertEquals("1E0", analysed(1.0f));
		assertEquals("1E-1", analysed(0.1f));
		for (float value : EDGES) assertShortest(value);
	}

	@Test
	@DisplayName("writes the shortest digits of every subnormal power of two and its neighbours")
	void writesSubnormals() {
		for (int bit = 0; bit < 23; bit++) {
			int bits = 1 << bit;
			assertShortest(Float.intBitsToFloat(bits));
			if (bits > 1) assertShortest(Float.intBitsToFloat(bits - 1));
			assertShortest(Float.intBitsToFloat(bits + 1));
		}
		for (int bits = 1; bits <= 600; bits++) assertShortest(Float.intBitsToFloat(bits));
	}

	@Test
	@DisplayName("writes the shortest digits at every exponent, powers of two included")
	void writesEveryExponent() {
		for (int exponent = 1; exponent < 255; exponent++) {
			int power = exponent << 23;
			for (int fraction : new int[] { 0, 1, 2, 0x400000, 0x7FFFFE, 0x7FFFFF }) {
				assertShortest(Float.intBitsToFloat(power | fraction));
			}
		}
	}

	@Test
	@DisplayName("writes the shortest digits of drawn floats")
	void writesDrawnFloats() {
		long state = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 20000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			float value = Float.intBitsToFloat((int) state & Integer.MAX_VALUE);
			if (!Float.isNaN(value) && !Float.isInfinite(value) && value != 0) assertShortest(
				value
			);
		}
	}

	@Test
	@DisplayName(
		"writes the shortest digits of decimals that carry as many digits as a float holds"
	)
	void writesNineDigitDecimals() {
		long state = 0xD1B54A32D192ED03L;
		for (int i = 0; i < 20000; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			float value = Float.parseFloat(
				((state >>> 40) % 900000000L) +
					100000000L +
					"E" +
					(int) (((state & 0xFFFF) % 90) - 45)
			);
			if (!Float.isInfinite(value) && value != 0) assertShortest(value);
		}
	}

	@Test
	@DisplayName("keeps the sign apart from the digits and leaves the zeros alone")
	void keepsTheSign() {
		FloatAnalyzer.Result result = new FloatAnalyzer.Result();
		FloatAnalyzer.analyze(-1.5f, result);
		assertTrue(result.sign);
		assertEquals("15E0", analysed(-1.5f));

		FloatAnalyzer.analyze(1.5f, result);
		assertFalse(result.sign);

		FloatAnalyzer.analyze(0.0f, result);
		assertEquals(0, result.mantissa);
		assertFalse(result.sign);
		FloatAnalyzer.analyze(-0.0f, result);
		assertEquals(0, result.mantissa);
		assertTrue(result.sign);
	}
}
