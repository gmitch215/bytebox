package dev.gmitch215.bytebox.security;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Supplies {@code java.security.MessageDigest} and {@code DigestException}, which the class library
 * does not carry.
 *
 * <p>Named explicitly and never by package, so the engine classes next to them stay what they are.
 *
 * @since 1.0.3
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.security.MessageDigest").or(named("java.security.DigestException"))
		).replacePackage("java.security", "dev.gmitch215.bytebox.security");
	}
}
