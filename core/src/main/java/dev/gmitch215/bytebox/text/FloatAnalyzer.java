package dev.gmitch215.bytebox.text;

/**
 * The class library's {@code FloatAnalyzer} (TeaVM 0.15.0, {@code org.teavm.classlib.impl.text}),
 * with its digits worked out by {@link Exact}.
 *
 * <p>This is what {@code Float.toString}, {@code String.valueOf(float)} and string concatenation of a
 * float write digits from. The original multiplies by a 32-bit table entry, which leaves the value and
 * the two edges of the interval of decimals that read back as the same float a few units of the last
 * digit off, so some floats came out as text that is not the shortest, or does not read back as the
 * float. Here every float goes through the exact route {@link DoubleAnalyzer} falls back on: there are no
 * tables, so a program that prints floats gets smaller, and a call costs a microsecond or so.
 *
 * <p>Found through {@link Substitutions}. It replaces the class under its own name, so the
 * {@code PRECISION} and {@code MAX_POS} the string builder reads are kept.
 */
public final class FloatAnalyzer {

	public static final int PRECISION = 9;
	public static final int MAX_POS = 100000000;

	private FloatAnalyzer() {}

	public static void analyze(float d, Result result) {
		int bits = Float.floatToIntBits(d);
		result.sign = bits < 0;
		bits &= Integer.MAX_VALUE;
		if (bits == 0) {
			result.mantissa = 0;
			result.exponent = 0;
			return;
		}

		long[] digits = Exact.analyze(bits);
		result.mantissa = (int) (digits[0] / 1000000000L);
		result.exponent = (int) digits[1];
	}

	public static class Result {

		public int mantissa;
		public int exponent;
		public boolean sign;
	}
}
