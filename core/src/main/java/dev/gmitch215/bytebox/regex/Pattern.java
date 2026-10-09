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
 * equivalent is refused by {@code Pattern.compile}, when the program runs, with a
 * {@code PatternSyntaxException} naming it, so a pattern either means here what it means on a JVM or
 * it does not compile. Refused: a flag written anywhere
 * but the start of the pattern, {@code \G}, {@code \X}, and every character property except the POSIX
 * names and the Unicode blocks of the basic multilingual plane.
 *
 * <p>A flag group that opens the pattern, such as {@code (?i)}, is read as the flags it sets, and
 * {@link #flags()} reports them the way a JVM does.
 *
 * <p>An atomic group, {@code (?>X)}, and a possessive quantifier, {@code X*+}, are written as a
 * lookahead that captures what the atom takes and a backreference to it, which matches the same text
 * and cannot be backtracked into. The group numbers, names and replacement references a caller uses
 * are the ones written in the pattern. Four things are refused. Inside a lookbehind they are
 * refused, because the platform reads a lookbehind backwards. A backreference to a group that is not
 * open yet is refused, because a JVM never matches it and the platform would. A backreference inside
 * the group it names, as in {@code (a\1)}, is refused: a JVM reads that group as not closed, so the
 * reference never matches on the first pass and reads the previous pass's text on later ones, where
 * the platform reads it as the empty string. And a capturing group
 * inside the atom is refused, because after an attempt that took the atom and was abandoned a JVM
 * keeps the span the group had there where the platform has none. A group beside the atom or around
 * it is read as written. Neither construct makes a pattern cheaper to run: the engine still
 * backtracks through everything else, and a pattern that backtracks badly runs far slower here than
 * on a JVM.
 *
 * <p>A class inside a class, {@code [a[b]]}, an intersection, {@code [a-z&&[^b]]}, a negated
 * block, {@code \P{InGreek}}, and a character above U+FFFF inside a class, written as itself or by
 * {@code \x{1F600}}, are written for the {@code v} flag, which reads the pattern as code points
 * where the platform otherwise reads UTF-16 units, so the flag is used for a pattern that has one of
 * them and for no other. Without it a negated block would match each half of a character above
 * U+FFFF by itself. Such a pattern is refused together with {@code UNICODE_CASE}, where the flag
 * would change how case folds. A JVM also starts a search in the middle of a surrogate pair: after
 * an empty match, from {@code find(int)}, and at every UTF-16 unit when nothing in the pattern can
 * take half of a pair. An engine that reads code points cannot start there, so the matcher makes
 * that try itself on the text from the middle of the pair, and finds what a JVM finds. A hyphen, an
 * ampersand or an escape that a JVM reads in a way no exact rewrite follows is refused, naming it.
 *
 * <p>The escapes that stand for one character are read as a JVM reads them and written out plainly:
 * {@code \x{h...h}}, {@code \0n}, {@code \0nn}, {@code \0mnn} and {@code \cX}, which the platform
 * reads as something else or not at all, and a surrogate pair written as two {@code u} escapes. One a
 * JVM refuses (a malformed hexadecimal or octal escape, a letter after a backslash that means
 * nothing, {@code \k} without a group of that name) is refused. Under {@code COMMENTS} a JVM skips
 * whitespace and comments wherever it reads the next character of such an escape, so whitespace
 * inside one is refused.
 *
 * <p>A pattern for the platform's own mode reads UTF-16 units, so {@code .}, a negated class such as
 * {@code [^x]}, the escapes that mean everything but something ({@code \S}, {@code \H}, {@code \V},
 * {@code \W}, {@code \D}, {@code \P{Alpha}}), a class that reaches the surrogates and a lone
 * surrogate would take half of a character above U+FFFF where a JVM takes the whole. Over text with
 * no such character they are exact, and over text with one, {@link #matcher} makes the matcher run a
 * second source of the same pattern written for the {@code v} flag, with the caveats of that flag
 * above. Under {@code UNICODE_CASE} there is no second source, so {@code matcher} refuses text that
 * holds such a character, with an {@code UnsupportedOperationException} naming the construct, when
 * the pattern has one of them. A lookbehind holding one of them is sized by a JVM in UTF-16 units, so
 * a lookbehind of several characters can differ over such text.
 *
 * <p>{@code CASE_INSENSITIVE} on its own folds only ASCII, as it does on a JVM: each letter is written
 * as the pair of its cases, and the platform's folding, which reaches across the whole of Unicode, is
 * not used. A backreference cannot be written that way, since it stands for whatever its group took,
 * so a pattern with one is refused under {@code CASE_INSENSITIVE} without {@code UNICODE_CASE}. A
 * hyphen beside a set or a finished range inside a class cannot be written as a pair and keeps the
 * platform's folding, so a non-ASCII letter folds there where a JVM's does not.
 *
 * <p>With {@code UNICODE_CASE} the platform's folding is used. It agrees with a JVM except for a few
 * dozen characters: the long s, the Kelvin, ohm and angstrom signs, the dotted and dotless i, the
 * capital sharp s, the capital theta symbol, and the Greek letters with an iota subscript and their
 * titlecase forms. A JVM folds each of those to its partner and the platform does not.
 *
 * @since 1.0.0
 */
public final class Pattern {

	/** Fold case when matching, across ASCII unless {@link #UNICODE_CASE} is set too. */
	public static final int CASE_INSENSITIVE = 0x02;

	/** Treat whitespace and {@code #} comments in the pattern as nothing. */
	public static final int COMMENTS = 0x04;

	/** Let {@code ^} and {@code $} match at every line. */
	public static final int MULTILINE = 0x08;

	/** Treat the pattern as a literal. */
	public static final int LITERAL = 0x10;

	/** Let {@code .} match a line terminator. */
	public static final int DOTALL = 0x20;

	/** Fold case across the whole of Unicode, with the platform's own folding. */
	public static final int UNICODE_CASE = 0x40;

	/** Normalise before matching, which is refused. */
	public static final int CANON_EQ = 0x80;

	/** Treat only {@code \n} as a line terminator. */
	public static final int UNIX_LINES = 0x01;

	/** Make the predefined classes match across the whole of Unicode, which is refused. */
	public static final int UNICODE_CHARACTER_CLASS = 0x100;

	private final String pattern;
	private final int flags;
	private final Translate.Groups groups;

	private final Engine engine;

	private final Form units;

	/** The same pattern for an input with a character above U+FFFF, or null when it needs none. */
	private final Form codePoints;

	/** What makes the pattern need {@link #codePoints}, or null. */
	private final String sensitive;

	/** Why there is no {@link #codePoints} when the pattern needs one. */
	private final String refusal;

	private Pattern(String pattern, int flags, Engine engine) {
		Translate.Result translated = Translate.translate(pattern, flags);
		this.pattern = pattern;
		this.flags = translated.flags;
		this.groups = translated.groups;
		this.engine = engine;
		this.units = new Form(
			engine,
			translated.source,
			translated.jsFlags,
			translated.midSource,
			translated.unitScan
		);
		this.codePoints =
			translated.codePointSource == null
				? null
				: new Form(
						engine,
						translated.codePointSource,
						"v",
						translated.codePointMidSource,
						translated.unitScan
					);
		this.sensitive = translated.sensitive;
		this.refusal = translated.codePointRefusal;
		// compiled here rather than lazily, so that a source the platform will not take is refused now
		try {
			units.searching();
		} catch (PatternSyntaxException refused) {
			throw refused;
		} catch (RuntimeException refused) {
			throw new PatternSyntaxException(String.valueOf(refused.getMessage()), pattern, -1);
		}
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
	 * @throws UnsupportedOperationException when the input holds a character above U+FFFF and the
	 *     pattern holds something that would read half of one, with {@code UNICODE_CASE}
	 */
	public Matcher matcher(CharSequence input) {
		return new Matcher(this, input.toString());
	}

	/** {@return the pattern as written} */
	public String pattern() {
		return pattern;
	}

	/** {@return the flags it was compiled with, with any flag group that opens the pattern applied} */
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
		return new LinkedHashMap<>(groups.names);
	}

	// #region what the matcher needs

	Engine engine() {
		return engine;
	}

	/**
	 * The source to run over an input. A pattern that reads UTF-16 units is exact unless the input holds a
	 * character above U+FFFF, and then the one for the {@code v} flag is used.
	 *
	 * @throws UnsupportedOperationException when the input holds such a character and the pattern has no
	 *     source that is exact over it
	 */
	Form form(String input) {
		if (sensitive == null || !hasPair(input)) return units;
		if (codePoints == null) {
			throw new UnsupportedOperationException(
				refusal + " (the pattern is " + pattern + " and the input has such a character)"
			);
		}
		return codePoints;
	}

	private static boolean hasPair(String input) {
		for (int index = 0; index + 1 < input.length(); index++) {
			if (
				Character.isHighSurrogate(input.charAt(index)) &&
				Character.isLowSurrogate(input.charAt(index + 1))
			) {
				return true;
			}
		}
		return false;
	}

	/** The groups the caller wrote, and the number the platform gave each. */
	Translate.Groups groups() {
		return groups;
	}

	/** One source for the platform, compiled when it is first needed. */
	static final class Form {

		private final Engine engine;
		private final String source;
		private final String flags;
		private final String mid;
		private final boolean unitScan;
		private Object searching;
		private Object anchored;
		private Object leading;
		private Object inside;

		Form(Engine engine, String source, String flags, String mid, boolean unitScan) {
			this.engine = engine;
			this.source = source;
			this.flags = flags;
			this.mid = mid;
			this.unitScan = unitScan;
		}

		Object searching() {
			if (searching == null) searching = engine.compile(source, flags);
			return searching;
		}

		Object anchored() {
			if (anchored == null) {
				anchored = engine.compile("^(?:" + source + ")" + Translate.END, flags);
			}
			return anchored;
		}

		Object leading() {
			if (leading == null) leading = engine.compile("^(?:" + source + ")", flags);
			return leading;
		}

		/** The match at the start of an input that begins with the second half of a pair. */
		Object inside() {
			if (inside == null) {
				inside = engine.compile("^(?:" + (mid == null ? source : mid) + ")", flags);
			}
			return inside;
		}

		/** Whether the platform reads this as code points, which is what the {@code v} flag does. */
		boolean unicode() {
			return flags.indexOf('v') >= 0;
		}

		/** Whether a JVM tries the middle of every pair when it looks for the next match. */
		boolean unitScan() {
			return unitScan;
		}
	}

	// #endregion
}
