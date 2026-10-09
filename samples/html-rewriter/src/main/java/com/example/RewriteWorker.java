package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.html.ContentOptions;
import dev.gmitch215.bytebox.html.HTMLRewriter;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * Rewrites a page that lives in the Worker, so it needs no network.
 *
 * <p>{@code /} opens links in a new tab, injects a banner and appends a footer. {@code /text}
 * rewrites words in the text, which the parser hands over in chunks, so a handler collects the
 * chunks of a text node and writes the result when the last one arrives.
 */
public class RewriteWorker implements Worker {

	private static final String PAGE = """
	<!DOCTYPE html>
	<html>
	<head><title>Links</title></head>
	<body>
	<h1>Reading list</h1>
	<p>The <a href="https://example.com/a">first</a> and the <a href="/b">second</a> link.</p>
	<p class="note">A cat sat on the mat. The cat stayed.</p>
	</body>
	</html>
	""";

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return switch (request.path()) {
			case "/" -> links(page());
			case "/text" -> words(page());
			default -> Bytebox.response("not found", 404);
		};
	}

	private static Response page() {
		TSObject headers = TSObject.object();
		headers.set("Content-Type", TSObject.of("text/html; charset=utf-8"));
		return Bytebox.response(PAGE, 200, headers);
	}

	private static Response links(Response page) {
		ContentOptions html = ContentOptions.html();
		return HTMLRewriter.create()
			.on("a[href]", link -> {
				link.setAttribute("target", "_blank");
				link.setAttribute("rel", "noopener");
			})
			.on("body", body -> body.prepend("<aside>Rewritten in Java</aside>", html))
			.onEnd(end -> end.append("<!-- served by bytebox -->", html))
			.transform(page);
	}

	private static Response words(Response page) {
		StringBuilder node = new StringBuilder();
		return HTMLRewriter.create()
			.onText("p.note", chunk -> {
				node.append(chunk.getText());
				if (!chunk.isLastInTextNode()) {
					chunk.remove();
					return;
				}
				chunk.replace(node.toString().replace("cat", "dog"));
				node.setLength(0);
			})
			.transform(page);
	}
}
