package dev.gmitch215.bytebox.text;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.FormatterClosedException;
import java.util.IllegalFormatCodePointException;
import java.util.IllegalFormatConversionException;
import java.util.IllegalFormatPrecisionException;
import java.util.Locale;
import java.util.MissingFormatWidthException;
import java.util.UnknownFormatConversionException;

/**
 * A formatter, standing in for {@code java.util.Formatter}.
 *
 * <p>The compiler substitutes {@code java.util.Formatter} for this one, which is what
 * {@code String.format} and {@code printf} go through, so a project using either works unchanged.
 *
 * <p>What it replaces is a version that reaches a decimal formatter, a currency table and the locale
 * data behind them from the single method that dispatches on the conversion character. Because that
 * method is one method, every conversion is reachable from every use, and a format string of
 * {@code "%s and %d"} costs the whole graph: measured at 85 KB of WebAssembly after compression,
 * against a hello world of 7 KB. The digits are worked out here instead, by {@link Decimal}, and
 * {@code Locale} - which is 0.9 KB by itself - is all that is left of the rest.
 *
 * <p>Two conversions are not here, and both would put back what this class exists to remove.
 * {@code %t} and {@code %T} would make the date and calendar graph reachable from every
 * {@code String.format} call; use {@code DateTimeFormatter} instead. {@code %a} and {@code %A} write a
 * double in hexadecimal; use {@code Double.toHexString}. Each throws and names its replacement.
 *
 * <p>Grouping is inserted every three digits, which is right for the locales that group in threes and
 * wrong for the ones that do not. The decimal point and the group separator themselves come from the
 * platform's locale data.
 *
 * @since 1.0.0
 */
public final class Formatter implements Closeable, Flushable {

	private static final String FLAGS = "-#+ 0,(<";

	private final Appendable out;
	private final Locale locale;
	private final Separators separators;

	private IOException failure;
	private boolean closed;

	/** Writing into a string, in the platform's own locale. */
	public Formatter() {
		this(new StringBuilder(), (Locale) null);
	}

	/**
	 * @param out where to write
	 */
	public Formatter(Appendable out) {
		this(out, (Locale) null);
	}

	/**
	 * @param locale which locale's separators to use, or null for no localisation
	 */
	public Formatter(Locale locale) {
		this(new StringBuilder(), locale);
	}

	/**
	 * @param out where to write
	 * @param locale which locale's separators to use, or null for no localisation
	 */
	public Formatter(Appendable out, Locale locale) {
		this(out, locale, Numbers.PLATFORM);
	}

	/** Over a given source of separators, which is how the test lane runs without the platform. */
	Formatter(Appendable out, Locale locale, Separators separators) {
		if (out == null) throw new NullPointerException("out");
		this.out = out;
		this.locale = locale;
		this.separators = separators;
	}

	/** {@return where this writes} */
	public Appendable out() {
		checkOpen();
		return out;
	}

	/** {@return the locale, or null when there is none} */
	public Locale locale() {
		checkOpen();
		return locale;
	}

	/** {@return the failure the destination reported, or null when it has not failed} */
	public IOException ioException() {
		return failure;
	}

	/**
	 * Writes a formatted string.
	 *
	 * @param format the format string
	 * @param args the arguments
	 * @return this formatter
	 */
	public Formatter format(String format, Object... args) {
		return format(locale, format, args);
	}

