package dev.gmitch215.bytebox.regex;

import java.util.regex.PatternSyntaxException;

/**
 * A Java pattern rewritten as a JavaScript one.
 *
 * <p>The two engines share most of their syntax and disagree about a handful of things that look
 * identical, which is the whole difficulty: {@code \s} is six characters in Java and rather more in
 * JavaScript, {@code \v} is vertical whitespace in Java and a vertical tab in JavaScript, {@code .}
 * excludes {@code \u0085} in Java and does not in JavaScript, and {@code $} matches before a final line
 * terminator in Java and only at the end in JavaScript. Every one of those is rewritten to something
 * exact rather than passed through.
 *
 * <p>What cannot be rewritten exactly is refused, with a {@code PatternSyntaxException} naming it. That
 * is the rule this class is built on: a pattern either means here what it means on a JVM, or it does not
 * compile. A translator that quietly matched something slightly different would be worse than no
 * translator, because the difference would show up as a validation that passes when it should not.
 */
final class Translate {

	/** Java's line terminators, as an alternation, longest first so that a pair is not split. */
	private static final String TERMINATOR = "\\r\\n|[\\n\\r\\u0085\\u2028\\u2029]";

	private static final String TERMINATOR_UNIX = "\\n";

	/** The end of the input, written so that it means only that in both engines. */
	static final String END = "(?![\\s\\S])";

	/** Java's whitespace is these six characters, where JavaScript's is a much wider set. */
	private static final String SPACE = " \\t\\n\\x0B\\f\\r";

	/** Java's horizontal whitespace. */
	private static final String HORIZONTAL =
		" \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000";

	/** Java's vertical whitespace, which is not what {@code \v} means in JavaScript. */
	private static final String VERTICAL = "\\n\\x0B\\f\\r\\u0085\\u2028\\u2029";

	private final String pattern;
	private final int flags;
	private final boolean unixLines;
	private final boolean multiline;
	private final boolean dotAll;
	private final boolean comments;

	private final StringBuilder out = new StringBuilder();

	private int at;

	private Translate(String pattern, int flags) {
		this.pattern = pattern;
		this.flags = flags;
		this.unixLines = (flags & Pattern.UNIX_LINES) != 0;
		this.multiline = (flags & Pattern.MULTILINE) != 0;
		this.dotAll = (flags & Pattern.DOTALL) != 0;
		this.comments = (flags & Pattern.COMMENTS) != 0;
	}

	/**
	 * A Java pattern as a JavaScript one.
	 *
	 * @param pattern the Java pattern
	 * @param flags the Java flags
	 * @return the JavaScript source
	 * @throws PatternSyntaxException when the pattern uses something with no exact equivalent
	 */
	static String source(String pattern, int flags) {
		if ((flags & Pattern.LITERAL) != 0) return quote(pattern);
		return new Translate(pattern, flags).run();
	}

	/** {@return the JavaScript flags, which are only ever case folding plus what the API needs} */
	static String jsFlags(int flags) {
		return (flags & Pattern.CASE_INSENSITIVE) != 0 ? "i" : "";
	}

	/**
	 * A literal, escaped so that every character stands for itself.
	 *
	 * @param text the literal
	 * @return the escaped source
	 */
	static String quote(String text) {
		StringBuilder escaped = new StringBuilder();
		for (int index = 0; index < text.length(); index++) escape(escaped, text.charAt(index));
		return escaped.toString();
	}

	private String run() {
		while (at < pattern.length()) {
			char character = pattern.charAt(at);
			if (comments && skipComment(character)) continue;

			switch (character) {
				case '\\':
					escapeSequence();
					break;
				case '.':
					at++;
					out.append(dot());
					break;
				case '^':
					at++;
					out.append(lineStart());
					break;
				case '$':
					at++;
					out.append(lineEnd());
					break;
				case '[':
					characterClass();
					break;
				case '(':
					group();
					break;
				case '*':
				case '+':
				case '?':
					at++;
					out.append(character);
					quantifierSuffix();
					break;
				case '{':
					braces();
					break;
				default:
					at++;
					out.append(character);
					break;
			}
		}
		return out.toString();
	}

