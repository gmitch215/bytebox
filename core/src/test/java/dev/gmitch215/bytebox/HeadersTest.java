package dev.gmitch215.bytebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gmitch215.bytebox.Stubs.StubHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Headers")
class HeadersTest {

	@Test
	@DisplayName("compares names without regard to case")
	void caseInsensitive() {
		StubHeaders headers = new StubHeaders();
		headers.set("Content-Type", "text/plain");

		assertEquals("text/plain", headers.get("content-type"));
		assertEquals("text/plain", headers.get("CONTENT-TYPE"));
		assertTrue(headers.has("Content-Type"));
	}

	@Test
	@DisplayName("falls back for a header that is absent")
	void fallback() {
		StubHeaders headers = new StubHeaders();

		assertNull(headers.get("accept"));
		assertEquals("*/*", headers.get("accept", "*/*"));
	}

	@Test
	@DisplayName("prefers a present header over the fallback")
	void presentBeatsFallback() {
		StubHeaders headers = new StubHeaders();
		headers.set("accept", "application/json");

		assertEquals("application/json", headers.get("accept", "*/*"));
	}

	@Test
	@DisplayName("joins a header sent more than once")
	void appended() {
		StubHeaders headers = new StubHeaders();
		headers.append("accept", "text/html");
		headers.append("accept", "application/json");

		assertEquals("text/html, application/json", headers.get("accept"));
	}

	@Test
	@DisplayName("removes a header")
	void removed() {
		StubHeaders headers = new StubHeaders();
		headers.set("x-trace", "abc");
		headers.delete("X-Trace");

		assertFalse(headers.has("x-trace"));
	}
}
