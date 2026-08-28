/**
 * The {@code java.net.http} client, over {@code fetch}.
 *
 * <p>The class library has none of it, so this is supplied rather than replaced. A project writes
 * {@code java.net.http} as it would anywhere and the compiler points the references here.
 *
 * <p>Two places where the shape of {@code fetch} shows through, both recorded on the classes
 * themselves: a request body is a whole body rather than a stream of items, so
 * {@code HttpRequest.BodyPublishers} is the complete set of them and a publisher of your own fails to
 * compile; and the same holds of {@code HttpResponse.BodyHandlers}. Everything a caller normally
 * writes - a builder, headers, a timeout, a redirect policy, a string or byte-array body - is here.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.net.http;
