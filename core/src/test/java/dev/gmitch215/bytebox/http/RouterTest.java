package dev.gmitch215.bytebox.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Matching, which is ordinary Java and so runs here rather than on the runtime.
 *
 * <p>A {@link Response} cannot be built without the platform, so every route here answers with a
 * recorder instead and the assertions read what it recorded.
 */
@DisplayName("the router")
class RouterTest {

	/** What a route was reached with, since a real Response needs the runtime to construct. */
	private record Reached(String label, Map<String, String> path) {}

	private final List<Reached> reached = new ArrayList<>();

	private Route record(String label) {
		return (request, path, env, ctx) -> {
			reached.add(new Reached(label, path));
			return null;
		};
	}

	private Response route(Router router, String method, String path) {
		return router.route(new StubRequest(method, path), null, null);
	}

	private Reached only() {
		assertEquals(1, reached.size(), "expected exactly one route to be reached: " + reached);
		return reached.get(0);
	}

	@Test
	@DisplayName("matches a literal path")
	void matchesALiteral() {
		Router router = Router.create().get("/health", record("health"));

		route(router, "GET", "/health");

		assertEquals("health", only().label());
		assertTrue(only().path().isEmpty());
	}

	@Test
	@DisplayName("captures a parameter")
	void capturesAParameter() {
		Router router = Router.create().get("/users/:id", record("user"));

		route(router, "GET", "/users/42");

		assertEquals("42", only().path().get("id"));
	}

	@Test
	@DisplayName("captures several parameters")
	void capturesSeveral() {
		Router router = Router.create().get("/users/:user/posts/:post", record("post"));

		route(router, "GET", "/users/7/posts/9");

		assertEquals("7", only().path().get("user"));
		assertEquals("9", only().path().get("post"));
	}

	@Test
	@DisplayName("captures the rest of the path with a wildcard")
	void capturesTheRest() {
		Router router = Router.create().get("/files/*", record("files"));

		route(router, "GET", "/files/a/b/c.txt");

		assertEquals("a/b/c.txt", only().path().get("*"));
	}

	@Test
	@DisplayName("matches a wildcard against nothing at all")
	void matchesAnEmptyWildcard() {
		Router router = Router.create().get("/files/*", record("files"));

		route(router, "GET", "/files");

		assertEquals("", only().path().get("*"));
	}

	@Test
	@DisplayName("takes the first route that matches, so a literal declared first wins")
	void takesTheFirstMatch() {
		Router router = Router.create()
			.get("/users/me", record("me"))
			.get("/users/:id", record("byId"));

		route(router, "GET", "/users/me");

		assertEquals("me", only().label());
	}

	@Test
	@DisplayName("separates methods")
	void separatesMethods() {
		Router router = Router.create()
			.get("/users", record("list"))
			.post("/users", record("create"));

		route(router, "POST", "/users");

		assertEquals("create", only().label());
	}

	@Test
	@DisplayName("takes every method with any")
	void takesAnyMethod() {
		Router router = Router.create().any("/thing", record("thing"));

		route(router, "PATCH", "/thing");
		route(router, "DELETE", "/thing");

		assertEquals(2, reached.size());
	}

	@Test
	@DisplayName("declares put, patch and delete")
	void declaresTheRest() {
		Router router = Router.create()
			.put("/a", record("put"))
			.patch("/b", record("patch"))
			.delete("/c", record("delete"));

		route(router, "PUT", "/a");
		route(router, "PATCH", "/b");
		route(router, "DELETE", "/c");

		assertEquals(
			List.of("put", "patch", "delete"),
			reached.stream().map(Reached::label).toList()
		);
	}

	@Test
	@DisplayName("ignores a leading and a trailing slash")
	void ignoresSlashes() {
		Router router = Router.create().get("/users/:id", record("user"));

		route(router, "GET", "/users/42/");
		route(router, "GET", "users/43");

		assertEquals(2, reached.size());
		assertEquals("42", reached.get(0).path().get("id"));
		assertEquals("43", reached.get(1).path().get("id"));
	}

	@Test
	@DisplayName("refuses a path longer than the pattern")
	void refusesALongerPath() {
		Router router = Router.create()
			.get("/users/:id", record("user"))
			.notFound(record("missing"));

		route(router, "GET", "/users/42/extra");

		assertEquals("missing", only().label());
	}

	@Test
	@DisplayName("refuses a path shorter than the pattern")
	void refusesAShorterPath() {
		Router router = Router.create()
			.get("/users/:id", record("user"))
			.notFound(record("missing"));

		route(router, "GET", "/users");

		assertEquals("missing", only().label());
	}

