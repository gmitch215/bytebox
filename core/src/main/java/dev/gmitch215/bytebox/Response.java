package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.json.JSON;
import java.util.function.Function;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;
import org.teavm.jso.typedarrays.ArrayBuffer;

/**
 * An HTTP response, outgoing or received.
 *
 * <p>{@link Bytebox} builds one to return. The reading half matters for a response that came back
 * from a {@code fetch}, a service binding or a Durable Object.
 *
 * @since 1.0.0
 */
public interface Response extends JSObject {
	/** {@return the status code} */
	@JSProperty
	int getStatus();

	/** {@return the status text} */
	@JSProperty
	String getStatusText();

	/** {@return whether the status is in the 200s} */
	@JSProperty
	boolean isOk();

	/** {@return the response headers} */
	@JSProperty
	Headers getHeaders();

	/** {@return the URL the body came from, which differs from the one asked for after a redirect} */
	@JSProperty
	String getUrl();

	/** {@return whether the body has already been read} */
	@JSProperty
	boolean isBodyUsed();

	/**
	 * Reads the whole body as text.
	 *
	 * @return the decoded body
	 */
	default String text() {
		return Async.await(readText()).stringValue();
	}

	/**
	 * Reads the whole body and parses it as JSON.
	 *
	 * @return the parsed body
	 */
	default TSObject json() {
		return Async.await(readJson());
	}

	/**
	 * Reads the whole body as bytes.
	 *
	 * @return the bytes
	 */
	default ArrayBuffer bytes() {
		return Async.await(readBytes());
	}

	/**
	 * Reads the whole body and converts it to a Java type.
	 *
	 * <p>Needs a codec, which the Gradle plugin generates for any type annotated
	 * {@link dev.gmitch215.bytebox.json.JSONType}.
	 *
	 * @param type the type to convert to
	 * @param <T> the type
	 * @return the converted body
	 */
	default <T> T json(Class<T> type) {
		return JSON.decode(json(), type);
	}

	/**
	 * Reads the whole body and converts it with a function of your own.
	 *
	 * @param mapper reads the parsed body
	 * @param <T> the type
	 * @return what the mapper returned
	 */
	default <T> T json(Function<TSObject, T> mapper) {
		return mapper.apply(json());
	}

	/**
	 * Throws unless the status is in the 200s, naming the status and the body.
	 *
	 * @return this response
	 */
	default Response assertOk() {
		if (isOk()) return this;
		throw new IllegalStateException(
			"the response failed with " + getStatus() + " " + getStatusText() + ": " + text()
		);
	}

	@JSMethod("text")
	JSPromise<JSString> readText();

	@JSMethod("json")
	JSPromise<TSObject> readJson();

	@JSMethod("arrayBuffer")
	JSPromise<ArrayBuffer> readBytes();
}
