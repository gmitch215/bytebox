package dev.gmitch215.bytebox;

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;

/**
 * HTTP headers. Names are compared case-insensitively, so {@code Content-Type} and
 * {@code content-type} are the same header.
 *
 * @since 1.0.0
 */
public interface Headers extends JSObject {
	/**
	 * Reads one header.
	 *
	 * <p>A header sent more than once reads back as its values joined with {@code ", "}, which is
	 * what the Fetch specification requires. {@link #getSetCookie()} is the exception, because
	 * joining cookies that way corrupts them.
	 *
	 * @param name the header name
	 * @return the value, or {@code null} when absent
	 */
	String get(String name);

	/**
	 * Sets one header, replacing any existing value.
	 *
	 * @param name the header name
	 * @param value the value
	 */
	void set(String name, String value);

	/**
	 * Adds one header without replacing existing values.
	 *
	 * @param name the header name
	 * @param value the value
	 */
	void append(String name, String value);

	/**
	 * Removes a header.
	 *
	 * @param name the header name
	 */
	void delete(String name);

	/**
	 * Reports whether a header is present.
	 *
	 * @param name the header name
	 * @return whether it is present
	 */
	boolean has(String name);

	/** {@return every {@code Set-Cookie} separately, which {@link #get(String)} cannot express} */
	JSArrayReader<org.teavm.jso.core.JSString> getSetCookie();

	/**
	 * Reads a header, falling back when absent.
	 *
	 * @param name the header name
	 * @param fallback the value to use when absent
	 * @return the value, or the fallback
	 */
	default String get(String name, String fallback) {
		String value = get(name);
		return value == null ? fallback : value;
	}

	/** {@return every {@code Set-Cookie}, as a Java list} */
	default List<String> setCookies() {
		JSArrayReader<org.teavm.jso.core.JSString> cookies = getSetCookie();
		if (cookies == null) return List.of();
		List<String> values = new ArrayList<>(cookies.getLength());
		for (int i = 0; i < cookies.getLength(); i++) values.add(cookies.get(i).stringValue());
		return values;
	}
}
