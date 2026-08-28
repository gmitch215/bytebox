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
 * An incoming HTTP request.
 *
 * <p>A body can be read once. Reading it twice raises, because it is a stream rather than a buffer.
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

	/** {@return whether the body has already been read} */
	@JSProperty
	boolean isBodyUsed();

	/** {@return Cloudflare's own request properties, such as {@code country} and {@code colo}} */
	@JSProperty("cf")
	TSObject getCf();

	/**
	 * Reads the whole body as text.
	 *
	 * @return the decoded body, empty when there is none
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

	/** {@return the request path, without the query string} */
	default String path() {
		return Queries.path(getUrl());
	}

	/** {@return the host, including the port when the URL carried one} */
	default String host() {
		return Queries.host(getUrl());
	}

	/** {@return the scheme and host, with no path} */
	default String origin() {
		return Queries.origin(getUrl());
	}

	/**
	 * Reads one query parameter.
	 *
	 * @param name the parameter name
	 * @return the first value, or {@code null} when absent
	 */
	default String query(String name) {
		JSString value = Queries.get(getUrl(), name);
		return value == null ? null : value.stringValue();
	}

	/**
	 * Reads one query parameter, falling back when absent.
	 *
	 * @param name the parameter name
	 * @param fallback the value to use when absent
	 * @return the value, or the fallback
	 */
	default String query(String name, String fallback) {
		String value = query(name);
		return value == null ? fallback : value;
	}

	/** {@return the two-letter country Cloudflare resolved the client to, or {@code null}} */
	default String country() {
		return getHeaders().get("cf-ipcountry");
	}

	/** {@return the client's IP address as Cloudflare saw it, or {@code null}} */
	default String ip() {
		return getHeaders().get("cf-connecting-ip");
	}

	/**
	 * {@return the Cloudflare data centre that handled the request, or {@code null}}
	 *
	 * <p>Read from {@code request.cf} where it is present, and from the {@code CF-Ray} header
	 * otherwise. That header is {@code <rayid>-<colo>}, so only its suffix is the answer.
	 */
	default String colo() {
		TSObject cf = getCf();
		if (cf != null && !cf.isNull()) {
			TSObject colo = cf.get("colo");
			if (colo != null && !colo.isNull()) return colo.asString();
		}
		String ray = getHeaders().get("cf-ray");
		if (ray == null) return null;
		int split = ray.lastIndexOf('-');
		return split < 0 ? null : ray.substring(split + 1);
	}

	/** {@return the ray id Cloudflare traces this request by, or {@code null}} */
	default String rayId() {
		String ray = getHeaders().get("cf-ray");
		if (ray == null) return null;
		int split = ray.lastIndexOf('-');
		return split < 0 ? ray : ray.substring(0, split);
	}

	/** {@return the user agent string, or {@code null}} */
	default String userAgent() {
		return getHeaders().get("user-agent");
	}

	/** {@return whether the request method is GET} */
	default boolean isGet() {
		return "GET".equals(getMethod());
	}

	/** {@return whether the request method is POST} */
	default boolean isPost() {
		return "POST".equals(getMethod());
	}

	/** {@return whether the request method is PUT} */
	default boolean isPut() {
		return "PUT".equals(getMethod());
	}

	/** {@return whether the request method is PATCH} */
	default boolean isPatch() {
		return "PATCH".equals(getMethod());
	}

	/** {@return whether the request method is DELETE} */
	default boolean isDelete() {
		return "DELETE".equals(getMethod());
	}

	/**
	 * Reads the whole body and converts it to a Java type.
	 *
	 * <p>Needs a codec, which the Gradle plugin generates for any type annotated
	 * {@link dev.gmitch215.bytebox.json.JSONType}. A type without one raises rather than returning
	 * something half-converted; {@link #json(Function)} is the way to read a type that has none.
	 *
	 * {@snippet lang = "java":
	 * @JSONType
	 * record Order(String sku, int quantity) {}
	 *
	 * Order order = request.json(Order.class);
	 *}
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
	 * <p>Needs no codec and no annotation, which makes it the direct route for a type the generator
	 * does not know about, and for a shape that is not a record at all.
	 *
	 * {@snippet lang = "java":
	 * record Order(String sku, int quantity) {}
	 *
	 * Order order = request.json(body ->
	 * 	new Order(body.get("sku").asString(), body.get("quantity").asInt())
	 * );
	 *}
	 *
	 * @param mapper reads the parsed body
	 * @param <T> the type
	 * @return what the mapper returned
	 */
	default <T> T json(Function<TSObject, T> mapper) {
		return mapper.apply(json());
	}

	@JSMethod("text")
	JSPromise<JSString> readText();

	@JSMethod("json")
	JSPromise<TSObject> readJson();

	@JSMethod("arrayBuffer")
	JSPromise<ArrayBuffer> readBytes();
}