	// #region the pieces

	private String dot() {
		if (dotAll) return "[\\s\\S]";
		return unixLines ? "[^\\n]" : "[^\\n\\r\\u0085\\u2028\\u2029]";
	}

	/**
	 * Where a line starts.
	 *
	 * <p>Without {@code MULTILINE} that is only the start of the input, which JavaScript agrees about.
	 * With it, Java also counts the position after a terminator - and after the carriage return of a
	 * pair, which is one terminator rather than two, hence the guard.
	 */
	private String lineStart() {
		if (!multiline) return "^";
		if (unixLines) return "(?:^|(?<=\\n))";
		return "(?:^|(?<=[\\n\\u0085\\u2028\\u2029])|(?<=\\r)(?!\\n))";
	}

	/**
	 * Where a line ends.
	 *
	 * <p>Without {@code MULTILINE} Java matches at the end of the input and also just before a
	 * terminator that ends it, which JavaScript does not. With it, Java matches before any terminator.
	 */
	private String lineEnd() {
		if (!multiline) {
			return unixLines ? "(?=\\n?" + END + ")" : "(?=(?:" + TERMINATOR + ")?" + END + ")";
		}
		if (unixLines) return "(?=" + END + "|\\n)";
		return "(?=" + END + "|\\r|(?<!\\r)[\\n\\u0085\\u2028\\u2029])";
	}

	private void escapeSequence() {
		at++;
		if (at >= pattern.length()) throw refuse("a trailing backslash", at);
		char character = pattern.charAt(at++);
		switch (character) {
			case 'Q':
				out.append(quote(literalRun()));
				return;
			case 'E':
				throw refuse("\\E without \\Q", at - 1);
			case 's':
				out.append('[').append(SPACE).append(']');
				return;
			case 'S':
				out.append("[^").append(SPACE).append(']');
				return;
			case 'h':
				out.append('[').append(HORIZONTAL).append(']');
				return;
			case 'H':
				out.append("[^").append(HORIZONTAL).append(']');
				return;
			case 'v':
				out.append('[').append(VERTICAL).append(']');
				return;
			case 'V':
				out.append("[^").append(VERTICAL).append(']');
				return;
			case 'R':
				out.append("(?:")
					.append(unixLines ? TERMINATOR_UNIX : TERMINATOR)
					.append(')');
				return;
			case 'A':
				out.append('^');
				return;
			case 'z':
				// not $, which means the end of the input in one engine and rather more in the other
				out.append(END);
				return;
			case 'Z':
				out.append(
					unixLines ? "(?=\\n?" + END + ")" : "(?=(?:" + TERMINATOR + ")?" + END + ")"
				);
				return;
			case 'a':
				out.append("\\x07");
				return;
			case 'e':
				out.append("\\x1B");
				return;
			case 'p':
			case 'P':
				out.append(property(character == 'P'));
				return;
			case 'G':
				throw refuse("\\G, the end of the previous match", at - 1);
			case 'X':
				throw refuse("\\X, a grapheme cluster", at - 1);
			case 'N':
				throw refuse("\\N, a character by name", at - 1);
			case 'b':
				if (at < pattern.length() && pattern.charAt(at) == '{') {
					throw refuse("\\b{g}, a grapheme boundary", at);
				}
				out.append("\\b");
				return;
			default:
				out.append('\\').append(character);
				return;
		}
	}

	/** Everything up to {@code \E}, or to the end when there is none, exactly as Java reads it. */
	private String literalRun() {
		int end = pattern.indexOf("\\E", at);
		String text = end < 0 ? pattern.substring(at) : pattern.substring(at, end);
		at = end < 0 ? pattern.length() : end + 2;
		return text;
	}

