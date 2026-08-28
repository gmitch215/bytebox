package dev.gmitch215.bytebox.text;

import org.teavm.jso.JSBody;

/**
 * The characters a locale writes numbers with, asked of the platform directly.
 *
 * <p>Separate from {@link Formatter} because that class is a substitute - the compiler renames it to
 * the one it stands in for - and a renamed class loses the annotations that turn a {@code native}
 * method into a call into JavaScript.
 *
 * <p>Only the separators come from here. The digits do not: the platform rounds the exact value of a
 * double where the runtime rounds the shortest decimal that reads back as it, and the two disagree on
 * the cases people notice. {@link Decimal} records that and does the arithmetic instead.
 */
final class Numbers implements Separators {

	static final Numbers PLATFORM = new Numbers();

	private Numbers() {}

	@Override
	public String of(String languageTag) {
		return separators(languageTag);
	}

	@JSBody(
		params = "locale",
		script = "var field = {};" +
			"var parts = new Intl.NumberFormat(locale).formatToParts(1234.5);" +
			"for (var i = 0; i < parts.length; i++) field[parts[i].type] = parts[i].value;" +
			"return (field.decimal || '.') + (field.group || ',');"
	)
	private static native String separators(String locale);
}
