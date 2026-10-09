package dev.gmitch215.bytebox.locks;

import dev.gmitch215.bytebox.sync.Deadline;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code java.util.concurrent.CompletableFuture}, which the class library does not carry.
 *
 * <p>Failures follow the JDK's wrapping: a stage that fails because of another is completed with a
 * {@link CompletionException} around the cause, {@link #join()} throws that, and {@link #get()}
 * unwraps it into an {@code ExecutionException}. {@link #supplyAsync(Supplier)} runs the task on a
 * new fiber, since there is no pool, and callbacks run on the fiber that completes the stage.
 *
 * <p>{@link #get()}, {@link #get(long, TimeUnit)} and {@link #join()} suspend the caller, so they
 * trap when no fiber is running, even on a future that is already done. The generated entry point
 * builds the handler, a Durable Object and a Workflow on a fiber, so their constructors and the
 * static initializers they trigger may call them.
 *
 * <p>When a {@code whenComplete} action throws on a stage that had already failed, the original
 * failure stands and the action's is dropped; the JDK adds it as a suppressed exception, and
 * {@code Throwable.addSuppressed} throws a {@code NullPointerException} on this runtime.
 *
 * <p>Left out: the {@code *Async} forms of the {@code then} methods, the {@code *Either} and
 * {@code *Both} forms, {@code orTimeout}, {@code completeAsync}, the {@code obtrude} methods and the
 * Java 19 additions. Calling one fails to link when the program is built.
 *
 * @param <T> the value type
 * @since 1.0.3
 */
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

	private final Object monitor = new Object();
	private volatile boolean done;
	private Object value;
	private Throwable failure;
	private List<Runnable> dependents;

	/** A future nothing has completed. */
	public CompletableFuture() {}

	// #region factories

	/**
	 * @param supplier computes the value on a new fiber
	 * @param <U> the value type
	 * @return the future its result completes
	 */
	public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
		return supply(supplier, null);
	}

	/**
	 * @param supplier computes the value
	 * @param executor runs the task
	 * @param <U> the value type
	 * @return the future its result completes
	 */
	public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
		if (executor == null) throw new NullPointerException();
		return supply(supplier, executor);
	}

	/**
	 * @param runnable runs on a new fiber
	 * @return the future that completes once it has run
	 */
	public static CompletableFuture<Void> runAsync(Runnable runnable) {
		if (runnable == null) throw new NullPointerException();
		return supply(() -> {
			runnable.run();
			return null;
		}, null);
	}

	/**
	 * @param runnable runs
	 * @param executor runs the task
	 * @return the future that completes once it has run
	 */
	public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
		if (runnable == null || executor == null) throw new NullPointerException();
		return supply(() -> {
			runnable.run();
			return null;
		}, executor);
	}

	/**
	 * @param value the value
	 * @param <U> the value type
	 * @return a future already holding it
	 */
	public static <U> CompletableFuture<U> completedFuture(U value) {
		CompletableFuture<U> out = new CompletableFuture<>();
		out.finish(value, null);
		return out;
	}

	/**
	 * @param ex the failure
	 * @param <U> the value type
	 * @return a future already failed with it
	 */
	public static <U> CompletableFuture<U> failedFuture(Throwable ex) {
		if (ex == null) throw new NullPointerException();
		CompletableFuture<U> out = new CompletableFuture<>();
		out.finish(null, ex);
		return out;
	}

	/**
	 * @param cfs the futures to wait for
	 * @return a future that completes once all of them have, failing with the first failure in
	 *     argument order
	 */
	public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs) {
		CompletableFuture<Void> out = new CompletableFuture<>();
		for (CompletableFuture<?> cf : cfs) if (cf == null) throw new NullPointerException();
		if (cfs.length == 0) {
			out.finish(null, null);
			return out;
		}
		int[] left = { cfs.length };
		for (CompletableFuture<?> cf : cfs) {
			cf.onDone(() -> {
				boolean last;
				synchronized (left) {
					last = --left[0] == 0;
				}
				if (!last) return;
				for (CompletableFuture<?> each : cfs) {
					if (each.failure != null) {
						out.finishWrapped(each.failure);
						return;
					}
				}
				out.finish(null, null);
			});
		}
		return out;
	}

	/**
	 * @param cfs the futures to race
	 * @return a future that completes with whichever finishes first, the first listed when several
	 *     already have; one that never completes when none are given
	 */
	public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs) {
		CompletableFuture<Object> out = new CompletableFuture<>();
		for (CompletableFuture<?> cf : cfs) if (cf == null) throw new NullPointerException();
		for (CompletableFuture<?> cf : cfs) {
			if (cf.done) {
				out.relay(cf);
				return out;
			}
		}
		for (CompletableFuture<?> cf : cfs) cf.onDone(() -> out.relay(cf));
		return out;
	}

	// #endregion

	// #region completing

	/**
	 * @param value the value
	 * @return whether this call completed the future
	 */
	public boolean complete(T value) {
		return finish(value, null);
	}

	/**
	 * @param ex the failure
	 * @return whether this call completed the future
	 */
	public boolean completeExceptionally(Throwable ex) {
		if (ex == null) throw new NullPointerException();
		return finish(null, ex);
	}

	/**
	 * @param mayInterruptIfRunning ignored
	 * @return whether the future is now cancelled
	 */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		boolean cancelled = finish(null, new CancellationException());
		return cancelled || isCancelled();
	}

	// #endregion

	// #region reading

	/** {@return whether the future has a result, a failure or was cancelled} */
	@Override
	public boolean isDone() {
		return done;
	}

	/** {@return whether the future was cancelled} */
	@Override
	public boolean isCancelled() {
		return done && failure instanceof CancellationException;
	}

	/** {@return whether the future failed, including by cancellation} */
	public boolean isCompletedExceptionally() {
		return done && failure != null;
	}

	/**
	 * Suspends the caller until the future is done.
	 *
	 * @return the value
	 * @throws InterruptedException if the caller was interrupted while waiting
	 * @throws ExecutionException if the future failed, with the underlying failure as its cause
	 */
	@Override
	public T get() throws InterruptedException, ExecutionException {
		synchronized (monitor) {
			while (!done) monitor.wait();
		}
		return reportGet();
	}

	/**
	 * @param timeout the longest to wait
	 * @param unit the unit of {@code timeout}
	 * @return the value
	 * @throws InterruptedException if the caller was interrupted while waiting
	 * @throws ExecutionException if the future failed, with the underlying failure as its cause
	 * @throws TimeoutException if the time ran out first
	 */
	@Override
	public T get(long timeout, TimeUnit unit)
		throws InterruptedException, ExecutionException, TimeoutException {
		long deadline = Deadline.after(unit.toNanos(timeout));
		synchronized (monitor) {
			while (!done) {
				long left = Deadline.left(deadline);
				if (left <= 0) throw new TimeoutException();
				monitor.wait(left);
			}
		}
		return reportGet();
	}

	/**
	 * Suspends the caller until the future is done.
	 *
	 * @return the value
	 * @throws CompletionException if the future failed, with the underlying failure as its cause
	 */
	public T join() {
		boolean interrupted = false;
		synchronized (monitor) {
			while (!done) {
				try {
					monitor.wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
		return reportJoin();
	}

	/**
	 * @param valueIfAbsent returned when the future is not done
	 * @return the value, or {@code valueIfAbsent}
	 */
	public T getNow(T valueIfAbsent) {
		return done ? reportJoin() : valueIfAbsent;
	}

	// #endregion

	// #region stages

	@Override
	public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
		if (fn == null) throw new NullPointerException();
		CompletableFuture<U> out = new CompletableFuture<>();
		onDone(() -> {
			if (failure != null) {
				out.finishWrapped(failure);
				return;
			}
			try {
				out.finish(fn.apply(result()), null);
			} catch (Throwable t) {
				out.finishWrapped(t);
			}
		});
		return out;
	}

	@Override
	public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
		if (action == null) throw new NullPointerException();
		return thenApply(v -> {
			action.accept(v);
			return null;
		});
	}

	@Override
	public CompletableFuture<Void> thenRun(Runnable action) {
		if (action == null) throw new NullPointerException();
		return thenApply(v -> {
			action.run();
			return null;
		});
	}

	@Override
	public <U, V> CompletableFuture<V> thenCombine(
		CompletionStage<? extends U> other,
		BiFunction<? super T, ? super U, ? extends V> fn
	) {
		if (fn == null) throw new NullPointerException();
		CompletableFuture<? extends U> second = other.toCompletableFuture();
		CompletableFuture<V> out = new CompletableFuture<>();
		onDone(() ->
			second.onDone(() -> {
				Throwable first = failure != null ? failure : second.failure;
				if (first != null) {
					out.finishWrapped(first);
					return;
				}
				try {
					out.finish(fn.apply(result(), second.result()), null);
				} catch (Throwable t) {
					out.finishWrapped(t);
				}
			})
		);
		return out;
	}

	@Override
	public <U> CompletableFuture<U> thenCompose(
		Function<? super T, ? extends CompletionStage<U>> fn
	) {
		if (fn == null) throw new NullPointerException();
		CompletableFuture<U> out = new CompletableFuture<>();
		onDone(() -> {
			if (failure != null) {
				out.finishWrapped(failure);
				return;
			}
			try {
				CompletableFuture<U> inner = fn.apply(result()).toCompletableFuture();
				inner.onDone(() -> out.relay(inner));
			} catch (Throwable t) {
				out.finishWrapped(t);
			}
		});
		return out;
	}

	@Override
	public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
		if (fn == null) throw new NullPointerException();
		CompletableFuture<T> out = new CompletableFuture<>();
		onDone(() -> {
			if (failure == null) {
				out.finish(value, null);
				return;
			}
			try {
				out.finish(fn.apply(failure), null);
			} catch (Throwable t) {
				out.finishWrapped(t);
			}
		});
		return out;
	}

	@Override
	public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		if (fn == null) throw new NullPointerException();
		CompletableFuture<U> out = new CompletableFuture<>();
		onDone(() -> {
			try {
				out.finish(fn.apply(failure == null ? result() : null, failure), null);
			} catch (Throwable t) {
				out.finishWrapped(t);
			}
		});
		return out;
	}

	@Override
	public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		if (action == null) throw new NullPointerException();
		CompletableFuture<T> out = new CompletableFuture<>();
		onDone(() -> {
			try {
				action.accept(failure == null ? result() : null, failure);
			} catch (Throwable t) {
				if (failure == null) {
					out.finishWrapped(t);
					return;
				}
			}
			if (failure == null) out.finish(value, null);
			else out.finishWrapped(failure);
		});
		return out;
	}

	@Override
	public CompletableFuture<T> toCompletableFuture() {
		return this;
	}

	// #endregion

	@Override
	public String toString() {
		String state;
		synchronized (monitor) {
			if (done) {
				state =
					failure == null
						? "[Completed normally]"
						: "[Completed exceptionally: " + failure + "]";
			} else {
				int n = dependents == null ? 0 : dependents.size();
				state = n == 0 ? "[Not completed]" : "[Not completed, " + n + " dependents]";
			}
		}
		return super.toString() + state;
	}

	private static <U> CompletableFuture<U> supply(Supplier<U> supplier, Executor executor) {
		if (supplier == null) throw new NullPointerException();
		CompletableFuture<U> out = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				out.finish(supplier.get(), null);
			} catch (Throwable t) {
				out.finishWrapped(t);
			}
		};
		if (executor == null) new Thread(task).start();
		else executor.execute(task);
		return out;
	}

	@SuppressWarnings("unchecked")
	private T result() {
		return (T) value;
	}

	private T reportGet() throws ExecutionException {
		Throwable x = failure;
		if (x == null) return result();
		if (x instanceof CancellationException) throw (CancellationException) x;
		if (x instanceof CompletionException && x.getCause() != null) x = x.getCause();
		throw new ExecutionException(x);
	}

	private T reportJoin() {
		Throwable x = failure;
		if (x == null) return result();
		if (x instanceof CancellationException) throw (CancellationException) x;
		if (x instanceof CompletionException) throw (CompletionException) x;
		throw new CompletionException(x);
	}

	private boolean finish(Object value, Throwable failure) {
		List<Runnable> run;
		synchronized (monitor) {
			if (done) return false;
			this.value = value;
			this.failure = failure;
			done = true;
			run = dependents;
			dependents = null;
			monitor.notifyAll();
		}
		// newest first, the order the jdk runs them in
		if (run != null) for (int i = run.size() - 1; i >= 0; i--) run.get(i).run();
		return true;
	}

	private void finishWrapped(Throwable x) {
		finish(null, x instanceof CompletionException ? x : new CompletionException(x));
	}

	private void relay(CompletableFuture<?> from) {
		if (from.failure != null) finishWrapped(from.failure);
		else finish(from.value, null);
	}

	private void onDone(Runnable callback) {
		synchronized (monitor) {
			if (!done) {
				if (dependents == null) dependents = new ArrayList<>();
				dependents.add(callback);
				return;
			}
		}
		callback.run();
	}
}
