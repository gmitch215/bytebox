package dev.gmitch215.bytebox.concurrent;

import java.util.List;
import java.util.function.Supplier;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;

/**
 * Waiting on JavaScript from Java.
 *
 * <p>Everything on this platform that touches the network, a binding, or a stream is a JavaScript
 * promise. {@link #await(JSPromise)} turns one into an ordinary blocking call: the compiler rewrites
 * the caller into a continuation, and the host resumes it when the promise settles. That is why
 * every binding on {@link dev.gmitch215.bytebox.Env} reads as a plain method rather than returning a
 * future.
 *
 * <p>Suspension is real but parallelism is not. Cloudflare Workers run one thread and do not provide
 * the Web Worker API, so a Java thread here is a fiber on the host's queue and two of them never run
 * at once.
 *
 * @since 1.0.0
 */
public final class Async {

	private Async() {}

	/**
	 * Waits for a promise and returns its value.
	 *
	 * <p>Suspends the calling fiber. Not callable from a module initializer, where nothing is
	 * draining the queue, nor from a function handed to JavaScript, where a JavaScript frame on the
	 * stack makes suspension impossible.
	 *
	 * @param promise the promise
	 * @param <T> the resolved type
	 * @return the resolved value
	 */
	public static <T extends JSObject> T await(JSPromise<T> promise) {
		return promise.await();
	}

	/**
	 * Waits for a promise whose value is not wanted.
	 *
	 * @param promise the promise
	 */
	public static void awaitVoid(JSPromise<?> promise) {
		@SuppressWarnings("unchecked")
		JSPromise<JSObject> typed = (JSPromise<JSObject>) promise;
		typed.await();
	}

	/**
	 * Waits for every promise, in order.
	 *
	 * <p>The promises were already in flight, so this costs one suspension rather than one per
	 * promise.
	 *
	 * @param promises the promises
	 * @param <T> the resolved type
	 * @return the resolved values, in the order given
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T extends JSObject> List<T> all(JSPromise<T>... promises) {
		JSArrayReader<T> settled = JSPromise.all(JSArray.of(promises)).await();
		List<T> values = new java.util.ArrayList<>(settled.getLength());
		for (int i = 0; i < settled.getLength(); i++) values.add(settled.get(i));
		return values;
	}

	/**
	 * Waits for whichever promise settles first.
	 *
	 * @param promises the promises
	 * @param <T> the resolved type
	 * @return the first value to arrive
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T extends JSObject> T race(JSPromise<T>... promises) {
		return JSPromise.race(JSArray.of(promises)).await();
	}

	/**
	 * Runs work on its own fiber, so the caller does not wait for it.
	 *
	 * <p>Whether the work finishes depends on the host draining its queue. Register the returned
	 * future with {@code ctx.waitUntil} to hold the invocation open for it.
	 *
	 * @param work the work
	 * @param <T> what the work produces
	 * @return a future for the result
	 */
	public static <T extends JSObject> Future<T> supply(Supplier<T> work) {
		Deferred<T> deferred = Deferred.create();
		new Thread(() -> {
			try {
				deferred.resolve(work.get());
			} catch (Throwable failure) {
				deferred.reject(failure);
			}
		}).start();
		return Future.of(deferred.promise());
	}

	/**
	 * Runs work on its own fiber, with no result.
	 *
	 * @param work the work
	 * @return a future that completes when the work does
	 */
	public static Future<JSObject> run(Runnable work) {
		return supply(() -> {
			work.run();
			return null;
		});
	}

	/**
	 * Sleeps without occupying the fiber, using the host's timer rather than a spin.
	 *
	 * <p>Workers pin the clock between I/O, so a timer is also the only thing that makes it
	 * advance.
	 *
	 * @param millis how long to wait
	 */
	public static void sleep(int millis) {
		awaitVoid(timer(millis));
	}

	/**
	 * Turns a Java throwable into something JavaScript can reject with.
	 *
	 * <p>A throwable that never came from JavaScript has no wrapper to hand back, and the runtime
	 * answers {@code undefined} for one rather than null. Rejecting a promise with {@code undefined}
	 * loses the failure: a waiter resuming from it has nothing to rethrow, so it does not resume at
	 * all. An {@code Error} carrying the Java message is what makes the rejection observable.
	 *
	 * @param failure the failure
	 * @return the JavaScript value
	 */
	public static JSObject toJs(Throwable failure) {
		JSObject wrapped = JSExceptions.getJSException(failure);
		if (wrapped != null && !JSObjects.isUndefined(wrapped)) return wrapped;
		return error(failure.getClass().getName() + ": " + failure.getMessage());
	}

	@JSBody(params = "millis", script = "return new Promise((r) => setTimeout(r, millis));")
	private static native JSPromise<JSObject> timer(int millis);

	@JSBody(params = "message", script = "return new Error(message);")
	private static native JSObject error(String message);
}
