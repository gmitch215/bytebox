package dev.gmitch215.bytebox.time;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Points {@code java.time} at an implementation of it, and its rule provider at this package's.
 *
 * <p>The class library has no {@code java.time} at all - not a subset, nothing - so a program using a
 * date fails while it is being compiled. What it is pointed at is ThreeTen-Backport, the reference
 * implementation of the same API by the same author, which compiles here unchanged apart from its
 * timezone database.
 *
 * <p>That database is the second rule. Left alone it reads a compiled 108 KB copy of the timezone rules
 * out of the binary through a service lookup; pointed at {@link ZoneRulesProvider} it reads the
 * platform's, and the copy along with its reader stops being reachable.
 *
 * @since 1.0.0
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.time.zone.ZoneRules").or(named("java.time.zone.ZoneRulesProvider"))
		).replacePackage("java.time.zone", "dev.gmitch215.bytebox.time");
		sink.selectClasses(
			named("org.threeten.bp.zone.ZoneRules").or(
				named("org.threeten.bp.zone.ZoneRulesProvider")
			)
		).replacePackage("org.threeten.bp.zone", "dev.gmitch215.bytebox.time");
		sink.selectClasses(inPackage("java.time", true)).replacePackage(
			"java.time",
			"org.threeten.bp"
		);
	}
}
