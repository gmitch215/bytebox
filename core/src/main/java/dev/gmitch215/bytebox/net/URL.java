package dev.gmitch215.bytebox.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A URL, standing in for {@code java.net.URL}.
 *
 * <p>The compiler substitutes {@code java.net.URL} for this one, so a library that opens a connection
 * the ordinary way works unchanged. What it replaces is a version wired to {@code XMLHttpRequest},
 * which this platform does not have: the class library's own {@code java.net.URL} compiles and then
 * fails on the first request. Underneath this one is {@code fetch}.
 *
 * <p>Parsing is the platform's, which is the WHATWG algorithm rather than the one the JDK uses. The
 * difference shows on inputs neither is asked for in practice, and it is what makes a relative spec
 * against a base resolve the way a browser resolves it.
 *
 * {@snippet lang = "java":
 * HttpURLConnection connection = (HttpURLConnection) new URL("https://example.com").openConnection();
 * connection.setRequestProperty("Accept", "application/json");
 * try (InputStream body = connection.getInputStream()) {
 * 	System.out.println(new String(body.readAllBytes()));
 * }
 *}
 *
 * <p>What is not here, because the platform has nothing to back it: registering a stream handler
 * factory, and the {@code getContent} family, which resolves a MIME type to a Java object through a
 * content handler. A program reaching for one fails to compile, which is where it should fail.
 *
 * @since 1.0.0
 */
public final class URL {

	private final String spec;
	private final Fields fields;

	/**
	 * Parses a URL.
	 *
	 * @param spec the URL
	 * @throws MalformedURLException when the platform cannot parse it
	 */
	public URL(String spec) throws MalformedURLException {
		this(null, spec);
	}

	/** An already-absolute URL over a given parser, which is how the test lane drives the accessors. */
	URL(String href, Fields fields) {
		this.spec = href;
		this.fields = fields;
	}

	/**
	 * Parses a URL against a base, so that a relative one resolves.
	 *
	 * @param context the base, or null
	 * @param spec the URL, absolute or relative
	 * @throws MalformedURLException when the platform cannot parse it
	 */
	public URL(URL context, String spec) throws MalformedURLException {
		String base = context == null ? null : context.spec;
		if (spec == null || !Urls.parses(spec, base)) {
			throw new MalformedURLException("not a URL: " + spec);
		}
		this.spec = Urls.field(spec, base, "href");
		this.fields = (href, name) -> Urls.field(href, null, name);
	}

	/**
	 * Builds a URL from its parts.
	 *
	 * @param protocol the scheme, without the colon
	 * @param host the host
	 * @param file the path, with any query
	 * @throws MalformedURLException when the parts do not make a URL
	 */
	public URL(String protocol, String host, String file) throws MalformedURLException {
		this(protocol, host, -1, file);
	}

	/**
	 * Builds a URL from its parts.
	 *
	 * @param protocol the scheme, without the colon
	 * @param host the host
	 * @param port the port, or -1 for the scheme's own
	 * @param file the path, with any query
	 * @throws MalformedURLException when the parts do not make a URL
	 */
	public URL(String protocol, String host, int port, String file) throws MalformedURLException {
		this(
			null,
			protocol +
				"://" +
				host +
				(port < 0 ? "" : ":" + port) +
				(file.startsWith("/") || file.isEmpty() ? file : "/" + file)
		);
	}

	/** {@return the scheme, without the colon} */
	public String getProtocol() {
		String scheme = field("protocol");
		return scheme.endsWith(":") ? scheme.substring(0, scheme.length() - 1) : scheme;
	}

	/** {@return the host, without the port} */
	public String getHost() {
		return field("hostname");
	}

	/** {@return the host and port as written, which is the host alone on a default port} */
	public String getAuthority() {
		return field("host");
	}

	/** {@return the port, or -1 when the URL leaves the scheme's own port implied} */
	public int getPort() {
		String port = field("port");
		return port.isEmpty() ? -1 : Integer.parseInt(port);
	}

	/** {@return the port the scheme uses when none is written, or -1 for a scheme with none} */
	public int getDefaultPort() {
		switch (getProtocol()) {
			case "http":
			case "ws":
				return 80;
			case "https":
			case "wss":
				return 443;
			case "ftp":
				return 21;
			default:
				return -1;
		}
	}

	/** {@return the path} */
	public String getPath() {
		return field("pathname");
	}

	/** {@return the query, without the question mark, or null when there is none} */
	public String getQuery() {
		String query = field("search");
		return query.isEmpty() ? null : query.substring(1);
	}

	/** {@return the path with any query, which is what the request line carries} */
	public String getFile() {
		String query = getQuery();
		return query == null ? getPath() : getPath() + "?" + query;
	}

	/** {@return the fragment, without the hash, or null when there is none} */
	public String getRef() {
		String fragment = field("hash");
		return fragment.isEmpty() ? null : fragment.substring(1);
	}

	/** {@return the user information before the host, or null when there is none} */
	public String getUserInfo() {
		String user = field("username");
		if (user.isEmpty()) return null;
		String password = field("password");
		return password.isEmpty() ? user : user + ":" + password;
	}

	/**
	 * Opens a connection to this URL, without sending anything yet.
	 *
	 * @return the connection, an {@link HttpURLConnection} for an HTTP or HTTPS URL
	 * @throws IOException when the scheme is one the platform cannot reach
	 */
	public URLConnection openConnection() throws IOException {
		String protocol = getProtocol();
		if (!protocol.equals("http") && !protocol.equals("https")) {
			throw new IOException(
				"the platform can only reach http and https from a URL, not " + protocol
			);
		}
		return new HttpURLConnection(this);
	}

	/**
	 * Sends a GET and returns the body.
	 *
	 * @return the body
	 * @throws IOException when the request fails
	 */
	public InputStream openStream() throws IOException {
		return openConnection().getInputStream();
	}

	/**
	 * This URL as a {@code URI}.
	 *
	 * @return the URI
	 * @throws URISyntaxException when the URI parser is stricter about it than the URL parser was
	 */
	public URI toURI() throws URISyntaxException {
		return new URI(spec);
	}

	/** {@return the URL as text} */
	public String toExternalForm() {
		return spec;
	}

	/**
	 * Whether two URLs name the same thing apart from the fragment.
	 *
	 * @param other the other URL
	 * @return whether they do
	 */
	public boolean sameFile(URL other) {
		if (other == null) return false;
		int hash = spec.indexOf('#');
		int otherHash = other.spec.indexOf('#');
		String mine = hash < 0 ? spec : spec.substring(0, hash);
		String theirs = otherHash < 0 ? other.spec : other.spec.substring(0, otherHash);
		return mine.equals(theirs);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof URL && spec.equals(((URL) other).spec);
	}

	@Override
	public int hashCode() {
		return spec.hashCode();
	}

	@Override
	public String toString() {
		return spec;
	}

	private String field(String name) {
		return fields.of(spec, name);
	}
}
