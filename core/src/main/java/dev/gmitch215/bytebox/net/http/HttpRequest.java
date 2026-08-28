package dev.gmitch215.bytebox.net.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A request, standing in for {@code java.net.http.HttpRequest}.
 *
 * <p>Built the same way as anywhere else, and sent by {@link HttpClient}.
 *
 * {@snippet lang = "java":
 * HttpRequest request = HttpRequest.newBuilder()
 * 	.uri(URI.create("https://example.com/things"))
 * 	.header("Content-Type", "application/json")
 * 	.POST(HttpRequest.BodyPublishers.ofString("{}"))
 * 	.build();
 *}
 *
 * @since 1.0.0
 */
public final class HttpRequest {

	private final URI uri;
	private final String method;
	private final Map<String, List<String>> headers;
	private final BodyPublisher body;
	private final Duration timeout;

	private HttpRequest(Builder builder) {
		this.uri = builder.uri;
		this.method = builder.method;
		this.headers = builder.headers;
		this.body = builder.body;
		this.timeout = builder.timeout;
	}

	/** {@return a builder} */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * A builder aimed at a URI.
	 *
	 * @param uri where to send it
	 * @return the builder
	 */
	public static Builder newBuilder(URI uri) {
		return new Builder().uri(uri);
	}

	/** {@return where the request goes} */
	public URI uri() {
		return uri;
	}

	/** {@return the method} */
	public String method() {
		return method;
	}

	/** {@return the headers} */
	public HttpHeaders headers() {
		return HttpHeaders.of(headers);
	}

	/** {@return the body, empty for a request that carries none} */
	public Optional<BodyPublisher> bodyPublisher() {
		return Optional.ofNullable(body);
	}

	/** {@return how long the request may take, empty for no limit} */
	public Optional<Duration> timeout() {
		return Optional.ofNullable(timeout);
	}

	@Override
	public String toString() {
		return uri + " " + method;
	}

	/** Assembles a request. */
	public static final class Builder {

		private URI uri;
		private String method = "GET";
		private final Map<String, List<String>> headers = new LinkedHashMap<>();
		private BodyPublisher body;
		private Duration timeout;

		private Builder() {}

		/**
		 * Where to send the request.
		 *
		 * @param uri an absolute HTTP or HTTPS URI
		 * @return this builder
		 */
		public Builder uri(URI uri) {
			if (uri == null || uri.getScheme() == null) {
				throw new IllegalArgumentException("a request needs an absolute URI");
			}
			String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				throw new IllegalArgumentException("not an HTTP URI: " + uri);
			}
			this.uri = uri;
			return this;
		}

		/**
		 * Sets a header, replacing any value already set.
		 *
		 * @param name the header name
		 * @param value the value
		 * @return this builder
		 */
		public Builder setHeader(String name, String value) {
			headers.put(name, new ArrayList<>(List.of(value)));
			return this;
		}

		/**
		 * Adds a header, keeping any value already set.
		 *
		 * @param name the header name
		 * @param value the value
		 * @return this builder
		 */
		public Builder header(String name, String value) {
			headers.computeIfAbsent(name, any -> new ArrayList<>()).add(value);
			return this;
		}

		/**
		 * Adds headers, given as alternating names and values.
		 *
		 * @param nameAndValues an even number of strings
		 * @return this builder
		 */
		public Builder headers(String... nameAndValues) {
			if (nameAndValues.length % 2 != 0) {
				throw new IllegalArgumentException("headers come in pairs");
			}
			for (int i = 0; i < nameAndValues.length; i += 2) {
				header(nameAndValues[i], nameAndValues[i + 1]);
			}
			return this;
		}

		/**
		 * How long the request may take.
		 *
		 * @param timeout the limit
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			if (timeout == null || timeout.isNegative() || timeout.isZero()) {
				throw new IllegalArgumentException("a timeout is a positive duration");
			}
			this.timeout = timeout;
			return this;
		}

		/** {@return this builder, sending a GET} */
		public Builder GET() {
			return method("GET", null);
		}

		/**
		 * Sends a POST.
		 *
		 * @param body the body
		 * @return this builder
		 */
		public Builder POST(BodyPublisher body) {
			return method("POST", body);
		}

		/**
		 * Sends a PUT.
		 *
		 * @param body the body
		 * @return this builder
		 */
		public Builder PUT(BodyPublisher body) {
			return method("PUT", body);
		}

		/** {@return this builder, sending a DELETE} */
		public Builder DELETE() {
			return method("DELETE", null);
		}

		/**
		 * Sends a method of your own.
		 *
		 * @param method the method
		 * @param body the body, or {@link BodyPublishers#noBody()} for none
		 * @return this builder
		 */
		public Builder method(String method, BodyPublisher body) {
			if (method == null || method.isEmpty()) {
				throw new IllegalArgumentException("a request needs a method");
			}
			this.method = method.toUpperCase(Locale.ROOT);
			this.body = body;
			return this;
		}

		/**
		 * Whether to expect a 100-continue, which the platform does not send.
		 *
		 * @param enable ignored
		 * @return this builder
		 */
		public Builder expectContinue(boolean enable) {
			return this;
		}

		/** {@return the request} */
		public HttpRequest build() {
			if (uri == null) throw new IllegalStateException("a request needs a URI");
			return new HttpRequest(this);
		}
	}

	/**
	 * A request body.
	 *
	 * <p>Only the ones {@link BodyPublishers} makes: the platform sends a request in one call rather
	 * than as a stream of items, so there is nothing here for a publisher of your own to be driven by.
	 * Writing one fails to compile, which is where it should fail.
	 */
	public static final class BodyPublisher {

		private final byte[] bytes;

		BodyPublisher(byte[] bytes) {
			this.bytes = bytes;
		}

		/** {@return the length, which is always known because the body is always in hand} */
		public long contentLength() {
			return bytes.length;
		}

		byte[] bytes() {
			return bytes;
		}
	}

	/** The request bodies there are. */
	public static final class BodyPublishers {

		private BodyPublishers() {}

		/**
		 * A body of text.
		 *
		 * @param body the text, encoded as UTF-8
		 * @return the publisher
		 */
		public static BodyPublisher ofString(String body) {
			return ofString(body, StandardCharsets.UTF_8);
		}

		/**
		 * A body of text in a given encoding.
		 *
		 * @param body the text
		 * @param charset how to encode it
		 * @return the publisher
		 */
		public static BodyPublisher ofString(String body, Charset charset) {
			return new BodyPublisher(body.getBytes(charset));
		}

		/**
		 * A body of bytes.
		 *
		 * @param body the bytes
		 * @return the publisher
		 */
		public static BodyPublisher ofByteArray(byte[] body) {
			return new BodyPublisher(body.clone());
		}

		/**
		 * Part of an array of bytes.
		 *
		 * @param body the bytes
		 * @param offset where to start
		 * @param length how many
		 * @return the publisher
		 */
		public static BodyPublisher ofByteArray(byte[] body, int offset, int length) {
			byte[] part = new byte[length];
			System.arraycopy(body, offset, part, 0, length);
			return new BodyPublisher(part);
		}

		/** {@return a publisher that sends nothing} */
		public static BodyPublisher noBody() {
			return new BodyPublisher(new byte[0]);
		}
	}
}
