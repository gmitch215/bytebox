package dev.gmitch215.bytebox.html;

import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Async;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArrayReader;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

/**
 * What the public types share. Each functor is what the platform calls, and each returns the
 * promise of a fiber: a Java call made straight from the platform's JavaScript frame has no fiber
 * to run on and traps, so the handler is started on its own and the platform waits for the promise.
 */
final class Hooks {

	private Hooks() {}

	@JSFunctor
	interface ElementFn extends JSObject {
		JSPromise<JSObject> call(Element element);
	}

	@JSFunctor
	interface TextFn extends JSObject {
		JSPromise<JSObject> call(TextChunk text);
	}

	@JSFunctor
	interface CommentFn extends JSObject {
		JSPromise<JSObject> call(Comment comment);
	}

	@JSFunctor
	interface DoctypeFn extends JSObject {
		JSPromise<JSObject> call(Doctype doctype);
	}

	@JSFunctor
	interface EndFn extends JSObject {
		JSPromise<JSObject> call(DocumentEnd end);
	}

	@JSFunctor
	interface EndTagFn extends JSObject {
		JSPromise<JSObject> call(EndTag endTag);
	}

	static JSPromise<JSObject> fiber(Runnable work) {
		return Async.run(work).promise();
	}

	static ElementFn element(ElementHandler handler) {
		return element -> fiber(() -> handler.element(element));
	}

	static TextFn text(TextHandler handler) {
		return text -> fiber(() -> handler.text(text));
	}

	static CommentFn comments(CommentHandler handler) {
		return comment -> fiber(() -> handler.comments(comment));
	}

	static DoctypeFn doctype(DoctypeHandler handler) {
		return doctype -> fiber(() -> handler.doctype(doctype));
	}

	static EndFn end(EndHandler handler) {
		return end -> fiber(() -> handler.end(end));
	}

	static EndTagFn endTag(EndTagHandler handler) {
		return endTag -> fiber(() -> handler.handle(endTag));
	}

	@JSBody(script = "return new HTMLRewriter();")
	static native HTMLRewriter rewriter();

	@JSBody(params = "html", script = "return { html: html };")
	static native ContentOptions options(boolean html);

	@JSBody(
		params = { "rewriter", "selector", "element", "comments", "text" },
		script = "var h = {};" +
			" if (element) h.element = element;" +
			" if (comments) h.comments = comments;" +
			" if (text) h.text = text;" +
			" return rewriter.on(selector, h);"
	)
	static native HTMLRewriter on(
		HTMLRewriter rewriter,
		String selector,
		ElementFn element,
		CommentFn comments,
		TextFn text
	);

	@JSBody(
		params = { "rewriter", "doctype", "comments", "text", "end" },
		script = "var h = {};" +
			" if (doctype) h.doctype = doctype;" +
			" if (comments) h.comments = comments;" +
			" if (text) h.text = text;" +
			" if (end) h.end = end;" +
			" return rewriter.onDocument(h);"
	)
	static native HTMLRewriter onDocument(
		HTMLRewriter rewriter,
		DoctypeFn doctype,
		CommentFn comments,
		TextFn text,
		EndFn end
	);

	@JSBody(params = { "rewriter", "response" }, script = "return rewriter.transform(response);")
	static native Response transform(HTMLRewriter rewriter, Response response);

	@JSBody(params = { "element", "handler" }, script = "element.onEndTag(handler);")
	static native void onEndTag(Element element, EndTagFn handler);

	@JSBody(
		params = "element",
		script = "var pairs = Array.from(element.attributes); var flat = [];" +
			" for (var i = 0; i < pairs.length; i++) { flat.push(pairs[i][0]); flat.push(pairs[i][1]); }" +
			" return flat;"
	)
	private static native JSArrayReader<JSString> flatAttributes(Element element);

	static Map<String, String> attributes(Element element) {
		JSArrayReader<JSString> flat = flatAttributes(element);
		Map<String, String> attributes = new LinkedHashMap<>();
		for (int i = 0; i < flat.getLength(); i += 2) {
			attributes.put(flat.get(i).stringValue(), flat.get(i + 1).stringValue());
		}
		return attributes;
	}
}
