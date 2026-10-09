package dev.gmitch215.bytebox.text;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Points {@code java.util.Formatter} and what it needs at the ones in this package.
 *
 * <p>Found through {@code META-INF/services}, which is how the compiler's own class library registers
 * its substitutions. {@code String.format} and {@code printf} both go through
 * {@code java.util.Formatter}, so replacing it is what makes them cost what they cost here.
 *
 * <p>The class library's {@code DoubleAnalyzer}, which {@code Double.toString} and every string
 * concatenation of a double write digits from, is pointed at {@link DoubleAnalyzer}, its copy that
 * picks the shortest digits exactly, and its {@code DoubleSynthesizer}, which {@code DecimalFormat}
 * reads a number with, at {@link DoubleSynthesizer}. Its {@code FloatAnalyzer}, which
 * {@code Float.toString} writes from, is pointed at {@link FloatAnalyzer}. {@code Double.parseDouble} and
 * {@code Float.parseFloat} no longer come through either: {@link ParsePlugin} sends them to
 * {@link Parse}.
 *
 * <p>{@code IllegalFormatException} comes along because its constructors are package-private in the
 * class library, so the two subclasses it is missing cannot be written anywhere else.
 * {@code Formattable} comes along because its one method takes a formatter.
 *
 * @since 1.0.0
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.util.Formatter")
				.or(named("java.util.StringJoiner"))
				.or(named("java.util.Formattable"))
				.or(named("java.util.FormattableFlags"))
				.or(named("java.util.IllegalFormatException"))
				.or(named("java.util.IllegalFormatWidthException"))
				.or(named("java.util.MissingFormatArgumentException"))
		).replacePackage("java.util", "dev.gmitch215.bytebox.text");
		sink.selectClasses(
			named("org.teavm.classlib.impl.text.DoubleAnalyzer")
				.or(named("org.teavm.classlib.impl.text.DoubleAnalyzer$Result"))
				.or(named("org.teavm.classlib.impl.text.DoubleSynthesizer"))
				.or(named("org.teavm.classlib.impl.text.FloatAnalyzer"))
				.or(named("org.teavm.classlib.impl.text.FloatAnalyzer$Result"))
		).replacePackage("org.teavm.classlib.impl.text", "dev.gmitch215.bytebox.text");
	}
}
