package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.html.Comment;
import dev.gmitch215.bytebox.html.CommentHandler;
import dev.gmitch215.bytebox.html.ContentOptions;
import dev.gmitch215.bytebox.html.Element;
import dev.gmitch215.bytebox.html.ElementHandler;
import dev.gmitch215.bytebox.html.HTMLRewriter;
import dev.gmitch215.bytebox.html.TextChunk;
import dev.gmitch215.bytebox.html.TextHandler;
import dev.gmitch215.bytebox.js.TSObject;

final class HtmlRoutes {

	private HtmlRoutes() {}

	private static final String DOC =
		"<!DOCTYPE html><html><head><title>t</title></head><body><!-- note --><a href=\"/x\">link</a></body></html>";

	static Response route(Request request, Env env) {
		String which = request.query("case");
		if (request.path().equals("/htmlreturn")) return returned(which, request.query("id", "0"));

		StringBuilder log = new StringBuilder();
		TSObject answer = TSObject.object();
		try {
			String html = input(which);
			HTMLRewriter rewriter = HTMLRewriter.create();
			configure(which, rewriter, log, env, request.query("sel", "*"));
			answer.set("body", TSObject.of(rewriter.transform(Bytebox.response(html, 200)).text()));
		} catch (Throwable failure) {
			answer.set("failed", TSObject.of(failure.getClass().getName()));
			answer.set("message", TSObject.of(String.valueOf(failure.getMessage())));
		}
		answer.set("log", TSObject.of(log.toString()));
		return Bytebox.json(answer);
	}

	private static String input(String which) {
		return switch (which) {
			case "attributes" -> "<a href=\"/x\" class=\"c\">l</a>";
			case "tag" -> "<b>x</b>";
			case "removal" -> "<p>a</p><i>gone</i><u>kept</u><s>old</s><p>b</p>";
			case "insert" -> "<div><p>x</p></div>";
			case "escape", "escapehtml", "inner", "innerhtml" -> "<p>x</p>";
			case "text", "textbefore" -> "<p>Hello world</p>";
			case "comments" -> "<div><!-- note --><!-- drop --></div>";
			case "document" -> "<!DOCTYPE html><html><body>x<!-- c --></body></html>";
			case "two" -> "<a class=\"x\">l</a>";
			case "both" -> "<div>t<!-- c --></div>";
			case "endtag", "endtagwait" -> "<p>hi</p><p>yo</p>";
			case "selectors" -> "<div id=\"a\" class=\"w\"><p>1</p><p>2</p></div><span>s</span>";
			case "select" -> "<div n=\"d\" id=\"a\" class=\"w x\" lang=\"en-US\" t=\"Bar baz\">" +
			"<p n=\"p1\">1</p><p n=\"p2\" t=\"BAR\">2</p><b n=\"b1\">3</b><p n=\"p3\" t=\"bar-x\">4</p>" +
			"</div><span n=\"s1\" class=\"w\">s</span><section n=\"sec\"><p n=\"p4\">5</p></section>";
			case "endtagops" -> "<p>a</p><i>b</i><u>c</u>";
			case "textops" -> "<p>ab</p><q>drop</q>";
			case "commentops" -> "<div><!--a--><!--b--><!--c--></div>";
			case "htmlall" -> "<div><p>x</p><i>y</i></div>";
			case "htmlmore" -> "<p>t<!--c--></p><q>z</q>";
			case "doctype" -> "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" " +
			"\"http://www.w3.org/TR/html4/strict.dtd\"><p>x</p>";
			default -> DOC;
		};
	}

