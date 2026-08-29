package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.StubHeaders;
import dev.gmitch215.bytebox.Stubs.StubRequest;
import dev.gmitch215.bytebox.Stubs.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The URL members are absent here on purpose: they delegate to the platform's own parser, so there
 * is nothing in Java to test. The workerd lane covers them against real URLs.
 */
@DisplayName("Request")
class RequestTest {

	@Test
	@DisplayName("reads the method and URL as given")
	void basics() {
		StubRequest request = new StubRequest(
			"https://example.com/users",
			"POST",
			new StubHeaders()
		);

		assertEquals("POST", request.getMethod());
		assertEquals("https://example.com/users", request.getUrl());
	}

	@Test
	@DisplayName("reads the country Cloudflare resolved")
	void country() {
		StubHeaders headers = new StubHeaders();
		headers.set("CF-IPCountry", "GB");

		assertEquals("GB", new StubRequest("https://example.com/", "GET", headers).country());
	}

	@Test
	@DisplayName("reads the client address Cloudflare saw")
	void ip() {
		StubHeaders headers = new StubHeaders();
		headers.set("cf-connecting-ip", "203.0.113.7");

		assertEquals("203.0.113.7", new StubRequest("https://example.com/", "GET", headers).ip());
	}

	@Test
	@DisplayName("answers null when Cloudflare sent no country")
	void countryAbsent() {
		assertNull(new StubRequest("https://example.com/").country());
	}

	@Test
	@DisplayName("reads the data centre from Cloudflare's own properties when they are there")
	void coloFromCf() {
		StubRequest request = new StubRequest("https://example.com/").withCf(
			Value.object().with("colo", "LHR")
		);

		assertEquals("LHR", request.colo());
	}

	@Test
	@DisplayName("falls back to the suffix of the ray, which is where the colo also appears")
	void coloFromRay() {
		assertEquals("LHR", new StubRequest("https://example.com/", "GET", ray()).colo());
	}

	@Test
	@DisplayName("answers null when neither carries one")
	void coloAbsent() {
		StubHeaders headers = new StubHeaders();
		headers.set("cf-ray", "noseparator");

		assertNull(new StubRequest("https://example.com/").colo());
		assertNull(new StubRequest("https://example.com/").withCf(Value.absent()).colo());
		assertNull(new StubRequest("https://example.com/", "GET", headers).colo());
	}

	@Test
	@DisplayName("reads the ray without the data centre appended to it")
	void rayId() {
		assertEquals("8f1a2b3c4d5e6f70", new StubRequest("https://x/", "GET", ray()).rayId());
		assertNull(new StubRequest("https://x/").rayId());

		StubHeaders bare = new StubHeaders();
		bare.set("cf-ray", "8f1a2b3c4d5e6f70");
		assertEquals("8f1a2b3c4d5e6f70", new StubRequest("https://x/", "GET", bare).rayId());
	}

	@Test
	@DisplayName("reads the user agent")
	void userAgent() {
		StubHeaders headers = new StubHeaders();
		headers.set("User-Agent", "curl/8.0");

		assertEquals("curl/8.0", new StubRequest("https://x/", "GET", headers).userAgent());
		assertNull(new StubRequest("https://x/").userAgent());
	}

	@Test
	@DisplayName("answers which method it is without a string comparison at the call site")
	void methods() {
		assertTrue(request("GET").isGet());
		assertTrue(request("POST").isPost());
		assertTrue(request("PUT").isPut());
		assertTrue(request("PATCH").isPatch());
		assertTrue(request("DELETE").isDelete());

		assertFalse(request("GET").isPost());
		assertFalse(request("HEAD").isGet());
	}

	@Test
	@DisplayName("falls back for a query parameter that is absent")
	void queryFallback() {
		StubRequest request = new StubRequest("https://x/?a=1").withQuery("a", "1");

		assertEquals("1", request.query("a", "fallback"));
		assertEquals("fallback", request.query("b", "fallback"));
	}

	private static StubRequest request(String method) {
		return new StubRequest("https://example.com/", method, new StubHeaders());
	}

	private static StubHeaders ray() {
		StubHeaders headers = new StubHeaders();
		headers.set("CF-Ray", "8f1a2b3c4d5e6f70-LHR");
		return headers;
	}
}
