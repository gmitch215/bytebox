package dev.gmitch215.bytebox.net;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.js.Bytes;
import dev.gmitch215.bytebox.js.TSObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An HTTP request, standing in for {@code java.net.HttpURLConnection}.
 *
 * <p>The compiler substitutes {@code java.net.HttpURLConnection} for this one, so a library that makes
 * a request the ordinary way works unchanged. Underneath it is {@code fetch}, which counts against the
 * subrequest limit - 50 per invocation on the free plan, 10,000 on paid - and against the six
 * simultaneous outgoing connections every plan allows.
 *
 * <p>One difference worth knowing, and it follows from {@code fetch} being one call rather than a
 * duplex stream: the request is not sent until the response is asked for. A body written to
 * {@link #getOutputStream()} is collected, and {@link #getResponseCode()},
 * {@link #getInputStream()} or a header lookup is what sends it. The response body is read in full
 * before {@link #getInputStream()} returns, so the stream never blocks.
 *
 * <p>The two timeouts are added together, because the platform gives one deadline for a whole request
 * rather than a separate one for the connection.
 *
 * @since 1.0.0
 */
public class HttpURLConnection extends URLConnection {

	/** The 200 status. */
	public static final int HTTP_OK = 200;

	/** The 201 status. */
	public static final int HTTP_CREATED = 201;

	/** The 202 status. */
	public static final int HTTP_ACCEPTED = 202;

	/** The 204 status. */
	public static final int HTTP_NO_CONTENT = 204;

	/** The 301 status. */
	public static final int HTTP_MOVED_PERM = 301;

	/** The 302 status. */
	public static final int HTTP_MOVED_TEMP = 302;

	/** The 304 status. */
	public static final int HTTP_NOT_MODIFIED = 304;

	/** The 400 status. */
	public static final int HTTP_BAD_REQUEST = 400;

	/** The 401 status. */
	public static final int HTTP_UNAUTHORIZED = 401;

	/** The 403 status. */
	public static final int HTTP_FORBIDDEN = 403;

	/** The 404 status. */
	public static final int HTTP_NOT_FOUND = 404;

	/** The 409 status. */
	public static final int HTTP_CONFLICT = 409;

	/** The 429 status, which the platform's own rate limits also use. */
	public static final int HTTP_TOO_MANY_REQUESTS = 429;

	/** The 500 status. */
	public static final int HTTP_INTERNAL_ERROR = 500;

	/** The 502 status. */
	public static final int HTTP_BAD_GATEWAY = 502;

	/** The 503 status. */
	public static final int HTTP_UNAVAILABLE = 503;

	private static final List<String> METHODS = List.of(
		"GET",
		"POST",
		"HEAD",
		"OPTIONS",
		"PUT",
		"DELETE",
		"PATCH",
		"TRACE"
	);

	private final Map<String, List<String>> requestHeaders = new LinkedHashMap<>();

	private String method = "GET";
	private boolean followRedirects = true;
	private ByteArrayOutputStream requestBody;

	private Response response;
	private byte[] responseBody;
	private Map<String, List<String>> responseHeaders;

	/**
	 * @param url the URL to request
	 */
	protected HttpURLConnection(URL url) {
		super(url);
	}

	/**
	 * The method to use.
	 *
	 * @param method one of GET, POST, HEAD, OPTIONS, PUT, DELETE, PATCH or TRACE
	 * @throws java.net.ProtocolException when it is not one of those, or the request is already sent
	 */
	public void setRequestMethod(String method) throws java.net.ProtocolException {
		if (connected) {
			throw new java.net.ProtocolException("the request has already been sent");
		}
		String upper = method == null ? "" : method.toUpperCase(Locale.ROOT);
		if (!METHODS.contains(upper)) {
			throw new java.net.ProtocolException("not an HTTP method: " + method);
		}
		this.method = upper;
	}

	/** {@return the method that will be used} */
	public String getRequestMethod() {
		return method;
	}

	/**
	 * Whether to follow a redirect rather than return it.
	 *
	 * @param followRedirects whether to
	 */
	public void setInstanceFollowRedirects(boolean followRedirects) {
		this.followRedirects = followRedirects;
	}

	/** {@return whether a redirect will be followed} */
	public boolean getInstanceFollowRedirects() {
		return followRedirects;
	}

	/**
	 * The status.
	 *
	 * @return the status code
	 * @throws IOException when the request fails
	 */
	public int getResponseCode() throws IOException {
		connect();
		return response.getStatus();
	}

	/**
	 * The status text.
	 *
	 * @return the reason phrase, which the platform leaves empty for most statuses
	 * @throws IOException when the request fails
	 */
	public String getResponseMessage() throws IOException {
		connect();
		return response.getStatusText();
	}

	@Override
	public void connect() throws IOException {
		if (connected) return;
		connected = true;

		byte[] body = requestBody == null ? new byte[0] : requestBody.toByteArray();
		TSObject init = Urls.init(
			method,
			body.length == 0 ? null : Bytes.toBuffer(body),
			connectTimeout + readTimeout,
			followRedirects
		);
		for (Map.Entry<String, List<String>> header : requestHeaders.entrySet()) {
			Urls.header(init, header.getKey(), String.join(", ", header.getValue()));
		}

		try {
			response = Bytebox.fetch(url.toExternalForm(), init);
		} catch (RuntimeException failed) {
			throw new IOException(
				"the request to " + url + " failed: " + failed.getMessage(),
				failed
			);
		}
	}

	@Override
	public InputStream getInputStream() throws IOException {
		connect();
		if (!doInput) {
			throw new java.net.ProtocolException(
				"the connection was told it would not read a body"
			);
		}
		if (response.getStatus() >= HTTP_BAD_REQUEST) {
			throw new IOException("the server answered " + response.getStatus() + " for " + url);
		}
		return new ByteArrayInputStream(body());
	}

	/**
	 * The body of a failing response, which {@link #getInputStream()} throws rather than return.
	 *
	 * @return the body, or null when the request has not been sent or did not fail
	 */
	public InputStream getErrorStream() {
		if (response == null || response.getStatus() < HTTP_BAD_REQUEST) return null;
		return new ByteArrayInputStream(body());
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		if (connected) {
			throw new java.net.ProtocolException("the request has already been sent");
		}
		if (!doOutput) {
			throw new java.net.ProtocolException(
				"call setDoOutput(true) before writing a request body"
			);
		}
		if (requestBody == null) requestBody = new ByteArrayOutputStream();
		return requestBody;
	}

	@Override
	public String getHeaderField(String name) {
		List<String> values = headers().get(key(name));
		return values == null ? null : values.get(values.size() - 1);
	}

	/**
	 * Every response header.
	 *
	 * @return the headers, keyed in lower case because HTTP does not distinguish
	 */
	public Map<String, List<String>> getHeaderFields() {
		return Collections.unmodifiableMap(headers());
	}

	@Override
	public void setRequestProperty(String name, String value) {
		if (connected) throw new IllegalStateException("the request has already been sent");
		requestHeaders.put(key(name), new ArrayList<>(List.of(value)));
	}

	@Override
	public void addRequestProperty(String name, String value) {
		if (connected) throw new IllegalStateException("the request has already been sent");
		requestHeaders.computeIfAbsent(key(name), any -> new ArrayList<>()).add(value);
	}

	@Override
	public String getRequestProperty(String name) {
		List<String> values = requestHeaders.get(key(name));
		return values == null ? null : String.join(", ", values);
	}

	/**
	 * Releases the response.
	 *
	 * <p>The platform closes a connection when the invocation ends rather than when a caller says so,
	 * so this drops the response this object holds and nothing more.
	 */
	public void disconnect() {
		response = null;
		responseBody = null;
		responseHeaders = null;
	}

	/** {@return whether a proxy is in use, which is never: the platform has no proxy setting} */
	public boolean usingProxy() {
		return false;
	}

	private byte[] body() {
		if (responseBody == null) {
			responseBody = response.isBodyUsed() ? new byte[0] : Bytes.fromBuffer(response.bytes());
		}
		return responseBody;
	}

	private Map<String, List<String>> headers() {
		if (responseHeaders == null) {
			if (response == null) return Map.of();
			responseHeaders = new LinkedHashMap<>();
			dev.gmitch215.bytebox.Headers headers = response.getHeaders();
			for (String name : Urls.names(TSObject.of(headers)).split(",")) {
				if (name.isEmpty()) continue;
				responseHeaders.put(key(name), List.of(headers.get(name)));
			}
		}
		return responseHeaders;
	}

	private static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}
}
