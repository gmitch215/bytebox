package dev.gmitch215.bytebox.text;

import java.util.function.ToDoubleFunction;

/**
 * Reads a string as a {@code double} or a {@code float}, the way {@code Double.parseDouble} and
 * {@code Float.parseFloat} do on JDK 25 and later, which {@link ParsePlugin} points them at.
 *
 * <p>The class library's lexer stops reading after an exponent, so {@code 1e5.5} was a number there;
 * it has no hexadecimal floating-point literals; it keeps 19 digits of a decimal (nine for a float)
 * and rounds from those; and it refuses without saying why. This one scans the whole string by the
 * JDK's grammar and refuses with the JDK's message.
 *
 * <p>A decimal is read by the platform's {@code Number}, which rounds any number of digits
 * correctly; a float takes one more step, so that the double between two floats does not round it
 * twice. A hexadecimal literal is rounded here, in whole numbers.
 */
final class Parse {

	private static final int ZERO = 0;
	private static final int NAN = 1;
	private static final int INFINITY = 2;
	private static final int DECIMAL = 3;
	private static final int HEX = 4;

	// a float midpoint has no more digits than this, so a decimal can be cut here and still compare
	private static final int FLOAT_DIGITS = 120;

	private static final int MAX_MESSAGE = 1000;
	private static final String OMITTED = " ... ";

	private Parse() {}

	/** What the scan found: where the number sits in the string and what it means. */
	private static final class Scan {

		boolean negative;
		int kind;
		// the significand's first character and the end of the exponent
		int from;
		int to;
		// the first digit that is not a zero, and one past the last digit that is not
		int first;
		int last;
		// how many digits lie between them, and the power of ten (of two, for hex) of the last
		int count;
		long power;
	}

	static double parseDouble(String in) {
		return parseDouble(in, Numbers::parse);
	}

	static float parseFloat(String in) {
		return parseFloat(in, Numbers::parse);
	}

	/**
	 * @param platform what reads a decimal, given as the text of one, to its nearest double
	 * @return the double nearest the string
	 * @throws NumberFormatException as the JDK does
	 */
	static double parseDouble(String in, ToDoubleFunction<String> platform) {
		Scan scan = scan(in);
		double magnitude;
		switch (scan.kind) {
			case NAN:
				return Double.NaN;
			case INFINITY:
				magnitude = Double.POSITIVE_INFINITY;
				break;
			case ZERO:
				magnitude = 0.0;
				break;
			case HEX:
				magnitude = hex(in, scan, false);
				break;
			default:
				magnitude = platform.applyAsDouble(in.substring(scan.from, scan.to));
		}
		// the sign bit is set directly, because the compiler's negation of a zero gives a positive one
		if (!scan.negative) return magnitude;
		return Double.longBitsToDouble(Double.doubleToRawLongBits(magnitude) | Long.MIN_VALUE);
	}

	/**
	 * @param platform what reads a decimal, given as the text of one, to its nearest double
	 * @return the float nearest the string
	 * @throws NumberFormatException as the JDK does
	 */
	static float parseFloat(String in, ToDoubleFunction<String> platform) {
		Scan scan = scan(in);
		float magnitude;
		switch (scan.kind) {
			case NAN:
				return Float.NaN;
			case INFINITY:
				magnitude = Float.POSITIVE_INFINITY;
				break;
			case ZERO:
				magnitude = 0.0f;
				break;
			case HEX:
				magnitude = (float) hex(in, scan, true);
				break;
			default:
				double nearest = platform.applyAsDouble(in.substring(scan.from, scan.to));
				StringBuilder digits = new StringBuilder();
				for (int at = scan.first; at < scan.last && digits.length() < FLOAT_DIGITS; at++) {
					char c = in.charAt(at);
					if (c != '.') digits.append(c);
				}
				long power = scan.power;
				if (scan.count > FLOAT_DIGITS) {
					// a nonzero digit stands for the rest, which is nonzero too
					digits.append('1');
					power += scan.count - FLOAT_DIGITS - 1;
				}
				magnitude = round(
					nearest,
					digits.toString(),
					(int) Math.max(-1000000000L, Math.min(1000000000L, power))
				);
		}
		if (!scan.negative) return magnitude;
		return Float.intBitsToFloat(Float.floatToRawIntBits(magnitude) | Integer.MIN_VALUE);
	}

