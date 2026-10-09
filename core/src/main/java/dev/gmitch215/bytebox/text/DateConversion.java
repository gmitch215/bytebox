package dev.gmitch215.bytebox.text;

import java.util.Locale;

/**
 * What {@code %t} and {@code %T} format with, which is nothing until {@link Dates#register} installs it.
 *
 * <p>This is not a nested type of {@link Formatter}, because the compiler renames that class and a
 * type that names it would be linked twice. The hook is a field here, and {@code Formatter} reads
 * it, so the date classes are reachable only from a program that installs them.
 */
abstract class DateConversion {

	/** Null unless the project asked for date conversions. */
	static DateConversion installed;

	/**
	 * Formats one {@code %t} conversion.
	 *
	 * @param suffix the character after {@code t}
	 * @param argument the value, never null
	 * @param locale the locale, or null for none
	 * @return the text, before padding and before upper-casing
	 */
	abstract String format(char suffix, Object argument, Locale locale);

	/**
	 * Reads and checks the character after {@code t}, in the order a JVM checks it in.
	 *
	 * <p>It lives here and not in {@code Formatter} so that a program that formats and never asks for
	 * dates does not carry the checks.
	 *
	 * @param format the format string
	 * @param at where the suffix would be
	 * @param conversion {@code t} or {@code T}
	 * @param precision the specifier's precision, or -1
	 * @return the suffix
	 */
	abstract char suffix(
		String format,
		int at,
		char conversion,
		int precision,
		boolean alternate,
		boolean signed,
		boolean space,
		boolean zero,
		boolean grouped,
		boolean parenthesised
	);
}
