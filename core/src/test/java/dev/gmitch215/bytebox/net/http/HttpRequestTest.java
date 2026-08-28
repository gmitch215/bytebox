package dev.gmitch215.bytebox.net.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The request and response types, and the bodies they carry.
 *
 * <p>Sending is the platform's, so what is checked here is everything up to and after the send: what a
 * builder accepts and refuses, how headers collapse, and what each body handler makes of the bytes.
 */
@DisplayName("java.net.http")
class HttpRequestTest {

	private static final URI SOMEWHERE = URI.create("https://example.com/things");

	// #region the request

	@Test
	@DisplayName("builds a request with a method, headers, a body and a timeout")
	void buildsARequest() {
		HttpRequest request = HttpRequest.newBuilder(SOMEWHERE)
			.header("Accept", "application/json")
			.header("Accept", "text/plain")
			.setHeader("Content-Type", "application/json")
			.timeout(Duration.ofSeconds(5))
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();

		assertEquals(SOMEWHERE, request.uri());
		assertEquals("POST", request.method());
		assertEquals(
			List.of("application/json", "text/plain"),
			request.headers().allValues("accept")
		);
		assertEquals("application/json", request.headers().firstValue("content-type").get());
		assertEquals(Duration.ofSeconds(5), request.timeout().get());
		assertEquals(2, request.bodyPublisher().get().contentLength());
		assertEquals("https://example.com/things POST", request.toString());
	}

	@Test
	@DisplayName("defaults to a GET with no body and no timeout")
	void defaultsToAGet() {
		HttpRequest request = HttpRequest.newBuilder().uri(SOMEWHERE).build();

		assertEquals("GET", request.method());
		assertTrue(request.bodyPublisher().isEmpty());
		assertTrue(request.timeout().isEmpty());
	}

	@Test
	@DisplayName("takes each method the builder names")
	void takesEachMethod() {
		assertEquals("GET", HttpRequest.newBuilder(SOMEWHERE).GET().build().method());
		assertEquals("DELETE", HttpRequest.newBuilder(SOMEWHERE).DELETE().build().method());
		assertEquals(
			"PUT",
			HttpRequest.newBuilder(SOMEWHERE)
				.PUT(HttpRequest.BodyPublishers.noBody())
				.build()
				.method()
		);
		assertEquals(
			"PATCH",
			HttpRequest.newBuilder(SOMEWHERE)
				.method("patch", HttpRequest.BodyPublishers.ofString("x"))
				.build()
				.method()
		);
		// the platform sends no 100-continue, and saying so is accepted rather than refused
		assertEquals(
			"GET",
			HttpRequest.newBuilder(SOMEWHERE).expectContinue(true).build().method()
		);
	}

