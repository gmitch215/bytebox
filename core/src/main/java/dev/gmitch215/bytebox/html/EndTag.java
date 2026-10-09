package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * An element's end tag, given to a handler from {@link Element#onEndTag(EndTagHandler)}.
 *
 * @since 1.0.3
 */
public interface EndTag extends JSObject {
	/** {@return the tag name} */
	@JSProperty
	String getName();

	/**
	 * Renames the tag.
	 *
	 * @param name the new name
	 */
	@JSProperty
	void setName(String name);

	/**
	 * Inserts text before the end tag.
	 *
	 * @param content the text, escaped
	 * @return this end tag
	 */
	@JSMethod
	EndTag before(String content);

	/**
	 * Inserts content before the end tag.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this end tag
	 */
	@JSMethod
	EndTag before(String content, ContentOptions options);

	/**
	 * Inserts text after the end tag.
	 *
	 * @param content the text, escaped
	 * @return this end tag
	 */
	@JSMethod
	EndTag after(String content);

	/**
	 * Inserts content after the end tag.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this end tag
	 */
	@JSMethod
	EndTag after(String content, ContentOptions options);

	/**
	 * Removes the end tag.
	 *
	 * @return this end tag
	 */
	@JSMethod
	EndTag remove();
}
