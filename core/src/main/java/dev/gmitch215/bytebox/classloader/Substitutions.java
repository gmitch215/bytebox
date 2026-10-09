package dev.gmitch215.bytebox.classloader;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Supplies a {@code java.lang.ClassLoader} that can say there is no classpath.
 *
 * <p>Named explicitly, so {@code Class} and the rest of {@code java.lang} stay what they are.
 *
 * @since 1.0.3
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(named("java.lang.ClassLoader")).replacePackage(
			"java.lang",
			"dev.gmitch215.bytebox.classloader"
		);
	}
}