	/**
	 * A named class, expanded to the characters it stands for.
	 *
	 * <p>Only the POSIX names, and only because each is a short explicit set. A Unicode category or
	 * script would need JavaScript's own property syntax, which needs the {@code u} flag, and that flag
	 * changes how the rest of the pattern is read - so those are refused instead.
	 */
	private String property(boolean negated) {
		if (at >= pattern.length() || pattern.charAt(at) != '{') {
			throw refuse("a one-letter character property", at);
		}
		int close = pattern.indexOf('}', at);
		if (close < 0) throw refuse("an unclosed character property", at);
		String name = pattern.substring(at + 1, close);
		at = close + 1;

		String set = posix(name);
		if (set == null) {
			throw refuse(
				"\\p{" +
					name +
					"}, which needs the platform's own property syntax and the flag that comes with it",
				at
			);
		}
		return (negated ? "[^" : "[") + set + "]";
	}

	private static String posix(String name) {
		switch (name) {
			case "Lower":
				return "a-z";
			case "Upper":
				return "A-Z";
			case "ASCII":
				return "\\x00-\\x7F";
			case "Alpha":
				return "a-zA-Z";
			case "Digit":
				return "0-9";
			case "Alnum":
				return "a-zA-Z0-9";
			case "Punct":
				return "!-\\/:-@\\[-`{-~";
			case "Graph":
				return "\\x21-\\x7E";
			case "Print":
				return "\\x20-\\x7E";
			case "Blank":
				return " \\t";
			case "Cntrl":
				return "\\x00-\\x1F\\x7F";
			case "XDigit":
				return "0-9a-fA-F";
			case "Space":
				return SPACE;
			default:
				return null;
		}
	}

	/**
	 * A character class, with the escapes inside it translated the same way.
	 *
	 * <p>A class holding another class, or two classes intersected, are Java's own additions to the
	 * syntax and have no equivalent, so they are refused rather than read as something else.
	 */
	private void characterClass() {
		int start = at;
		out.append('[');
		at++;
		if (at < pattern.length() && pattern.charAt(at) == '^') {
			out.append('^');
			at++;
		}
		if (at < pattern.length() && pattern.charAt(at) == ']') {
			out.append("\\]");
			at++;
		}

		while (at < pattern.length()) {
			char character = pattern.charAt(at);
			if (character == ']') {
				at++;
				out.append(']');
				return;
			}
			if (character == '[') throw refuse("a character class inside another", at);
			if (character == '&' && at + 1 < pattern.length() && pattern.charAt(at + 1) == '&') {
				throw refuse("&&, an intersection of character classes", at);
			}
			if (character == '\\') {
				classEscape();
				continue;
			}
			at++;
			out.append(character);
		}
		throw refuse("an unclosed character class", start);
	}

	/** The same escapes, without the brackets, since they are already inside a class. */
	private void classEscape() {
		at++;
		if (at >= pattern.length()) throw refuse("a trailing backslash", at);
		char character = pattern.charAt(at++);
		switch (character) {
			case 'Q':
				out.append(quote(literalRun()));
				return;
			case 's':
				out.append(SPACE);
				return;
			case 'h':
				out.append(HORIZONTAL);
				return;
			case 'v':
				out.append(VERTICAL);
				return;
			case 'a':
				out.append("\\x07");
				return;
			case 'e':
				out.append("\\x1B");
				return;
			case 'p':
				out.append(propertyInClass(false));
				return;
			case 'P':
				throw refuse("a negated property inside a character class", at - 1);
			case 'S':
			case 'H':
			case 'V':
				throw refuse(
					"\\" +
						character +
						" inside a character class, which no exact set can stand for",
					at - 1
				);
			case 'R':
			case 'X':
			case 'G':
			case 'N':
				throw refuse("\\" + character + " inside a character class", at - 1);
			default:
				out.append('\\').append(character);
				return;
		}
	}

	private String propertyInClass(boolean negated) {
		String expanded = property(negated);
		return expanded.substring(1, expanded.length() - 1);
	}