	private static void configure(
		String which,
		HTMLRewriter rewriter,
		StringBuilder log,
		Env env,
		String selector
	) {
		ContentOptions html = ContentOptions.html();
		switch (which) {
			case "attributes" -> rewriter.on("a", a -> {
				log.append(a.getTagName()).append('|').append(a.getAttribute("href")).append('|');
				log.append(a.getAttribute("title")).append('|').append(a.hasAttribute("class"));
				log.append('|').append(a.hasAttribute("title")).append('|').append(a.attributes());
				log.append('|').append(a.getNamespaceURI());
				a.setAttribute("target", "_blank").removeAttribute("class");
				a.setAttribute("href", "/y");
			});
			case "tag" -> rewriter.on("b", b -> b.setTagName("strong"));
			case "removal" -> {
				rewriter.on("i", Element::remove);
				rewriter.on("i", i -> log.append("removed=").append(i.isRemoved()));
				rewriter.on("u", Element::removeAndKeepContent);
				rewriter.on("s", s -> s.replace("new"));
			}
			case "insert" -> rewriter.on("p", p -> {
				p.before("B");
				p.prepend("P");
				p.append("A");
				p.after("F");
			});
			case "escape" -> rewriter.on("p", p -> p.append("<b>&</b>"));
			case "escapehtml" -> rewriter.on("p", p -> p.append("<b>&</b>", html));
			case "inner" -> rewriter.on("p", p -> p.setInnerContent("<i>n</i>"));
			case "innerhtml" -> rewriter.on("p", p -> p.setInnerContent("<i>n</i>", html));
			case "text" -> rewriter.onText("p", t -> {
				log.append('[').append(t.getText()).append(':').append(t.isLastInTextNode());
				log.append(']');
				if (t.isLastInTextNode()) t.after("!", html);
			});
			case "textbefore" -> rewriter.onText("p", t -> {
				if (!t.getText().isEmpty()) t.replace(t.getText().toUpperCase());
			});
			case "comments" -> rewriter.onComments("div", c -> {
				log.append('[').append(c.getText()).append(']');
				if (c.getText().contains("drop")) c.remove();
				else c.setText(" edited ");
			});
			case "document" -> {
				rewriter.onDoctype(d -> {
					log.append(d.getName()).append('|').append(d.getPublicId()).append('|');
					log.append(d.getSystemId()).append(';');
				});
				rewriter.onDocumentComments(c -> log.append("comment:").append(c.getText()).append(';'));
				rewriter.onDocumentText(t -> {
					if (!t.getText().isEmpty()) log.append("text:").append(t.getText()).append(';');
				});
				rewriter.onEnd(e -> e.append("<footer>f</footer>", html));
			}
			case "two" -> {
				rewriter.on("a", a -> {
					log.append("a;");
					a.setAttribute("data-a", "1");
				});
				rewriter.on(".x", a -> {
					log.append("x;");
					a.setAttribute("data-x", "1");
				});
			}
			case "both" -> rewriter.on("div", new Both(log));
			case "endtag" -> rewriter.on("p", p -> p.onEndTag(end -> {
				log.append(end.getName()).append(';');
				end.before("!");
			}));
			case "endtagwait" -> rewriter.on("p", p -> p.onEndTag(end -> {
				Async.sleep(5);
				end.before("?");
			}));
			case "selectors" -> {
				rewriter.on("p:first-child", p -> p.setAttribute("data-first", "1"));
				rewriter.on("div > p:nth-child(2)", p -> p.setAttribute("data-second", "1"));
				rewriter.on(".w", d -> d.setAttribute("data-class", "1"));
				rewriter.on("#a", d -> d.setAttribute("data-id", "1"));
				rewriter.on("span:not(.w)", s -> s.setAttribute("data-not", "1"));
				rewriter.on("[id]", d -> d.setAttribute("data-attr", "1"));
			}
			case "select" -> rewriter.on(selector, e -> log.append(e.getAttribute("n")).append(','));
			case "endtagops" -> {
				rewriter.on("p", p -> p.onEndTag(end -> end.after("A")));
				rewriter.on("i", i -> i.onEndTag(end -> end.remove()));
				rewriter.on("u", u -> u.onEndTag(end -> end.setName("w")));
			}
			case "textops" -> {
				rewriter.onText("p", t -> {
					if (t.getText().isEmpty()) t.after("]");
					else t.before("[");
				});
				rewriter.onText("q", t -> {
					t.remove();
					log.append(t.isRemoved()).append(';');
				});
			}
			case "commentops" -> rewriter.onComments("div", c -> {
				switch (c.getText()) {
					case "a" -> c.before("[");
					case "b" -> {
						c.replace("B");
						log.append("removed=").append(c.isRemoved());
					}
					default -> c.after("]");
				}
			});
			case "htmlall" -> {
				rewriter.on("p", p -> {
					p.before("<u>1</u>", html);
					p.after("<u>2</u>", html);
					p.prepend("<u>3</u>", html);
					p.append("<u>4</u>", html);
				});
				rewriter.on("i", i -> i.replace("<s>r</s>", html));
			}
			case "htmlmore" -> {
				rewriter.onText("p", t -> {
					if (t.getText().isEmpty()) return;
					t.before("<b>", html);
					t.after("</b>", html);
				});
				rewriter.onComments("p", c -> {
					c.before("<i>", html);
					c.after("</i>", html);
				});
				rewriter.on("p", p -> p.onEndTag(end -> {
					end.before("<s>", html);
					end.after("<u>", html);
				}));
				rewriter.onText("q", t -> {
					if (!t.getText().isEmpty()) t.replace("<em>Z</em>", html);
				});
			}
			case "doctype" -> rewriter.onDoctype(d -> {
				log.append(d.getName()).append('|').append(d.getPublicId()).append('|');
				log.append(d.getSystemId());
			});
			case "throw" -> rewriter.on("a", a -> {
				throw new IllegalStateException("boom");
			});
			case "await" -> rewriter.on("a", a -> {
				Async.sleep(5);
				a.setAttribute("data-waited", "yes");
			});
			case "binding" -> rewriter.on("a", a -> {
				env.kv().put("html-seen", a.getAttribute("href"));
				a.setAttribute("data-kv", env.kv().get("html-seen"));
			});
			default -> {}
		}
	}

	private static int inflight;
	private static int peak;

	private static Response returned(String which, String id) {
		if (which.equals("peak")) return Bytebox.response(String.valueOf(peak));
		HTMLRewriter rewriter = HTMLRewriter.create();
		switch (which) {
			case "slow" -> rewriter.on("a", a -> {
				peak = Math.max(peak, ++inflight);
				Async.sleep(10);
				a.setAttribute("data-id", id);
				Async.sleep(10);
				a.append(id);
				inflight--;
			});
			case "throw" -> rewriter.on("a", a -> {
				throw new IllegalStateException("boom");
			});
			default -> rewriter.on("a", a -> a.setAttribute("data-id", id));
		}
		return rewriter.transform(Bytebox.response(DOC, 200));
	}

	private static final class Both implements ElementHandler, TextHandler, CommentHandler {

		private final StringBuilder log;

		Both(StringBuilder log) {
			this.log = log;
		}

		@Override
		public void element(Element element) {
			log.append("element;");
		}

		@Override
		public void text(TextChunk text) {
			if (!text.getText().isEmpty()) log.append("text:").append(text.getText()).append(';');
		}

		@Override
		public void comments(Comment comment) {
			log.append("comment:").append(comment.getText()).append(';');
		}
	}
}
