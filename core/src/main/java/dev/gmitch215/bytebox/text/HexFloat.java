package dev.gmitch215.bytebox.text;

/** The digits {@code %a} writes, rounded the way the JDK rounds them. */
final class HexFloat {

	private static final double SCALE_UP = Double.longBitsToDouble((1023L + 54) << 52);

	private HexFloat() {}

	/**
	 * A finite, positive or zero double in hexadecimal, without the leading {@code 0x}.
	 *
	 * @param value the magnitude
	 * @param digits how many hexadecimal digits to keep after the point, or zero for all of them
	 * @return the digits, the letter {@code p} and a decimal exponent
	 */
	static String of(double value, int digits) {
		long bits = Double.doubleToRawLongBits(value);
		if (value == 0.0 || digits == 0 || digits >= 13) return text(bits, 0);

		// a subnormal is rounded as a normal one 2^54 times larger and the exponent corrected after
		boolean subnormal = bits >>> 52 == 0;
		if (subnormal) bits = Double.doubleToRawLongBits(value * SCALE_UP);

		int shift = 53 - (1 + digits * 4);
		long kept = bits >> shift;
		long dropped = bits & ~(~0L << shift);
		boolean leastZero = (kept & 1L) == 0L;
		boolean round = ((1L << (shift - 1)) & dropped) != 0L;
		boolean sticky = shift > 1 && (~(1L << (shift - 1)) & dropped) != 0;
		if ((leastZero && round && sticky) || (!leastZero && round)) kept++;

		long rounded = kept << shift;
		if (rounded >>> 52 == 0x7FF) return "1.0p1024";
		return text(rounded, subnormal ? -54 : 0);
	}

	private static String text(long bits, int adjust) {
		int field = (int) (bits >>> 52) & 0x7FF;
		long fraction = bits & 0xFFFFFFFFFFFFFL;
		String hex = Long.toHexString(fraction | (1L << 52)).substring(1);
		int end = hex.length();
		while (end > 1 && hex.charAt(end - 1) == '0') end--;
		int exponent = field != 0 ? field - 1023 : fraction != 0 ? -1022 : 0;
		return (field != 0 ? "1." : "0.") + hex.substring(0, end) + "p" + (exponent + adjust);
	}
}