	/**
	 * Writes a formatted string in a given locale.
	 *
	 * @param locale which locale's separators to use, or null for no localisation
	 * @param format the format string
	 * @param args the arguments
	 * @return this formatter
	 */
	public Formatter format(Locale locale, String format, Object... args) {
		checkOpen();
		if (format == null) throw new NullPointerException("format");
		Object[] arguments = args == null ? new Object[0] : args;

		int at = 0;
		int next = 0;
		Object previous = null;
		boolean anyTaken = false;

		while (at < format.length()) {
			int percent = format.indexOf('%', at);
			if (percent < 0) {
				write(format.substring(at));
				return this;
			}
			write(format.substring(at, percent));

			Specifier specifier = new Specifier(format, percent);
			at = specifier.end;

			if (specifier.conversion == '%' || specifier.conversion == 'n') {
				write(literal(specifier));
				continue;
			}

			Object argument;
			if (specifier.previous) {
				if (!anyTaken) {
					throw new MissingFormatArgumentException(specifier.text);
				}
				argument = previous;
			} else if (specifier.index > 0) {
				if (specifier.index > arguments.length) {
					throw new MissingFormatArgumentException(specifier.text);
				}
				argument = arguments[specifier.index - 1];
			} else {
				if (next >= arguments.length) {
					throw new MissingFormatArgumentException(specifier.text);
				}
				argument = arguments[next++];
			}
			previous = argument;
			anyTaken = true;

			write(convert(specifier, argument, locale));
		}
		return this;
	}

	@Override
	public void flush() {
		checkOpen();
		if (out instanceof Flushable) {
			try {
				((Flushable) out).flush();
			} catch (IOException reported) {
				failure = reported;
			}
		}
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		if (out instanceof Closeable) {
			try {
				((Closeable) out).close();
			} catch (IOException reported) {
				failure = reported;
			}
		}
	}

	@Override
	public String toString() {
		checkOpen();
		return out.toString();
	}

	// #region the specifier

	/** One {@code %...} run, taken apart. */
	private static final class Specifier {

		private final String text;
		private final int end;
		private final int index;
		private final int width;
		private final int precision;
		private final char conversion;

		private final boolean left;
		private final boolean alternate;
		private final boolean signed;
		private final boolean space;
		private final boolean zero;
		private final boolean grouped;
		private final boolean parenthesised;
		private final boolean previous;

		Specifier(String format, int percent) {
			int at = percent + 1;
			int length = format.length();

			int index = -1;
			int digitsStart = at;
			while (at < length && isDigit(format.charAt(at))) at++;
			if (at < length && at > digitsStart && format.charAt(at) == '$') {
				index = number(format, digitsStart, at, percent);
				at++;
			} else {
				at = digitsStart;
			}

			boolean left = false;
			boolean alternate = false;
			boolean signed = false;
			boolean space = false;
			boolean zero = false;
			boolean grouped = false;
			boolean parenthesised = false;
			boolean previous = false;
			while (at < length && FLAGS.indexOf(format.charAt(at)) >= 0) {
				char flag = format.charAt(at++);
				boolean already;
				switch (flag) {
					case '-':
						already = left;
						left = true;
						break;
					case '#':
						already = alternate;
						alternate = true;
						break;
					case '+':
						already = signed;
						signed = true;
						break;
					case ' ':
						already = space;
						space = true;
						break;
					case '0':
						already = zero;
						zero = true;
						break;
					case ',':
						already = grouped;
						grouped = true;
						break;
					case '(':
						already = parenthesised;
						parenthesised = true;
						break;
					default:
						already = previous;
						previous = true;
						break;
				}
				if (already) {
					throw new java.util.DuplicateFormatFlagsException(String.valueOf(flag));
				}
			}

			int widthStart = at;
			while (at < length && isDigit(format.charAt(at))) at++;
			int width = at > widthStart ? number(format, widthStart, at, percent) : -1;

			int precision = -1;
			if (at < length && format.charAt(at) == '.') {
				at++;
				int precisionStart = at;
				while (at < length && isDigit(format.charAt(at))) at++;
				if (at == precisionStart) {
					throw new UnknownFormatConversionException(format.substring(percent, at));
				}
				precision = number(format, precisionStart, at, percent);
			}

			if (at >= length) {
				throw new UnknownFormatConversionException(format.substring(percent));
			}
			char conversion = format.charAt(at++);
			if (conversion == 't' || conversion == 'T') {
				throw new UnsupportedOperationException(
					"%" +
						conversion +
						" is not supported here, because the date conversions would make the calendar" +
						" and locale graph reachable from every String.format call; DateTimeFormatter" +
						" formats a date"
				);
			}
			if (conversion == 'a' || conversion == 'A') {
				throw new UnsupportedOperationException(
					"%" +
						conversion +
						" is not supported here; Double.toHexString writes a double" +
						" in hexadecimal"
				);
			}

			this.text = format.substring(percent, at);
			this.end = at;
			this.index = index;
			this.width = width;
			this.precision = precision;
			this.conversion = conversion;
			this.left = left;
			this.alternate = alternate;
			this.signed = signed;
			this.space = space;
			this.zero = zero;
			this.grouped = grouped;
			this.parenthesised = parenthesised;
			this.previous = previous;

			if (left && width < 0) throw new MissingFormatWidthException(this.text);
			if (zero && width < 0) throw new MissingFormatWidthException(this.text);
			if (left && zero) {
				throw new java.util.IllegalFormatFlagsException("-0");
			}
			if (signed && space) {
				throw new java.util.IllegalFormatFlagsException("+ ");
			}
			if (index == 0) {
				throw new IllegalFormatException("Illegal format argument index = 0");
			}
		}

