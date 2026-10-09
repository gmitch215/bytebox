package dev.gmitch215.bytebox.util;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Supplies the {@code java.util.concurrent} queue the class library leaves out.
 *
 * @since 1.0.3
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(named("java.util.concurrent.LinkedBlockingQueue")).replacePackage(
			"java.util.concurrent",
			"dev.gmitch215.bytebox.util"
		);
	}
}
