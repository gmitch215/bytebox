package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * Anything reachable by sending it a request.
 *
 * <p>Four bindings are this shape and they differ only in what sits on the other end: a service
 * binding reaches another Worker with no network hop and no charge for the subrequest, an assets
 * binding reaches static files, an mTLS binding reaches an origin using a client certificate, and a
 * Browser Rendering binding reaches a headless browser.
 *
 * @since 1.0.0
 */
public interface Fetcher extends JSObject {
	/**
	 * Sends a request.
	 *
	 * @param request the request
	 * @return the response
	 */
	default Response fetch(Request request) {
		return Async.await(send(request));
	}

	/**
	 * Sends a GET to a URL.
	 *
	 * @param url the URL
	 * @return the response
	 */
	default Response fetch(String url) {
		return Async.await(send(url));
	}

	/**
	 * Sends a request built from a URL and options.
	 *
	 * @param url the URL
	 * @param options {@code method}, {@code headers}, {@code body}
	 * @return the response
	 */
	default Response fetch(String url, TSObject options) {
		return Async.await(send(url, options));
	}

	/**
	 * Calls a method the other side exposes over RPC.
	 *
	 * <p>Only meaningful for a service binding whose target extends Cloudflare's
	 * {@code WorkerEntrypoint}.
	 *
	 * @param method the method name
	 * @param args the arguments
	 * @return what the method returned
	 */
	default TSObject rpc(String method, TSObject... args) {
		return Async.await(Rpc.invoke(this, method, args));
	}

	@JSMethod("fetch")
	JSPromise<Response> send(Request request);

	@JSMethod("fetch")
	JSPromise<Response> send(String url);

	@JSMethod("fetch")
	JSPromise<Response> send(String url, TSObject options);
}
