package dev.gmitch215.bytebox.concurrent;

import java.util.function.Function;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * A value that is not here yet, backed by a JavaScript promise.
 *
 * <p>{@code java.util.concurrent.CompletableFuture} does not exist on this platform: the class
 * library ships a small subset of {@code java.util.concurrent} with no {@code Future} at all, and a
 * thread pool cannot be built on a runtime with one thread. This carries the part of that API which
 * means something here, over the promise the runtime already understands.
 *
 * <p>Most code does not need it. A binding call blocks and returns a value, which is more direct
 * than a future and costs the same. Reach for this to hold work open past a handler's return, to
 * start several operations before waiting on any of them, or to hand a promise back to JavaScript.
 *
 * {@snippet lang = "java":
 * Future<Response> primary = Async.supply(() -> fetchPrimary(env));
 * Future<Response> backup = Async.supply(() -> fetchBackup(env));
 * ctx.waitUntil(audit(env).promise());
 * return primary.orElseGet(backup::join);
 *}
 *
 * @param <T> the value type
 * @since 1.0.0
 */
public final class Future<T extends JSObject> {

	private final JSPromise<T> promise;

	private Future(JSPromise<T> promise) {
		this.promise = promise;
	}

	/**
	 * Adopts an existing promise.
	 *
	 * @param promise the promise
	 * @param <T> the value type
	 * @return a future over it
	 */
	public static <T extends JSObject> Future<T> of(JSPromise<T> promise) {
		return new Future<>(promise);
	}

	/**
	 * A future that is already done.
	 *
	 * @param value the value
	 * @param <T> the value type
	 * @return the completed future
	 */
	public static <T extends JSObject> Future<T> completed(T value) {
		return new Future<>(JSPromise.resolve(value));
	}

	/**
	 * A future that has already failed.
	 *
	 * @param failure the failure
	 * @param <T> the value type
	 * @return the failed future
	 */
	public static <T extends JSObject> Future<T> failed(Throwable failure) {
		Deferred<T> deferred = Deferred.create();
		deferred.reject(failure);
		return new Future<>(deferred.promise());
	}

	/** {@return the underlying promise, to hand to JavaScript or to {@code ctx.waitUntil}} */
	public JSPromise<T> promise() {
		return promise;
	}

	/**
	 * Waits for the value.
	 *
	 * <p>Suspends the calling fiber, and rethrows whatever the future failed with.
	 *
	 * @return the value
	 */
	public T join() {
		return Async.await(promise);
	}

	/**
	 * Waits for the value, answering a fallback if the future failed.
	 *
	 * @param fallback the value to use on failure
	 * @return the value, or the fallback
	 */
	public T orElse(T fallback) {
		try {
			return join();
		} catch (Throwable ignored) {
			return fallback;
		}
	}

	/**
	 * Waits for the value, calling a supplier if the future failed.
	 *
	 * @param fallback produces the value to use on failure
	 * @return the value, or what the fallback produced
	 */
	public T orElseGet(java.util.function.Supplier<T> fallback) {
		try {
			return join();
		} catch (Throwable ignored) {
			return fallback.get();
		}
	}

	/**
	 * Transforms the value once it arrives, on a fiber of its own.
	 *
	 * @param mapper the transformation
	 * @param <R> the new value type
	 * @return a future for the transformed value
	 */
	public <R extends JSObject> Future<R> map(Function<T, R> mapper) {
		return Async.supply(() -> mapper.apply(join()));
	}

	/**
	 * Chains another asynchronous step after this one.
	 *
	 * @param next produces the next future
	 * @param <R> the new value type
	 * @return a future for the chained result
	 */
	public <R extends JSObject> Future<R> then(Function<T, Future<R>> next) {
		return Async.supply(() -> next.apply(join()).join());
	}

	/**
	 * Handles a failure without unwinding.
	 *
	 * @param recovery produces a replacement value from the failure
	 * @return a future that does not fail
	 */
	public Future<T> recover(Function<Throwable, T> recovery) {
		return Async.supply(() -> {
			try {
				return join();
			} catch (Throwable failure) {
				return recovery.apply(failure);
			}
		});
	}
}
