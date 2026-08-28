package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.concurrent.Future;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

/**
 * The per-invocation context Cloudflare passes to every handler.
 *
 * @since 1.0.0
 */
public interface ExecutionCtx extends JSObject {
	/**
	 * Keeps the invocation alive until the given work finishes.
	 *
	 * <p>Work still running when a handler returns is cancelled. That includes a fiber nobody joined
	 * and a fetch nobody awaited, which is what makes this the difference between logging that
	 * happens and logging that silently does not.
	 *
	 * @param work the work to wait for
	 */
	void waitUntil(JSObject work);

	/**
	 * Keeps the invocation alive until a future completes.
	 *
	 * @param work the work to wait for
	 */
	default void waitUntil(Future<?> work) {
		waitUntil((JSObject) work.promise());
	}

	/**
	 * Keeps the invocation alive until a promise settles.
	 *
	 * @param work the work to wait for
	 */
	default void waitUntil(JSPromise<?> work) {
		waitUntil((JSObject) work);
	}

	/**
	 * Marks the invocation so an uncaught exception does not retry it.
	 *
	 * <p>Only meaningful where the platform retries: a queue consumer and a Durable Object alarm. A
	 * {@code fetch} is never retried, so calling this there does nothing.
	 */
	void passThroughOnException();

	/**
	 * The Worker's own properties, when the invocation carries them.
	 *
	 * <p>Set by a dispatch namespace when one Worker invokes another, and absent otherwise.
	 *
	 * @return the properties, or {@code null}
	 */
	@JSProperty
	TSObject getProps();
}
