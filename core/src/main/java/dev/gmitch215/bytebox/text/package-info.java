/**
 * What makes {@code String.format} cost what it costs here, which nothing in a project has to name.
 *
 * <p>{@code String.format} and {@code printf} both go through {@code java.util.Formatter}, and the
 * class library's version reaches a decimal formatter, a currency table and the locale data behind
 * them from the single method that dispatches on the conversion character. Because that is one method,
 * every conversion is reachable from every use, so a format string of {@code "%s and %d"} pays for all
 * of it: 85 KB of WebAssembly after compression, against a hello world of 7 KB. The digits come from
 * the platform instead, and {@code Locale} - 0.9 KB on its own - is what remains.
 *
 * <p>{@link dev.gmitch215.bytebox.text.Formatter} records what that costs in behaviour: {@code %t} and
 * {@code %a} throw and name their replacements, a precision above 100 pads with zeros, and grouping is
 * every three digits.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.text;
