package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.js.JSArrays;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSPromise;

/**
 * Calls a method by name across a service or Durable Object boundary.
 *
 * <p>Cloudflare's RPC surfaces every public method of a {@code WorkerEntrypoint},
 * {@code DurableObject} or {@code RpcTarget} on the caller's stub, and none of those names are known
 * at compile time from this side. So the call is by name, and the result is a {@code TSObject}.
 */
final class Rpc {

	private Rpc() {}

	static JSPromise<TSObject> invoke(JSObject target, String method, TSObject[] args) {
		return apply(target, method, JSArrays.of(args));
	}

	@JSBody(
		params = { "target", "method", "args" },
		script = "if (typeof target[method] !== 'function')" +
			" throw new Error('the target exposes no method named ' + method);" +
			" return Promise.resolve(target[method].apply(target, args));"
	)
	private static native JSPromise<TSObject> apply(
		JSObject target,
		String method,
		JSArray<TSObject> args
	);
}
