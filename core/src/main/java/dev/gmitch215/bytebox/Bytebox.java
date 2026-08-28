package dev.gmitch215.bytebox;

import org.teavm.jso.JSBody;

/**
 * Factories for the platform types a handler returns.
 *
 * @since 1.0.0
 */
public final class Bytebox {

	private Bytebox() {}

	/**
	 * Builds a 200 response carrying the given text.
	 *
	 * @param body the body
	 * @return the response
	 */
	@JSBody(params = "body", script = "return new Response(body);")
	public static native Response response(String body);

	/**
	 * Builds a response with an explicit status.
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
	 * Builds a response carrying already-serialised JSON.
	 *
	 * @param json the JSON text
	 * @return the response
	 */
	@JSBody(
		params = "json",
		script = "return new Response(json, { headers: { 'content-type': 'application/json' } });"
	)
	public static native Response json(String json);
}
