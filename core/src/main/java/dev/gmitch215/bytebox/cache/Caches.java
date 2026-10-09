package dev.gmitch215.bytebox.cache;

import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;

/**
 * The Workers cache storage, the {@code caches} global. Needs no binding.
 *
 * {@snippet lang = "java":
 * Cache shared = Caches.defaultCache();
 * Cache mine = Caches.open("thumbnails");
 *}
 *
 * @since 1.0.3
 */
public final class Caches {

	private Caches() {}

	/** {@return the cache every Worker on the zone shares, {@code caches.default}} */
	@JSBody(script = "return caches.default;")
	public static native Cache defaultCache();

	/**
	 * Opens a named cache, which is separate from the default one and from the other names.
	 *
	 * @param name the cache name
	 * @return the cache
	 */
	public static Cache open(String name) {
		return Async.await(openNamed(name));
	}

	@JSBody(params = "name", script = "return caches.open(name);")
	private static native JSPromise<Cache> openNamed(String name);
}