	@Test
	@DisplayName("refuses a method no route declares")
	void refusesAnUndeclaredMethod() {
		Router router = Router.create().get("/users", record("list")).notFound(record("missing"));

		route(router, "DELETE", "/users");

		assertEquals("missing", only().label());
	}

	@Test
	@DisplayName("matches the root path")
	void matchesTheRoot() {
		Router router = Router.create().get("/", record("root"));

		route(router, "GET", "/");

		assertEquals("root", only().label());
	}

	@Test
	@DisplayName("does not confuse a parameter with a segment that starts the same way")
	void doesNotConfusePrefixes() {
		Router router = Router.create()
			.get("/usersx/:id", record("wrong"))
			.get("/users/:id", record("right"));

		route(router, "GET", "/users/1");

		assertEquals("right", only().label());
	}

	@Test
	@DisplayName("runs filters outermost first, then the route")
	void runsFiltersInOrder() {
		List<String> order = new ArrayList<>();
		Router router = Router.create()
			.use((request, path, env, ctx, next) -> {
				order.add("first in");
				Response response = next.get();
				order.add("first out");
				return response;
			})
			.use((request, path, env, ctx, next) -> {
				order.add("second in");
				return next.get();
			})
			.get("/thing", (request, path, env, ctx) -> {
				order.add("route");
				return null;
			});

		route(router, "GET", "/thing");

		assertEquals(List.of("first in", "second in", "route", "first out"), order);
	}

	@Test
	@DisplayName("lets a filter answer instead of the route")
	void letsAFilterRefuse() {
		Router router = Router.create()
			.use((request, path, env, ctx, next) -> null)
			.get("/thing", record("thing"));

		route(router, "GET", "/thing");

		assertTrue(reached.isEmpty(), "the route should not have been reached");
	}

	@Test
	@DisplayName("gives a filter what the pattern captured")
	void givesAFilterTheCaptures() {
		List<String> seen = new ArrayList<>();
		Router router = Router.create()
			.use((request, path, env, ctx, next) -> {
				seen.add(path.get("id"));
				return next.get();
			})
			.get("/users/:id", record("user"));

		route(router, "GET", "/users/99");

		assertEquals(List.of("99"), seen);
	}

	@Test
	@DisplayName("runs filters for a request no route matched")
	void runsFiltersForAMiss() {
		List<String> seen = new ArrayList<>();
		Router router = Router.create()
			.use((request, path, env, ctx, next) -> {
				seen.add("filtered");
				return next.get();
			})
			.notFound(record("missing"));

		route(router, "GET", "/nowhere");

		assertEquals(List.of("filtered"), seen);
		assertEquals("missing", only().label());
	}

	@Test
	@DisplayName("answers 404 by default, without a handler being declared")
	void answersNotFoundByDefault() {
		// the default builds a real Response, which needs the runtime, so what is asserted here is
		// that it is reached rather than what it answers; the workerd lane covers the answer
		Router router = Router.create().get("/thing", record("thing"));

		assertNull(routeUnchecked(router, "GET", "/nowhere"));
		assertTrue(reached.isEmpty());
	}

	/** The default handler calls into the platform, so a miss is expected to fail here. */
	private Response routeUnchecked(Router router, String method, String path) {
		try {
			return route(router, method, path);
		} catch (RuntimeException | LinkageError absent) {
			return null;
		}
	}

	/** Only the two reads the router makes. */
	private record StubRequest(String method, String path) implements Request {
		@Override
		public String getMethod() {
			return method;
		}

		@Override
		public String getUrl() {
			return "https://example.com" + path;
		}

		@Override
		public String path() {
			return path;
		}

		@Override
		public dev.gmitch215.bytebox.Headers getHeaders() {
			throw new UnsupportedOperationException();
		}

		@Override
		public org.teavm.jso.core.JSPromise<org.teavm.jso.core.JSString> readText() {
			throw new UnsupportedOperationException();
		}

		@Override
		public org.teavm.jso.core.JSPromise<dev.gmitch215.bytebox.js.TSObject> readJson() {
			throw new UnsupportedOperationException();
		}

		@Override
		public org.teavm.jso.core.JSPromise<org.teavm.jso.typedarrays.ArrayBuffer> readBytes() {
			throw new UnsupportedOperationException();
		}

		@Override
		public dev.gmitch215.bytebox.js.TSObject getCf() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isBodyUsed() {
			throw new UnsupportedOperationException();
		}
	}
}
