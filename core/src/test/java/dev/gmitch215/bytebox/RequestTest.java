package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.gmitch215.bytebox.Stubs.StubHeaders;
import dev.gmitch215.bytebox.Stubs.StubRequest;
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
}
