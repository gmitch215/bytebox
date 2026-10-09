package dev.gmitch215.bytebox.html;

import dev.gmitch215.bytebox.Response;
import org.teavm.jso.JSObject;

/**
 * Rewrites an HTML response as it streams, with Java handlers.
 *
 * <p>{@link #transform(Response)} returns at once and the platform calls the handlers while
 * whoever holds the new response reads its body, which can be after the handler that built it has
 * returned. A handler that throws stops the parse and errors the new body with the exception; the
 * client sees a truncated response if part of it was already sent.
 *
 * <p>Each handler call runs on its own fiber and the platform waits for it before it goes on, so a
 * handler can wait on a binding or a {@code fetch}, as the platform's async handlers can. A call
 * costs a fiber, so register only the kinds you need: {@code on} with a class that implements one
 * interface registers one callback, and each extra interface adds one.
 *
 * {@snippet lang = "java":
 * Response page = HTMLRewriter.create()
 *     .on("a", link -> link.setAttribute("target", "_blank"))
 *     .on("body", body -> body.prepend("<p>hello</p>", ContentOptions.html()))
 *     .transform(origin);
 *}
 *
 * @since 1.0.3
 */
public interface HTMLRewriter extends JSObject {
	/** {@return a rewriter with no handlers} */
	static HTMLRewriter create() {
		return Hooks.rewriter();
	}

	/**
	 * Attaches a handler for each element the selector matches.
	 *
	 * <p>A handler that also implements {@link TextHandler} or {@link CommentHandler} gets the text
	 * and the comments inside each match as well.
	 *
	 * @param selector a CSS selector, as in the platform's list
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter on(String selector, ElementHandler handler) {
		return Hooks.on(
			this,
			selector,
			Hooks.element(handler),
			handler instanceof CommentHandler comments ? Hooks.comments(comments) : null,
			handler instanceof TextHandler text ? Hooks.text(text) : null
		);
	}

	/**
	 * Attaches a handler for each chunk of text inside what the selector matches.
	 *
	 * @param selector a CSS selector
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onText(String selector, TextHandler handler) {
		return Hooks.on(this, selector, null, null, Hooks.text(handler));
	}

	/**
	 * Attaches a handler for each comment inside what the selector matches.
	 *
	 * @param selector a CSS selector
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onComments(String selector, CommentHandler handler) {
		return Hooks.on(this, selector, null, Hooks.comments(handler), null);
	}

	/**
	 * Attaches a handler for the document's doctype.
	 *
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onDoctype(DoctypeHandler handler) {
		return Hooks.onDocument(this, Hooks.doctype(handler), null, null, null);
	}

	/**
	 * Attaches a handler for every comment in the document, outside the {@code html} tag included.
	 *
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onDocumentComments(CommentHandler handler) {
		return Hooks.onDocument(this, null, Hooks.comments(handler), null, null);
	}

	/**
	 * Attaches a handler for every chunk of text in the document, outside the {@code html} tag
	 * included.
	 *
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onDocumentText(TextHandler handler) {
		return Hooks.onDocument(this, null, null, Hooks.text(handler), null);
	}

	/**
	 * Attaches a handler for the end of the document, where content can still be appended.
	 *
	 * @param handler the handler
	 * @return this rewriter
	 */
	default HTMLRewriter onEnd(EndHandler handler) {
		return Hooks.onDocument(this, null, null, null, Hooks.end(handler));
	}

	/**
	 * Wraps a response so its body is rewritten as it is read.
	 *
	 * @param response the response to rewrite
	 * @return a response with the rewritten body, with the original's status and headers
	 */
	default Response transform(Response response) {
		return Hooks.transform(this, response);
	}
}