	/**
	 * The float nearest a decimal, given the double nearest it.
	 *
	 * <p>A double rounds to a float correctly, and the decimal to the double correctly, but the two
	 * together round twice, which differs from a JVM only when the double lands exactly halfway between
	 * two floats and the decimal was not exactly there. That case is told apart by comparing the
	 * decimal with the halfway point in whole numbers, which is never inexact.
	 *
	 * @param nearest the double nearest the decimal, not negative
	 * @param digits the decimal is these digits times ten to the {@code power}
	 * @return the float nearest the decimal
	 */
	static float round(double nearest, String digits, int power) {
		float rounded = (float) nearest;
		double low;
		double high;
		if (Float.isInfinite(rounded)) {
			// the largest float and 2^128, whose halfway point is where the rounding to infinity starts
			low = Float.MAX_VALUE;
			high = 0x1.0p128;
		} else {
			double near = rounded;
			if (nearest == near) return rounded;
			double other = nearest > near ? Math.nextUp(rounded) : Math.nextDown(rounded);
			low = Math.min(near, other);
			high = Math.max(near, other);
		}
		if (nearest != (low + high) / 2) return rounded;

		int side = Exact.compare(digits, power, nearest);
		if (side == 0) return rounded;
		return (float) (side > 0 ? high : low);
	}

	// #region the scan

	private static Scan scan(String in) {
		int length = in.length();
		int i = skipSpace(in, 0);
		if (i == length) throw new NumberFormatException("empty String");

		Scan scan = new Scan();
		int ch = in.charAt(i);
		if (ch == '-' || ch == '+') {
			scan.negative = ch == '-';
			i++;
		}

		boolean decimal = true;
		if (i < length) {
			ch = in.charAt(i);
			if (ch == 'I') {
				word(in, i, "Infinity");
				scan.kind = INFINITY;
				return scan;
			}
			if (ch == 'N') {
				word(in, i, "NaN");
				scan.kind = NAN;
				return scan;
			}
			if (ch == '0' && i + 1 < length && lower(in.charAt(i + 1)) == 'x') {
				decimal = false;
				i += 2;
			}
		}

		int point = 0;
		int start = i;
		while (i < length && ((ch = in.charAt(i)) == '0' || ch == '.')) {
			i++;
			if (ch == '.') point = pointAt(point, i);
		}
		int zeros = i;

		int tail = 0;
		while (i < length && (isDigit((ch = in.charAt(i)), decimal) || ch == '.')) {
			i++;
			if (ch == '.') point = pointAt(point, i);
			else if (ch != '0') tail = i;
		}
		check(in, i - start > (point != 0 ? 1 : 0));
		int stop = i;

		long exponent = 0;
		boolean hasExponent = false;
		if (
			i < length &&
			(((ch = lower(in.charAt(i))) == 'e' && decimal) || (ch == 'p' && !decimal))
		) {
			i++;
			int sign = ' ';
			if (i < length && ((ch = in.charAt(i)) == '-' || ch == '+')) {
				sign = ch;
				i++;
			}
			while (i < length && isDigit((ch = in.charAt(i)), true)) {
				i++;
				exponent = exponent < 1000000000L ? 10 * exponent + (ch - '0') : 10000000000L;
			}
			check(in, i - stop >= 3 || (i - stop == 2 && sign == ' '));
			if (sign == '-') exponent = -exponent;
			hasExponent = true;
		}
		check(in, decimal | hasExponent);
		int end = i;

		if (i < length && ((ch = lower(in.charAt(i))) == 'f' || ch == 'd')) i++;
		check(in, skipSpace(in, i) == length);

		if (tail == 0) {
			scan.kind = ZERO;
			return scan;
		}

		// move the point to just after the last digit that is not a zero
		long step = decimal ? 1 : 4;
		int count = tail - zeros;
		if (point == 0) {
			exponent += step * (stop - tail);
		} else {
			exponent += step * (point - tail);
			if (point > tail) exponent -= step;
			else if (zeros < point) count--;
		}

		scan.kind = decimal ? DECIMAL : HEX;
		scan.from = start;
		scan.to = end;
		scan.first = zeros;
		scan.last = tail;
		scan.count = count;
		scan.power = exponent;
		return scan;
	}

