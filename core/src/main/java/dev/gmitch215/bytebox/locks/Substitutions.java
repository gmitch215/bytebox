package dev.gmitch215.bytebox.locks;

import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Supplies the pieces of {@code java.util.concurrent} that block ordinary libraries.
 *
 * <p>The class library has no {@code java.util.concurrent.locks} package at all, its atomics stop
 * before the array forms, and it has no {@code CompletableFuture} or the {@code Future} it builds on.
 * None of these gaps is about this platform being single-threaded, and each is enough on its own to
 * refuse a library that only wanted to guard a cache.
 *
 * @since 1.0.2
 */
public final class Substitutions extends SimpleSubstitutionPolicy {

	@Override
	public void contribute(SubstitutionSink sink) {
		sink.selectClasses(
			named("java.util.concurrent.locks.ReentrantLock")
				.or(named("java.util.concurrent.locks.Lock"))
				.or(named("java.util.concurrent.locks.Condition"))
				.or(named("java.util.concurrent.locks.ReentrantLock$Waiters"))
		).replacePackage("java.util.concurrent.locks", "dev.gmitch215.bytebox.locks");
		sink.selectClasses(
			named("java.util.concurrent.atomic.AtomicReferenceArray")
				.or(named("java.util.concurrent.atomic.AtomicLongArray"))
				.or(named("java.util.concurrent.atomic.AtomicIntegerArray"))
		).replacePackage("java.util.concurrent.atomic", "dev.gmitch215.bytebox.locks");
		sink.selectClasses(
			named("java.util.concurrent.CompletableFuture")
				.or(named("java.util.concurrent.CompletionStage"))
				.or(named("java.util.concurrent.Future"))
				.or(named("java.util.concurrent.CompletionException"))
				.or(named("java.util.concurrent.TimeoutException"))
		).replacePackage("java.util.concurrent", "dev.gmitch215.bytebox.locks");
	}
}
