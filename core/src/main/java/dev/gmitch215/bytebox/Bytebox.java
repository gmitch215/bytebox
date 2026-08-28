package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.json.JSON;
import dev.gmitch215.bytebox.socket.WebSocket;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * Builds the platform types a handler returns, and reaches the platform functions that are not
 * bindings.
 *
 * {@snippet lang = "java":
 * public Response fetch(Request request, Env env, ExecutionCtx ctx) {
 * 	if (!request.getMethod().equals("GET")) return Bytebox.status(405);
 * 	return Bytebox.json("{\"ok\":true}");
 * }
 *}
 *
 * @since 1.0.0
 */
public final class Bytebox {

	private Bytebox() {}

	/**
	 * A 200 carrying text.
	 *
	 * @param body the body
	 * @return the response
	 */
	@JSBody(params = "body", script = "return new Response(body);")
	public static native Response response(String body);

	/**
	 * A response with an explicit status.
	 *
	 * @param body the body
	 * @param status the status code
	 * @return the response
	 */
	@JSBody(
		params = { "body", "status" },
		script = "return new Response(body, { status: status });"
	)
	public static native Response response(String body, int status);

	/**
	 * A response with a status and headers.
	 *
	 * @param body the body
	 * @param status the status code
	 * @param headers the headers, as an object of name to value
	 * @return the response
	 */
	@JSBody(
		params = { "body", "status", "headers" },
		script = "return new Response(body, { status: status, headers: headers });"
	)
	public static native Response response(String body, int status, TSObject headers);

	/**
	 * A response carrying bytes.
	 *
	 * @param body the body
	 * @param contentType the media type to serve it as
	 * @return the response
	 */
	@JSBody(
		params = { "body", "contentType" },
		script = "return new Response(body, { headers: { 'content-type': contentType } });"
	)
	public static native Response bytes(ArrayBuffer body, String contentType);

	/**
	 * A response carrying bytes.
	 *
	 * @param body the body
	 * @param contentType the media type to serve it as
	 * @return the response
	 */
	public static Response bytes(byte[] body, String contentType) {
		return bytes(Bytes.toBuffer(body), contentType);
	}

	/**
	 * The 101 that completes a WebSocket upgrade.
	 *
	 * <p>Carries the client end of a pair whose server end the Worker kept. A response with a
	 * {@code webSocket} and any other status is refused by the runtime.
	 *
	 * @param client the client end, from {@link dev.gmitch215.bytebox.socket.WebSocketPair#client}
	 * @return the response
	 */
	@JSBody(
		params = "client",
		script = "return new Response(null, { status: 101, webSocket: client });"
	)
	public static native Response upgrade(WebSocket client);

	/**
	 * A 200 carrying already-serialised JSON.
	 *
	 * @param json the JSON text
	 * @return the response
	 */
	@JSBody(
		params = "json",
		script = "return new Response(json, { headers: { 'content-type': 'application/json' } });"
	)
	public static native Response json(String json);

	/**
	 * A JSON response with an explicit status.
	 *
	 * @param json the JSON text
	 * @param status the status code
	 * @return the response
	 */
	@JSBody(
		params = { "json", "status" },
		script = "return new Response(json, { status: status," +
			" headers: { 'content-type': 'application/json' } });"
	)
	public static native Response json(String json, int status);

	/**
	 * A 200 carrying a Java value as JSON.
	 *
	 * <p>Needs a codec, which the Gradle plugin generates for any type annotated
	 * {@link dev.gmitch215.bytebox.json.JSONType}.
	 *
	 * @param value the value
	 * @param type the value's type
	 * @param <T> the type
	 * @return the response
	 */
	public static <T> Response json(T value, Class<T> type) {
		return json(JSON.stringify(value, type));
	}

	/**
	 * A JSON response built from a JavaScript value, for anything with no codec.
	 *
	 * @param value the value
	 * @return the response
	 */
	public static Response json(TSObject value) {
		return json(value.toJson());
	}

	/**
	 * An empty response with a status, for the cases where the status is the whole answer.
	 *
	 * @param status the status code
	 * @return the response
	 */
	@JSBody(params = "status", script = "return new Response(null, { status: status });")
	public static native Response status(int status);

	/**
	 * A redirect.
	 *
	 * @param location where to send the client
	 * @param status 301, 302, 307 or 308
	 * @return the response
	 */
	@JSBody(
		params = { "location", "status" },
		script = "return Response.redirect(location, status);"
	)
	public static native Response redirect(String location, int status);

	/**
	 * Builds a request, for sending somewhere rather than for answering.
	 *
	 * @param url the URL
	 * @param method the HTTP method
	 * @param body the body, or {@code null}
	 * @param headers the headers, or {@code null}
	 * @return the request
	 */
	@JSBody(
		params = { "url", "method", "body", "headers" },
		script = "const init = { method: method };" +
			" if (body !== null) init.body = body;" +
			" if (headers !== null) init.headers = headers;" +
			" return new Request(url, init);"
	)
	public static native Request request(String url, String method, String body, TSObject headers);

	/**
	 * Sends a request to the internet.
	 *
	 * <p>Counts against the subrequest limit, which is 50 per invocation on the free plan and 10,000
	 * on paid, and against the six simultaneous outgoing connections every plan allows.
	 *
	 * @param url the URL
	 * @return the response
	 */
	public static Response fetch(String url) {
		return Async.await(sendUrl(url));
	}

	/**
	 * Sends a request to the internet.
	 *
	 * @param request the request
	 * @return the response
	 */
	public static Response fetch(Request request) {
		return Async.await(send(request));
	}

	/**
	 * Sends a request with options.
	 *
	 * @param url the URL
	 * @param options {@code method}, {@code headers}, {@code body}, {@code cf}
	 * @return the response
	 */
	public static Response fetch(String url, TSObject options) {
		return Async.await(sendUrl(url, options));
	}

	/** {@return a header collection nothing has been put in yet} */
	@JSBody(script = "return new Headers();")
	public static native Headers headers();

	/** {@return a cryptographically random UUID} */
	@JSBody(script = "return crypto.randomUUID();")
	public static native String uuid();

	/**
	 * Fills a buffer with cryptographically random bytes.
	 *
	 * @param bytes how many
	 * @return the bytes
	 */
	@JSBody(
		params = "bytes",
		script = "return crypto.getRandomValues(new Uint8Array(bytes)).buffer;"
	)
	public static native ArrayBuffer random(int bytes);

	/**
	 * Writes a line to the Worker's log, which {@code wrangler tail} shows.
	 *
	 * @param message the message
	 */
	@JSBody(params = "message", script = "console.log(message);")
	public static native void log(String message);

	@JSBody(params = "url", script = "return fetch(url);")
	private static native JSPromise<Response> sendUrl(String url);

	@JSBody(params = { "url", "options" }, script = "return fetch(url, options);")
	private static native JSPromise<Response> sendUrl(String url, TSObject options);

	@JSBody(params = "request", script = "return fetch(request);")
	private static native JSPromise<Response> send(Request request);
}
