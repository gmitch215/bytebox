package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;

/**
 * The bindings declared in the Wrangler configuration, addressed by their binding names.
 *
 * <p>Names default to a type-derived value and gain a numeric suffix when a type is declared more
 * than once: the first KV namespace is {@code KV} and the second {@code KV_2}, the first D1
 * database is {@code DB}, the first R2 bucket is {@code BLOB}, and a Durable Object binding is
 * {@code DO_} followed by the class name.
 *
 * <p>Variables and secrets are readable during module evaluation. Anything that performs I/O is
 * not, because Cloudflare Workers forbid I/O outside a request context.
 *
 * @since 1.0.0
 */
public interface Env extends JSObject {
	/**
	 * Reads one binding by name.
	 *
	 * @param name the binding name
	 * @return the binding, or {@code null} when it is not declared
	 */
	@org.teavm.jso.JSIndexer
	JSObject get(String name);
}
