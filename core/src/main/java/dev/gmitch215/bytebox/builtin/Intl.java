package dev.gmitch215.bytebox.builtin;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;

/**
 * The platform's own locale data, exposed as itself.
 *
 * <p>The isolate already carries locale, currency and timezone data for {@code Intl}. Reaching it
 * costs nothing, while the class library's equivalent would compile CLDR tables into the binary:
 * {@code String.format} and its locale graph measure over 230 KB of wasm, against a hello world of
 * 16 KB.
 *
 * <p>This is deliberately not the {@code java.text} API. It is the JavaScript one, named as such,
 * for the cases where a standard-looking Java surface would be a lie about which rules apply. Where
 * a Java API can be backed faithfully, it is, and that needs no separate name.
 *
 * @since 1.0.0
 */
public final class Intl {

	private Intl() {}

	/**
	 * Formats a number for a locale.
	 *
	 * @param value the number
	 * @param locale a BCP 47 tag, such as {@code en-US}
	 * @return the formatted number
	 */
	@JSBody(
		params = { "value", "locale" },
		script = "return new Intl.NumberFormat(locale).format(value);"
	)
	public static native String number(double value, String locale);

	/**
	 * Formats a number with options.
	 *
	 * @param value the number
	 * @param locale a BCP 47 tag
	 * @param options {@code style}, {@code currency}, {@code minimumFractionDigits} and the rest
	 * @return the formatted number
	 */
	@JSBody(
		params = { "value", "locale", "options" },
		script = "return new Intl.NumberFormat(locale, options).format(value);"
	)
	public static native String number(double value, String locale, TSObject options);

	/**
	 * Formats an amount of money.
	 *
	 * @param value the amount
	 * @param locale a BCP 47 tag
	 * @param currency an ISO 4217 code, such as {@code USD}
	 * @return the formatted amount
	 */
	@JSBody(
		params = { "value", "locale", "currency" },
		script = "return new Intl.NumberFormat(locale," +
			" { style: 'currency', currency: currency }).format(value);"
	)
	public static native String currency(double value, String locale, String currency);

	/**
	 * Formats an instant in a timezone.
	 *
	 * @param millis milliseconds since the epoch
	 * @param locale a BCP 47 tag
	 * @param timeZone an IANA zone, such as {@code Europe/London}
	 * @return the formatted instant
	 */
	@JSBody(
		params = { "millis", "locale", "timeZone" },
		script = "return new Intl.DateTimeFormat(locale," +
			" { timeZone: timeZone, dateStyle: 'medium', timeStyle: 'medium' })" +
			" .format(new Date(millis));"
	)
	public static native String dateTime(double millis, String locale, String timeZone);

	/**
	 * Formats an instant with options.
	 *
	 * @param millis milliseconds since the epoch
	 * @param locale a BCP 47 tag
	 * @param options {@code timeZone}, {@code dateStyle}, {@code timeStyle} and the rest
	 * @return the formatted instant
	 */
	@JSBody(
		params = { "millis", "locale", "options" },
		script = "return new Intl.DateTimeFormat(locale, options).format(new Date(millis));"
	)
	public static native String dateTime(double millis, String locale, TSObject options);

	/**
	 * Formats a duration the way a person would say it, such as {@code 3 days ago}.
	 *
	 * @param value how many units, negative for the past
	 * @param unit {@code second}, {@code minute}, {@code hour}, {@code day}, {@code month},
	 *     {@code year}
	 * @param locale a BCP 47 tag
	 * @return the formatted duration
	 */
	@JSBody(
		params = { "value", "unit", "locale" },
		script = "return new Intl.RelativeTimeFormat(locale).format(value, unit);"
	)
	public static native String relative(double value, String unit, String locale);

	/**
	 * Joins a list the way a language does, such as {@code a, b and c}.
	 *
	 * @param items the items
	 * @param locale a BCP 47 tag
	 * @return the joined list
	 */
	@JSBody(
		params = { "items", "locale" },
		script = "return new Intl.ListFormat(locale).format(items);"
	)
	public static native String list(TSObject items, String locale);

	/**
	 * Compares two strings the way a locale sorts them.
	 *
	 * @param left the first string
	 * @param right the second string
	 * @param locale a BCP 47 tag
	 * @return negative, zero or positive
	 */
	@JSBody(
		params = { "left", "right", "locale" },
		script = "return new Intl.Collator(locale).compare(left, right);"
	)
	public static native int compare(String left, String right, String locale);

	/** {@return the timezone the runtime believes it is in, which on Workers is always UTC} */
	@JSBody(script = "return new Intl.DateTimeFormat().resolvedOptions().timeZone;")
	public static native String timeZone();

	/**
	 * Reports whether the runtime carries data for a locale.
	 *
	 * <p>Worth checking once rather than assuming: a runtime built without full ICU falls back to a
	 * single locale, and every formatter then silently produces English.
	 *
	 * @param locale a BCP 47 tag
	 * @return whether the runtime resolves it to itself rather than falling back
	 */
	@JSBody(
		params = "locale",
		script = "return Intl.NumberFormat.supportedLocalesOf([locale]).length > 0;"
	)
	public static native boolean supports(String locale);
}
