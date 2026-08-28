package dev.gmitch215.bytebox.regex;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Points {@code java.util.regex.Pattern} and {@code Matcher} at the ones in this package.
 *
 * <p>Found through {@code META-INF/services}, which is how the compiler's own class library registers
 * its substitutions. The class library has a full pattern compiler and matcher, and they measure 68 KB
 * of WebAssembly after compression to do what the isolate can already do.
 *
 * <p>{@code MatchResult} and {@code PatternSyntaxException} are left alone: the first is an interface the
 * matcher implements, and the second already has a usable constructor.
 *
 * @since 1.0.0
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		// named rather than by package, because the class holding the calls into JavaScript lives here
		// too and a substituted class loses them
		sink.selectClasses(
			named("java.util.regex.Pattern").or(named("java.util.regex.Matcher"))
		).replacePackage("java.util.regex", "dev.gmitch215.bytebox.regex");
	}
}
