package dev.gmitch215.bytebox.net.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

/**
 * A response, standing in for {@code java.net.http.HttpResponse}.
 *
 * @param <T> what the body was read as
 * @since 1.0.0
 */
public final class HttpResponse<T> {

	private final int status;
	private final HttpHeaders headers;
	private final T body;
	private final HttpRequest request;
	private final URI uri;

	HttpResponse(int status, HttpHeaders headers, T body, HttpRequest request, URI uri) {
		this.status = status;
		this.headers = headers;
		this.body = body;
		this.request = request;
		this.uri = uri;
	}

	/** {@return the status code} */
	public int statusCode() {
		return status;
	}

	/** {@return the response headers} */
	public HttpHeaders headers() {
		return headers;
	}

	/** {@return the body, read the way the handler asked for} */
	public T body() {
		return body;
	}

	/** {@return the request this answers} */
	public HttpRequest request() {
		return request;
	}

	/** {@return the URI the body came from, which differs from the request's after a redirect} */
	public URI uri() {
		return uri;
	}

	/** {@return the response that redirected to this one, which is never present: fetch hides them} */
	public Optional<HttpResponse<T>> previousResponse() {
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "(" + request + ") " + status;
	}

	/**
	 * How to read a body.
	 *
	 * <p>Only the ones {@link BodyHandlers} makes. The platform hands back a whole body rather than a
	 * stream of items, so there is nothing here for a subscriber of your own to be driven by, and
	 * writing one fails to compile - which is where it should fail.
	 *
	 * @param <T> what the body is read as
	 */
	public static final class BodyHandler<T> {

		private final Function<byte[], T> read;

		BodyHandler(Function<byte[], T> read) {
			this.read = read;
		}

		T apply(byte[] body) {
			return read.apply(body);
		}
	}

	/** The ways to read a body. */
	public static final class BodyHandlers {

		private BodyHandlers() {}

		/** {@return a handler reading the body as UTF-8 text} */
		public static BodyHandler<String> ofString() {
			return ofString(StandardCharsets.UTF_8);
		}

		/**
		 * Reads the body as text.
		 *
		 * @param charset how to decode it
		 * @return the handler
		 */
		public static BodyHandler<String> ofString(Charset charset) {
			return new BodyHandler<>(body -> new String(body, charset));
		}

		/** {@return a handler reading the body as bytes} */
		public static BodyHandler<byte[]> ofByteArray() {
			return new BodyHandler<>(body -> body);
		}

		/** {@return a handler reading the body as a stream over bytes already in hand} */
		public static BodyHandler<InputStream> ofInputStream() {
			return new BodyHandler<>(ByteArrayInputStream::new);
		}

		/** {@return a handler that reads the body and throws it away} */
		public static BodyHandler<Void> discarding() {
			return new BodyHandler<>(body -> null);
		}
	}
}
