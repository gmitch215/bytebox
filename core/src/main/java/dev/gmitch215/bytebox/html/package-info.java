/**
 * The Workers {@code HTMLRewriter}, with Java handlers.
 *
 * <p>{@link dev.gmitch215.bytebox.html.HTMLRewriter#create()} builds a rewriter, handlers attach
 * with {@code on} and the {@code onDocument*} methods, and {@code transform} wraps a
 * {@link dev.gmitch215.bytebox.Response}. The platform calls the handlers while the body is read,
 * each on its own fiber, so a handler may wait.
 *
 * @since 1.0.3
 */
package dev.gmitch215.bytebox.html;