		private static int number(String format, int from, int to, int percent) {
			try {
				return Integer.parseInt(format.substring(from, to));
			} catch (NumberFormatException tooLarge) {
				throw new UnknownFormatConversionException(format.substring(percent, to));
			}
		}
	}

	// #endregion

	// #region conversions

	private String literal(Specifier specifier) {
		if (specifier.conversion == 'n') {
			if (specifier.width >= 0 || specifier.precision >= 0) {
				throw new java.util.IllegalFormatFlagsException(specifier.text);
			}
			// the platform's separator rather than a newline, which is what the conversion means; on
			// this runtime the two are the same, and on a JVM under Windows they are not
			// the platform's separator rather than a newline, which is what the conversion means; on
			// this runtime the two are the same, and on a JVM under Windows they are not
			return System.lineSeparator();
		}
		if (specifier.precision >= 0) {
			throw new IllegalFormatPrecisionException(specifier.precision);
		}
		return justify("%", specifier);
	}

	private String convert(Specifier specifier, Object argument, Locale locale) {
		char conversion = Character.toLowerCase(specifier.conversion);
		boolean upper = Character.isUpperCase(specifier.conversion);
		String value;
		switch (conversion) {
			case 's':
				value = string(specifier, argument, upper);
				break;
			case 'b':
				value = justify(
					truncate(
						argument == null
							? "false"
							: argument instanceof Boolean
								? argument.toString()
								: "true",
						specifier.precision
					),
					specifier
				);
				break;
			case 'h':
				value = justify(
					truncate(
						argument == null ? "null" : Integer.toHexString(argument.hashCode()),
						specifier.precision
					),
					specifier
				);
				break;
			case 'c':
				value = character(specifier, argument);
				break;
			case 'd':
				value = integer(specifier, argument, locale);
				break;
			case 'o':
				value = radix(specifier, argument, 8);
				break;
			case 'x':
				value = radix(specifier, argument, 16);
				break;
			case 'f':
				value = decimal(specifier, argument, locale);
				break;
			case 'e':
				value = exponent(specifier, argument, locale);
				break;
			case 'g':
				value = general(specifier, argument, locale);
				break;
			default:
				throw new UnknownFormatConversionException(String.valueOf(specifier.conversion));
		}
		return upper ? value.toUpperCase(locale == null ? Locale.ROOT : locale) : value;
	}

	/** A type that formats itself is handed the flags and does its own padding, as the runtime does. */
	private String string(Specifier specifier, Object argument, boolean upper) {
		if (argument instanceof Formattable) {
			int flags = 0;
			if (specifier.left) flags |= FormattableFlags.LEFT_JUSTIFY;
			if (specifier.alternate) flags |= FormattableFlags.ALTERNATE;
			if (upper) flags |= FormattableFlags.UPPERCASE;
			StringBuilder into = new StringBuilder();
			((Formattable) argument).formatTo(
				new Formatter(into, locale, separators),
				flags,
				specifier.width,
				specifier.precision
			);
			return into.toString();
		}
		if (specifier.alternate) {
			throw new java.util.FormatFlagsConversionMismatchException("#", 's');
		}
		return justify(truncate(String.valueOf(argument), specifier.precision), specifier);
	}

