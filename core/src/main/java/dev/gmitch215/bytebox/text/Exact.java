package dev.gmitch215.bytebox.text;

/**
 * The shortest decimal that reads back as a double, worked out exactly.
 *
 * <p>This is the form {@code Double.toString} gives on JDK 19 and later: the fewest digits that
 * identify the value, the closest to the value when more than one candidate has that many, and the
 * closest two-digit decimal when one digit would do. {@link DoubleAnalyzer} reaches for it where its
 * table arithmetic cannot tell the answer from a neighbour, and for every subnormal.
 *
 * <p>The value and the two edges of its interval are each scaled by a power of ten into an 18-digit
 * integer without error, on numbers held as arrays of 32-bit limbs so that no {@code java.math} class
 * is needed. What lies right of the point is kept as one more bit, which is all it takes to compare
 * any whole number against them exactly.
 */
final class Exact {

	private static final long MASK = 0xFFFFFFFFL;

	private Exact() {}

	/**
	 * @param bits the bits of a positive finite double
	 * @param seeds the table arithmetic's value and the distances of its two edges from it, scaled by
	 * ten to the {@code scale}, or {@code null} for a double small enough not to need them
	 * @param scale the power of ten the seeds were scaled by
	 * @return the digits as an 18-digit mantissa, then the power of ten of the first digit
	 */
	static long[] analyze(long bits, long[] seeds, int scale) {
		long fraction = bits & 0xFFFFFFFFFFFFFL;
		int field = (int) (bits >>> 52) & 0x7FF;
		long significand = field == 0 ? fraction : fraction | (1L << 52);
		// below a power of two the next double is half as far off as above it
		boolean closer = fraction == 0 && field > 1;
		int binary = field == 0 ? -1074 : field - 1075;
		return analyze(significand, binary, closer, Double.longBitsToDouble(bits), seeds, scale);
	}

	/**
	 * The same for a float, which never has seeds: the digits as an 18-digit mantissa, of which the
	 * last nine are zeros.
	 *
	 * @param bits the bits of a positive finite float
	 */
	static long[] analyze(int bits) {
		int fraction = bits & 0x7FFFFF;
		int field = (bits >>> 23) & 0xFF;
		long significand = field == 0 ? fraction : fraction | (1L << 23);
		boolean closer = fraction == 0 && field > 1;
		int binary = field == 0 ? -149 : field - 150;
		return analyze(significand, binary, closer, Float.intBitsToFloat(bits), null, 0);
	}

	private static long[] analyze(
		long significand,
		int binary,
		boolean closer,
		double value,
		long[] seeds,
		int scale
	) {
		// the value and its edges, over four, times two to the binary
		binary -= 2;
		long[] parts = { 4 * significand, 4 * significand - (closer ? 1 : 2), 4 * significand + 2 };

		int power = scale - 1;
		if (seeds == null) {
			power = 17 - (int) Math.floor(Math.log10(value));
			while (true) {
				long whole =
					scaled(
						parts[0],
						binary,
						power,
						power5(Math.abs(power), size(binary, power)),
						null,
						0
					) >> 1;
				if (whole < 100000000000000000L) {
					power++;
				} else if (whole >= 1000000000000000000L) {
					power--;
				} else {
					break;
				}
			}
		}
		int size = size(binary, power);
		int[] five = power5(Math.abs(power), size);
		if (power < 0 && binary + power < 0) shiftLeft(five, -(binary + power));
		long[] scaled = new long[3];
		for (int at = 0; at < 3; at++) {
			scaled[at] = scaled(parts[at], binary, power, five, seeds, at);
		}
		return digits(scaled[0], scaled[1], scaled[2], (significand & 1) == 0, power);
	}

