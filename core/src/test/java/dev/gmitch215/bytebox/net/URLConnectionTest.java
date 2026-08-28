package dev.gmitch215.bytebox.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a connection answers before and after the request is sent.
 *
 * <p>Sending is the platform's. Everything else is here: the settings, the header lookups derived from
 * the response, and every refusal that keeps a caller from writing a body after the request has gone.
 */
@DisplayName("URLConnection")
class URLConnectionTest {

	/** A connection with a fixed set of response headers and nothing behind it. */
	private static final class Stub extends URLConnection {

		private final Map<String, List<String>> headers = new LinkedHashMap<>();

		Stub() {
			super(null);
		}

		@Override
		public void connect() {
			connected = true;
		}

		@Override
		public InputStream getInputStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public OutputStream getOutputStream() {
			return OutputStream.nullOutputStream();
		}

		@Override
		public String getHeaderField(String name) {
			List<String> values = headers.get(name.toLowerCase(java.util.Locale.ROOT));
			return values == null ? null : values.get(values.size() - 1);
		}

		@Override
		public void setRequestProperty(String name, String value) {}

		@Override
		public void addRequestProperty(String name, String value) {}

		@Override
		public String getRequestProperty(String name) {
			return null;
		}

		Stub with(String name, String value) {
			headers.put(name.toLowerCase(java.util.Locale.ROOT), List.of(value));
			return this;
		}
	}

	@Test
	@DisplayName("reads a header as a number, and answers the fallback when it is not one")
	void readsAHeaderAsANumber() {
		Stub connection = new Stub().with("Content-Length", " 42 ").with("Weird", "nope");

		assertEquals(42, connection.getHeaderFieldInt("content-length", -1));
		assertEquals(42L, connection.getHeaderFieldLong("Content-Length", -1L));
		assertEquals(42, connection.getContentLength());
		assertEquals(42L, connection.getContentLengthLong());

		assertEquals(-7, connection.getHeaderFieldInt("weird", -7));
		assertEquals(-7L, connection.getHeaderFieldLong("weird", -7L));
		assertEquals(-7, connection.getHeaderFieldInt("missing", -7));
		assertEquals(-7L, connection.getHeaderFieldLong("missing", -7L));
	}

	@Test
	@DisplayName("answers -1 for a body whose length the response does not declare")
	void answersMinusOneForAnUndeclaredLength() {
		Stub connection = new Stub();

		assertEquals(-1, connection.getContentLength());
		assertEquals(-1L, connection.getContentLengthLong());
		assertNull(connection.getContentType());
		assertNull(connection.getContentEncoding());
	}

	@Test
	@DisplayName("reads the declared media type and encoding")
	void readsTheMediaType() {
		Stub connection = new Stub()
			.with("Content-Type", "application/json")
			.with("Content-Encoding", "gzip");

		assertEquals("application/json", connection.getContentType());
		assertEquals("gzip", connection.getContentEncoding());
	}

	@Test
	@DisplayName("carries the settings that were set, and refuses a negative timeout")
	void carriesTheSettings() {
		Stub connection = new Stub();

		connection.setDoOutput(true);
		connection.setDoInput(false);
		connection.setConnectTimeout(1000);
		connection.setReadTimeout(2000);
		connection.setUseCaches(false);

		assertTrue(connection.getDoOutput());
		assertFalse(connection.getDoInput());
		assertEquals(1000, connection.getConnectTimeout());
		assertEquals(2000, connection.getReadTimeout());
		// the platform's cache is not something a caller turns off from here
		assertTrue(connection.getUseCaches());
		assertNull(connection.getURL());

		assertThrows(IllegalArgumentException.class, () -> connection.setConnectTimeout(-1));
		assertThrows(IllegalArgumentException.class, () -> connection.setReadTimeout(-1));
	}

	// #region the HTTP connection's own refusals

	private static HttpURLConnection connection() {
		return new HttpURLConnection(new URL("https://example.com/x", (href, name) -> ""));
	}

