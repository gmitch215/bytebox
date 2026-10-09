package dev.gmitch215.bytebox.html;

/**
 * Called for each comment.
 *
 * @since 1.0.3
 */
@FunctionalInterface
public interface CommentHandler {
	/**
	 * Handles one comment.
	 *
	 * @param comment the comment, for use inside this call
	 */
	void comments(Comment comment);
}
