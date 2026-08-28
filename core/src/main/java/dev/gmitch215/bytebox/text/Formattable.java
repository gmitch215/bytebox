package dev.gmitch215.bytebox.text;

/**
 * A type that formats itself, standing in for {@code java.util.Formattable}.
 *
 * <p>Replaced rather than supplied, because its one method takes a formatter and the formatter is
 * {@link Formatter} here.
 *
 * @since 1.0.0
 */
public interface Formattable {
	/**
	 * Writes this into a formatter.
	 *
	 * @param formatter where to write
	 * @param flags the flags the specifier carried, from {@link FormattableFlags}
	 * @param width the width the specifier carried, or -1 for none
	 * @param precision the precision the specifier carried, or -1 for none
	 */
	void formatTo(Formatter formatter, int flags, int width, int precision);
}