	/**
	 * {@code part * 2^binary * 10^power}, as twice its whole part, plus one if it has a fraction.
	 *
	 * <p>A candidate, a whole number, is twice itself in these units: it never equals an odd figure
	 * and lies strictly on one side of it.
	 *
	 * @param five five to the size of the power, which for a negative one is also shifted left by what
	 * the binary exponent takes off
	 */
	private static long scaled(
		long part,
		int binary,
		int power,
		int[] five,
		long[] seeds,
		int which
	) {
		int shift = binary + power;
		if (power >= 0) {
			int[] number = multiply(fromLong(part, five.length), five);
			if (shift >= 0) return 2 * (window(number, 0) << shift);
			return 2 * window(number, -shift) + (below(number, -shift) ? 1 : 0);
		}

		// the figure over a power of five, whose whole part the table's, a tenth of it, is within one of
		int[] top = fromLong(part, five.length);
		if (shift >= 0) shiftLeft(top, shift);
		if (seeds == null) {
			// a float is at least 2^36 here, so the shift is never negative
			boolean rest = divideByPower5(top, -power);
			return 2 * window(top, 0) + (rest ? 1 : 0);
		}
		long base = Long.divideUnsigned(seeds[0], 10);
		long offset = Math.floorDiv(
			Long.remainderUnsigned(seeds[0], 10) + (which == 0 ? 0 : seeds[which]),
			10
		);
		long guess = base + offset + 1;
		int[] product = multiply(five, fromLong(guess, five.length));
		if (compare(product, top) > 0) {
			guess--;
			subtract(product, five);
		}
		return 2 * guess + (compare(product, top) == 0 ? 0 : 1);
	}

	/**
	 * @param digits the decimal is these digits, with no sign, point or exponent, times ten to the
	 * {@code power}
	 * @param value a positive finite double
	 * @return -1, 0 or 1 as the decimal is below, equal to or above the value
	 */
	static int compare(String digits, int power, double value) {
		long bits = Double.doubleToRawLongBits(value);
		int field = (int) (bits >>> 52) & 0x7FF;
		long significand = (bits & 0xFFFFFFFFFFFFFL) | (field == 0 ? 0 : 1L << 52);
		int binary = field == 0 ? -1074 : field - 1075;

		// digits * 5^power * 2^power against significand * 2^binary, whole numbers on both sides
		int shift = power - binary;
		int size =
			(66 +
				(digits.length() * 3322) / 1000 +
				(Math.abs(power) * 2322) / 1000 +
				1 +
				Math.abs(shift) +
				66) /
				32 +
			2;
		int[] left = new int[size];
		for (int at = 0; at < digits.length(); at++) {
			long carry = digits.charAt(at) - '0';
			for (int limb = 0; limb < size; limb++) {
				long product = (left[limb] & MASK) * 10 + carry;
				left[limb] = (int) product;
				carry = product >>> 32;
			}
		}
		int[] right = fromLong(significand, size);
		if (power > 0) left = multiply(left, power5(power, size));
		if (power < 0) right = multiply(right, power5(-power, size));
		if (shift > 0) shiftLeft(left, shift);
		if (shift < 0) shiftLeft(right, -shift);
		return compare(left, right);
	}

	private static long[] digits(long value, long low, long high, boolean even, int power) {
		// a candidate is inside the interval if it is past the lower edge and short of the upper
		long insideLow = even ? low : low + 1;
		long insideHigh = even ? high : high - 1;

		// the interval holds a multiple of ten, which is a step of twenty here
		long step = 20;
		while (step <= 200000000000000000L) {
			long next = step * 10;
			if ((insideHigh / next) * next < insideLow) break;
			step = next;
		}
		long chosen = nearest(value, step, insideLow, insideHigh);

		// one digit would do, so the JDK writes the closest decimal with two
		if (chosen / step < 10) {
			long grid = 2;
			for (long left = value / 2; left >= 100; left /= 10) grid *= 10;
			chosen = nearest(value, grid, 0, Long.MAX_VALUE);
		}

		long mantissa = chosen / 2;
		int exponent = 17 - power;
		if (mantissa >= 1000000000000000000L) {
			mantissa /= 10;
			exponent++;
		}
		return new long[] { mantissa, exponent };
	}

	/** The multiple of {@code step} closest to the value that is inside, and the even one on a tie. */
	private static long nearest(long value, long step, long insideLow, long insideHigh) {
		long below = (value / step) * step;
		long above = below + step;
		boolean belowIn = below >= insideLow && below <= insideHigh;
		boolean aboveIn = above >= insideLow && above <= insideHigh;
		if (belowIn != aboveIn) return belowIn ? below : above;
		long fromBelow = value - below;
		long fromAbove = above - value;
		if (fromBelow != fromAbove) return fromBelow < fromAbove ? below : above;
		return ((below / step) & 1) == 0 ? below : above;
	}

