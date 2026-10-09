package dev.gmitch215.bytebox.cache;

import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Async;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSBoolean;
import org.teavm.jso.core.JSObjects;
import org.teavm.jso.core.JSPromise;

/**
 * A Workers cache, from {@link Caches#defaultCache()} or {@link Caches#open(String)}.
 *
 * <p>Entries are keyed by request URL and only a {@code GET} request can be a key. The cache is
 * local to the data center the Worker ran in, so an entry put in one location is not there in
 * another. A response is stored when it is cacheable by its {@code Cache-Control} header; the
 * platform decides, and {@code put} returns either way.
 *
 * <p>The blocking methods wait for the platform. The {@code *Async} forms return the promise, for
 * {@code ctx.waitUntil} when a response should not wait on the write.
 *
 * {@snippet lang = "java":
 * Cache cache = Caches.defaultCache();
 * Response hit = cache.match(request);
 * if (hit == null) cache.put(request, response.copy());
 *}
 *
 * @since 1.0.3
 */
public interface Cache extends JSObject {
	/**
	 * Looks up the entry for a request.
	 *
	 * @param request the request, whose URL is the key
	 * @return the stored response, or {@code null} on a miss
	 */
	default Response match(Request request) {
		return hit(Async.await(matchAsync(request)));
	}

	/**
	 * Looks up the entry for a URL.
	 *
	 * @param url the URL
	 * @return the stored response, or {@code null} on a miss
	 */
	default Response match(String url) {
		return hit(Async.await(matchAsync(url)));
	}

	// a miss resolves to undefined, which TeaVM does not turn into null
	private static Response hit(Response found) {
		return found == null || JSObjects.isUndefined(found) ? null : found;
	}

	/**
	 * Stores a response. Consumes its body; pass {@link Response#copy()} when the handler also
	 * returns it.
	 *
	 * @param request the request, whose URL is the key; the method must be {@code GET}
	 * @param response the response to store
	 */
	default void put(Request request, Response response) {
		Async.awaitVoid(putAsync(request, response));
	}

	/**
	 * Stores a response under a URL. Consumes its body.
	 *
	 * @param url the URL
	 * @param response the response to store
	 */
	default void put(String url, Response response) {
		Async.awaitVoid(putAsync(url, response));
	}

	/**
	 * Removes an entry.
	 *
	 * @param request the request, whose URL is the key
	 * @return whether an entry was there to remove
	 */
	default boolean delete(Request request) {
		return Async.await(deleteAsync(request)).booleanValue();
	}

	/**
	 * Removes an entry.
	 *
	 * @param url the URL
	 * @return whether an entry was there to remove
	 */
	default boolean delete(String url) {
		return Async.await(deleteAsync(url)).booleanValue();
	}

	/**
	 * Looks up the entry for a request without waiting.
	 *
	 * @param request the request, whose URL is the key
	 * @return a promise of the response, or of {@code undefined} on a miss
	 */
	@JSMethod("match")
	JSPromise<Response> matchAsync(Request request);

	/**
	 * Looks up the entry for a URL without waiting.
	 *
	 * @param url the URL
	 * @return a promise of the response, or of {@code undefined} on a miss
	 */
	@JSMethod("match")
	JSPromise<Response> matchAsync(String url);

	/**
	 * Stores a response without waiting, for {@code ctx.waitUntil}.
	 *
	 * @param request the request, whose URL is the key
	 * @param response the response to store
	 * @return a promise that settles when the platform is done
	 */
	@JSMethod("put")
	JSPromise<JSObject> putAsync(Request request, Response response);

	/**
	 * Stores a response under a URL without waiting, for {@code ctx.waitUntil}.
	 *
	 * @param url the URL
	 * @param response the response to store
	 * @return a promise that settles when the platform is done
	 */
	@JSMethod("put")
	JSPromise<JSObject> putAsync(String url, Response response);

	/**
	 * Removes an entry without waiting.
	 *
	 * @param request the request, whose URL is the key
	 * @return a promise of whether an entry was removed
	 */
	@JSMethod("delete")
	JSPromise<JSBoolean> deleteAsync(Request request);

	/**
	 * Removes an entry without waiting.
	 *
	 * @param url the URL
	 * @return a promise of whether an entry was removed
	 */
	@JSMethod("delete")
	JSPromise<JSBoolean> deleteAsync(String url);
}
