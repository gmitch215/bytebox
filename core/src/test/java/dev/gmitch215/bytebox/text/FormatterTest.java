package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The formatter, compared against the runtime that defines it.
 *
 * <p>The claim this class makes is that it formats what {@code String.format} formats, and this lane
 * runs on a JVM, so the claim is checkable rather than assertable: every format string is run both ways
 * and the two results are compared. Only the separators are stood in for, because they are the one
 * thing the platform supplies; the digits and everything else are the formatter's own.
 */
@DisplayName("Formatter")
class FormatterTest {

	/** What the platform answers for an English locale, so the comparison needs no platform. */
	private static final Separators SEPARATORS = languageTag -> ".,";

	/** Formatted by this class, in the same locale. */
	private static String ours(String format, Object... args) {
		StringBuilder into = new StringBuilder();
		new Formatter(into, Locale.US, SEPARATORS).format(format, args);
		return into.toString();
	}

	private static void assertMatches(String format, Object... args) {
		assertEquals(
			String.format(Locale.US, format, args),
			ours(format, args),
			"format: " + format
		);
	}

	// #region strings, characters and booleans

	@ParameterizedTest
	@ValueSource(
		strings = {
			"%s",
			"%S",
			"%10s",
			"%-10s|",
			"%.2s",
			"%10.2s|",
			"%-10.3s|",
			"[%s][%s]",
			"%2$s %1$s",
			"%s %<s %<s"
		}
	)
	@DisplayName("matches on strings")
	void matchesOnStrings(String format) {
		assertMatches(format, "abcdef", "xyz");
	}

	@Test
	@DisplayName("matches on a null, which is the four letters rather than nothing")
	void matchesOnNull() {
		assertMatches("%s", (Object) null);
		assertMatches("%8s|", (Object) null);
		assertMatches("%b", (Object) null);
		assertMatches("%h", (Object) null);
		assertMatches("%c", (Object) null);
	}

	@Test
	@DisplayName("matches on booleans, which anything not null is")
	void matchesOnBooleans() {
		assertMatches("%b %b %b %b", true, false, "text", 7);
		assertMatches("%B", true);
		assertMatches("%-8b|", true);
		assertMatches("%.2b", true);
	}

	@Test
	@DisplayName("matches on characters, including one outside the basic plane")
	void matchesOnCharacters() {
		assertMatches("%c", 'q');
		assertMatches("%C", 'q');
		assertMatches("%5c|", 'q');
		assertMatches("%-5c|", 'q');
		assertMatches("%c", 65);
		assertMatches("%c", (byte) 65);
		assertMatches("%c", (short) 65);
		assertMatches("%c", 0x1F600);
	}

	@Test
	@DisplayName("matches on a hash, which is the hex of hashCode")
	void matchesOnHashes() {
		assertMatches("%h", "abc");
		assertMatches("%H", "abc");
		assertMatches("%.3h", "abc");
	}

	// #endregion

	// #region integers

	@ParameterizedTest
	@ValueSource(
		strings = {
			"%d",
			"%10d",
			"%-10d|",
			"%010d",
			"%+d",
			"% d",
			"%,d",
			"%,012d",
			"%(d",
			"%+,(12d",
			"%(012d"
		}
	)
	@DisplayName("matches on integers, positive and negative")
	void matchesOnIntegers(String format) {
		assertMatches(format, 1234567);
		assertMatches(format, -1234567);
		assertMatches(format, 0);
		assertMatches(format, 7);
	}

