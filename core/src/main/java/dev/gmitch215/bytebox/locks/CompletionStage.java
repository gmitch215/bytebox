package dev.gmitch215.bytebox.locks;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code java.util.concurrent.CompletionStage}, which the class library does not carry. It lists the
 * methods {@link CompletableFuture} implements, and the {@code *Async} and {@code *Either} forms are
 * not among them.
 *
 * @param <T> the value type
 * @since 1.0.3
 */
public interface CompletionStage<T> {
	/**
	 * @param fn maps the value
	 * @param <U> the mapped type
	 * @return a stage holding the mapped value
	 */
	<U> CompletionStage<U> thenApply(Function<? super T, ? extends U> fn);

	/**
	 * @param action takes the value
	 * @return a stage that completes once the action has run
	 */
	CompletionStage<Void> thenAccept(Consumer<? super T> action);

	/**
	 * @param action runs once this stage completes normally
	 * @return a stage that completes once the action has run
	 */
	CompletionStage<Void> thenRun(Runnable action);

	/**
	 * @param other the second stage
	 * @param fn combines both values
	 * @param <U> the second value type
	 * @param <V> the combined type
	 * @return a stage holding the combined value
	 */
	<U, V> CompletionStage<V> thenCombine(
		CompletionStage<? extends U> other,
		BiFunction<? super T, ? super U, ? extends V> fn
	);

	/**
	 * @param fn returns the stage whose result this one adopts
	 * @param <U> the adopted type
	 * @return a stage holding that result
	 */
	<U> CompletionStage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);

	/**
	 * @param fn turns a failure into a value
	 * @return a stage holding the value or the recovered one
	 */
	CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn);

	/**
	 * @param fn maps the value and the failure, one of which is null
	 * @param <U> the mapped type
	 * @return a stage holding the mapped value
	 */
	<U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);

	/**
	 * @param action sees the value and the failure, one of which is null
	 * @return a stage with this one's result
	 */
	CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);

	/** {@return this stage as a {@link CompletableFuture}} */
	CompletableFuture<T> toCompletableFuture();
}