	/**
	 * A group, or one of the constructs written like one.
	 *
	 * <p>The flag constructs are the interesting case. A flag written at the very start of a pattern
	 * applies to all of it, which is a flag; written anywhere else it applies from there on, which
	 * nothing in JavaScript expresses, so it is refused.
	 */
	private void group() {
		if (at + 1 >= pattern.length() || pattern.charAt(at + 1) != '?') {
			at++;
			out.append('(');
			return;
		}

		char kind = at + 2 < pattern.length() ? pattern.charAt(at + 2) : 0;
		if (kind == ':' || kind == '=' || kind == '!') {
			out.append("(?").append(kind);
			at += 3;
			return;
		}
		if (kind == '<') {
			char after = at + 3 < pattern.length() ? pattern.charAt(at + 3) : 0;
			if (after == '=' || after == '!') {
				out.append("(?<").append(after);
				at += 4;
				return;
			}
			int close = pattern.indexOf('>', at + 3);
			if (close < 0) throw refuse("an unclosed group name", at);
			out.append("(?<")
				.append(pattern, at + 3, close)
				.append('>');
			at = close + 1;
			return;
		}
		if (kind == '>') throw refuse("(?>, an atomic group", at);
		throw inlineFlags();
	}

	/** Folded into the flags when it opens the pattern, refused anywhere else. */
	private PatternSyntaxException inlineFlags() {
		int close = pattern.indexOf(')', at);
		String written = close < 0 ? pattern.substring(at) : pattern.substring(at, close + 1);
		if (at != 0) {
			return refuse(written + ", a flag that applies from where it is written", at);
		}
		return refuse(
			written +
				", a flag written in the pattern; pass it to Pattern.compile as a flag instead",
			at
		);
	}

	/** A possessive quantifier is Java's own, and nothing in JavaScript stands for it. */
	private void quantifierSuffix() {
		if (at >= pattern.length()) return;
		char character = pattern.charAt(at);
		if (character == '?') {
			at++;
			out.append('?');
			return;
		}
		if (character == '+') throw refuse("a possessive quantifier", at);
	}

	/** A count, which is what a brace always starts: the runtime refuses one that is not a count. */
	private void braces() {
		int close = pattern.indexOf('}', at);
		String body = close < 0 ? null : pattern.substring(at + 1, close);
		if (body == null || !isCount(body)) {
			throw new PatternSyntaxException("Illegal repetition", pattern, at);
		}
		out.append('{').append(body).append('}');
		at = close + 1;
		quantifierSuffix();
	}

	private static boolean isCount(String body) {
		if (body.isEmpty()) return false;
		boolean seenComma = false;
		boolean seenDigit = false;
		for (int index = 0; index < body.length(); index++) {
			char character = body.charAt(index);
			if (character == ',') {
				if (seenComma || !seenDigit) return false;
				seenComma = true;
			} else if (character >= '0' && character <= '9') {
				seenDigit = true;
			} else {
				return false;
			}
		}
		return seenDigit;
	}

	/** Whitespace and a comment to the end of the line are not part of the pattern under {@code x}. */
	private boolean skipComment(char character) {
		if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
			at++;
			return true;
		}
		if (character != '#') return false;
		while (at < pattern.length() && pattern.charAt(at) != '\n') at++;
		return true;
	}

	// #endregion

	private static void escape(StringBuilder into, char character) {
		// the hyphen is here for the quoted run inside a character class, where a raw one would be
		// read as a range; outside a class the escape is a legal identity escape and means the same
		if ("\\^$.|?*+()[]{}/-".indexOf(character) >= 0) {
			into.append('\\').append(character);
		} else if (character == '\n') {
			into.append("\\n");
		} else if (character == '\r') {
			into.append("\\r");
		} else if (character < 0x20 || character == 0x7F) {
			into.append("\\x")
				.append(character < 0x10 ? "0" : "")
				.append(Integer.toHexString(character));
		} else {
			into.append(character);
		}
	}

	private PatternSyntaxException refuse(String what, int index) {
		return new PatternSyntaxException(
			"this platform's regular expressions cannot express " + what,
			pattern,
			Math.min(index, pattern.length())
		);
	}
}