	@Test
	@DisplayName("matches on every width of integer, and on the value with no positive counterpart")
	void matchesOnEveryWidth() {
		assertMatches(
			"%d %d %d %d",
			(byte) -128,
			(short) -32768,
			Integer.MIN_VALUE,
			Long.MIN_VALUE
		);
		assertMatches("%d %d", Long.MAX_VALUE, Integer.MAX_VALUE);
		assertMatches("%,d", Long.MIN_VALUE);
		assertMatches("%d", BigInteger.valueOf(-1234567890123L).multiply(BigInteger.TEN));
		assertMatches("%,+d", new BigInteger("123456789012345678901234567890"));
		assertMatches("%(d", new BigInteger("-123456789012345678901234567890"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "%x", "%X", "%o", "%#x", "%#X", "%#o", "%08x", "%-8x|", "%08o" })
	@DisplayName("matches on hexadecimal and octal, which read the bits unsigned")
	void matchesOnRadix(String format) {
		assertMatches(format, 255);
		assertMatches(format, -1);
		assertMatches(format, (byte) -1);
		assertMatches(format, (short) -1);
		assertMatches(format, -1L);
		assertMatches(format, 0);
	}

	/**
	 * A value wider than a long is read from its text, which gives its decimal digits and not its
	 * digits in another base, so the two radix conversions refuse it the way they refuse a string.
	 */
	@Test
	@DisplayName("refuses a big integer in hexadecimal, because its text is decimal")
	void refusesBigRadix() {
		assertThrows(java.util.IllegalFormatConversionException.class, () ->
			ours("%x", new BigInteger("255"))
		);
		assertThrows(java.util.IllegalFormatConversionException.class, () ->
			ours("%o", new BigInteger("8"))
		);
	}

	// #endregion

	// #region floating point

	@ParameterizedTest
	@ValueSource(
		strings = {
			"%f",
			"%.0f",
			"%.1f",
			"%.2f",
			"%.10f",
			"%12.2f",
			"%-12.2f|",
			"%012.2f",
			"%+.2f",
			"% .2f",
			"%,.2f",
			"%(.2f",
			"%#.0f",
			"%,015.3f"
		}
	)
	@DisplayName("matches on decimals")
	void matchesOnDecimals(String format) {
		assertMatches(format, 3.14159);
		assertMatches(format, -3.14159);
		assertMatches(format, 0.0);
		assertMatches(format, 1234567.891);
		assertMatches(format, 0.5);
		assertMatches(format, 2.5);
		assertMatches(format, -0.5);
		assertMatches(format, 1e-7);
	}

	@Test
	@DisplayName("matches on a half that rounds away from zero on both sides")
	void matchesOnHalves() {
		assertMatches("%.1f %.1f %.1f %.1f", 0.25, -0.25, 0.35, -0.35);
		assertMatches("%.0f %.0f %.0f %.0f", 0.5, -0.5, 1.5, -1.5);
		assertMatches("%.2f %.2f", 0.125, -0.125);
	}

	@Test
	@DisplayName("matches on negative zero, which is negative and does not compare as such")
	void matchesOnNegativeZero() {
		assertMatches("%f", -0.0);
		assertMatches("%.2f", -0.0);
		assertMatches("%e", -0.0);
		assertMatches("%+.1f", -0.0);
	}

	@Test
	@DisplayName("matches on not-a-number and on infinity, which carry a sign and no digits")
	void matchesOnSpecials() {
		assertMatches("%f %f %f", Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
		assertMatches("%.2f %e %g", Double.NaN, Double.NaN, Double.NaN);
		assertMatches("%10f|%-10f|", Double.NaN, Double.POSITIVE_INFINITY);
		assertMatches("%+f %(f", Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
	}

	@ParameterizedTest
	@ValueSource(
		strings = { "%e", "%E", "%.0e", "%.3e", "%15.3e", "%-15.3e|", "%015.3e", "%+.3e", "%(.3e" }
	)
	@DisplayName("matches in exponential form, whose exponent carries a sign and two digits")
	void matchesOnExponents(String format) {
		assertMatches(format, 1234.5);
		assertMatches(format, -1234.5);
		assertMatches(format, 0.0);
		assertMatches(format, 1e-7);
		assertMatches(format, 9.9999e99);
		assertMatches(format, 1e100);
	}

	@ParameterizedTest
	@ValueSource(strings = { "%g", "%G", "%.1g", "%.3g", "%.8g", "%15.3g", "%-15.3g|", "%,.8g" })
	@DisplayName("matches in general form, which chooses between the two by the exponent")
	void matchesOnGeneral(String format) {
		assertMatches(format, 1234.5);
		assertMatches(format, -1234.5);
		assertMatches(format, 0.00001234);
		assertMatches(format, 0.0001234);
		assertMatches(format, 123456789.0);
		assertMatches(format, 0.0);
	}

	@Test
	@DisplayName("matches on a float, which widens before it is formatted")
	void matchesOnFloats() {
		assertMatches("%.2f", 1.5f);
		assertMatches("%e", 1.5f);
	}

	@Test
	@DisplayName("matches on a decimal that carries its own scale")
	void matchesOnBigDecimals() {
		assertMatches("%.2f", new BigDecimal("1.005"));
		assertMatches("%.4f", new BigDecimal("-123.456789"));
		assertMatches("%,.2f", new BigDecimal("1234567.891"));
		assertMatches("%.0f", new BigDecimal("2.5"));
		assertMatches("%.0f", new BigDecimal("-2.5"));
	}

	// #endregion

	// #region the literals and the whole string

	@Test
	@DisplayName("matches on a literal percent and a line separator")
	void matchesOnLiterals() {
		assertMatches("100%%");
		assertMatches("%5%|");
		assertMatches("%-5%|");
		assertEquals("\n", ours("%n"));
		assertMatches("a%nb");
	}

	@Test
	@DisplayName("matches on text with no specifier at all, and on an empty string")
	void matchesOnPlainText() {
		assertMatches("nothing to fill");
		assertMatches("");
		assertMatches("trailing text %s here", "x");
	}

	@Test
	@DisplayName("matches on a format string that mixes most of them")
	void matchesOnAMixture() {
		assertMatches(
			"%s has %,d items at %.2f%% (%08.3f) %-10s|%x|%o|%e|%b|%c|",
			"a",
			1234567,
			12.5,
			3.14159,
			"x",
			255,
			8,
			1234.5,
			true,
			'z'
		);
	}

	// #endregion

	// #region refusals

	@Test
	@DisplayName("refuses a specifier with no argument to fill it")
	void refusesAMissingArgument() {
		assertEquals(
			"Format specifier '%s'",
			assertThrows(MissingFormatArgumentException.class, () -> ours("%s")).getMessage()
		);
		assertEquals(
			"%2$s",
			assertThrows(MissingFormatArgumentException.class, () ->
				ours("%2$s", "one")
			).getFormatSpecifier()
		);
		assertThrows(MissingFormatArgumentException.class, () -> ours("%<s"));
	}

	@Test
	@DisplayName("refuses an argument the conversion cannot take")
	void refusesAWrongType() {
		assertThrows(java.util.IllegalFormatConversionException.class, () -> ours("%d", "text"));
		assertThrows(java.util.IllegalFormatConversionException.class, () -> ours("%f", 1));
		assertThrows(java.util.IllegalFormatConversionException.class, () -> ours("%x", 1.5));
		assertThrows(java.util.IllegalFormatConversionException.class, () -> ours("%c", 1.5));
		assertThrows(java.util.IllegalFormatConversionException.class, () ->
			ours("%d", (Object) null)
		);
	}

	@Test
	@DisplayName("refuses a conversion that is not one")
	void refusesAnUnknownConversion() {
		assertThrows(java.util.UnknownFormatConversionException.class, () -> ours("%q", 1));
		assertThrows(java.util.UnknownFormatConversionException.class, () -> ours("%"));
		assertThrows(java.util.UnknownFormatConversionException.class, () -> ours("%.s", "a"));
	}

	@Test
	@DisplayName("refuses flags that contradict each other, or a repeated one")
	void refusesContradictoryFlags() {
		assertThrows(java.util.IllegalFormatFlagsException.class, () -> ours("%-08d", 1));
		assertThrows(java.util.IllegalFormatFlagsException.class, () -> ours("%+ d", 1));
		assertThrows(java.util.DuplicateFormatFlagsException.class, () -> ours("%--8d", 1));
		assertThrows(java.util.DuplicateFormatFlagsException.class, () -> ours("%++d", 1));
	}

	@Test
	@DisplayName("refuses a flag that needs a width without one")
	void refusesAFlagWithoutAWidth() {
		assertThrows(java.util.MissingFormatWidthException.class, () -> ours("%-d", 1));
		assertThrows(java.util.MissingFormatWidthException.class, () -> ours("%0d", 1));
	}

	@Test
	@DisplayName("refuses a precision on a conversion that has no fraction")
	void refusesAPrecisionThatMeansNothing() {
		assertThrows(java.util.IllegalFormatPrecisionException.class, () -> ours("%.2d", 1));
		assertThrows(java.util.IllegalFormatPrecisionException.class, () -> ours("%.2x", 1));
		assertThrows(java.util.IllegalFormatPrecisionException.class, () -> ours("%.2o", 1));
		assertThrows(java.util.IllegalFormatPrecisionException.class, () -> ours("%.2c", 'a'));
		assertThrows(java.util.IllegalFormatPrecisionException.class, () -> ours("%.2%"));
	}

	@Test
	@DisplayName("refuses a flag the conversion has no use for")
	void refusesAMismatchedFlag() {
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () ->
			ours("%#s", "a")
		);
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () -> ours("%#d", 1));
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () -> ours("%,x", 1));
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () -> ours("%+x", 1));
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () -> ours("% x", 1));
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () -> ours("%(x", 1));
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () ->
			ours("%,e", 1.5)
		);
		assertThrows(java.util.FormatFlagsConversionMismatchException.class, () ->
			ours("%#g", 1.5)
		);
	}

	@Test
	@DisplayName("refuses a code point that is not one, and an argument index of zero")
	void refusesABadCodePointAndIndex() {
		assertThrows(java.util.IllegalFormatCodePointException.class, () -> ours("%c", -1));
		assertThrows(java.util.IllegalFormatCodePointException.class, () -> ours("%c", 0x110000));
		assertTrue(
			assertThrows(IllegalFormatException.class, () -> ours("%0$s", "a"))
				.getMessage()
				.contains("index = 0")
		);
	}

	/**
	 * The two conversions that are refused on purpose, because supporting either would put back the
	 * graph this class exists to keep out.
	 */
	@Test
	@DisplayName("refuses the date and hexadecimal-float conversions, naming what to use instead")
	void refusesTheDateAndHexConversions() {
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () -> ours("%tF", 0L))
				.getMessage()
				.contains("DateTimeFormatter")
		);
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () -> ours("%TF", 0L))
				.getMessage()
				.contains("DateTimeFormatter")
		);
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () -> ours("%a", 1.5))
				.getMessage()
				.contains("toHexString")
		);
		assertTrue(
			assertThrows(UnsupportedOperationException.class, () -> ours("%A", 1.5))
				.getMessage()
				.contains("toHexString")
		);
	}

	// #endregion

	// #region inputs chosen to hurt

	/**
	 * A precision near the largest int made the digit count wrap negative, and a negative count rounded
	 * the value away, so {@code %.2147483647f} of 1.5 answered {@code 0}.
	 */
	@Test
	@DisplayName("a precision near the largest int does not wrap the digits away")
	void aHugePrecisionDoesNotWrap() {
		Decimal digits = Decimal.of(1.5);

		assertEquals("1.5", digits.toFraction(Integer.MAX_VALUE).plain(1));
		assertEquals("1.5", digits.toFraction(Integer.MAX_VALUE - 1).plain(1));
		assertEquals("1.5", digits.toFraction(1_000_000).plain(1));
		// a scale below zero still rounds the whole value away, which is what it means
		assertEquals("0", digits.toFraction(-5).plain(0));
	}

	@Test
	@DisplayName("matches on the smallest and largest doubles, whose expansions are extreme")
	void matchesOnExtremeDoubles() {
		assertMatches("%.5f", Double.MIN_VALUE);
		assertMatches("%e", Double.MIN_VALUE);
		assertMatches("%.2f", Double.MAX_VALUE);
		assertMatches("%e", Double.MAX_VALUE);
		assertMatches("%g", Double.MIN_NORMAL);
		assertMatches("%.17f", Math.PI);
		assertMatches("%.30f", 0.1);
	}

	@Test
	@DisplayName("matches on the integers that have no positive counterpart")
	void matchesOnTheMostNegative() {
		assertMatches(
			"%d %,d %(d %x %o",
			Long.MIN_VALUE,
			Long.MIN_VALUE,
			Long.MIN_VALUE,
			Long.MIN_VALUE,
			Long.MIN_VALUE
		);
		assertMatches("%d %,d %x", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		assertMatches("%d %x", (byte) -128, (byte) -128);
		assertMatches("%d %x", (short) -32768, (short) -32768);
	}

	@Test
	@DisplayName("pads to a width far wider than the value, without dropping a character")
	void padsToAWideWidth() {
		assertEquals(1000, ours("%1000s", "x").length());
		assertEquals(1000, ours("%-1000s", "x").length());
		assertEquals(1000, ours("%01000d", 1).length());
		assertMatches("%1000s|", "x");
		assertMatches("%01000d|", -1);
	}

	@Test
	@DisplayName("matches on an empty format string and on an empty argument")
	void matchesOnEmptyThings() {
		assertMatches("", new Object[0]);
		assertMatches("%s", "");
		assertMatches("%10s|", "");
		assertMatches("%.0s|", "abc");
	}

	@Test
	@DisplayName("matches on an argument far longer than anything a width could pad it to")
	void matchesOnAVeryLongArgument() {
		String long1 = "a".repeat(100_000);

		assertEquals(100_000, ours("%s", long1).length());
		assertEquals(50, ours("%.50s", long1).length());
		assertMatches("%.50s", long1);
	}

	// #endregion

	// #region the formatter as an object

	@Test
	@DisplayName("carries its destination, its locale and any failure the destination reported")
	void carriesItsState() {
		StringBuilder into = new StringBuilder();
		Formatter formatter = new Formatter(into, Locale.US, SEPARATORS);

		assertSame(into, formatter.out());
		assertEquals(Locale.US, formatter.locale());
		assertNull(formatter.ioException());
		assertSame(formatter, formatter.format("%d", 1));
		assertEquals("1", formatter.toString());
	}

	@Test
	@DisplayName("writes into a string when it was given nothing to write into")
	void writesIntoAString() {
		Formatter formatter = new Formatter(Locale.US);
		formatter.format("%s", "a");
		assertEquals("a", formatter.toString());
		assertEquals(Locale.US, formatter.locale());

		Formatter plain = new Formatter();
		plain.format("%s", "b");
		assertEquals("b", plain.toString());
		assertNull(plain.locale());
	}

	@Test
	@DisplayName("refuses everything once it is closed, and closing twice is not a failure")
	void refusesOnceClosed() {
		Formatter formatter = new Formatter(new StringBuilder(), Locale.US, SEPARATORS);
		formatter.close();
		formatter.close();

		assertThrows(java.util.FormatterClosedException.class, () -> formatter.format("%s", "a"));
		assertThrows(java.util.FormatterClosedException.class, formatter::out);
		assertThrows(java.util.FormatterClosedException.class, formatter::locale);
		assertThrows(java.util.FormatterClosedException.class, formatter::toString);
		assertThrows(java.util.FormatterClosedException.class, formatter::flush);
	}

	@Test
	@DisplayName("keeps the failure a destination reported rather than throwing it")
	void keepsTheFailure() {
		IOException broken = new IOException("no room");
		Formatter formatter = new Formatter(
			new Appendable() {
				@Override
				public Appendable append(CharSequence text) throws IOException {
					throw broken;
				}

				@Override
				public Appendable append(CharSequence text, int from, int to) throws IOException {
					throw broken;
				}

				@Override
				public Appendable append(char character) throws IOException {
					throw broken;
				}
			},
			Locale.US,
			SEPARATORS
		);

		formatter.format("%s", "a");
		assertSame(broken, formatter.ioException());
	}

	@Test
	@DisplayName("refuses a destination of null")
	void refusesANullDestination() {
		assertThrows(NullPointerException.class, () -> new Formatter((Appendable) null));
		assertThrows(NullPointerException.class, () ->
			new Formatter(new StringBuilder(), Locale.US, SEPARATORS).format(null, 1)
		);
	}

	@Test
	@DisplayName("lets a value format itself, and hands it the flags rather than padding for it")
	void letsAValueFormatItself() {
		Formattable rectangle = (formatter, flags, width, precision) ->
			formatter.format(
				"[%s%s%s]",
				(flags & FormattableFlags.LEFT_JUSTIFY) != 0 ? "left" : "right",
				(flags & FormattableFlags.UPPERCASE) != 0 ? " upper" : "",
				(flags & FormattableFlags.ALTERNATE) != 0 ? " alt" : ""
			);

		assertEquals("[RIGHT UPPER]", ours("%S", rectangle));
		assertEquals("[left]", ours("%-8s", rectangle));
		assertEquals("[right alt]", ours("%#s", rectangle));
	}

	// #endregion
}