	private String character(Specifier specifier, Object argument) {
		if (specifier.precision >= 0) {
			throw new IllegalFormatPrecisionException(specifier.precision);
		}
		if (argument == null) return justify("null", specifier);
		int codePoint;
		if (argument instanceof Character) codePoint = (Character) argument;
		else if (argument instanceof Byte) codePoint = (Byte) argument;
		else if (argument instanceof Short) codePoint = (Short) argument;
		else if (argument instanceof Integer) codePoint = (Integer) argument;
		else throw new IllegalFormatConversionException('c', argument.getClass());
		if (!Character.isValidCodePoint(codePoint)) {
			throw new IllegalFormatCodePointException(codePoint);
		}
		return justify(new String(Character.toChars(codePoint)), specifier);
	}

	private String integer(Specifier specifier, Object argument, Locale locale) {
		if (specifier.precision >= 0) {
			throw new IllegalFormatPrecisionException(specifier.precision);
		}
		if (specifier.alternate) {
			throw new java.util.FormatFlagsConversionMismatchException("#", 'd');
		}
		String text = wholeNumber(argument);
		if (text == null) {
			throw new IllegalFormatConversionException(
				'd',
				argument == null ? Object.class : argument.getClass()
			);
		}
		boolean negative = text.startsWith("-");
		// the most negative long has no positive counterpart, so the digits come from the text
		String body = negative ? text.substring(1) : text;
		if (specifier.grouped) body = group(body, locale);
		return signed(body, negative, specifier);
	}

	private String radix(Specifier specifier, Object argument, int radix) {
		char conversion = radix == 8 ? 'o' : 'x';
		if (specifier.precision >= 0) {
			throw new IllegalFormatPrecisionException(specifier.precision);
		}
		if (specifier.grouped) {
			throw new java.util.FormatFlagsConversionMismatchException(",", conversion);
		}
		if (specifier.parenthesised) {
			throw new java.util.FormatFlagsConversionMismatchException("(", conversion);
		}

		if (specifier.signed) {
			throw new java.util.FormatFlagsConversionMismatchException("+", conversion);
		}
		if (specifier.space) {
			throw new java.util.FormatFlagsConversionMismatchException(" ", conversion);
		}
		String body = unsigned(argument, conversion, radix);
		if (specifier.alternate) body = (radix == 8 ? "0" : "0x") + body;
		return signed(body, false, specifier);
	}

	private String decimal(Specifier specifier, Object argument, Locale locale) {
		int scale = specifier.precision < 0 ? 6 : specifier.precision;
		Fraction value = fraction(specifier, argument, 'f');
		if (value.special != null) return value.special;
		String body = value.digits.toFraction(scale).plain(scale);
		return decimalBody(specifier, locale, body, value.negative);
	}

	private String exponent(Specifier specifier, Object argument, Locale locale) {
		int scale = specifier.precision < 0 ? 6 : specifier.precision;
		if (specifier.grouped) {
			throw new java.util.FormatFlagsConversionMismatchException(",", 'e');
		}
		Fraction value = fraction(specifier, argument, 'e');
		if (value.special != null) return value.special;
		String body = value.digits.toSignificant(scale + 1).scientific(scale);
		return decimalBody(specifier, locale, body, value.negative);
	}

