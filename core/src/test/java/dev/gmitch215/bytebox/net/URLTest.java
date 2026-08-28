package dev.gmitch215.bytebox.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The accessors {@code URL} derives from a parsed URL.
 *
 * <p>Parsing itself is the platform's, so the parser is stood in for by a JVM's own and what is checked
 * is the arithmetic on top of it: the port a scheme implies, the path and query joined the way a
 * request line carries them, and whether two URLs name the same thing.
 */
@DisplayName("URL")
class URLTest {

	/** The platform's field names, answered from a JVM's parser instead. */
	private static final Fields JDK = (href, name) -> {
		URI uri = URI.create(href);
		switch (name) {
			case "protocol":
				return uri.getScheme() + ":";
			case "hostname":
				return or(uri.getHost());
			case "port":
				return uri.getPort() < 0 ? "" : String.valueOf(uri.getPort());
			case "pathname":
				return uri.getRawPath() == null || uri.getRawPath().isEmpty()
					? "/"
					: uri.getRawPath();
			case "search":
				return uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
			case "hash":
				return uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
			case "host":
				return uri.getPort() < 0 ? or(uri.getHost()) : uri.getHost() + ":" + uri.getPort();
			case "username":
				return or(uri.getUserInfo() == null ? null : uri.getUserInfo().split(":")[0]);
			case "password":
				String info = uri.getUserInfo();
				return info != null && info.contains(":") ? info.split(":", 2)[1] : "";
			default:
				return href;
		}
	};

	private static String or(String value) {
		return value == null ? "" : value;
	}

	private static URL of(String href) {
		return new URL(href, JDK);
	}

	@Test
	@DisplayName("reads the parts of a URL")
	void readsTheParts() {
		URL url = of("https://user:secret@example.com:8443/a/b?q=1&r=2#top");

		assertEquals("https", url.getProtocol());
		assertEquals("example.com", url.getHost());
		assertEquals("example.com:8443", url.getAuthority());
		assertEquals(8443, url.getPort());
		assertEquals("/a/b", url.getPath());
		assertEquals("q=1&r=2", url.getQuery());
		assertEquals("/a/b?q=1&r=2", url.getFile());
		assertEquals("top", url.getRef());
		assertEquals("user:secret", url.getUserInfo());
	}

	@Test
	@DisplayName("answers null rather than an empty string for the parts a URL leaves out")
	void answersNullForWhatIsMissing() {
		URL url = of("http://example.com/a");

		assertEquals(-1, url.getPort());
		assertNull(url.getQuery());
		assertNull(url.getRef());
		assertNull(url.getUserInfo());
		assertEquals("/a", url.getFile());
	}

	@Test
	@DisplayName("names the port a scheme implies, and -1 for a scheme with none")
	void namesTheDefaultPort() {
		assertEquals(80, of("http://example.com/").getDefaultPort());
		assertEquals(443, of("https://example.com/").getDefaultPort());
		assertEquals(80, of("ws://example.com/").getDefaultPort());
		assertEquals(443, of("wss://example.com/").getDefaultPort());
		assertEquals(21, of("ftp://example.com/").getDefaultPort());
		assertEquals(-1, of("mailto://example.com/").getDefaultPort());
	}

	@Test
	@DisplayName("compares two URLs ignoring the fragment")
	void comparesIgnoringTheFragment() {
		URL plain = of("https://example.com/a");
		URL fragment = of("https://example.com/a#top");
		URL other = of("https://example.com/b");

		assertTrue(plain.sameFile(fragment));
		assertTrue(fragment.sameFile(plain));
		assertFalse(plain.sameFile(other));
		assertFalse(plain.sameFile(null));
	}

	@Test
	@DisplayName("is equal by its whole text, fragment included")
	void isEqualByItsText() {
		assertEquals(of("https://example.com/a"), of("https://example.com/a"));
		assertEquals("https://example.com/a".hashCode(), of("https://example.com/a").hashCode());
		assertFalse(of("https://example.com/a").equals(of("https://example.com/a#top")));
		assertFalse(of("https://example.com/a").equals("https://example.com/a"));
		assertEquals("https://example.com/a", of("https://example.com/a").toString());
		assertEquals("https://example.com/a", of("https://example.com/a").toExternalForm());
	}

	@Test
	@DisplayName("refuses to open a scheme the platform cannot reach")
	void refusesASchemeItCannotReach() {
		IOException refused = assertThrows(IOException.class, () ->
			of("ftp://example.com/file").openConnection()
		);
		assertTrue(refused.getMessage().contains("ftp"), refused.getMessage());
	}

	@Test
	@DisplayName("opens an HTTP connection for an HTTP URL")
	void opensAnHttpConnection() throws IOException {
		assertTrue(of("https://example.com/").openConnection() instanceof HttpURLConnection);
		assertTrue(of("http://example.com/").openConnection() instanceof HttpURLConnection);
	}
}
