package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * The per-invocation context Cloudflare passes to every handler.
 *
 * @since 1.0.0
 */
public interface ExecutionCtx extends JSObject {
	/**
	 * Keeps the invocation alive until the given work finishes. Work left running when a handler
	 * returns is cancelled unless it is registered here.
	 *
	 * @param work the work to wait for
	 */
	void waitUntil(JSObject work);

	/** Marks the invocation so an uncaught exception does not retry it. */
	void passThroughOnException();

	/**
	 * The Worker's own properties, when the invocation carries them.
	 *
	 * @return the properties, or {@code null}
	 */
	@JSProperty
	JSObject getProps();
}
