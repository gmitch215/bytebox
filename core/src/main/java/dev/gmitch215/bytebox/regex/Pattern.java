package dev.gmitch215.bytebox.regex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * A compiled pattern, standing in for {@code java.util.regex.Pattern}.
 *
 * <p>The compiler substitutes {@code java.util.regex.Pattern} for this one, so a project matching the
 * ordinary way works unchanged. Underneath it is the platform's own regular expressions, which the
 * isolate already has: the class library's pattern compiler and matcher measure 68 KB of WebAssembly
 * after compression, and V8 has an engine already.
 *
 * <p>The pattern is rewritten rather than passed through, because the two syntaxes agree about most
 * things and disagree about a few that look identical - {@link Translate} lists them. What has no exact
 * equivalent is refused at compile time with a {@code PatternSyntaxException} naming it, so a pattern
 * either means here what it means on a JVM or it does not compile. Refused: atomic groups, possessive
 * quantifiers, flags written inside the pattern, character-class intersection and nesting, {@code \G},
 * {@code \X}, and every character property except the POSIX names.
 *
 * <p>One difference that is not refused, because refusing case folding would be absurd:
 * {@code CASE_INSENSITIVE} on its own folds only ASCII on a JVM, and the platform's folding is aware of
 * the whole of Unicode. A pattern that relies on {@code A} not matching a non-ASCII letter with a
 * similar case mapping will behave differently. Adding {@code UNICODE_CASE} makes the two agree.
 *
 * @since 1.0.0
 */
public final class Pattern {

	/** Fold case when matching. */
	public static final int CASE_INSENSITIVE = 0x02;

	/** Treat whitespace and {@code #} comments in the pattern as nothing. */
	public static final int COMMENTS = 0x04;

	/** Let {@code ^} and {@code $} match at every line. */
	public static final int MULTILINE = 0x08;

	/** Treat the pattern as a literal. */
	public static final int LITERAL = 0x10;

	/** Let {@code .} match a line terminator. */
	public static final int DOTALL = 0x20;

	/** Fold case across the whole of Unicode, which the platform does anyway. */
	public static final int UNICODE_CASE = 0x40;

	/** Normalise before matching, which is refused. */
	public static final int CANON_EQ = 0x80;

	/** Treat only {@code \n} as a line terminator. */
	public static final int UNIX_LINES = 0x01;

	/** Make the predefined classes match across the whole of Unicode, which is refused. */
	public static final int UNICODE_CHARACTER_CLASS = 0x100;

	private final String pattern;
	private final int flags;
	private final String source;
	private final String jsFlags;
	private final List<String> names;

	private final Engine engine;

	private Object searching;
	private Object anchored;
	private Object leading;

	private Pattern(String pattern, int flags, Engine engine) {
		if ((flags & CANON_EQ) != 0) {
			throw new PatternSyntaxException(
				"this platform cannot normalise before matching, which CANON_EQ asks for",
				pattern,
				-1
			);
		}
		if ((flags & UNICODE_CHARACTER_CLASS) != 0) {
			throw new PatternSyntaxException(
				"this platform cannot widen the predefined classes to the whole of Unicode, which" +
					" UNICODE_CHARACTER_CLASS asks for",
				pattern,
				-1
			);
		}
		this.pattern = pattern;
		this.flags = flags;
		this.source = Translate.source(pattern, flags);
		this.jsFlags = Translate.jsFlags(flags);
		this.names = groupNames(this.source);
		// compiled here rather than lazily, so that a source the platform will not take is refused now
		this.engine = engine;
		this.searching = engine.compile(this.source, this.jsFlags);
	}

	/**
	 * Compiles a pattern.
	 *
	 * @param pattern the pattern
	 * @return the compiled pattern
	 * @throws PatternSyntaxException when the pattern is not one, or uses something with no exact
	 *     equivalent here
	 */
	public static Pattern compile(String pattern) {
		return new Pattern(pattern, 0, Regex.PLATFORM);
	}

	/**
	 * Compiles a pattern with flags.
	 *
	 * @param pattern the pattern
	 * @param flags the flags, combined with {@code |}
	 * @return the compiled pattern
	 * @throws PatternSyntaxException when the pattern is not one, or uses something with no exact
	 *     equivalent here
	 */
	public static Pattern compile(String pattern, int flags) {
		return new Pattern(pattern, flags, Regex.PLATFORM);
	}

	/** Over a given engine, which is how the test lane runs without the platform. */
	static Pattern compile(String pattern, int flags, Engine engine) {
		return new Pattern(pattern, flags, engine);
	}