	private String general(Specifier specifier, Object argument, Locale locale) {
		int precision =
			specifier.precision < 0 ? 6 : specifier.precision == 0 ? 1 : specifier.precision;
		if (specifier.alternate) {
			throw new java.util.FormatFlagsConversionMismatchException("#", 'g');
		}
		Fraction value = fraction(specifier, argument, 'g');
		if (value.special != null) return value.special;

		// which form to write is decided by where the leading digit lands once it has been rounded
		Decimal rounded = value.digits.toSignificant(precision);
		int exponent = rounded.magnitude();
		String body =
			exponent < -4 || exponent >= precision
				? rounded.scientific(precision - 1)
				: rounded.plain(Math.max(precision - 1 - exponent, 0));
		return decimalBody(specifier, locale, body, value.negative);
	}

	/** A number ready to be written, or the whole answer already when it is not a number at all. */
	private static final class Fraction {

		private final Decimal digits;
		private final boolean negative;
		private final String special;

		Fraction(Decimal digits, boolean negative, String special) {
			this.digits = digits;
			this.negative = negative;
			this.special = special;
		}
	}

	/**
	 * The digits of a floating-point argument.
	 *
	 * <p>A value that carries its own digits - an arbitrary-precision decimal - is read from its text
	 * rather than through its type, which keeps {@code java.math} out of the binary and is exact where
	 * going through a double would not be.
	 */
	private Fraction fraction(Specifier specifier, Object argument, char conversion) {
		if (argument instanceof Double || argument instanceof Float) {
			double value = argument instanceof Double ? (Double) argument : (Float) argument;
			String special = special(value, specifier);
			if (special != null) return new Fraction(null, false, special);
			return new Fraction(Decimal.of(Math.abs(value)), isNegative(value), null);
		}
		// a whole number is not a floating-point argument, which the runtime refuses rather than widens
		boolean whole =
			argument instanceof Long ||
			argument instanceof Integer ||
			argument instanceof Short ||
			argument instanceof Byte;
		String text = !whole && argument instanceof Number ? argument.toString() : null;
		if (text != null && isDecimal(text)) {
			boolean negative = text.startsWith("-");
			return new Fraction(Decimal.of(negative ? text.substring(1) : text), negative, null);
		}
		throw new IllegalFormatConversionException(
			conversion,
			argument == null ? Object.class : argument.getClass()
		);
	}

	// #endregion

	// #region shared shaping

	private String decimalBody(Specifier specifier, Locale locale, String body, boolean negative) {
		if (specifier.grouped && body.indexOf('e') < 0) body = groupFraction(body, locale);
		String point = separator(locale, 0);
		if (!".".equals(point)) body = body.replace(".", point);
		if (specifier.alternate && body.indexOf(point.charAt(0)) < 0 && body.indexOf('e') < 0) {
			body = body + point;
		}
		return signed(body, negative, specifier);
	}

	/** Groups only the digits before the point, which is the only part that is grouped. */
	private String groupFraction(String body, Locale locale) {
		int point = body.indexOf('.');
		if (point < 0) return group(body, locale);
		return group(body.substring(0, point), locale) + body.substring(point);
	}

	private String group(String body, Locale locale) {
		String separator = separator(locale, 1);
		StringBuilder grouped = new StringBuilder();
		int lead = body.length() % 3 == 0 ? 3 : body.length() % 3;
		grouped.append(body, 0, lead);
		for (int at = lead; at < body.length(); at += 3) {
			grouped.append(separator).append(body, at, at + 3);
		}
		return grouped.toString();
	}

	/** The sign, the padding and the parentheses, in the order the runtime writes them. */
	private String signed(String body, boolean negative, Specifier specifier) {
		String prefix = negative
			? specifier.parenthesised
				? ""
				: "-"
			: specifier.signed
				? "+"
				: specifier.space
					? " "
					: "";
		String suffix = "";
		if (negative && specifier.parenthesised) {
			prefix = "(";
			suffix = ")";
		}
		if (specifier.zero && specifier.width > prefix.length() + body.length() + suffix.length()) {
			body = pad(
				body,
				specifier.width - prefix.length() - body.length() - suffix.length(),
				true
			);
		}
		return justify(prefix + body + suffix, specifier);
	}

