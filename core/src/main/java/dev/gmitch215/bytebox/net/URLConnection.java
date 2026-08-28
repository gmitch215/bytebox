package dev.gmitch215.bytebox.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A connection to a URL, standing in for {@code java.net.URLConnection}.
 *
 * <p>Everything the platform can reach from a URL is HTTP, so the only subclass is
 * {@link HttpURLConnection} and this exists for the cast a caller writes rather than for a second
 * implementation. It is replaced along with {@link URL} because the class library's version is wired
 * to a connection type built on {@code XMLHttpRequest}, which this platform does not have.
 *
 * @since 1.0.0
 */
public abstract class URLConnection {

	/** The URL this connection was opened for. */
	protected final URL url;

	/** Whether {@link #connect()} has run. */
	protected boolean connected;

	/** Whether the caller intends to write a body. */
	protected boolean doOutput;

	/** Whether the caller intends to read a body. */
	protected boolean doInput = true;

	/** How long to allow for the connection, in milliseconds, or zero for no limit. */
	protected int connectTimeout;

	/** How long to allow for reading, in milliseconds, or zero for no limit. */
	protected int readTimeout;

	/**
	 * @param url the URL to connect to
	 */
	protected URLConnection(URL url) {
		this.url = url;
	}

	/**
	 * Sends the request, if it has not been sent.
	 *
	 * @throws IOException when the request fails
	 */
	public abstract void connect() throws IOException;

	/** {@return the URL this connection was opened for} */
	public URL getURL() {
		return url;
	}

	/**
	 * The body.
	 *
	 * @return the body, which is read in full before the stream is returned
	 * @throws IOException when the request fails
	 */
	public abstract InputStream getInputStream() throws IOException;

	/**
	 * Where to write the request body.
	 *
	 * @return the stream, which is collected and sent when the response is asked for
	 * @throws IOException when the connection does not accept a body
	 */
	public abstract OutputStream getOutputStream() throws IOException;

	/**
	 * One response header.
	 *
	 * @param name the header name, matched without regard to case
	 * @return the value, or null when the response does not carry it
	 */
	public abstract String getHeaderField(String name);

	/**
	 * The value of a response header read as a number.
	 *
	 * @param name the header name
	 * @param fallback what to answer when the header is absent or is not a number
	 * @return the number
	 */
	public int getHeaderFieldInt(String name, int fallback) {
		String value = getHeaderField(name);
		if (value == null) return fallback;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException notANumber) {
			return fallback;
		}
	}

	/**
	 * The value of a response header read as a number.
	 *
	 * @param name the header name
	 * @param fallback what to answer when the header is absent or is not a number
	 * @return the number
	 */
	public long getHeaderFieldLong(String name, long fallback) {
		String value = getHeaderField(name);
		if (value == null) return fallback;
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException notANumber) {
			return fallback;
		}
	}

	/** {@return the declared body length, or -1 when the response does not declare one} */
	public int getContentLength() {
		return getHeaderFieldInt("Content-Length", -1);
	}

	/** {@return the declared body length, or -1 when the response does not declare one} */
	public long getContentLengthLong() {
		return getHeaderFieldLong("Content-Length", -1L);
	}

	/** {@return the declared media type, or null when the response does not declare one} */
	public String getContentType() {
		return getHeaderField("Content-Type");
	}

	/** {@return the declared body encoding, or null when the response does not declare one} */
	public String getContentEncoding() {
		return getHeaderField("Content-Encoding");
	}

	/**
	 * Sets a request header, replacing any value already set.
	 *
	 * @param name the header name
	 * @param value the value
	 */
	public abstract void setRequestProperty(String name, String value);

	/**
	 * Adds a request header, keeping any value already set.
	 *
	 * @param name the header name
	 * @param value the value
	 */
	public abstract void addRequestProperty(String name, String value);

	/**
	 * One request header, as set.
	 *
	 * @param name the header name
	 * @return the value, or null when none was set
	 */
	public abstract String getRequestProperty(String name);

	/**
	 * Whether the caller intends to write a body.
	 *
	 * @param doOutput whether it does
	 */
	public void setDoOutput(boolean doOutput) {
		this.doOutput = doOutput;
	}

	/** {@return whether the caller said it would write a body} */
	public boolean getDoOutput() {
		return doOutput;
	}

	/**
	 * Whether the caller intends to read a body.
	 *
	 * @param doInput whether it does
	 */
	public void setDoInput(boolean doInput) {
		this.doInput = doInput;
	}

	/** {@return whether the caller said it would read a body} */
	public boolean getDoInput() {
		return doInput;
	}

	/**
	 * How long to allow for the connection.
	 *
	 * <p>The platform gives one deadline for a whole request rather than a separate one for the
	 * connection, so this and {@link #setReadTimeout} are added together.
	 *
	 * @param millis the limit, or zero for no limit
	 */
	public void setConnectTimeout(int millis) {
		if (millis < 0) throw new IllegalArgumentException("a timeout cannot be negative");
		this.connectTimeout = millis;
	}

	/** {@return the connection limit in milliseconds} */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * How long to allow for reading.
	 *
	 * @param millis the limit, or zero for no limit
	 */
	public void setReadTimeout(int millis) {
		if (millis < 0) throw new IllegalArgumentException("a timeout cannot be negative");
		this.readTimeout = millis;
	}

	/** {@return the read limit in milliseconds} */
	public int getReadTimeout() {
		return readTimeout;
	}

	/**
	 * Whether to use a cached copy, which is ignored: the platform's own cache decides.
	 *
	 * @param useCaches whether to
	 */
	public void setUseCaches(boolean useCaches) {}

	/** {@return true, because the platform's cache is not something a caller turns off here} */
	public boolean getUseCaches() {
		return true;
	}
}