	/**
	 * Whether a whole input matches a pattern, compiling it each time.
	 *
	 * @param pattern the pattern
	 * @param input what to match
	 * @return whether it matches
	 */
	public static boolean matches(String pattern, CharSequence input) {
		return compile(pattern).matcher(input).matches();
	}

	/**
	 * A pattern that matches a literal.
	 *
	 * @param text the literal
	 * @return a pattern matching exactly it
	 */
	public static String quote(String text) {
		return "\\Q" + text.replace("\\E", "\\E\\\\E\\Q") + "\\E";
	}

	/**
	 * A matcher over an input.
	 *
	 * @param input what to search
	 * @return the matcher
	 */
	public Matcher matcher(CharSequence input) {
		return new Matcher(this, input.toString());
	}

	/** {@return the pattern as written} */
	public String pattern() {
		return pattern;
	}

	/** {@return the flags it was compiled with} */
	public int flags() {
		return flags;
	}

	/** {@return the pattern as written} */
	@Override
	public String toString() {
		return pattern;
	}

	/**
	 * Splits an input around every match.
	 *
	 * @param input what to split
	 * @return the pieces
	 */
	public String[] split(CharSequence input) {
		return split(input, 0);
	}

	/**
	 * Splits an input around every match.
	 *
	 * @param input what to split
	 * @param limit how many pieces at most, zero for no limit with trailing empties dropped, negative
	 *     for no limit with them kept
	 * @return the pieces
	 */
	public String[] split(CharSequence input, int limit) {
		String text = input.toString();
		List<String> pieces = new ArrayList<>();
		Matcher matcher = matcher(text);
		int from = 0;
		while (matcher.find()) {
			if (limit > 0 && pieces.size() == limit - 1) break;
			// a zero-width match at the start of a non-empty input produces no leading empty piece
			if (matcher.end() == 0 && matcher.start() == 0 && !text.isEmpty()) continue;
			if (from == matcher.start() && matcher.start() == matcher.end() && from == 0) continue;
			pieces.add(text.substring(from, matcher.start()));
			from = matcher.end();
		}
		if (pieces.isEmpty() && limit == 0) return new String[] { text };
		pieces.add(text.substring(from));
		if (limit == 0) {
			while (!pieces.isEmpty() && pieces.get(pieces.size() - 1).isEmpty()) {
				pieces.remove(pieces.size() - 1);
			}
		}
		return pieces.toArray(new String[0]);
	}

	/** {@return a predicate answering whether the pattern is found anywhere in a string} */
	public Predicate<String> asPredicate() {
		return text -> matcher(text).find();
	}

	/** {@return a predicate answering whether the pattern matches a whole string} */
	public Predicate<String> asMatchPredicate() {
		return text -> matcher(text).matches();
	}

	/**
	 * The pieces an input splits into, as a stream.
	 *
	 * @param input what to split
	 * @return the pieces
	 */
	public Stream<String> splitAsStream(CharSequence input) {
		return Stream.of(split(input, -1));
	}

	/** {@return every named group, in the order the pattern declares them} */
	public Map<String, Integer> namedGroups() {
		Map<String, Integer> found = new LinkedHashMap<>();
		for (int index = 0; index < names.size(); index++) found.put(names.get(index), index + 1);
		return found;
	}

	// #region what the matcher needs

	Engine engine() {
		return engine;
	}

	Object searching() {
		return searching;
	}

	Object anchored() {
		if (anchored == null) {
			anchored = engine.compile("^(?:" + source + ")" + Translate.END, jsFlags);
		}
		return anchored;
	}

	Object leading() {
		if (leading == null) leading = engine.compile("^(?:" + source + ")", jsFlags);
		return leading;
	}

	List<String> names() {
		return names;
	}

	/**
	 * Read from the translated source rather than from the pattern as written, because that is what the
	 * groups in the compiled expression are numbered by.
	 */
	private static List<String> groupNames(String source) {
		List<String> found = new ArrayList<>();
		for (int at = 0; at + 3 < source.length(); at++) {
			if (source.charAt(at) != '(' || source.charAt(at + 1) != '?') continue;
			if (source.charAt(at + 2) != '<') continue;
			char first = source.charAt(at + 3);
			if (first == '=' || first == '!') continue;
			int close = source.indexOf('>', at + 3);
			if (close > 0) found.add(source.substring(at + 3, close));
		}
		return found;
	}

	// #endregion
}
