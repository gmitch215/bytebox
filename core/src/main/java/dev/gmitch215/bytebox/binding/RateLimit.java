package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

/**
 * A rate limiting binding. Declared with {@code rateLimit()}, named {@code RATELIMIT} by default.
 *
 * <p>The limit and period come from the Wrangler configuration rather than from the call, so this
 * surface is one question: has this key had its allowance.
 *
 * <p>Counting is per colo rather than global, so the effective limit across the network is higher
 * than the configured one.
 *
 * {@snippet lang = "java":
 * if (!env.rateLimit().allow(request.getHeaders().get("cf-connecting-ip"))) {
 * 	return Bytebox.response("slow down", 429);
 * }
 *}
 *
 * @since 1.0.0
 */
public interface RateLimit extends JSObject {
	/**
	 * Consumes one unit of a key's allowance.
	 *
	 * @param key what to count against, such as an IP or a user id
	 * @return whether the request is within the limit
	 */
	default boolean allow(String key) {
		return Async.await(limit(keyOf(key))).isSuccess();
	}

	@JSMethod("limit")
	JSPromise<Outcome> limit(TSObject options);

	private static TSObject keyOf(String key) {
		TSObject options = TSObject.object();
		options.set("key", TSObject.of(key));
		return options;
	}

	/**
	 * Whether a key was within its allowance.
	 *
	 * @since 1.0.0
	 */
	interface Outcome extends JSObject {
		/** {@return whether the request is allowed} */
		@JSProperty
		boolean isSuccess();
	}
}
