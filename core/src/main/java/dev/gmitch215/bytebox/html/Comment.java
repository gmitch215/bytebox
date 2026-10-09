package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * An HTML comment.
 *
 * @since 1.0.3
 */
public interface Comment extends JSObject {
	/** {@return the text between {@code <!--} and {@code -->}} */
	@JSProperty
	String getText();

	/**
	 * Changes the comment's text.
	 *
	 * @param text the new text
	 */
	@JSProperty
	void setText(String text);

	/** {@return whether an earlier handler already removed or replaced this comment} */
	@JSProperty
	boolean isRemoved();

	/**
	 * Inserts text before the comment.
	 *
	 * @param content the text, escaped
	 * @return this comment
	 */
	@JSMethod
	Comment before(String content);

	/**
	 * Inserts content before the comment.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this comment
	 */
	@JSMethod
	Comment before(String content, ContentOptions options);

	/**
	 * Inserts text after the comment.
	 *
	 * @param content the text, escaped
	 * @return this comment
	 */
	@JSMethod
	Comment after(String content);

	/**
	 * Inserts content after the comment.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this comment
	 */
	@JSMethod
	Comment after(String content, ContentOptions options);

	/**
	 * Replaces the comment by text.
	 *
	 * @param content the text, escaped
	 * @return this comment
	 */
	@JSMethod
	Comment replace(String content);

	/**
	 * Replaces the comment by other content.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this comment
	 */
	@JSMethod
	Comment replace(String content, ContentOptions options);

	/**
	 * Removes the comment.
	 *
	 * @return this comment
	 */
	@JSMethod
	Comment remove();
}
