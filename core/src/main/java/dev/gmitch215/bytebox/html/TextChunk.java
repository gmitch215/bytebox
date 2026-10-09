package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * A piece of text. The parser does not buffer, so one text node can arrive as several chunks; the
 * last has {@link #isLastInTextNode()} set and may be empty.
 *
 * @since 1.0.3
 */
public interface TextChunk extends JSObject {
	/** {@return the text of this chunk} */
	@JSProperty
	String getText();

	/** {@return whether this is the last chunk of its text node} */
	@JSProperty
	boolean isLastInTextNode();

	/** {@return whether an earlier handler already removed or replaced this chunk} */
	@JSProperty
	boolean isRemoved();

	/**
	 * Inserts text before the chunk.
	 *
	 * @param content the text, escaped
	 * @return this chunk
	 */
	@JSMethod
	TextChunk before(String content);

	/**
	 * Inserts content before the chunk.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this chunk
	 */
	@JSMethod
	TextChunk before(String content, ContentOptions options);

	/**
	 * Inserts text after the chunk.
	 *
	 * @param content the text, escaped
	 * @return this chunk
	 */
	@JSMethod
	TextChunk after(String content);

	/**
	 * Inserts content after the chunk.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this chunk
	 */
	@JSMethod
	TextChunk after(String content, ContentOptions options);

	/**
	 * Replaces the chunk by text.
	 *
	 * @param content the text, escaped
	 * @return this chunk
	 */
	@JSMethod
	TextChunk replace(String content);

	/**
	 * Replaces the chunk by other content.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this chunk
	 */
	@JSMethod
	TextChunk replace(String content, ContentOptions options);

	/**
	 * Removes the chunk.
	 *
	 * @return this chunk
	 */
	@JSMethod
	TextChunk remove();
}
