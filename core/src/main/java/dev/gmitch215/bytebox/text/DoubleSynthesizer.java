package dev.gmitch215.bytebox.text;

/**
 * Where {@code DecimalFormat} gets the double for a number it read, in place of the class library's.
 *
 * <p>The original multiplies by a 64-bit table entry and rounds, so its answer is off by one unit in
 * the last place for some decimals ({@code 1e23}, {@code 3.687639494787356E-165}) and is zero for
 * {@code 4.9E-324}. The platform's {@code Number} rounds a decimal correctly, so the digits the lexer
 * collected are handed to it. The lexer keeps the first 19 significant digits and drops the rest.
 *
 * <p>Found through {@link Substitutions}.
 */
public final class DoubleSynthesizer {

	private DoubleSynthesizer() {}

	/**
	 * @param mantissa up to 19 digits, unsigned, the first at 10^18
	 * @param exp the power of ten of the first digit
	 * @param negative whether the sign was a minus
	 * @return the double nearest the decimal
	 */
	public static double synthesizeDouble(long mantissa, int exp, boolean negative) {
		if (mantissa == 0) return negative ? -0.0 : 0.0;
		double value = Numbers.parse(text(mantissa, exp));
		return negative ? -value : value;
	}

	static String text(long mantissa, int exp) {
		return Long.toUnsignedString(mantissa) + "e" + (exp - 18);
	}
}
