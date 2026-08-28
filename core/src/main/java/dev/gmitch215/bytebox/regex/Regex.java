package dev.gmitch215.bytebox.regex;

import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSBody;

/**
 * The platform's own regular expressions.
 *
 * <p>Separate from {@link Pattern} and {@link Matcher} because those two are substitutes - the compiler
 * renames them to the classes they stand in for - and a renamed class loses the annotations that turn a
 * {@code native} method into a call into JavaScript.
 *
 * <p>Every expression is compiled with the flag that reports where each group matched, which is what
 * {@code Matcher.start} and {@code Matcher.end} need and the only way to get it.
 */
final class Regex implements Engine {

	static final Regex PLATFORM = new Regex();

	private Regex() {}

	@Override
	public Object compile(String source, String flags) {
		return newExpression(source, flags);
	}

	@Override
	public Object find(Object expression, String input, int from) {
		return exec((TSObject) expression, input, from);
	}

	@Override
	public int count(Object match) {
		return length((TSObject) match);
	}

	@Override
	public String group(Object match, int index) {
		return groupAt((TSObject) match, index);
	}

	@Override
	public String named(Object match, String name) {
		return groupNamed((TSObject) match, name);
	}

	@Override
	public int start(Object match, int index) {
		return startAt((TSObject) match, index);
	}

	@Override
	public int end(Object match, int index) {
		return endAt((TSObject) match, index);
	}

	@Override
	public int namedStart(Object match, String name) {
		return startNamed((TSObject) match, name);
	}

	@Override
	public int namedEnd(Object match, String name) {
		return endNamed((TSObject) match, name);
	}

	@JSBody(params = { "source", "flags" }, script = "return new RegExp(source, flags + 'dg');")
	private static native TSObject newExpression(String source, String flags);

	@JSBody(
		params = { "expression", "input", "from" },
		script = "expression.lastIndex = from; return expression.exec(input);"
	)
	private static native TSObject exec(TSObject expression, String input, int from);

	@JSBody(params = "match", script = "return match.length;")
	private static native int length(TSObject match);

	@JSBody(
		params = { "match", "index" },
		script = "var value = match[index]; return value === undefined ? null : value;"
	)
	private static native String groupAt(TSObject match, int index);

	@JSBody(
		params = { "match", "name" },
		script = "if (!match.groups) return null;" +
			"var value = match.groups[name]; return value === undefined ? null : value;"
	)
	private static native String groupNamed(TSObject match, String name);

	@JSBody(
		params = { "match", "index" },
		script = "var span = match.indices[index]; return span == null ? -1 : span[0];"
	)
	private static native int startAt(TSObject match, int index);

	@JSBody(
		params = { "match", "index" },
		script = "var span = match.indices[index]; return span == null ? -1 : span[1];"
	)
	private static native int endAt(TSObject match, int index);

	@JSBody(
		params = { "match", "name" },
		script = "if (!match.indices.groups) return -1;" +
			"var span = match.indices.groups[name]; return span == null ? -1 : span[0];"
	)
	private static native int startNamed(TSObject match, String name);

	@JSBody(
		params = { "match", "name" },
		script = "if (!match.indices.groups) return -1;" +
			"var span = match.indices.groups[name]; return span == null ? -1 : span[1];"
	)
	private static native int endNamed(TSObject match, String name);
}
