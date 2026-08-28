package dev.gmitch215.bytebox.text;

/**
 * The characters a locale writes numbers with.
 *
 * <p>The only thing {@link Formatter} needs a locale for, and the only thing it asks the platform for:
 * the digits it works out itself, because the platform rounds differently. {@link Formatter} takes this
 * rather than calling out directly so that the whole class runs under test on a JVM.
 */
@FunctionalInterface
interface Separators {
	/**
	 * @param languageTag a BCP 47 tag
	 * @return the decimal point and the group separator, in that order, as a two-character string
	 */
	String of(String languageTag);
}
