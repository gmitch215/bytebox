package dev.gmitch215.bytebox.html;

import java.util.Map;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * An element the rewriter has reached. Use it inside the handler call that received it.
 *
 * <p>Content is a string: text, escaped, or raw HTML with {@link ContentOptions#html()}. The
 * platform also takes a {@code Response} or a {@code ReadableStream}; those are not wrapped here.
 *
 * @since 1.0.3
 */
public interface Element extends JSObject {
	/** {@return the tag name, such as {@code h1}} */
	@JSProperty
	String getTagName();

	/**
	 * Renames the tag.
	 *
	 * @param name the new name
	 */
	@JSProperty
	void setTagName(String name);

	/** {@return whether an earlier handler already removed or replaced this element} */
	@JSProperty
	boolean isRemoved();

	/** {@return the namespace URI, such as {@code http://www.w3.org/1999/xhtml}} */
	@JSProperty
	String getNamespaceURI();

	/** {@return the attributes in document order, copied at the call} */
	default Map<String, String> attributes() {
		return Hooks.attributes(this);
	}

	/**
	 * Reads an attribute.
	 *
	 * @param name the attribute name
	 * @return the value, or {@code null} when the element has none
	 */
	@JSMethod
	String getAttribute(String name);

	/**
	 * Tests for an attribute.
	 *
	 * @param name the attribute name
	 * @return whether the element has it
	 */
	@JSMethod
	boolean hasAttribute(String name);

	/**
	 * Sets an attribute, adding it when the element has none.
	 *
	 * @param name the attribute name
	 * @param value the value
	 * @return this element
	 */
	@JSMethod
	Element setAttribute(String name, String value);

	/**
	 * Removes an attribute.
	 *
	 * @param name the attribute name
	 * @return this element
	 */
	@JSMethod
	Element removeAttribute(String name);

	/**
	 * Inserts text before the element.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element before(String content);

	/**
	 * Inserts content before the element.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element before(String content, ContentOptions options);

	/**
	 * Inserts text after the element.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element after(String content);

	/**
	 * Inserts content after the element.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element after(String content, ContentOptions options);

	/**
	 * Inserts text right after the start tag.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element prepend(String content);

	/**
	 * Inserts content right after the start tag.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element prepend(String content, ContentOptions options);

	/**
	 * Inserts text right before the end tag.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element append(String content);

	/**
	 * Inserts content right before the end tag.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element append(String content, ContentOptions options);

	/**
	 * Replaces the element, with its content, by text.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element replace(String content);

	/**
	 * Replaces the element, with its content, by other content.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element replace(String content, ContentOptions options);

	/**
	 * Replaces what is inside the element by text.
	 *
	 * @param content the text, escaped
	 * @return this element
	 */
	@JSMethod
	Element setInnerContent(String content);

	/**
	 * Replaces what is inside the element by other content.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this element
	 */
	@JSMethod
	Element setInnerContent(String content, ContentOptions options);

	/**
	 * Removes the element with everything inside it.
	 *
	 * @return this element
	 */
	@JSMethod
	Element remove();

	/**
	 * Removes the start and end tags and keeps what is inside.
	 *
	 * @return this element
	 */
	@JSMethod
	Element removeAndKeepContent();

	/**
	 * Calls a handler when the element's end tag is reached.
	 *
	 * @param handler the handler
	 */
	default void onEndTag(EndTagHandler handler) {
		Hooks.onEndTag(this, Hooks.endTag(handler));
	}
}
