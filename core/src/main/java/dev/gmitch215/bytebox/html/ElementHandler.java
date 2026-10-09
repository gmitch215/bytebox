package dev.gmitch215.bytebox.html;

/**
 * Called for each element a selector matches.
 *
 * <p>A class that also implements {@link TextHandler} or {@link CommentHandler} gets the text and
 * comments inside the match too, from {@link HTMLRewriter#on(String, ElementHandler)}.
 *
 * <p>Each call runs on its own fiber and the platform waits for it before it goes on, so a handler
 * can wait on a binding or a {@code fetch}.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface ElementHandler {
	/**
	 * Handles one element.
	 *
	 * @param element the element, for use inside this call
	 */
	void element(Element element);
}
