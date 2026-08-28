/**
 * Routing a request to a handler.
 *
 * <p>A {@link dev.gmitch215.bytebox.http.Router} matches on method and path segments rather than on a
 * regular expression. A path is already a list of segments, so comparing them is both the direct
 * implementation and the faster one, and it needs no pattern engine.
 *
 * <p>A JavaScript router such as Hono would work in front of a compiled program, but it puts the
 * routing table in a different language from the handlers and crosses the WebAssembly boundary after
 * JavaScript has already parsed the URL.
 *
 * @since 1.0.0
 */
package dev.gmitch215.bytebox.http;
