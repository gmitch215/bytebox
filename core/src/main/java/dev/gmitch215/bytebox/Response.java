package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * An outgoing HTTP response.
 *
 * @since 1.0.0
 */
public interface Response extends JSObject {
	/** {@return the status code} */
	@JSProperty
	int getStatus();

	/** {@return the response headers} */
	@JSProperty
	Headers getHeaders();
}
