package dev.gmitch215.bytebox.html;

/**
 * Called for each chunk of text.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface TextHandler {
	/**
	 * Handles one chunk.
	 *
	 * @param text the chunk, for use inside this call
	 */
	void text(TextChunk text);
}