	private String justify(String value, Specifier specifier) {
		if (specifier.width <= value.length()) return value;
		StringBuilder padding = new StringBuilder();
		for (int i = value.length(); i < specifier.width; i++) padding.append(' ');
		return specifier.left ? value + padding : padding + value;
	}

	private static String pad(String body, int zeros, boolean before) {
		StringBuilder padding = new StringBuilder();
		for (int i = 0; i < zeros; i++) padding.append('0');
		return before ? padding + body : body + padding;
	}

	private static String truncate(String value, int precision) {
		return precision < 0 || precision >= value.length() ? value : value.substring(0, precision);
	}

	/** Infinity and not-a-number carry a sign and nothing else, so they skip the digits entirely. */
	private String special(double value, Specifier specifier) {
		if (!Double.isNaN(value) && !Double.isInfinite(value)) return null;
		if (Double.isNaN(value)) return justify("NaN", specifier);
		return signed("Infinity", value < 0, specifier);
	}

	/** Negative zero is negative, and comparing it against zero does not say so. */
	private static boolean isNegative(double value) {
		return value < 0 || (value == 0.0 && Double.doubleToRawLongBits(value) != 0L);
	}

	private String separator(Locale locale, int which) {
		if (locale == null) return which == 0 ? "." : ",";
		String pair = separators.of(locale.toLanguageTag());
		return pair.length() < 2 ? (which == 0 ? "." : ",") : String.valueOf(pair.charAt(which));
	}

	/**
	 * An argument's digits when it is a whole number, and null when it is not one.
	 *
	 * <p>A value wider than a long - an arbitrary-precision integer - is read from its text rather than
	 * through its type, which keeps {@code java.math} out of the binary.
	 */
	private static String wholeNumber(Object argument) {
		if (argument instanceof Long) return Long.toString((Long) argument);
		if (argument instanceof Integer) return Integer.toString((Integer) argument);
		if (argument instanceof Short) return Short.toString((Short) argument);
		if (argument instanceof Byte) return Byte.toString((Byte) argument);
		if (!(argument instanceof Number)) return null;
		String text = argument.toString();
		return isWhole(text) ? text : null;
	}

	private static boolean isWhole(String text) {
		int at = text.startsWith("-") ? 1 : 0;
		if (at >= text.length()) return false;
		for (; at < text.length(); at++) {
			if (!isDigit(text.charAt(at))) return false;
		}
		return true;
	}

	/** Digits, at most one point, and no exponent, which is how a decimal writes itself. */
	private static boolean isDecimal(String text) {
		int at = text.startsWith("-") ? 1 : 0;
		if (at >= text.length()) return false;
		boolean seenPoint = false;
		for (; at < text.length(); at++) {
			char character = text.charAt(at);
			if (character == '.') {
				if (seenPoint) return false;
				seenPoint = true;
			} else if (character == 'E' || character == 'e' || character == '+') {
				continue;
			} else if (!isDigit(character)) {
				return false;
			}
		}
		return true;
	}

	/** Read without sign extension, so that a negative byte reads as two hexadecimal digits. */
	private static String unsigned(Object argument, char conversion, int radix) {
		if (argument instanceof Byte) {
			return Integer.toString((Byte) argument & 0xFF, radix);
		}
		if (argument instanceof Short) {
			return Integer.toString((Short) argument & 0xFFFF, radix);
		}
		if (argument instanceof Integer) {
			return radix == 8
				? Integer.toOctalString((Integer) argument)
				: Integer.toHexString((Integer) argument);
		}
		if (argument instanceof Long) {
			return radix == 8
				? Long.toOctalString((Long) argument)
				: Long.toHexString((Long) argument);
		}
		throw new IllegalFormatConversionException(
			conversion,
			argument == null ? Object.class : argument.getClass()
		);
	}

	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}

	private void write(String value) {
		try {
			out.append(value);
		} catch (IOException reported) {
			failure = reported;
		}
	}

	private void checkOpen() {
		if (closed) throw new FormatterClosedException();
	}

	// #endregion
}
