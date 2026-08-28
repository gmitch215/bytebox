package dev.gmitch215.bytebox.net;

/**
 * One field of a parsed URL, named as the WHATWG algorithm names it.
 *
 * <p>{@link URL} takes this rather than calling the platform directly, so that the accessors derived
 * from the fields - the port a scheme implies, the path and query joined, whether two URLs name the
 * same thing - can be driven from a JVM's own parser under test.
 */
@FunctionalInterface
interface Fields {
	/**
	 * @param href the whole URL
	 * @param name {@code protocol}, {@code hostname}, {@code port}, {@code pathname}, {@code search},
	 *     {@code hash}, {@code host}, {@code username}, {@code password} or {@code href}
	 * @return the field, empty rather than null when the URL does not carry it
	 */
	String of(String href, String name);
}
