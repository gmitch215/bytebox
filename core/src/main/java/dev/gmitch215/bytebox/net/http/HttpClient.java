package dev.gmitch215.bytebox.net.http;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.TSObject;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An HTTP client, standing in for {@code java.net.http.HttpClient}.
 *
 * <p>The compiler substitutes {@code java.net.http.HttpClient} for this one, so a library that makes a
 * request the modern way works unchanged. Underneath it is {@code fetch}, which counts against the
 * subrequest limit - 50 per invocation on the free plan, 10,000 on paid - and against the six
 * simultaneous outgoing connections every plan allows.
 *
 * {@snippet lang = "java":
 * HttpClient client = HttpClient.newHttpClient();
 * HttpResponse<String> response = client.send(
 * 	HttpRequest.newBuilder(URI.create("https://example.com")).build(),
 * 	HttpResponse.BodyHandlers.ofString()
 * );
 *}
 *
 * <p>{@link #send} blocks the way it does on a JVM, which here means suspending the fiber rather than
 * an OS thread. Nothing would run in parallel with it in any case, because the runtime has one thread.
 *
 * <p>{@code sendAsync} is absent, and for a reason worth naming: it returns a
 * {@code CompletableFuture}, which the class library does not have. A method that cannot work should
 * not be on the surface, so calling it is a compile error in your own project rather than an
 * unresolved reference when the program is compiled to WebAssembly.
 * {@link dev.gmitch215.bytebox.concurrent.Async#supply} runs work on its own fiber where that is what
 * was wanted.
 *
 * <p>Also not here, because the platform has nothing to back it: HTTP/2 negotiation as a choice (the
 * platform decides), a proxy, an executor, a cookie handler, an authenticator, and a client-side
 * certificate. Redirect following is a choice; the rest are the platform's to make.
 *
 * @since 1.0.0
 */
public final class HttpClient {

	private final Redirect redirect;
	private final Duration connectTimeout;

	private HttpClient(Builder builder) {
		this.redirect = builder.redirect;
		this.connectTimeout = builder.connectTimeout;
	}

	/** {@return a client with the defaults} */
	public static HttpClient newHttpClient() {
		return newBuilder().build();
	}

	/** {@return a builder} */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Sends a request and waits for the answer.
	 *
	 * @param request the request
	 * @param handler how to read the body
	 * @param <T> what the body is read as
	 * @return the response
	 * @throws IOException when the request fails
	 */
	public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
		throws IOException {
		try {
			return read(request, handler, Bytebox.fetch(request.uri().toString(), init(request)));
		} catch (RuntimeException failed) {
			throw new IOException("the request to " + request.uri() + " failed", failed);
		}
	}

	/** {@return how redirects are handled} */
	public Redirect followRedirects() {
		return redirect;
	}

	/** {@return how long a connection may take to establish, empty for no limit} */
	public Optional<Duration> connectTimeout() {
		return Optional.ofNullable(connectTimeout);
	}

	/** {@return the version the platform reports, which it chooses rather than the caller} */
	public Version version() {
		return Version.HTTP_2;
	}

	private TSObject init(HttpRequest request) {
		byte[] body = request.bodyPublisher().map(HttpRequest.BodyPublisher::bytes).orElse(null);
		long millis = request
			.timeout()
			.or(() -> Optional.ofNullable(connectTimeout))
			.map(Duration::toMillis)
			.orElse(0L);
		TSObject init = Fetches.init(
			request.method(),
			body == null || body.length == 0 ? null : Bytes.toBuffer(body),
			(int) Math.min(millis, Integer.MAX_VALUE),
			redirect != Redirect.NEVER
		);
		for (Map.Entry<String, List<String>> header : request.headers().map().entrySet()) {
			Fetches.header(init, header.getKey(), String.join(", ", header.getValue()));
		}
		return init;
	}

	private <T> HttpResponse<T> read(
		HttpRequest request,
		HttpResponse.BodyHandler<T> handler,
		Response response
	) {
		byte[] body = response.isBodyUsed() ? new byte[0] : Bytes.fromBuffer(response.bytes());
		Map<String, List<String>> headers = new LinkedHashMap<>();
		dev.gmitch215.bytebox.Headers received = response.getHeaders();
		for (String name : Fetches.names(TSObject.of(received)).split(",")) {
			if (!name.isEmpty()) headers.put(name, List.of(received.get(name)));
		}
		// the platform leaves the URL empty on a response it did not fetch, such as a stubbed one
		String from = response.getUrl();
		return new HttpResponse<>(
			response.getStatus(),
			HttpHeaders.of(headers),
			handler.apply(body),
			request,
			URI.create(from == null || from.isEmpty() ? request.uri().toString() : from)
		);
	}

	/** Which HTTP version to use, which on this platform the platform decides. */
	public enum Version {
		/** HTTP/1.1. */
		HTTP_1_1,
		/** HTTP/2. */
		HTTP_2
	}

	/** What to do with a redirect. */
	public enum Redirect {
		/** Return it rather than follow it. */
		NEVER,
		/** Follow it, which is the default here as it is elsewhere. */
		ALWAYS,
		/** Follow it, since the platform does not downgrade a scheme anyway. */
		NORMAL
	}

	/** Assembles a client. */
	public static final class Builder {

		private Redirect redirect = Redirect.NEVER;
		private Duration connectTimeout;

		private Builder() {}

		/**
		 * What to do with a redirect.
		 *
		 * @param redirect the policy
		 * @return this builder
		 */
		public Builder followRedirects(Redirect redirect) {
			this.redirect = redirect == null ? Redirect.NEVER : redirect;
			return this;
		}

		/**
		 * How long a connection may take to establish.
		 *
		 * @param timeout the limit
		 * @return this builder
		 */
		public Builder connectTimeout(Duration timeout) {
			if (timeout == null || timeout.isNegative() || timeout.isZero()) {
				throw new IllegalArgumentException("a timeout is a positive duration");
			}
			this.connectTimeout = timeout;
			return this;
		}

		/**
		 * Which version to ask for, which the platform ignores because it negotiates its own.
		 *
		 * @param version ignored
		 * @return this builder
		 */
		public Builder version(Version version) {
			return this;
		}

		/** {@return the client} */
		public HttpClient build() {
			return new HttpClient(this);
		}
	}
}
