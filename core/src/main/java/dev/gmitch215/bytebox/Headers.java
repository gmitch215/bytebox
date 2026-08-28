package dev.gmitch215.bytebox;

import org.teavm.jso.JSObject;

/**
 * HTTP headers. Names are compared case-insensitively.
 *
 * @since 1.0.0
 */
public interface Headers extends JSObject {
	/**
	 * Reads one header.
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
	 * Reports whether a header is present.
	 *
	 * @param name the header name
	 * @return whether it is present
	 */
	boolean has(String name);
}
