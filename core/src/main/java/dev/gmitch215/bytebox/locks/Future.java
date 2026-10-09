package dev.gmitch215.bytebox.locks;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@code java.util.concurrent.Future}, which the class library does not carry. Only the five methods
 * a {@link CompletableFuture} implements are here; the default methods Java 19 added are not.
 *
 * @param <V> the value type
 * @since 1.0.3
 */
public interface Future<V> {
	/**
	 * @param mayInterruptIfRunning ignored by {@link CompletableFuture}
	 * @return whether the future is now cancelled
	 */
	boolean cancel(boolean mayInterruptIfRunning);

	/** {@return whether the future was cancelled} */
	boolean isCancelled();

	/** {@return whether the future has a result, a failure or was cancelled} */
	boolean isDone();

	/**
	 * Suspends the caller until the future is done.
	 *
	 * @return the value
	 * @throws InterruptedException if the caller was interrupted while waiting
	 * @throws ExecutionException if the future failed
	 */
	V get() throws InterruptedException, ExecutionException;

	/**
	 * @param timeout the longest to wait
	 * @param unit the unit of {@code timeout}
	 * @return the value
	 * @throws InterruptedException if the caller was interrupted while waiting
	 * @throws ExecutionException if the future failed
	 * @throws TimeoutException if the time ran out first
	 */
	V get(long timeout, TimeUnit unit)
		throws InterruptedException, ExecutionException, TimeoutException;
}
