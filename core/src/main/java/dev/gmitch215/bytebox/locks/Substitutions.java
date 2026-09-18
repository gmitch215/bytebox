package dev.gmitch215.bytebox.locks;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Supplies the two pieces of {@code java.util.concurrent} that block ordinary libraries.
 *
 * <p>The class library has no {@code java.util.concurrent.locks} package at all, and its atomics stop
 * before the array forms. Neither gap is about this platform being single-threaded, and both are
 * enough on their own to refuse a library that only wanted to guard a cache.
 *
 * @since 1.0.2
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(named("java.util.concurrent.locks.ReentrantLock")).replacePackage(
			"java.util.concurrent.locks",
			"dev.gmitch215.bytebox.locks"
		);
		sink.selectClasses(
			named("java.util.concurrent.atomic.AtomicReferenceArray")
				.or(named("java.util.concurrent.atomic.AtomicLongArray"))
				.or(named("java.util.concurrent.atomic.AtomicIntegerArray"))
		).replacePackage("java.util.concurrent.atomic", "dev.gmitch215.bytebox.locks");
	}
}
