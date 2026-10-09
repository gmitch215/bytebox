package dev.gmitch215.bytebox.regex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * <p>Case folding is one of them. Without {@code UNICODE_CASE} a JVM folds only ASCII and the platform
 * folds all of Unicode, so each ASCII letter is written as the pair of its cases and the platform's
 * flag is left off.
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
	private final boolean unixLines;
	private final boolean multiline;
	private final boolean dotAll;
	private final boolean comments;
	private final boolean fold;

	/** Whether case is folded as ASCII only, which is what a JVM does without UNICODE_CASE. */
	private final boolean asciiCase;

	/** Whether the result is for the {@code v} flag, which a class inside a class needs. */
	private final boolean unicodeSets;

	private StringBuilder out = new StringBuilder();

	private int at;

	/** The state of the character class being read when {@code fold} is on. */
	private boolean[] covered = new boolean[52];

	private int previous;
	private boolean open;
	private int elements;

	/** Where each atom a possessive quantifier follows begins, found on the first pass. */
	private final Set<Integer> possessive;

	private final Set<Integer> found = new HashSet<>();

	/** The platform's number for the group added around each possessive atom, by where it begins. */
	private final Map<Integer, Integer> wrapped = new HashMap<>();

	private final ArrayDeque<Scope> scopes = new ArrayDeque<>();

	/** The platform's number for each group the caller can see, by the caller's number. */
	private final List<Integer> jsNumber = new ArrayList<>(List.of(0));

	private final Map<String, Integer> names = new LinkedHashMap<>();

	private int jsCount;

	/** Where the atom a quantifier would apply to begins in the pattern. */
	private int atom = -1;

	private int behind;

	/** The first construct that reads half of a character above U+FFFF where a JVM reads it whole. */
	private String sensitive;

	/** The last single-character escape as it was written, when the platform reads it the same way. */
	private String plain;

	/** Whether the result is for a search that begins inside a surrogate pair, where no start anchor holds. */
	private final boolean midPair;

	/** Whether the pattern has a start anchor, which a search inside a pair needs written otherwise. */
	private boolean anchored;

	/**
	 * Whether a JVM would mark the pattern as one that can take half of a surrogate pair, which makes its
	 * search skip the middle of each pair: a character above U+FFFF, a surrogate, a negation, a property or
	 * a class that reaches the surrogates, where a dot alone does not.
	 */
	private boolean wide;

	private Translate(
		String pattern,
		int flags,
		int start,
		boolean fold,
		boolean unicodeSets,
		Set<Integer> possessive,
		boolean midPair
	) {
		this.midPair = midPair;
		for (int index = 0; index < pattern.length() && !wide; index++) {
			wide = Character.isSurrogate(pattern.charAt(index));
		}
		this.pattern = pattern;
		this.unixLines = (flags & Pattern.UNIX_LINES) != 0;
		this.multiline = (flags & Pattern.MULTILINE) != 0;
		this.dotAll = (flags & Pattern.DOTALL) != 0;
		this.comments = (flags & Pattern.COMMENTS) != 0;
		this.asciiCase =
			(flags & Pattern.CASE_INSENSITIVE) != 0 && (flags & Pattern.UNICODE_CASE) == 0;
		this.fold = fold;
		this.unicodeSets = unicodeSets;
		this.at = start;
		this.possessive = possessive;
	}

	/** A pattern and flags as the platform takes them. */
	static final class Result {

		/** The JavaScript source. */
		final String source;

		/** The JavaScript flags, which are only ever case folding. */
		final String jsFlags;

		/** The flags the pattern came to, the caller's with a leading {@code (?flags)} folded in. */
		final int flags;

		/** The groups the caller sees, and the platform's number for each. */
		final Groups groups;

		/**
		 * What in the pattern reads half of a character above U+FFFF where a JVM reads it whole, such as
		 * {@code .}, or null when the source is exact over any input. A source for the {@code v} flag
		 * never has one, because that flag reads code points.
		 */
		final String sensitive;

		/** The source for the {@code v} flag, which is exact over an input with such a character. */
		final String codePointSource;

		/** Why there is no {@link #codePointSource}, when {@link #sensitive} is set and there is not. */
		final String codePointRefusal;

		/**
		 * The {@link #source} for a search that begins inside a surrogate pair, when it is for the {@code v}
		 * flag and the pattern has a start anchor, which is not true there. Null otherwise.
		 */
		final String midSource;

		/** The same for the {@link #codePointSource}. */
		final String codePointMidSource;

		/**
		 * Whether a JVM tries every UTF-16 unit as the start of a match for a source for the {@code v} flag
		 * (this one, or the {@link #codePointSource}), which is so while nothing in the pattern can take half
		 * of a surrogate pair. The flag's engine skips the middle of a pair, so each one is tried by hand.
		 */
		final boolean unitScan;

		private Result(
			String source,
			String jsFlags,
			int flags,
			Groups groups,
			String sensitive,
			String codePointSource,
			String codePointRefusal,
			String midSource,
			String codePointMidSource,
			boolean unitScan
		) {
			this.source = source;
			this.jsFlags = jsFlags;
			this.flags = flags;
			this.groups = groups;
			this.sensitive = sensitive;
			this.codePointSource = codePointSource;
			this.codePointRefusal = codePointRefusal;
			this.midSource = midSource;
			this.codePointMidSource = codePointMidSource;
			this.unitScan = unitScan;
		}
	}

	/**
	 * The groups of a pattern as the caller numbers them, and the platform's number for each.
	 *
	 * <p>The two differ when an atomic group or a possessive quantifier is written as a lookahead that
	 * captures: that capture is a group the caller never wrote, and it shifts the number of every group
	 * opened after it.
	 */
	static final class Groups {

		static final Groups NONE = new Groups(new int[] { 0 }, Collections.emptyMap());

		private final int[] js;

		/** The caller's number for each name. */
		final Map<String, Integer> names;

		private Groups(int[] js, Map<String, Integer> names) {
			this.js = js;
			this.names = names;
		}

		/** {@return how many groups the caller wrote} */
		int count() {
			return js.length - 1;
		}

		/**
		 * @param group a group by the caller's number, zero for the whole match
		 * @return the number the platform gave it
		 */
		int js(int group) {
			return js[group];
		}
	}

	/**
	 * An open group, with the number of the capture added for it when it is atomic and the number the
	 * caller gives it when it captures.
	 */
	private static final class Scope {

		final int start;
		final boolean lookbehind;
		final int synthetic;
		final int group;

		Scope(int start, boolean lookbehind, int synthetic, int group) {
			this.start = start;
			this.lookbehind = lookbehind;
			this.synthetic = synthetic;
			this.group = group;
		}
	}

	/** Thrown from inside a translation that cannot fold case exactly, so it is done again without. */
	private static final class Unfoldable extends RuntimeException {

		Unfoldable() {
			super("not exact under ASCII case folding");
		}
	}

	/** Thrown on a construct that needs the {@code v} flag, so the translation is done again for it. */
	private static final class NeedsUnicodeSets extends RuntimeException {

		final String what;

		NeedsUnicodeSets(String what) {
			super("needs the v flag");
			this.what = what;
		}
	}

	/**
	 * A Java pattern as a JavaScript one.
	 *
	 * <p>Flags written at the very start of the pattern are folded into the caller's flags first, the
	 * way {@code java.util.regex} reads them. Case folding without {@code UNICODE_CASE} is ASCII only on
	 * a JVM, and the platform's own folding is not, so the letters are written out as pairs and the
	 * platform's flag is left off. A backreference cannot be written that way and the platform's own
	 * folding is not a JVM's, so a pattern with one is refused. A hyphen beside a set or a finished
	 * range inside a class cannot be written as a pair, and keeps the platform's folding.
	 *
	 * <p>The escapes that stand for one character ({@code \x}, the four-digit one with {@code u},
	 * {@code \0} and {@code \c}) are read as a JVM reads them and written out as plain ones, so the
	 * platform's different reading of them never comes into it.
	 *
	 * <p>What reads UTF-16 units where a JVM reads code points ({@code .}, a negated class, {@code \S}
	 * and the like) is exact over an input with no character above U+FFFF, and the result carries a
	 * second source for the {@code v} flag, which is exact over one that has.
	 *
	 * @param pattern the Java pattern
	 * @param flags the Java flags
	 * @return the JavaScript source and flags, and the flags the pattern came to
	 * @throws PatternSyntaxException when the pattern uses something with no exact equivalent
	 */
	static Result translate(String pattern, int flags) {
		int merged = flags;
		int start = 0;
		if ((flags & Pattern.LITERAL) == 0) {
			int[] leading = leadingFlags(pattern, flags);
			merged = leading[0];
			start = leading[1];
		}
		if ((merged & Pattern.CANON_EQ) != 0) {
			throw new PatternSyntaxException(
				"this platform cannot normalise before matching, which CANON_EQ asks for",
				pattern,
				-1
			);
		}
		if ((merged & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
			throw new PatternSyntaxException(
				"this platform cannot widen the predefined classes to the whole of Unicode, which" +
					" UNICODE_CHARACTER_CLASS asks for",
				pattern,
				-1
			);
		}

		boolean insensitive = (merged & Pattern.CASE_INSENSITIVE) != 0;
		boolean ascii = insensitive && (merged & Pattern.UNICODE_CASE) == 0;
		if (ascii) {
			try {
				return attempt(pattern, merged, start, true, "");
			} catch (Unfoldable unfoldable) {
				// the platform's folding is the nearer of the two for what cannot be written out
			}
		}
		return attempt(pattern, merged, start, false, insensitive ? "i" : "");
	}

	/**
	 * A translation, done again for the {@code v} flag when a class inside a class, an intersection or a
	 * negated block turns up. That flag changes how the rest of the pattern is read, so it is used for
	 * those patterns and no others, and not at all where the platform's own case folding is in play,
	 * which it changes.
	 */
	private static Result attempt(
		String pattern,
		int flags,
		int start,
		boolean fold,
		String jsFlags
	) {
		try {
			return build(pattern, flags, start, fold, false, jsFlags);
		} catch (NeedsUnicodeSets needed) {
			return buildUnicodeSets(pattern, flags, start, fold, jsFlags, needed.what);
		} catch (Unfoldable unfoldable) {
			// the class reader for v knows where each hyphen goes, so it does not have to give up there
			String what = fold ? needsUnicodeSets(pattern, flags, start) : null;
			if (what != null) return buildUnicodeSets(pattern, flags, start, fold, jsFlags, what);
			throw unfoldable;
		}
	}

	private static Result buildUnicodeSets(
		String pattern,
		int flags,
		int start,
		boolean fold,
		String jsFlags,
		String what
	) {
		// the platform's own folding is only in play under UNICODE_CASE, and the v flag changes it
		if (!jsFlags.isEmpty()) {
			throw new PatternSyntaxException(
				"this platform's regular expressions cannot express " +
					what +
					" together with UNICODE_CASE, because the flag it needs changes how case is folded",
				pattern,
				-1
			);
		}
		return build(pattern, flags, start, fold, true, "v");
	}

	/** What in the pattern needs the {@code v} flag, read without case folding, or null when nothing does. */
	private static String needsUnicodeSets(String pattern, int flags, int start) {
		try {
			new Translate(pattern, flags, start, false, false, Collections.emptySet(), false).run();
			return null;
		} catch (NeedsUnicodeSets needed) {
			return needed.what;
		} catch (RuntimeException other) {
			return null;
		}
	}

	/**
	 * One translation, or two when a possessive quantifier is in it: the group added around its atom
	 * has to be counted from the atom's first character, so the first pass only finds where they are.
	 */
	private static Result build(
		String pattern,
		int flags,
		int start,
		boolean fold,
		boolean unicodeSets,
		String jsFlags
	) {
		Translate pass = new Translate(
			pattern,
			flags,
			start,
			fold,
			unicodeSets,
			Collections.emptySet(),
			false
		);
		String source;
		if ((flags & Pattern.LITERAL) != 0) {
			source = quote(pattern, fold, unicodeSets);
			pass.surrogates(pattern);
		} else {
			source = pass.run();
			if (!pass.found.isEmpty()) {
				pass = new Translate(pattern, flags, start, fold, unicodeSets, pass.found, false);
				source = pass.run();
			}
		}
		Groups groups = (flags & Pattern.LITERAL) != 0 ? Groups.NONE : pass.groups();
		String mid = null;
		if (unicodeSets && pass.anchored) {
			mid = new Translate(pattern, flags, start, fold, true, pass.possessive, true).run();
		}
		boolean unitScan = unicodeSets && !pass.wide;
		if (pass.sensitive == null) {
			return new Result(
				source,
				jsFlags,
				flags,
				groups,
				null,
				null,
				null,
				mid,
				null,
				unitScan
			);
		}
		try {
			String what = pass.sensitive + " over a character above U+FFFF";
			Result whole = buildUnicodeSets(pattern, flags, start, fold, jsFlags, what);
			return new Result(
				source,
				jsFlags,
				flags,
				groups,
				pass.sensitive,
				whole.source,
				null,
				null,
				whole.midSource,
				whole.unitScan
			);
		} catch (PatternSyntaxException refused) {
			return new Result(
				source,
				jsFlags,
				flags,
				groups,
				pass.sensitive,
				null,
				refused.getDescription(),
				null,
				null,
				false
			);
		}
	}

	private Groups groups() {
		int[] js = new int[jsNumber.size()];
		for (int index = 0; index < js.length; index++) js[index] = jsNumber.get(index);
		return new Groups(js, Collections.unmodifiableMap(names));
	}

	/**
	 * The flags a pattern comes to, and where its own flag groups end.
	 *
	 * <p>Only groups at the very start count: one written there applies to the whole pattern, which is
	 * what a flag is. Several in a row apply in turn.
	 */
	private static int[] leadingFlags(String pattern, int flags) {
		int merged = flags;
		int at = 0;
		while (pattern.startsWith("(?", at)) {
			int on = 0;
			int off = 0;
			boolean minus = false;
			int cursor = at + 2;
			for (; cursor < pattern.length(); cursor++) {
				char letter = pattern.charAt(cursor);
				if (letter == '-' && !minus) {
					minus = true;
					continue;
				}
				int bit = flagBit(letter);
				if (bit == 0) break;
				if (minus) off |= bit;
				else on |= bit;
			}
			if (cursor >= pattern.length() || pattern.charAt(cursor) != ')') break;
			merged = (merged | on) & ~off;
			at = cursor + 1;
		}
		return new int[] { merged, at };
	}

	private static int flagBit(char letter) {
		switch (letter) {
			case 'i':
				return Pattern.CASE_INSENSITIVE;
			case 'd':
				return Pattern.UNIX_LINES;
			case 'm':
				return Pattern.MULTILINE;
			case 's':
				return Pattern.DOTALL;
			case 'u':
				return Pattern.UNICODE_CASE;
			case 'x':
				return Pattern.COMMENTS;
			case 'U':
				return Pattern.UNICODE_CHARACTER_CLASS;
			case 'c':
				return Pattern.CANON_EQ;
			default:
				return 0;
		}
	}

	/**
	 * A literal, escaped so that every character stands for itself.
	 *
	 * @param text the literal
	 * @return the escaped source
	 */
	static String quote(String text) {
		return quote(text, false, false);
	}

	/** A literal, with each ASCII letter written as the pair of its cases when {@code fold} is on. */
	private static String quote(String text, boolean fold, boolean unicodeSets) {
		StringBuilder escaped = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (fold && isLetter(character)) pair(escaped, character);
			else escape(escaped, character, unicodeSets);
		}
		return escaped.toString();
	}

	private static boolean isLetter(int character) {
		return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
	}

	/** An ASCII letter as the class of both its cases, so that no flag has to fold it. */
	private static void pair(StringBuilder into, char letter) {
		into.append('[')
			.append(Character.toLowerCase(letter))
			.append(Character.toUpperCase(letter))
			.append(']');
	}

	private String run() {
		while (at < pattern.length()) {
			char character = pattern.charAt(at);
			if (comments && skipComment(character)) continue;
			if (character == ')') {
				closeGroup();
				continue;
			}
			boolean quantifier =
				character == '*' || character == '+' || character == '?' || character == '{';
			if (!quantifier && !pattern.startsWith("\\Q", at)) beginAtom(at);

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
					if (unicodeSets) unicodeClass();
					else characterClass();
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
					if (Character.isSurrogate(character)) {
						surrogate(character);
						break;
					}
					at++;
					if (fold && isLetter(character)) pair(out, character);
					else if (unicodeSets && (character == ']' || character == '}')) {
						out.append('\\').append(character);
					} else out.append(character);
					break;
			}
		}
		return out.toString();
	}

	// #region the pieces

	/** Notes where an atom begins, and opens the group around it when a possessive quantifier follows. */
	private void beginAtom(int index) {
		atom = index;
		if (!possessive.contains(index)) return;
		jsCount++;
		wrapped.put(index, jsCount);
		out.append("(?:(?=(");
	}

	/** Notes a construct that a pattern for UTF-16 units reads half of a character above U+FFFF with. */
	private void halves(String what) {
		if (!what.equals("a dot")) wide = true;
		if (!unicodeSets && sensitive == null) sensitive = what;
	}

	/** Notes a lone surrogate in a literal, which a pattern for UTF-16 units would match half a pair with. */
	private void surrogates(CharSequence text) {
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (
				Character.isHighSurrogate(character) &&
				index + 1 < text.length() &&
				Character.isLowSurrogate(text.charAt(index + 1))
			) {
				index++;
			} else if (Character.isSurrogate(character)) {
				halves("a lone surrogate");
				return;
			}
		}
	}

	private String dot() {
		halves("a dot");
		if (dotAll) return "[\\s\\S]";
		return unixLines ? "[^\\n]" : "[^\\n\\r\\u0085\\u2028\\u2029]";
	}

	/**
	 * Where a line starts.
	 *
	 * <p>Without {@code MULTILINE} that is only the start of the input, which JavaScript agrees about.
	 * With it, Java also counts the position after a terminator - and after the carriage return of a
	 * pair, which is one terminator rather than two, hence the guard. A JVM never starts a line at the
	 * end of the input, after a final terminator or in an empty input, which is the other guard.
	 */
	private String lineStart() {
		String begin = begin();
		if (!multiline) return begin;
		if (unixLines) return "(?:(?=[\\s\\S])(?:" + begin + "|(?<=\\n)))";
		return "(?:(?=[\\s\\S])(?:" + begin + "|(?<=[\\n\\u0085\\u2028\\u2029])|(?<=\\r)(?!\\n)))";
	}

	/** The start of the input, which is nothing at all for a search that begins inside a pair. */
	private String begin() {
		anchored = true;
		return midPair ? "(?!)" : "^";
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
				quoted();
				return;
			case 'E':
				throw refuse("\\E without \\Q", at - 1);
			case 's':
				out.append('[').append(SPACE).append(']');
				return;
			case 'S':
				halves("\\S");
				out.append("[^").append(SPACE).append(']');
				return;
			case 'h':
				out.append('[').append(HORIZONTAL).append(']');
				return;
			case 'H':
				halves("\\H");
				out.append("[^").append(HORIZONTAL).append(']');
				return;
			case 'v':
				out.append('[').append(VERTICAL).append(']');
				return;
			case 'V':
				halves("\\V");
				out.append("[^").append(VERTICAL).append(']');
				return;
			case 'D':
			case 'W':
				halves("\\" + character);
				out.append('\\').append(character);
				return;
			case 'R':
				out.append("(?:")
					.append(unixLines ? TERMINATOR_UNIX : TERMINATOR)
					.append(')');
				return;
			case 'A':
				out.append(begin());
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
				out.append(property(character == 'P', false));
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
				if (asciiCase && ((character >= '1' && character <= '9') || character == 'k')) {
					throw refuse(
						"a backreference under CASE_INSENSITIVE without UNICODE_CASE, where a JVM folds ASCII" +
							" only and the platform's own folding would also match the non-ASCII case variants",
						at - 1
					);
				}
				int code = codeEscape(character);
				if (code >= 0) {
					literal(code);
					return;
				}
				if (character == 'k') {
					namedReference();
					return;
				}
				if (character >= '1' && character <= '9') {
					backreference(character - '0');
					return;
				}
				if (isLetter(character) && "dwtnrfB".indexOf(character) < 0) {
					throw illegalEscape(at - 2);
				}
				// the v flag reads an escaped character that is not a syntax character as an error
				if (unicodeSets && !isLetter(character) && !isDigit(character)) {
					escape(out, character, true);
					return;
				}
				out.append('\\').append(character);
				return;
		}
	}

	/**
	 * A backreference, read as a JVM reads it: as many digits as still name a group opened so far, and
	 * written with the number the platform gave that group.
	 *
	 * <p>A group that is not open yet is refused. A JVM never matches a reference to one, where the
	 * platform would match nothing or read the digits as an octal escape.
	 */
	private void backreference(int first) {
		int number = first;
		int opened = jsNumber.size() - 1;
		while (at < pattern.length() && isDigit(pattern.charAt(at))) {
			int wider = number * 10 + (pattern.charAt(at) - '0');
			if (wider > opened) break;
			number = wider;
			at++;
		}
		if (number > opened) {
			throw refuse("a backreference to group " + number + ", which is not open yet", at - 1);
		}
		refuseOpen(number, at - 1);
		String reference = "\\" + jsNumber.get(number);
		// a digit that follows would read as part of the number, and so would a quotation or a comment
		boolean joins =
			comments ||
			(at < pattern.length() &&
				(isDigit(pattern.charAt(at)) || pattern.startsWith("\\Q", at)));
		out.append(joins ? "(?:" + reference + ")" : reference);
	}

	/**
	 * A backreference by name, which a JVM takes only for a group already written. The platform reads
	 * {@code \k} as the letter {@code k} when the pattern has no named group, so a name that is not
	 * there is refused here rather than left to match something else.
	 */
	private void namedReference() {
		if (at >= pattern.length() || pattern.charAt(at) != '<') {
			throw malformed("\\k is not followed by '<' for named capturing group", at);
		}
		int close = pattern.indexOf('>', at);
		if (close < 0) throw malformed("named capturing group is missing trailing '>'", at);
		String name = pattern.substring(at + 1, close);
		if (!names.containsKey(name)) {
			throw malformed("named capturing group <" + name + "> does not exist", at);
		}
		refuseOpen(names.get(name), at - 2);
		out.append("\\k<").append(name).append('>');
		at = close + 1;
	}

	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}

	/**
	 * A quotation. A quantifier after it applies to the last character, which is the atom.
	 */
	private void quoted() {
		int from = at;
		String run = literalRun();
		if (run.isEmpty()) return;
		surrogates(run);
		int last = run.offsetByCodePoints(run.length(), -1);
		out.append(quote(run.substring(0, last), fold, unicodeSets));
		beginAtom(from + last);
		out.append(wholeCharacter(quote(run.substring(last), fold, unicodeSets)));
	}

	/** A surrogate written as itself: a pair is one character, and a lone one is half of any it meets. */
	private void surrogate(char character) {
		boolean pair =
			Character.isHighSurrogate(character) &&
			at + 1 < pattern.length() &&
			Character.isLowSurrogate(pattern.charAt(at + 1));
		if (pair) {
			at += 2;
			out.append(wholeCharacter(pattern.substring(at - 2, at)));
			return;
		}
		halves("a lone surrogate");
		out.append(character);
		at++;
	}

	/** A character above U+FFFF is two units, which a quantifier after it has to take together. */
	private String wholeCharacter(String text) {
		boolean wide = text.length() == 2 && Character.isHighSurrogate(text.charAt(0));
		return wide && !unicodeSets && quantifierFollows() ? "(?:" + text + ")" : text;
	}

	private boolean quantifierFollows() {
		int index = comments ? pastSpace(at) : at;
		return index < pattern.length() && "*+?{".indexOf(pattern.charAt(index)) >= 0;
	}

	// #region the escapes that stand for one character, read as a JVM reads them

	/**
	 * The character an escape stands for, read from just after its letter, or -1 when the letter is not
	 * one of those escapes. A JVM reads {@code \xhh}, {@code \x{h...h}}, the four-digit escape that
	 * starts with {@code u} (two of them make a surrogate pair), {@code \0n}, {@code \0nn} and
	 * {@code \0mnn}, and {@code \cX}, and the platform reads each of them differently or not at all, so
	 * they are read here and written as plain escapes.
	 *
	 * @throws PatternSyntaxException on a malformed one, as a JVM does
	 */
	private int codeEscape(char escape) {
		int from = at - 2;
		int code = readEscape(escape);
		if (code >= 0xD800 && (code <= 0xDFFF || code > 0xFFFF)) wide = true;
		// two hex digits and four, as written, are read the same way by both and are left as they are
		plain = null;
		if ((escape == 'x' && at - from == 4) || (escape == 'u' && at - from == 6)) {
			boolean joined = unicodeSets && code >= 0xD800 && code <= 0xDFFF;
			if (!joined) plain = pattern.substring(from, at);
		}
		return code;
	}

	private int readEscape(char escape) {
		switch (escape) {
			case 'x':
				return hexCode();
			case 'u':
				return unicodeCode();
			case '0':
				return octalCode();
			case 'c':
				if (at >= pattern.length()) throw malformed("Illegal control escape sequence", at);
				refuseGap(at);
				int control = pattern.codePointAt(at);
				at += Character.charCount(control);
				return control ^ 64;
			default:
				return -1;
		}
	}

	/** {@code \xhh} or {@code \x{h...h}}, from just after the {@code x}. */
	private int hexCode() {
		if (at < pattern.length() && pattern.charAt(at) == '{') {
			int index = at + 1;
			int code = 0;
			int digits = 0;
			while (true) {
				refuseGap(index);
				int digit = index < pattern.length() ? hexDigit(pattern.charAt(index)) : -1;
				if (digit < 0) break;
				code = code * 16 + digit;
				if (code > Character.MAX_CODE_POINT) {
					throw malformed("Hexadecimal codepoint is too big", index);
				}
				digits++;
				index++;
			}
			if (digits == 0) throw malformed("Illegal hexadecimal escape sequence", at);
			if (index >= pattern.length() || pattern.charAt(index) != '}') {
				throw malformed("Unclosed hexadecimal escape sequence", index);
			}
			at = index + 1;
			return code;
		}
		return hexDigits(2, "Illegal hexadecimal escape sequence");
	}

	/** The four-digit escape with a {@code u}, joined to the one after it when they are a surrogate pair. */
	private int unicodeCode() {
		int unit = hexDigits(4, "Illegal Unicode escape sequence");
		if (!Character.isHighSurrogate((char) unit)) return unit;
		refuseGap(at);
		if (!pattern.startsWith("\\u", at)) return unit;
		int mark = at;
		at += 2;
		int low = hexDigits(4, "Illegal Unicode escape sequence");
		if (Character.isLowSurrogate((char) low)) return Character.toCodePoint(
			(char) unit,
			(char) low
		);
		at = mark;
		return unit;
	}

	/**
	 * An octal escape from just after its {@code 0}: one to three digits, three only when the first is
	 * at most 3 so that the value stays within a byte.
	 */
	private int octalCode() {
		refuseGap(at);
		if (!octalDigit(at)) throw malformed("Illegal octal escape sequence", at);
		int first = pattern.charAt(at) - '0';
		int code = first;
		at++;
		refuseGap(at);
		if (!octalDigit(at)) return code;
		code = code * 8 + (pattern.charAt(at++) - '0');
		refuseGap(at);
		if (first <= 3 && octalDigit(at)) code = code * 8 + (pattern.charAt(at++) - '0');
		return code;
	}

	private boolean octalDigit(int index) {
		return (
			index < pattern.length() && pattern.charAt(index) >= '0' && pattern.charAt(index) <= '7'
		);
	}

	private int hexDigits(int width, String message) {
		int code = 0;
		for (int index = 0; index < width; index++) {
			refuseGap(at + index);
			int digit = at + index < pattern.length() ? hexDigit(pattern.charAt(at + index)) : -1;
			if (digit < 0) throw malformed(message, at + index);
			code = code * 16 + digit;
		}
		at += width;
		return code;
	}

	private static int hexDigit(char character) {
		if (character >= '0' && character <= '9') return character - '0';
		if (character >= 'a' && character <= 'f') return character - 'a' + 10;
		if (character >= 'A' && character <= 'F') return character - 'A' + 10;
		return -1;
	}

	/**
	 * Under {@code COMMENTS} a JVM skips whitespace and comments wherever it reads the next character of
	 * an escape, so {@code \x4 1} is {@code \x41}. That is refused rather than followed.
	 */
	private void refuseGap(int index) {
		if (
			comments &&
			index < pattern.length() &&
			(isSpace(pattern.charAt(index)) || pattern.charAt(index) == '#')
		) {
			throw refuse(
				"whitespace or a comment inside an escape under COMMENTS, which a JVM reads past",
				index
			);
		}
	}

	private PatternSyntaxException malformed(String message, int index) {
		return new PatternSyntaxException(message, pattern, Math.min(index, pattern.length()));
	}

	private PatternSyntaxException illegalEscape(int index) {
		return malformed("Illegal/unsupported escape sequence", index);
	}

	/** A character an escape stood for, written so that the platform reads it as that character. */
	private void literal(int code) {
		if (fold && isLetter(code)) {
			pair(out, (char) code);
			return;
		}
		if (code >= 0xD800 && code <= 0xDFFF) halves("a lone surrogate");
		out.append(plain != null ? plain : codeSource(code));
	}

	/** A character as an escape: two digits up to 0xFF, four up to 0xFFFF, and braces under {@code v}. */
	private String codeSource(int code) {
		if (code < 0x100) return "\\x" + (code < 0x10 ? "0" : "") + Integer.toHexString(code);
		// the v flag joins a high and a low surrogate written as four digits, which a JVM does not do here
		boolean surrogate = code >= 0xD800 && code <= 0xDFFF;
		if (unicodeSets && surrogate) return "\\u{" + Integer.toHexString(code) + "}";
		if (code <= 0xFFFF) return unit(code);
		if (unicodeSets) return unit(code);
		char[] units = Character.toChars(code);
		return "(?:" + unit(units[0]) + unit(units[1]) + ")";
	}

	// #endregion

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
	 * <p>The POSIX names and the Unicode blocks of the basic multilingual plane, each because it is an
	 * explicit set of code units. A Unicode category or script would need JavaScript's own property
	 * syntax, which needs the {@code u} flag, and that flag changes how the rest of the pattern is read
	 * - so those are refused instead.
	 *
	 * @param negated whether it was written {@code \P}
	 * @param inClass whether it sits inside a character class, which takes the set without brackets
	 */
	private String property(boolean negated, boolean inClass) {
		if (at >= pattern.length() || pattern.charAt(at) != '{') {
			throw refuse("a one-letter character property", at);
		}
		int close = pattern.indexOf('}', at);
		if (close < 0) throw refuse("an unclosed character property", at);
		String name = pattern.substring(at + 1, close);
		at = close + 1;

		String block = blockName(name);
		if (block != null || negated) wide = true;
		if (block != null) {
			int[] range = Blocks.find(block);
			if (range == null) {
				throw refuse(
					"\\p{" +
						name +
						"}, which is not a block of the basic multilingual plane (it is unknown, made" +
						" of surrogates, or above U+FFFF, which a pattern without the u flag cannot hold)",
					at
				);
			}
			// a pattern read as UTF-16 units would match each half of a character above U+FFFF on its own
			if (negated && !unicodeSets) {
				throw new NeedsUnicodeSets(
					"a negated Unicode block, which has to take a character above U+FFFF whole"
				);
			}
			String set = blockSet(range, negated && inClass, unicodeSets);
			if (!inClass) return (negated ? "[^" : "[") + set + "]";
			if (negated) {
				markRange(0, range[0] - 1);
				markRange(range[1] + 1, 0xFFFF);
			} else {
				markRange(range[0], range[1]);
			}
			return set;
		}

		String set = posix(name);
		if (set == null) {
			throw refuse(
				"\\p{" +
					name +
					"}, which needs the platform's own property syntax and the flag that comes with it",
				at
			);
		}
		if (inClass && negated) throw refuse("a negated property inside a character class", at);
		if (inClass) return set;
		if (negated) halves("a negated property");
		return (negated ? "[^" : "[") + set + "]";
	}

	/** The block a property name asks for, {@code InGreek} or {@code block=Greek}, else null. */
	private static String blockName(String name) {
		int equals = name.indexOf('=');
		if (equals >= 0) {
			String key = name.substring(0, equals).toLowerCase(Locale.ROOT);
			return key.equals("blk") || key.equals("block") ? name.substring(equals + 1) : null;
		}
		return name.startsWith("In") ? name.substring(2) : null;
	}

	/**
	 * A block as explicit code units. Negated inside a class it is the two ranges around it, since a
	 * class cannot hold a negated escape of its own.
	 */
	private static String blockSet(int[] range, boolean around, boolean unicodeSets) {
		if (!around) return unit(range[0]) + "-" + unit(range[1]);
		StringBuilder set = new StringBuilder();
		if (range[0] > 0) set.append(unit(0))
			.append('-')
			.append(unit(range[0] - 1));
		// under the v flag a character above U+FFFF is one character, and the rest of the plane is the set
		if (range[1] < 0xFFFF || unicodeSets) set.append(unit(range[1] + 1))
			.append('-')
			.append(unicodeSets ? "\\u{10ffff}" : unit(0xFFFF));
		return set.toString();
	}

	private static String unit(int code) {
		String hex = Integer.toHexString(code);
		if (code > 0xFFFF) return "\\u{" + hex + "}";
		return "\\u" + "0000".substring(hex.length()) + hex;
	}

	private String posix(String name) {
		switch (name) {
			case "Lower":
				return fold ? "a-zA-Z" : "a-z";
			case "Upper":
				return fold ? "a-zA-Z" : "A-Z";
			case "ASCII":
				return "\\x00-\\x7F";
			case "Alpha":
				return "a-zA-Z";
			case "Digit":
				return "0-9";
			case "Alnum":
				return "a-zA-Z0-9";
			case "Punct":
				// the v flag reads these ends of a range as escapes, and a bare brace as an error
				return unicodeSets ? "\\!-\\/\\:-\\@\\[-\\`\\{-\\~" : "!-\\/:-@\\[-`{-~";
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
	 * syntax and need the {@code v} flag, so the translation is done again by {@link #unicodeClass}.
	 */
	private void characterClass() {
		int start = at;
		out.append('[');
		at++;
		if (at < pattern.length() && pattern.charAt(at) == '^') {
			halves("a negated class");
			out.append('^');
			at++;
		}
		previous = -1;
		open = false;
		elements = 0;
		java.util.Arrays.fill(covered, false);
		if (at < pattern.length() && pattern.charAt(at) == ']') {
			out.append("\\]");
			at++;
			single(']');
		}

		while (at < pattern.length()) {
			char character = pattern.charAt(at);
			if (comments && skipComment(character)) continue;
			if (character == ']') {
				at++;
				if (fold) addOtherCase();
				out.append(']');
				return;
			}
			if (character == '[') throw new NeedsUnicodeSets("a character class inside another");
			if (character == '&' && at + 1 < pattern.length() && pattern.charAt(at + 1) == '&') {
				throw new NeedsUnicodeSets("an intersection");
			}
			if (character == '\\') {
				classEscape();
				continue;
			}
			if (character >= 0xD800) {
				boolean pair =
					Character.isHighSurrogate(character) &&
					at + 1 < pattern.length() &&
					Character.isLowSurrogate(pattern.charAt(at + 1));
				inClass(pair ? pattern.codePointAt(at) : character);
			}
			at++;
			if (fold && character == '-') {
				hyphen();
				continue;
			}
			out.append(character);
			if (fold) single(character);
		}
		throw refuse("an unclosed character class", start);
	}

	// #region a class for the v flag, where a class can hold another and be intersected with one

	/**
	 * A character class written for the {@code v} flag, read the way a JVM reads it.
	 *
	 * <p>The flag has a class inside a class and {@code &&} too, but wants each operand of an
	 * intersection to be a class of its own, every punctuation mark inside a class escaped, and a hyphen
	 * only where it makes a range. The odd corners of a JVM's reading (a hyphen next to a set, a lone
	 * ampersand after {@code &&}) are either followed exactly or refused.
	 */
	private void unicodeClass() {
		int start = at;
		at++;
		boolean negated = at < pattern.length() && pattern.charAt(at) == '^';
		if (negated) {
			wide = true;
			at++;
		}
		StringBuilder outer = out;
		boolean[] outerCovered = covered;
		List<String> operands = new ArrayList<>();
		List<Boolean> onlyNested = new ArrayList<>();

		boolean more = true;
		while (more) {
			more = false;
			out = new StringBuilder();
			covered = new boolean[52];
			int items = 0;
			int nested = 0;
			while (true) {
				skipClassSpace();
				if (at >= pattern.length()) throw refuse("an unclosed character class", start);
				char character = pattern.charAt(at);
				if (character == ']') {
					at++;
					if (items == 0 && operands.isEmpty()) {
						classChar(']');
						items++;
						continue;
					}
					break;
				}
				if (
					character == '&' && at + 1 < pattern.length() && pattern.charAt(at + 1) == '&'
				) {
					char next = at + 2 < pattern.length() ? pattern.charAt(at + 2) : 0;
					if (items == 0 || next == ']') throw refuse("an empty operand of &&", at);
					if (next == '&') throw refuse("&&&, which no intersection explains", at);
					at += 2;
					more = true;
					break;
				}
				if (character == '&' && !operands.isEmpty()) {
					throw refuse(
						"a single & after &&, which a JVM reads as the end of the operand",
						at
					);
				}
				if (character == '[') {
					unicodeClass();
					nested++;
					items++;
					continue;
				}
				items += classItem();
			}
			if (fold) addOtherCase();
			operands.add(out.toString());
			onlyNested.add(items == 1 && nested == 1);
		}

		out = outer;
		covered = outerCovered;
		out.append('[');
		if (negated) out.append('^');
		for (int index = 0; index < operands.size(); index++) {
			if (index > 0) out.append("&&");
			boolean bare = operands.size() == 1 || onlyNested.get(index);
			if (!bare) out.append('[');
			out.append(operands.get(index));
			if (!bare) out.append(']');
		}
		out.append(']');
	}

	/** Whitespace and comments inside a class, which a JVM drops under {@code COMMENTS} there too. */
	private void skipClassSpace() {
		while (comments && at < pattern.length() && skipComment(pattern.charAt(at))) {}
	}

	/**
	 * One item of a class: a set, a character, or a range of them. A JVM reads a hyphen as a range
	 * between two characters and as itself anywhere else, which is followed here, so that the result
	 * only ever has a hyphen where it is a range.
	 *
	 * @return how many items it was
	 */
	private int classItem() {
		if (pattern.charAt(at) == '\\') {
			if (at + 1 >= pattern.length()) throw refuse("a trailing backslash", at + 1);
			char escape = pattern.charAt(at + 1);
			switch (escape) {
				case 'Q':
					return quotation();
				case 'p':
				case 'P':
					at += 2;
					out.append(property(escape == 'P', true));
					return 1;
				case 'd':
				case 'D':
				case 'w':
				case 'W':
					at += 2;
					if (escape == 'D' || escape == 'W') wide = true;
					out.append('\\').append(escape);
					return 1;
				case 's':
					at += 2;
					out.append(SPACE);
					return 1;
				case 'h':
					at += 2;
					out.append(HORIZONTAL);
					return 1;
				case 'v':
					at += 2;
					out.append(VERTICAL);
					return 1;
				case 'S':
				case 'H':
				case 'V':
					throw refuse(
						"\\" +
							escape +
							" inside a character class, which no exact set can stand for",
						at
					);
				case 'R':
				case 'X':
				case 'G':
				case 'N':
					throw refuse("\\" + escape + " inside a character class", at);
				default:
					break;
			}
		}

		int low = classValue();
		int dash = at;
		skipClassSpace();
		if (at < pattern.length() && pattern.charAt(at) == '-') {
			at++;
			skipClassSpace();
			if (at < pattern.length() && pattern.charAt(at) != ']' && pattern.charAt(at) != '[') {
				int high = rangeEnd();
				if (high < low) throw refuse("an illegal character range", dash);
				if (low <= 0xDFFF && high >= 0xD800) wide = true;
				classChar(low);
				out.append('-');
				classChar(high);
				markRange(low, high);
				return 1;
			}
		}
		at = dash;
		classChar(low);
		markRange(low, low);
		return 1;
	}

	private int rangeEnd() {
		if (pattern.charAt(at) == '\\' && at + 1 < pattern.length()) {
			char escape = pattern.charAt(at + 1);
			if (escape == 'Q') {
				throw refuse(
					"a quotation next to a hyphen in a character class, which makes a range",
					at
				);
			}
			if ("pPdDwWshvSHVRXGN".indexOf(escape) >= 0) {
				throw refuse("a character range that ends at a set", at);
			}
		}
		return classValue();
	}

	/** A quotation inside a class: each character stands for itself and takes part in no range. */
	private int quotation() {
		at += 2;
		String run = literalRun();
		if (
			at + 1 < pattern.length() &&
			pattern.charAt(at) == '-' &&
			pattern.charAt(at + 1) != ']' &&
			pattern.charAt(at + 1) != '['
		) {
			throw refuse(
				"a quotation next to a hyphen in a character class, which makes a range",
				at
			);
		}
		int count = 0;
		for (
			int index = 0;
			index < run.length();
			index += Character.charCount(run.codePointAt(index))
		) {
			int code = run.codePointAt(index);
			classChar(code);
			markRange(code, code);
			count++;
		}
		return count;
	}

	/** One character, or the one an escape stands for, as a code point. */
	private int classValue() {
		char character = pattern.charAt(at);
		if (character != '\\') {
			int code = pattern.codePointAt(at);
			at += Character.charCount(code);
			return code;
		}
		at++;
		if (at >= pattern.length()) throw refuse("a trailing backslash", at);
		char escape = pattern.charAt(at++);
		switch (escape) {
			case 't':
				return 9;
			case 'n':
				return 10;
			case 'r':
				return 13;
			case 'f':
				return 12;
			case 'a':
				return 7;
			case 'e':
				return 27;
			default:
				int code = codeEscape(escape);
				if (code >= 0) return code;
				if (isLetter(escape) || isDigit(escape)) {
					throw refuse(
						"the escape \\" +
							escape +
							" inside a character class that holds another, which has no exact reading",
						at - 2
					);
				}
				if (Character.isHighSurrogate(escape) && at < pattern.length()) {
					char low = pattern.charAt(at);
					if (Character.isLowSurrogate(low)) {
						at++;
						return Character.toCodePoint(escape, low);
					}
				}
				return escape;
		}
	}

	/** A character inside a class under the v flag, which reads every punctuation mark as syntax. */
	private void classChar(int code) {
		if (code > 0xFFFF) {
			out.appendCodePoint(code);
		} else if (code >= 0xD800 && code <= 0xDFFF) {
			out.append("\\u{").append(Integer.toHexString(code)).append('}');
		} else if (code < 0x20 || code == 0x7F) {
			out.append("\\x")
				.append(code < 0x10 ? "0" : "")
				.append(Integer.toHexString(code));
		} else if (code < 0x80 && "^$\\.*+?()[]{}|/&-!#%,:;<=>@`~".indexOf(code) >= 0) {
			out.append('\\').append((char) code);
		} else {
			out.append((char) code);
		}
	}

	// #endregion

	/** The same escapes, without the brackets, since they are already inside a class. */
	private void classEscape() {
		at++;
		if (at >= pattern.length()) throw refuse("a trailing backslash", at);
		char character = pattern.charAt(at++);
		switch (character) {
			case 'Q':
				String run = literalRun();
				for (int index = 0; index < run.length(); index++) {
					int code = run.codePointAt(index);
					inClass(code);
					index += Character.charCount(code) - 1;
				}
				out.append(quote(run));
				if (fold) {
					set();
					for (int index = 0; index < run.length(); index++) {
						markRange(run.charAt(index), run.charAt(index));
					}
				}
				return;
			case 's':
				out.append(SPACE);
				set();
				return;
			case 'h':
				out.append(HORIZONTAL);
				set();
				return;
			case 'v':
				out.append(VERTICAL);
				set();
				return;
			case 'a':
				out.append("\\x07");
				single(7);
				return;
			case 'e':
				out.append("\\x1B");
				single(27);
				return;
			case 'p':
			case 'P':
				out.append(property(character == 'P', true));
				set();
				return;
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
				int code = codeEscape(character);
				if (code >= 0) {
					inClass(code);
					out.append(plain != null ? plain : codeSource(code));
					single(code);
					return;
				}
				boolean illegal = isLetter(character)
					? "dDwWtnrf".indexOf(character) < 0
					: character >= '1' && character <= '9';
				if (illegal) throw illegalEscape(at - 2);
				if (character == 'D' || character == 'W') halves("\\" + character);
				out.append('\\').append(character);
				if (fold) heldBy(character);
				return;
		}
	}

	/**
	 * What a character in a class means for a pattern read as UTF-16 units. A character above U+FFFF has
	 * to be read whole, which only the {@code v} flag does, and anything from the surrogates up may be
	 * half of one.
	 */
	private void inClass(int code) {
		if (code > 0xFFFF) {
			throw new NeedsUnicodeSets(
				"a character above U+FFFF in a class, which has to be read whole"
			);
		}
		if (code >= 0xD800) halves("a class that reaches the surrogates");
	}

	/** An escape inside a class that is not one of the named ones, noting which letters it holds. */
	private void heldBy(char character) {
		switch (character) {
			case 't':
				single(9);
				return;
			case 'n':
				single(10);
				return;
			case 'f':
				single(12);
				return;
			case 'r':
				single(13);
				return;
			default:
				if (isLetter(character) || Character.isLetterOrDigit(character)) set();
				else single(character);
		}
	}

	// #region the letters a class holds, when case is folded by writing out both

	/** A single character, which starts a range or ends the one that is open. */
	private void single(int code) {
		if (!fold) return;
		elements++;
		if (open) {
			markRange(previous, code);
			open = false;
			previous = -1;
		} else {
			markRange(code, code);
			previous = code;
		}
	}

	/** A set of characters, which cannot be the end of a range. */
	private void set() {
		if (!fold) return;
		if (open) throw new Unfoldable();
		elements++;
		previous = -1;
	}

	/**
	 * A hyphen, which is a range only between two characters. Anywhere else it is literal, and is
	 * written escaped so that the letters added at the end cannot turn it into a range.
	 */
	private void hyphen() {
		boolean last = at < pattern.length() && pattern.charAt(at) == ']';
		if (open) throw new Unfoldable();
		if (previous >= 0 && !last) {
			open = true;
			out.append('-');
			return;
		}
		if (previous < 0 && elements > 0 && !last) throw new Unfoldable();
		out.append("\\-");
		single('-');
	}

	/** Notes which ASCII letters a range holds. */
	private void markRange(int low, int high) {
		if (!fold) return;
		for (int code = Math.max(low, 'A'); code <= Math.min(high, 'Z'); code++) {
			covered[26 + code - 'A'] = true;
		}
		for (int code = Math.max(low, 'a'); code <= Math.min(high, 'z'); code++) {
			covered[code - 'a'] = true;
		}
	}

	/** Adds, for each letter the class holds in one case, the other, which is what folding does. */
	private void addOtherCase() {
		for (int pass = 0; pass < 2; pass++) {
			char base = pass == 0 ? 'a' : 'A';
			int own = pass == 0 ? 0 : 26;
			int other = pass == 0 ? 26 : 0;
			int index = 0;
			while (index < 26) {
				if (!covered[other + index] || covered[own + index]) {
					index++;
					continue;
				}
				int last = index;
				while (last + 1 < 26 && covered[other + last + 1] && !covered[own + last + 1])
					last++;
				out.append((char) (base + index));
				if (last > index) out.append('-').append((char) (base + last));
				index = last + 1;
			}
		}
	}

	// #endregion

	/**
	 * A group, or one of the constructs written like one.
	 *
	 * <p>The flag constructs are the interesting case. A flag written at the very start of a pattern
	 * applies to all of it, which is a flag, and {@link #leadingFlags} has already read it; written
	 * anywhere else it applies from there on, or to one group, which nothing in JavaScript expresses,
	 * so it is refused.
	 */
	private void group() {
		int start = at;
		if (at + 1 >= pattern.length() || pattern.charAt(at + 1) != '?') {
			refuseInAtom(start);
			at++;
			scopes.push(new Scope(start, false, 0, capture(null)));
			out.append('(');
			return;
		}

		char kind = at + 2 < pattern.length() ? pattern.charAt(at + 2) : 0;
		if (kind == ':' || kind == '=' || kind == '!') {
			scopes.push(new Scope(start, false, 0, 0));
			out.append("(?").append(kind);
			at += 3;
			return;
		}
		if (kind == '<') {
			char after = at + 3 < pattern.length() ? pattern.charAt(at + 3) : 0;
			if (after == '=' || after == '!') {
				scopes.push(new Scope(start, true, 0, 0));
				behind++;
				out.append("(?<").append(after);
				at += 4;
				return;
			}
			int close = pattern.indexOf('>', at + 3);
			if (close < 0) throw refuse("an unclosed group name", at);
			refuseInAtom(start);
			scopes.push(new Scope(start, false, 0, capture(pattern.substring(at + 3, close))));
			out.append("(?<")
				.append(pattern, at + 3, close)
				.append('>');
			at = close + 1;
			return;
		}
		if (kind == '>') {
			refuseBehind("(?>, an atomic group", at);
			jsCount++;
			scopes.push(new Scope(start, false, jsCount, 0));
			out.append("(?:(?=(");
			at += 3;
			return;
		}
		throw inlineFlags();
	}

	/**
	 * A capturing group in an atom is refused. After an attempt that took the atom and was given up, a
	 * JVM still holds the span the group had there when the match that is reported did not use the group,
	 * and the platform has restored it to nothing, so the two answer differently with no way to tell.
	 *
	 * @param start where the group opens, which is where an atom it is the whole of would begin
	 */
	private void refuseInAtom(int start) {
		if (possessive.contains(start)) {
			throw refuse("a capturing group that a possessive quantifier applies to", start);
		}
		for (Scope scope : scopes) {
			if (scope.synthetic > 0) throw refuse(
				"a capturing group inside an atomic group",
				start
			);
			if (possessive.contains(scope.start)) {
				throw refuse("a capturing group inside the atom of a possessive quantifier", start);
			}
		}
	}

	/**
	 * A group the caller can read, which the platform numbers in order after any added before it.
	 *
	 * @return the number the caller gives it
	 */
	private int capture(String name) {
		jsCount++;
		jsNumber.add(jsCount);
		int number = jsNumber.size() - 1;
		if (name != null) names.put(name, number);
		return number;
	}

	/** Refuses a reference to a group that is still open, which a JVM never matches on its first pass. */
	private void refuseOpen(int number, int index) {
		for (Scope scope : scopes) {
			if (scope.group == number) {
				throw refuse(
					"a backreference to group " +
						number +
						" inside group " +
						number +
						", which has not closed yet (a JVM never matches it on the first pass, and the platform" +
						" reads it as the empty string)",
					index
				);
			}
		}
	}

	private void closeGroup() {
		at++;
		Scope scope = scopes.poll();
		if (scope == null) {
			out.append(')');
			return;
		}
		atom = scope.start;
		if (scope.lookbehind) behind--;
		out.append(scope.synthetic > 0 ? "))\\" + scope.synthetic + ")" : ")");
	}

	/**
	 * The platform reads a lookbehind backwards, so the backreference that stands for what an atomic
	 * group or a possessive quantifier took would be matched against the text on the wrong side.
	 */
	private void refuseBehind(String what, int index) {
		if (behind > 0) {
			throw refuse(
				what +
					" inside a lookbehind, which the platform reads backwards, so what it took could not be read back",
				index
			);
		}
	}

	/** Folded into the flags when it opens the pattern, so what reaches here is refused. */
	private PatternSyntaxException inlineFlags() {
		int cursor = at + 2;
		while (
				cursor < pattern.length() &&
				(pattern.charAt(cursor) == '-' || flagBit(pattern.charAt(cursor)) != 0)
			)
			cursor++;
		String written = pattern.substring(at, Math.min(cursor + 1, pattern.length()));
		char end = cursor < pattern.length() ? pattern.charAt(cursor) : 0;
		if (end == ':') return refuse(written + ", a flag that applies to one group", at);
		if (end == ')') {
			return refuse(
				written +
					", a flag that applies from where it is written (only a flag that opens the pattern is read)",
				at
			);
		}
		return refuse(written + ", which is not a group or a flag this platform reads", at);
	}

	/**
	 * The mark after a quantifier: lazy is the same in both, and possessive is Java's own, written as a
	 * lookahead around the whole quantified atom that a backreference then takes.
	 */
	private void quantifierSuffix() {
		while (comments && at < pattern.length() && skipComment(pattern.charAt(at))) {}
		if (at >= pattern.length()) return;
		char character = pattern.charAt(at);
		if (character == '?') {
			at++;
			out.append('?');
			return;
		}
		if (character != '+') return;
		if (atom < 0) throw new PatternSyntaxException("Dangling meta character '+'", pattern, at);
		refuseBehind("a possessive quantifier", at);
		at++;
		found.add(atom);
		if (possessive.contains(atom)) out.append("))\\").append(wrapped.get(atom)).append(')');
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
		if (!isSpace(character) && character != '#') return false;
		at = pastSpace(at);
		return true;
	}

	/**
	 * Where the pattern goes on after whitespace and comments. A JVM's whitespace is ASCII only and a
	 * comment ends at a line terminator, which is {@code \n} alone under {@code UNIX_LINES}. A terminator
	 * that is not ASCII whitespace (next line, line separator, paragraph separator) ends the comment and
	 * then stands for itself in the pattern, as it does there.
	 */
	private int pastSpace(int index) {
		while (index < pattern.length()) {
			char character = pattern.charAt(index);
			if (isSpace(character)) {
				index++;
			} else if (character == '#') {
				index++;
				while (index < pattern.length() && !endsLine(pattern.charAt(index))) index++;
			} else {
				break;
			}
		}
		return index;
	}

	private static boolean isSpace(char character) {
		return " \t\n\u000B\f\r".indexOf(character) >= 0;
	}

	private boolean endsLine(char character) {
		if (unixLines) return character == '\n';
		return (
			character == '\n' ||
			character == '\r' ||
			character == '\u0085' ||
			(character | 1) == 0x2029
		);
	}

	// #endregion

	private static void escape(StringBuilder into, char character, boolean unicodeSets) {
		// the hyphen is here for the quoted run inside a character class, where a raw one would be
		// read as a range; outside a class the escape is a legal identity escape and means the same,
		// except under the v flag, which reads an escaped hyphen outside a class as an error
		if (unicodeSets && character == '-') {
			into.append('-');
		} else if ("\\^$.|?*+()[]{}/-".indexOf(character) >= 0) {
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
