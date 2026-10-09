package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.cache.Cache;
import dev.gmitch215.bytebox.cache.Caches;
import dev.gmitch215.bytebox.js.TSObject;

/** The routes that drive the Cache API, kept apart from the rest of the fixture. */
final class CacheRoutes {

	private CacheRoutes() {}

	static Response route(Request request) {
		String key = request.query("key");
		TSObject answer = TSObject.object();
		switch (request.path()) {
			case "/cacheround" -> round(key, answer);
			case "/cacherequest" -> byRequest(key, answer);
			case "/cachenames" -> names(key, answer);
			case "/cachecopy" -> copy(key, answer);
			default -> refuse(key, answer);
		}
		return Bytebox.json(answer);
	}

	private static void round(String key, TSObject answer) {
		Cache cache = Caches.defaultCache();
		answer.set("before", text(cache.match(key)));

		TSObject headers = TSObject.object();
		headers.set("cache-control", TSObject.of("max-age=60"));
		headers.set("x-answer", TSObject.of("42"));
		cache.put(key, Bytebox.response("hello from the cache", 200, headers));

		Response hit = cache.match(key);
		answer.set("hit", text(hit));
		answer.set("header", TSObject.of(hit.getHeaders().get("x-answer")));
		answer.set("status", TSObject.of(String.valueOf(hit.getStatus())));
		answer.set("deleted", TSObject.of(String.valueOf(cache.delete(key))));
		answer.set("after", text(cache.match(key)));
		answer.set("deletedAgain", TSObject.of(String.valueOf(cache.delete(key))));
	}

	private static void byRequest(String key, TSObject answer) {
		Cache cache = Caches.defaultCache();
		cache.put(key, cacheable("stored by url"));

		Request lookup = Bytebox.request(key, "GET", null, TSObject.object());
		answer.set("byRequest", text(cache.match(lookup)));
		answer.set("deletedByRequest", TSObject.of(String.valueOf(cache.delete(lookup))));
		answer.set("afterDelete", text(cache.match(lookup)));
	}

	private static void names(String key, TSObject answer) {
		Cache x = Caches.open("x");
		x.put(key, cacheable("in x"));

		answer.set("named", text(x.match(key)));
		answer.set("otherNamed", text(Caches.open("y").match(key)));
		answer.set("default", text(Caches.defaultCache().match(key)));
		answer.set("reopened", text(Caches.open("x").match(key)));
		x.delete(key);
	}

	private static void copy(String key, TSObject answer) {
		Cache cache = Caches.defaultCache();
		Response made = cacheable("body to share");
		cache.put(key, made.copy());

		answer.set("returned", text(made));
		answer.set("cached", text(cache.match(key)));
		cache.delete(key);
	}

	private static void refuse(String key, TSObject answer) {
		Cache cache = Caches.defaultCache();

		Request post = Bytebox.request(key, "POST", "x", TSObject.object());
		answer.set("post", attempt(() -> cache.put(post, cacheable("post"))));
		answer.set("partial", attempt(() -> cache.put(key, Bytebox.response("part", 206))));

		TSObject star = TSObject.object();
		star.set("vary", TSObject.of("*"));
		star.set("cache-control", TSObject.of("max-age=60"));
		answer.set("varyStar", attempt(() -> cache.put(key, Bytebox.response("vary", 200, star))));

		TSObject noStore = TSObject.object();
		noStore.set("cache-control", TSObject.of("no-store"));
		answer.set(
			"noStoreOutcome",
			attempt(() -> cache.put(key + "/no-store", Bytebox.response("n", 200, noStore)))
		);
		answer.set("noStore", text(cache.match(key + "/no-store")));

		TSObject cookie = TSObject.object();
		cookie.set("set-cookie", TSObject.of("a=b"));
		cookie.set("cache-control", TSObject.of("max-age=60"));
		answer.set(
			"setCookieOutcome",
			attempt(() -> cache.put(key + "/cookie", Bytebox.response("c", 200, cookie)))
		);
		answer.set("setCookie", text(cache.match(key + "/cookie")));
	}

	private static Response cacheable(String body) {
		TSObject headers = TSObject.object();
		headers.set("cache-control", TSObject.of("max-age=60"));
		return Bytebox.response(body, 200, headers);
	}

	private static TSObject text(Response response) {
		return TSObject.of(response == null ? "null" : response.text());
	}

	/** The failure the platform raised, or "returned" when it raised none. */
	private static TSObject attempt(Runnable action) {
		try {
			action.run();
			return TSObject.of("returned");
		} catch (Throwable failure) {
			return TSObject.of(failure.getClass().getSimpleName() + ": " + failure.getMessage());
		}
	}
}
