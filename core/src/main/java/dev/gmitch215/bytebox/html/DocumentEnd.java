package dev.gmitch215.bytebox.html;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;

/**
 * The end of the document, given to {@link EndHandler#end(DocumentEnd)}.
 *
 * @since 1.0.3
 */
public interface DocumentEnd extends JSObject {
	/**
	 * Inserts text after the end of the document.
	 *
	 * @param content the text, escaped
	 * @return this end
	 */
	@JSMethod
	DocumentEnd append(String content);

	/**
	 * Inserts content after the end of the document.
	 *
	 * @param content the content
	 * @param options how to read it
	 * @return this end
	 */
	@JSMethod
	DocumentEnd append(String content, ContentOptions options);
}
