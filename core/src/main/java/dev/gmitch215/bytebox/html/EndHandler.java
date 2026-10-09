package dev.gmitch215.bytebox.html;

/**
 * Called once at the end of the document, where content can still be appended.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface EndHandler {
	/**
	 * Handles the end of the document.
	 *
	 * @param end the end, for use inside this call
	 */
	void end(DocumentEnd end);
}
