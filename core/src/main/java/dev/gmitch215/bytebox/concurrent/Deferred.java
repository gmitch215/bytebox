package dev.gmitch215.bytebox.concurrent;

import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSError;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

/**
 * A JavaScript promise that Java settles.
 *
 * <p>This is what lets a handler suspend. A promise's executor runs synchronously and with a
 * JavaScript frame on the stack, so a suspending call inside one fails; capturing the two settle
 * functions instead moves the work outside that frame, onto a fiber that is free to suspend.
 *
 * <p>Every trigger's exported entry point is built this way, which is why the host sees a promise
 * returned immediately and resolved only once the handler has genuinely finished.
 *
 * {@snippet lang = "java":
 * Deferred<Response> deferred = Deferred.create();
 * new Thread(() -> {
 * 	try {
 * 		deferred.resolve(handler.fetch(request, env, ctx));
 * 	} catch (Throwable failure) {
 * 		deferred.reject(failure);
 * 	}
 * }).start();
 * return deferred.promise();
 *}
 *
 * @param <T> what the promise resolves to
 * @since 1.0.0
 */
public final class Deferred<T extends JSObject> {

	private final JSPromise<T> promise;
	private JSConsumer<T> settle;
	private JSConsumer<Object> fail;

	private Deferred() {
		// capturing is not suspending, so this executor is safe to run inline
		promise = new JSPromise<>((resolve, reject) -> {
			settle = resolve;
			fail = reject;
		});
	}

	/**
	 * @param <T> what the promise resolves to
	 * @return a promise nothing has settled yet
	 */
	public static <T extends JSObject> Deferred<T> create() {
		return new Deferred<>();
	}

	/** {@return the promise, to hand back to JavaScript} */
	public JSPromise<T> promise() {
		return promise;
	}

	/**
	 * Resolves the promise.
	 *
	 * @param value the value to resolve with, which may be {@code null}
	 */
	public void resolve(T value) {
		settle.accept(value);
	}

	/**
	 * Rejects the promise with a Java throwable, which crosses back as a JavaScript error.
	 *
	 * @param failure the failure
	 */
	public void reject(Throwable failure) {
		fail.accept(Async.toJs(failure));
	}

	/**
	 * Rejects the promise with a JavaScript value.
	 *
	 * @param error the error
	 */
	public void reject(JSError error) {
		fail.accept(error);
	}
}
