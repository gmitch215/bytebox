package dev.gmitch215.bytebox.html;

/**
 * Called when an element's end tag is reached, from {@link Element#onEndTag(EndTagHandler)}.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface EndTagHandler {
	/**
	 * Handles the end tag.
	 *
	 * @param endTag the end tag, for use inside this call
	 */
	void handle(EndTag endTag);
}