	@Test
	@DisplayName("takes each HTTP method and refuses anything else")
	void takesEachMethod() throws Exception {
		HttpURLConnection connection = connection();

		for (String method : List.of("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "PATCH")) {
			connection.setRequestMethod(method);
			assertEquals(method, connection.getRequestMethod());
		}
		connection.setRequestMethod("post");
		assertEquals("POST", connection.getRequestMethod());

		assertThrows(java.net.ProtocolException.class, () -> connection.setRequestMethod("FETCH"));
		assertThrows(java.net.ProtocolException.class, () -> connection.setRequestMethod(null));
	}

	@Test
	@DisplayName("collects request headers, replacing or adding as asked")
	void collectsRequestHeaders() {
		HttpURLConnection connection = connection();

		connection.setRequestProperty("Accept", "a");
		connection.addRequestProperty("accept", "b");
		assertEquals("a, b", connection.getRequestProperty("ACCEPT"));

		connection.setRequestProperty("Accept", "c");
		assertEquals("c", connection.getRequestProperty("accept"));
		assertNull(connection.getRequestProperty("missing"));
	}

	@Test
	@DisplayName("refuses a request body unless it was told one was coming")
	void refusesAnUnannouncedBody() {
		HttpURLConnection connection = connection();

		IOException refused = assertThrows(IOException.class, connection::getOutputStream);
		assertTrue(refused.getMessage().contains("setDoOutput"), refused.getMessage());
	}

	@Test
	@DisplayName("collects a request body and keeps every byte written to it")
	void collectsARequestBody() throws Exception {
		HttpURLConnection connection = connection();
		connection.setDoOutput(true);

		OutputStream body = connection.getOutputStream();
		body.write("hello ".getBytes());
		body.write("world".getBytes());
		// the same stream comes back, so a library that asks twice does not lose what it wrote
		assertEquals(body, connection.getOutputStream());
	}

	@Test
	@DisplayName("answers no error stream before the request has been sent")
	void answersNoErrorStreamYet() {
		assertNull(connection().getErrorStream());
	}

	@Test
	@DisplayName("reports no proxy, because the platform has no proxy setting")
	void reportsNoProxy() {
		assertFalse(connection().usingProxy());
	}

	@Test
	@DisplayName("answers an empty header map before the request has been sent")
	void answersNoHeadersYet() {
		assertTrue(connection().getHeaderFields().isEmpty());
		assertNull(connection().getHeaderField("date"));
	}

	@Test
	@DisplayName("follows redirects unless told otherwise")
	void followsRedirects() {
		HttpURLConnection connection = connection();

		assertTrue(connection.getInstanceFollowRedirects());
		connection.setInstanceFollowRedirects(false);
		assertFalse(connection.getInstanceFollowRedirects());
	}

	@Test
	@DisplayName("names the statuses a caller compares against")
	void namesTheStatuses() {
		assertEquals(200, HttpURLConnection.HTTP_OK);
		assertEquals(201, HttpURLConnection.HTTP_CREATED);
		assertEquals(202, HttpURLConnection.HTTP_ACCEPTED);
		assertEquals(204, HttpURLConnection.HTTP_NO_CONTENT);
		assertEquals(301, HttpURLConnection.HTTP_MOVED_PERM);
		assertEquals(302, HttpURLConnection.HTTP_MOVED_TEMP);
		assertEquals(304, HttpURLConnection.HTTP_NOT_MODIFIED);
		assertEquals(400, HttpURLConnection.HTTP_BAD_REQUEST);
		assertEquals(401, HttpURLConnection.HTTP_UNAUTHORIZED);
		assertEquals(403, HttpURLConnection.HTTP_FORBIDDEN);
		assertEquals(404, HttpURLConnection.HTTP_NOT_FOUND);
		assertEquals(409, HttpURLConnection.HTTP_CONFLICT);
		assertEquals(429, HttpURLConnection.HTTP_TOO_MANY_REQUESTS);
		assertEquals(500, HttpURLConnection.HTTP_INTERNAL_ERROR);
		assertEquals(502, HttpURLConnection.HTTP_BAD_GATEWAY);
		assertEquals(503, HttpURLConnection.HTTP_UNAVAILABLE);
	}

	@Test
	@DisplayName("forgets the response when it is disconnected")
	void forgetsTheResponse() {
		HttpURLConnection connection = connection();
		connection.disconnect();
		assertNull(connection.getErrorStream());
	}

	// #endregion
}
