package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * An incoming HTTP request.
 *
 * @since 1.0.0
 */
public interface Request extends JSObject {
	/** {@return the full request URL} */
	@JSProperty
	String getUrl();

	/** {@return the HTTP method} */
	@JSProperty
	String getMethod();

	/** {@return the request headers} */
	@JSProperty
	Headers getHeaders();

	/**
	 * Reads the whole body as text. Suspends until the body has arrived.
	 *
	 * @return the decoded body
	 */
	String text();
}
