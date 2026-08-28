package dev.gmitch215.bytebox.text;

/**
 * A double as decimal digits, rounded the way {@code String.format} rounds.
 *
 * <p>Which digits those are is the whole of the difference between a formatter that agrees with the
 * runtime and one that does not, and it is not the obvious answer. A double has an exact decimal
 * expansion - 0.35 is really 0.34999999999999997779553950749686919152736663818359375 - and rounding
 * that expansion half away from zero gives {@code 0.3}. The runtime answers {@code 0.4}, because it
 * rounds the shortest decimal that reads back as the same double, which is {@code 0.35}. So this starts
 * from {@link Double#toString}, which is that shortest decimal, and rounds from there.
 *
 * <p>The consequence worth stating: the platform's own number formatting rounds the exact expansion, so
 * it cannot be used here. {@code (0.35).toFixed(1)} is {@code 0.3} and {@code (1.005).toFixed(2)} is
 * {@code 1.00}, against {@code 0.4} and {@code 1.01} from the runtime. The arithmetic below is a few
 * dozen lines and has no such disagreement.
 *
 * <p>The value is held as {@code 0.digits} times ten to the {@code exponent}, so that the digits carry
 * no leading zero and the exponent carries where the point goes.
 */
final class Decimal {

	private static final Decimal ZERO = new Decimal("0", 1, true);

	private final String digits;
	private final int exponent;
	private final boolean zero;

	private Decimal(String digits, int exponent, boolean zero) {
		this.digits = digits;
		this.exponent = exponent;
		this.zero = zero;
	}

	/**
	 * The shortest decimal that reads back as this double.
	 *
	 * @param magnitude the value, finite and not negative
	 * @return its digits
	 */
	static Decimal of(double magnitude) {
		return of(Double.toString(magnitude));
	}

	/**
	 * A decimal written out, however it was written.
	 *
	 * <p>This is also how a value that carries its own digits gets here: an arbitrary-precision decimal
	 * or integer writes them exactly in its {@code toString}, so parsing that is exact and costs no
	 * reference to {@code java.math}, which would put 22 KB of WebAssembly into every program that
	 * formats a number.
	 *
	 * @param text digits, with an optional point and an optional exponent, and no sign
	 * @return its digits
	 */
	static Decimal of(String text) {
		int marker = text.indexOf('E');
		if (marker < 0) marker = text.indexOf('e');
		int shift = 0;
		if (marker >= 0) {
			String power = text.substring(marker + 1);
			shift = Integer.parseInt(power.startsWith("+") ? power.substring(1) : power);
			text = text.substring(0, marker);
		}
		int point = text.indexOf('.');
		String all = point < 0 ? text : text.substring(0, point) + text.substring(point + 1);
		int at = (point < 0 ? text.length() : point) + shift;

		int lead = 0;
		while (lead < all.length() - 1 && all.charAt(lead) == '0') lead++;
		at -= lead;
		all = trim(all.substring(lead));
		return all.equals("0") ? ZERO : new Decimal(all, at, false);
	}

	/** {@return the power of ten of the leading digit, which is the exponent {@code %e} writes} */
	int magnitude() {
		return exponent - 1;
	}

	/**
	 * Rounded to a number of digits after the point.
	 *
	 * @param scale how many digits after the point
	 * @return the rounded value
	 */
	Decimal toFraction(int scale) {
		// a precision near the largest int would wrap the sum negative and round the value away
		long keep = (long) exponent + scale;
		return keep(keep > digits.length() ? digits.length() : (int) Math.max(keep, -1L));
	}

	/**
	 * Rounded to a number of significant digits.
	 *
	 * @param count how many digits, at least one
	 * @return the rounded value
	 */
	Decimal toSignificant(int count) {
		return keep(Math.max(count, 1));
	}

	/**
	 * Written out, with exactly this many digits after the point.
	 *
	 * @param scale how many digits after the point, zero for none
	 * @return the digits, with a leading zero where there is no integer part
	 */
	String plain(int scale) {
		StringBuilder text = new StringBuilder();
		if (zero || exponent <= 0) {
			text.append('0');
		} else {
			for (int at = 0; at < exponent; at++) text.append(digit(at));
		}
		if (scale > 0) {
			text.append('.');
			for (int at = 0; at < scale; at++) text.append(zero ? '0' : digit(exponent + at));
		}
		return text.toString();
	}

	/**
	 * In exponential form, with exactly this many digits after the point in the mantissa.
	 *
	 * <p>The exponent carries a sign and at least two digits, which is what the runtime writes.
	 *
	 * @param scale how many digits after the point
	 * @return the mantissa, the letter {@code e}, the sign and the exponent
	 */
	String scientific(int scale) {
		StringBuilder text = new StringBuilder();
		text.append(zero ? '0' : digit(0));
		if (scale > 0) {
			text.append('.');
			for (int at = 1; at <= scale; at++) text.append(zero ? '0' : digit(at));
		}
		int power = zero ? 0 : magnitude();
		String powerDigits = Integer.toString(Math.abs(power));
		if (powerDigits.length() < 2) powerDigits = "0" + powerDigits;
		return text
			.append('e')
			.append(power < 0 ? '-' : '+')
			.append(powerDigits)
			.toString();
	}

	/** Half away from zero on the digits, with the carry able to move the point a place right. */
	private Decimal keep(int count) {
		if (zero || count >= digits.length()) return this;
		if (count < 0) return ZERO;
		if (count == 0) {
			return digits.charAt(0) >= '5' ? new Decimal("1", exponent + 1, false) : ZERO;
		}
		if (digits.charAt(count) < '5') {
			return new Decimal(trim(digits.substring(0, count)), exponent, false);
		}

		char[] kept = digits.substring(0, count).toCharArray();
		int at = count - 1;
		while (at >= 0 && kept[at] == '9') kept[at--] = '0';
		if (at < 0) return new Decimal("1", exponent + 1, false);
		kept[at]++;
		return new Decimal(trim(new String(kept)), exponent, false);
	}

	private char digit(int at) {
		return at >= 0 && at < digits.length() ? digits.charAt(at) : '0';
	}

	private static String trim(String digits) {
		int end = digits.length();
		while (end > 1 && digits.charAt(end - 1) == '0') end--;
		return digits.substring(0, end);
	}
}