	/** How many limbs it takes to hold every number made for this scale, a product and a shift included. */
	private static int size(int binary, int power) {
		int five = (Math.abs(power) * 2322) / 1000 + 1;
		int bits = Math.max(
			five + Math.max(-(binary + power), 0),
			58 + Math.max(binary + power, 0)
		);
		return (bits + 66) / 32 + 1;
	}

	/** @return five to the {@code exponent}, in an array of {@code size} limbs */
	private static int[] power5(int exponent, int size) {
		int[] out = new int[size];
		out[0] = 1;
		int used = 1;
		for (int left = exponent; left > 0; ) {
			int step = Math.min(left, 13);
			long factor = 1;
			for (int at = 0; at < step; at++) factor *= 5;
			long carry = 0;
			for (int at = 0; at < used; at++) {
				long product = (out[at] & MASK) * factor + carry;
				out[at] = (int) product;
				carry = product >>> 32;
			}
			if (carry != 0) out[used++] = (int) carry;
			left -= step;
		}
		return out;
	}

	/** Divides {@code a} by five to the {@code exponent} in place, and says whether it left anything. */
	private static boolean divideByPower5(int[] a, int exponent) {
		boolean rest = false;
		for (int left = exponent; left > 0; ) {
			int step = Math.min(left, 13);
			long factor = 1;
			for (int at = 0; at < step; at++) factor *= 5;
			long carry = 0;
			for (int at = a.length - 1; at >= 0; at--) {
				long current = (carry << 32) | (a[at] & MASK);
				a[at] = (int) (current / factor);
				carry = current % factor;
			}
			rest |= carry != 0;
			left -= step;
		}
		return rest;
	}

	private static int[] fromLong(long value, int size) {
		int[] out = new int[size];
		out[0] = (int) value;
		out[1] = (int) (value >>> 32);
		return out;
	}

	private static int limbs(int[] a) {
		int at = a.length;
		while (at > 0 && a[at - 1] == 0) at--;
		return at;
	}

	private static int[] multiply(int[] a, int[] b) {
		int[] out = new int[a.length];
		int size = limbs(a);
		int with = limbs(b);
		for (int at = 0; at < size; at++) {
			long carry = 0;
			long factor = a[at] & MASK;
			for (int next = 0; next < with; next++) {
				long product = factor * (b[next] & MASK) + (out[at + next] & MASK) + carry;
				out[at + next] = (int) product;
				carry = product >>> 32;
			}
			out[at + with] = (int) carry;
		}
		return out;
	}

	private static void shiftLeft(int[] a, int bits) {
		int whole = bits >> 5;
		int part = bits & 31;
		for (int at = a.length - 1; at >= 0; at--) {
			long value = at - whole >= 0 ? (a[at - whole] & MASK) << part : 0;
			if (part != 0 && at - whole - 1 >= 0) value |=
				(a[at - whole - 1] & MASK) >>> (32 - part);
			a[at] = (int) value;
		}
	}

	/** The 64 bits of {@code a} from bit {@code from} up. */
	private static long window(int[] a, int from) {
		int at = from >> 5;
		int part = from & 31;
		long low = at < a.length ? a[at] & MASK : 0;
		long middle = at + 1 < a.length ? a[at + 1] & MASK : 0;
		long high = at + 2 < a.length ? a[at + 2] & MASK : 0;
		long value = low | (middle << 32);
		return part == 0 ? value : (value >>> part) | (high << (64 - part));
	}

	/** Whether any bit of {@code a} under bit {@code from} is set. */
	private static boolean below(int[] a, int from) {
		int whole = Math.min(from >> 5, a.length);
		for (int at = 0; at < whole; at++) {
			if (a[at] != 0) return true;
		}
		int part = from & 31;
		return part != 0 && whole < a.length && (a[whole] & ((1 << part) - 1)) != 0;
	}

	private static int compare(int[] a, int[] b) {
		for (int at = a.length - 1; at >= 0; at--) {
			if (a[at] != b[at]) return Long.compare(a[at] & MASK, b[at] & MASK);
		}
		return 0;
	}

	private static void subtract(int[] a, int[] b) {
		long borrow = 0;
		for (int at = 0; at < a.length; at++) {
			long difference = (a[at] & MASK) - (b[at] & MASK) - borrow;
			a[at] = (int) difference;
			borrow = difference < 0 ? 1 : 0;
		}
	}
}
