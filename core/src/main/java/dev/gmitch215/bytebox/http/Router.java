package dev.gmitch215.bytebox.http;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a request to a handler by method and path.
 *
 * <p>Matching compares path segments rather than running a regular expression. For a path that is
 * already a list of segments, comparing them is both the direct implementation and the faster one, and
 * it needs no pattern engine at all.
 *
 * {@snippet lang = "java":
 * public class Api implements Worker {
 * 	private static final Router ROUTES = Router.create()
 * 		.get("/health", (request, path, env, ctx) -> Bytebox.response("ok"))
 * 		.get("/users/:id", (request, path, env, ctx) -> Bytebox.response(path.get("id")))
 * 		.post("/users", Api::create)
 * 		.notFound((request, path, env, ctx) -> Bytebox.response("no such route", 404));
 *
 * 	@Override
 * 	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
 * 		return ROUTES.route(request, env, ctx);
 * 	}
 * }
 *}
 *
 * <p>A pattern is a path with two kinds of placeholder. {@code :name} matches one segment and captures
 * it. A trailing {@code *} matches the rest of the path and captures it as {@code *}. Everything else
 * is compared literally.
 *
 * <p>Routes are tried in the order they were added, so a literal declared before a parameter wins over
 * it. That is the order the code reads in, which is the order a reader expects.
 *
 * @since 1.0.0
 */
public final class Router {

	private final List<Entry> routes = new ArrayList<>();
	private final List<Filter> filters = new ArrayList<>();
	private Route missing = (request, path, env, ctx) -> Bytebox.response("not found", 404);

	private Router() {}

	/** {@return a new router with no routes} */
	public static Router create() {
		return new Router();
	}

	// #region declaring

	/**
	 * Adds a route.
	 *
	 * @param method the HTTP method, uppercase, or {@code *} for any
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router on(String method, String pattern, Route route) {
		routes.add(new Entry(method, split(pattern), route));
		return this;
	}

	/**
	 * Adds a {@code GET} route.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router get(String pattern, Route route) {
		return on("GET", pattern, route);
	}

	/**
	 * Adds a {@code POST} route.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router post(String pattern, Route route) {
		return on("POST", pattern, route);
	}

	/**
	 * Adds a {@code PUT} route.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router put(String pattern, Route route) {
		return on("PUT", pattern, route);
	}

	/**
	 * Adds a {@code PATCH} route.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router patch(String pattern, Route route) {
		return on("PATCH", pattern, route);
	}

	/**
	 * Adds a {@code DELETE} route.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router delete(String pattern, Route route) {
		return on("DELETE", pattern, route);
	}

	/**
	 * Adds a route for every method.
	 *
	 * @param pattern the path pattern
	 * @param route what answers it
	 * @return this router
	 */
	public Router any(String pattern, Route route) {
		return on("*", pattern, route);
	}

	/**
	 * Sets what answers a request no route matched.
	 *
	 * @param route what answers it
	 * @return this router
	 */
	public Router notFound(Route route) {
		missing = route;
		return this;
	}

	/**
	 * Adds a filter, which sees every request before the route does.
	 *
	 * <p>Run in the order added, outermost first. A filter that answers rather than continuing is what
	 * makes authentication and rate limiting work.
	 *
	 * @param filter the filter
	 * @return this router
	 */
	public Router use(Filter filter) {
		filters.add(filter);
		return this;
	}

	// #endregion

	/**
	 * Routes a request.
	 *
	 * @param request the request
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return whatever answered it
	 */
	public Response route(Request request, Env env, ExecutionCtx ctx) {
		String method = request.getMethod();
		List<String> segments = split(request.path());

		Route matched = missing;
		Map<String, String> captured = Map.of();
		for (Entry entry : routes) {
			if (!entry.method.equals("*") && !entry.method.equals(method)) continue;
			Map<String, String> path = match(entry.pattern, segments);
			if (path == null) continue;
			matched = entry.route;
			captured = path;
			break;
		}

		return chain(0, matched, captured).handle(request, captured, env, ctx);
	}

	/** Wraps the matched route in the filters, outermost first. */
	private Route chain(int at, Route route, Map<String, String> path) {
		if (at == filters.size()) return route;
		Filter filter = filters.get(at);
		Route rest = chain(at + 1, route, path);
		return (request, captured, env, ctx) ->
			filter.filter(request, captured, env, ctx, () ->
				rest.handle(request, captured, env, ctx)
			);
	}

	/**
	 * The captures a pattern takes from a path, or {@code null} when it does not match.
	 *
	 * <p>An empty map is a match with nothing captured, which is why absence is {@code null} rather
	 * than empty.
	 */
	private static Map<String, String> match(List<String> pattern, List<String> path) {
		Map<String, String> captured = new LinkedHashMap<>();
		for (int i = 0; i < pattern.size(); i++) {
			String expected = pattern.get(i);
			if (expected.equals("*")) {
				captured.put(
					"*",
					String.join("/", path.subList(Math.min(i, path.size()), path.size()))
				);
				return captured;
			}
			if (i >= path.size()) return null;
			String actual = path.get(i);
			if (expected.startsWith(":")) {
				captured.put(expected.substring(1), actual);
				continue;
			}
			if (!expected.equals(actual)) return null;
		}
		return pattern.size() == path.size() ? captured : null;
	}

	/** A path as its segments, with the empty ones a leading or trailing slash produces dropped. */
	private static List<String> split(String path) {
		List<String> segments = new ArrayList<>();
		int at = 0;
		while (at < path.length()) {
			int slash = path.indexOf('/', at);
			int end = slash < 0 ? path.length() : slash;
			if (end > at) segments.add(path.substring(at, end));
			at = end + 1;
		}
		return segments;
	}

	private record Entry(String method, List<String> pattern, Route route) {}
}
