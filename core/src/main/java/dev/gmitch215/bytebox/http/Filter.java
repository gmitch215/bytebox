package dev.gmitch215.bytebox.http;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Something that sees every request before the route does.
 *
 * <p>Authentication, rate limiting, a header on every response. A filter either calls what comes next
 * or answers instead, which is what makes refusing a request possible.
 *
 * {@snippet lang = "java":
 * Router.create()
 * 	.use((request, path, env, ctx, next) -> {
 * 		if (!"secret".equals(request.header("authorization"))) {
 * 			return Bytebox.response("no", 401);
 * 		}
 * 		return next.get();
 * 	})
 * 	.get("/private", Api::secret);
 *}
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface Filter {
	/**
	 * Sees the request.
	 *
	 * @param request the request
	 * @param path what the matched pattern captured
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @param next what comes after this filter, called to continue and skipped to refuse
	 * @return the response
	 */
	Response filter(
		Request request,
		Map<String, String> path,
		Env env,
		ExecutionCtx ctx,
		Supplier<Response> next
	);
}
