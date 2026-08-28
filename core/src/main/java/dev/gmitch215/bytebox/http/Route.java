package dev.gmitch215.bytebox.http;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import java.util.Map;

/**
 * What answers one matched request.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface Route {
	/**
	 * Answers the request.
	 *
	 * @param request the request
	 * @param path what the pattern captured, by parameter name without the colon; empty for a
	 *     pattern with no parameters
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return the response
	 */
	Response handle(Request request, Map<String, String> path, Env env, ExecutionCtx ctx);
}