	private static int lower(int ch) {
		return ch | 0x20;
	}

	private static boolean isDigit(int ch, boolean decimal) {
		if ('0' <= ch && ch <= '9') return true;
		int low = lower(ch);
		return !decimal && 'a' <= low && low <= 'f';
	}

	private static int digitOf(int ch) {
		return ch <= '9' ? ch - '0' : lower(ch) - ('a' - 10);
	}

	/** The index after every character from {@code i} that is a space or a control character. */
	private static int skipSpace(String in, int i) {
		int length = in.length();
		while (i < length && in.charAt(i) <= ' ') i++;
		return i;
	}

	private static void word(String in, int at, String word) {
		check(in, in.startsWith(word, at) && skipSpace(in, at + word.length()) == in.length());
	}

	private static int pointAt(int point, int i) {
		if (point != 0) throw new NumberFormatException("multiple points");
		return i;
	}

	private static void check(String in, boolean expected) {
		if (expected) return;
		String shown = in;
		if (in.length() > MAX_MESSAGE) {
			int head = (MAX_MESSAGE - OMITTED.length()) / 2;
			int tail = MAX_MESSAGE - (head + OMITTED.length());
			shown = in.substring(0, head) + OMITTED + in.substring(in.length() - tail);
		}
		throw new NumberFormatException("For input string: \"" + shown + "\"");
	}

	// #endregion

	// #region hexadecimal

	/**
	 * The magnitude of a hexadecimal literal, correctly rounded, as a double that holds a float
	 * exactly when {@code single} says that is what is being read.
	 *
	 * <p>The most significant {@code P + 1} bits and one for whatever follows are enough to round the
	 * rest, so no more than that many digits are read.
	 */
	private static double hex(String in, Scan scan, boolean single) {
		int precision = single ? 24 : 53;
		int minExponent = single ? -149 : -1074;
		int minLeading = single ? -125 : -1021;
		int maxLeading = single ? 128 : 1024;
		int room = precision / 4 + 2;

		int read = Math.min(scan.count, room);
		long bits = 0;
		int taken = 0;
		for (int at = scan.first; taken < read; at++) {
			int ch = in.charAt(at);
			if (ch != '.') {
				taken++;
				bits = (bits << 4) | digitOf(ch);
			}
		}
		long exponent = scan.power;
		if (scan.count > read) {
			bits |= 1;
			exponent += 4L * (scan.count - read);
		}

		int length = Long.SIZE - Long.numberOfLeadingZeros(bits);
		if (exponent < minExponent - length) return 0.0;
		if (exponent > maxLeading - length) return Double.POSITIVE_INFINITY;

		int q = (int) exponent;
		int shift;
		if (q >= minLeading - length) {
			shift = length - precision;
			q += shift;
		} else {
			shift = minExponent - q;
			q = minExponent;
		}
		if (shift > 0) {
			long threshold = 1L << shift;
			long dropped = (bits & (threshold - 1)) << 1;
			bits >>>= shift;
			if (dropped > threshold || (dropped == threshold && (bits & 1) != 0)) {
				bits++;
				if (bits >= 1L << precision) {
					bits >>>= 1;
					q++;
				}
			}
		} else {
			bits <<= -shift;
		}

		if (single) {
			int field = bits < 1L << 23 ? 0 : q + 150;
			return Float.intBitsToFloat((field << 23) | ((int) bits & 0x7FFFFF));
		}
		long field = bits < 1L << 52 ? 0 : q + 1075;
		return Double.longBitsToDouble((field << 52) | (bits & 0xFFFFFFFFFFFFFL));
	}

	// #endregion
}