	@Test
	@DisplayName("adds headers in pairs, and refuses an odd number of them")
	void addsHeadersInPairs() {
		HttpRequest request = HttpRequest.newBuilder(SOMEWHERE).headers("A", "1", "B", "2").build();

		assertEquals("1", request.headers().firstValue("a").get());
		assertEquals("2", request.headers().firstValue("b").get());
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).headers("A")
		);
	}

	@Test
	@DisplayName("refuses a URI that is not an absolute HTTP one, and a request with none")
	void refusesABadUri() {
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder().uri(URI.create("/relative"))
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder().uri(URI.create("ftp://example.com/x"))
		);
		assertThrows(IllegalArgumentException.class, () -> HttpRequest.newBuilder().uri(null));
		assertThrows(IllegalStateException.class, () -> HttpRequest.newBuilder().build());
	}

	@Test
	@DisplayName("refuses a timeout that is not a positive duration, and a method with no name")
	void refusesABadTimeout() {
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).timeout(Duration.ZERO)
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).timeout(Duration.ofSeconds(-1))
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).timeout(null)
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).method("", null)
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpRequest.newBuilder(SOMEWHERE).method(null, null)
		);
	}

	// #endregion

	// #region the bodies

	@Test
	@DisplayName("encodes each kind of request body, and copies the array it was handed")
	void encodesEachBody() {
		assertArrayEquals(
			"hello".getBytes(StandardCharsets.UTF_8),
			HttpRequest.BodyPublishers.ofString("hello").bytes()
		);
		assertArrayEquals(
			new byte[] { (byte) 0xE9 },
			HttpRequest.BodyPublishers.ofString("é", StandardCharsets.ISO_8859_1).bytes()
		);
		assertEquals(0, HttpRequest.BodyPublishers.noBody().contentLength());

		byte[] source = { 1, 2, 3, 4 };
		HttpRequest.BodyPublisher whole = HttpRequest.BodyPublishers.ofByteArray(source);
		source[0] = 9;
		assertArrayEquals(new byte[] { 1, 2, 3, 4 }, whole.bytes());

		assertArrayEquals(
			new byte[] { 2, 3 },
			HttpRequest.BodyPublishers.ofByteArray(new byte[] { 1, 2, 3, 4 }, 1, 2).bytes()
		);
	}

	@Test
	@DisplayName("reads each kind of response body")
	void readsEachBody() throws IOException {
		byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

		assertEquals("hello", HttpResponse.BodyHandlers.ofString().apply(bytes));
		assertEquals(
			"é",
			HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1).apply(new byte[] {
				(byte) 0xE9
			})
		);
		assertArrayEquals(bytes, HttpResponse.BodyHandlers.ofByteArray().apply(bytes));
		assertEquals(null, HttpResponse.BodyHandlers.discarding().apply(bytes));

		InputStream stream = HttpResponse.BodyHandlers.ofInputStream().apply(bytes);
		assertTrue(stream instanceof ByteArrayInputStream);
		assertArrayEquals(bytes, stream.readAllBytes());
	}

	// #endregion

	// #region the response and its headers

	@Test
	@DisplayName("carries the status, headers, body and the URI the body came from")
	void carriesTheResponse() {
		HttpRequest request = HttpRequest.newBuilder(SOMEWHERE).build();
		HttpResponse<String> response = new HttpResponse<>(
			201,
			HttpHeaders.of(Map.of("Location", List.of("/new"))),
			"made",
			request,
			URI.create("https://example.com/things/1")
		);

		assertEquals(201, response.statusCode());
		assertEquals("made", response.body());
		assertEquals("/new", response.headers().firstValue("location").get());
		assertSameRequest(request, response);
		assertEquals(URI.create("https://example.com/things/1"), response.uri());
		assertTrue(response.previousResponse().isEmpty());
		assertEquals("(https://example.com/things GET) 201", response.toString());
	}

	private static void assertSameRequest(HttpRequest request, HttpResponse<?> response) {
		assertEquals(request.uri(), response.request().uri());
	}

	@Test
	@DisplayName("matches a header without regard to case, and takes the last of several values")
	void matchesHeadersWithoutCase() {
		HttpHeaders headers = HttpHeaders.of(
			Map.of("Content-Length", List.of("1", "2"), "ETAG", List.of("abc"))
		);

		assertEquals("2", headers.firstValue("content-length").get());
		assertEquals("2", headers.firstValue("Content-Length").get());
		assertEquals(List.of("1", "2"), headers.allValues("CONTENT-LENGTH"));
		assertEquals("abc", headers.firstValue("etag").get());
		assertTrue(headers.firstValue("missing").isEmpty());
		assertTrue(headers.allValues("missing").isEmpty());
		assertTrue(headers.allValues(null).isEmpty());
	}

	@Test
	@DisplayName("reads a header as a number, and says nothing when it is not one")
	void readsAHeaderAsANumber() {
		HttpHeaders headers = HttpHeaders.of(
			Map.of("Content-Length", List.of(" 42 "), "Weird", List.of("nope"))
		);

		assertEquals(42L, headers.firstValueAsLong("content-length").getAsLong());
		assertTrue(headers.firstValueAsLong("weird").isEmpty());
		assertTrue(headers.firstValueAsLong("missing").isEmpty());
	}

	@Test
	@DisplayName("collapses two spellings of one header into one entry")
	void collapsesTwoSpellings() {
		HttpHeaders headers = HttpHeaders.of(
			new java.util.LinkedHashMap<>(Map.of("Accept", List.of("a"), "accept", List.of("b")))
		);

		assertEquals(1, headers.map().size());
		assertEquals(2, headers.allValues("accept").size());
	}

	@Test
	@DisplayName("is equal by its headers, and cannot be modified through the map it hands out")
	void isEqualByItsHeaders() {
		HttpHeaders headers = HttpHeaders.of(Map.of("A", List.of("1")));

		assertEquals(HttpHeaders.of(Map.of("a", List.of("1"))), headers);
		assertEquals(HttpHeaders.of(Map.of("a", List.of("1"))).hashCode(), headers.hashCode());
		assertFalse(headers.equals(HttpHeaders.of(Map.of("a", List.of("2")))));
		assertEquals("HttpHeaders {a=[1]}", headers.toString());
		assertThrows(UnsupportedOperationException.class, () -> headers.map().clear());
		assertThrows(UnsupportedOperationException.class, () -> headers.allValues("a").add("2"));
	}

	// #endregion

	// #region the client's own settings

	@Test
	@DisplayName("carries the settings a client was built with")
	void carriesClientSettings() {
		HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.connectTimeout(Duration.ofSeconds(3))
			.version(HttpClient.Version.HTTP_1_1)
			.build();

		assertEquals(HttpClient.Redirect.ALWAYS, client.followRedirects());
		assertEquals(Duration.ofSeconds(3), client.connectTimeout().get());
		assertEquals(HttpClient.Version.HTTP_2, client.version());

		HttpClient plain = HttpClient.newHttpClient();
		assertEquals(HttpClient.Redirect.NEVER, plain.followRedirects());
		assertTrue(plain.connectTimeout().isEmpty());
		assertEquals(
			HttpClient.Redirect.NEVER,
			HttpClient.newBuilder().followRedirects(null).build().followRedirects()
		);
	}

	@Test
	@DisplayName("refuses a connect timeout that is not a positive duration")
	void refusesABadConnectTimeout() {
		assertThrows(IllegalArgumentException.class, () ->
			HttpClient.newBuilder().connectTimeout(Duration.ZERO)
		);
		assertThrows(IllegalArgumentException.class, () ->
			HttpClient.newBuilder().connectTimeout(null)
		);
	}

	// #endregion
}
