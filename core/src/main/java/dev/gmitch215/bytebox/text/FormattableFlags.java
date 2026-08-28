package dev.gmitch215.bytebox.text;

/**
 * The flags a {@link Formattable} is told about, standing in for
 * {@code java.util.FormattableFlags}.
 *
 * @since 1.0.0
 */
public final class FormattableFlags {

	/** The specifier carried {@code -}. */
	public static final int LEFT_JUSTIFY = 1;

	/** The conversion was written in upper case. */
	public static final int UPPERCASE = 2;

	/** The specifier carried {@code #}. */
	public static final int ALTERNATE = 4;

	private FormattableFlags() {}
}
