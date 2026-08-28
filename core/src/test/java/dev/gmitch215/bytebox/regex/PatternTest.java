package dev.gmitch215.bytebox.regex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The pattern and the matcher, compared against the engine that defines them.
 *
 * <p>Two claims are being checked, and they are different. The first is that a translated pattern means
 * what the pattern it came from means: every case runs the translation through a JVM's engine and the
 * original through the same engine, and the sequences of matches are compared. The second is that what
 * is written here rather than delegated - where a search resumes, how a match of nothing moves on, what
 * a replacement expands to, how a split treats an empty piece - agrees with the runtime's own answer.
 *
 * <p>What no test on a JVM can check is whether the platform's engine and a JVM's agree about the syntax
 * they share. That is the residual risk, and it is why everything that could differ is rewritten to
 * something explicit rather than passed through.
 */
@DisplayName("java.util.regex")
class PatternTest {

	private static Pattern ours(String pattern) {
		return Pattern.compile(pattern, 0, Native.ENGINE);
	}

	private static Pattern ours(String pattern, int flags) {
		return Pattern.compile(pattern, flags, Native.ENGINE);
	}

	/** Every match, as {@code start:end:group} so that one comparison covers all three. */
	private static List<String> found(String pattern, int flags, String input) {
		List<String> matches = new ArrayList<>();
		Matcher matcher = ours(pattern, flags).matcher(input);
		while (matcher.find()) {
			StringBuilder record = new StringBuilder();
			record.append(matcher.start()).append(':').append(matcher.end());
			for (int group = 1; group <= matcher.groupCount(); group++) {
				record.append(':').append(matcher.group(group));
			}
			matches.add(record.toString());
		}
		return matches;
	}

	private static List<String> expected(String pattern, int flags, String input) {
		List<String> matches = new ArrayList<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern, flags).matcher(
			input
		);
		while (matcher.find()) {
			StringBuilder record = new StringBuilder();
			record.append(matcher.start()).append(':').append(matcher.end());
			for (int group = 1; group <= matcher.groupCount(); group++) {
				record.append(':').append(matcher.group(group));
			}
			matches.add(record.toString());
		}
		return matches;
	}

	private static void assertMatches(String pattern, String... inputs) {
		assertMatches(pattern, 0, inputs);
	}

	private static void assertMatches(String pattern, int flags, String... inputs) {
		for (String input : inputs) {
			assertEquals(
				expected(pattern, flags, input),
				found(pattern, flags, input),
				"pattern " + pattern + " over " + show(input)
			);
			assertEquals(
				java.util.regex.Pattern.compile(pattern, flags).matcher(input).matches(),
				ours(pattern, flags).matcher(input).matches(),
				"whole match of " + pattern + " over " + show(input)
			);
			assertEquals(
				java.util.regex.Pattern.compile(pattern, flags).matcher(input).lookingAt(),
				ours(pattern, flags).matcher(input).lookingAt(),
				"leading match of " + pattern + " over " + show(input)
			);
		}
	}

	private static String show(String input) {
		return (
			"[" + input.replace("\n", "\\n").replace("\r", "\\r").replace("\u0085", "\\u0085") + "]"
		);
	}

	// #region the constructs that translate straight through

	@ParameterizedTest
	@ValueSource(
		strings = {
			"abc",
			"a|b",
			"a*",
			"a+?",
			"a{2,3}",
			"(a)(b)",
			"(?:ab)+",
			"[a-z]+",
			"[^a-z]+",
			"\\d+",
			"\\w+",
			"\\bword\\b",
			"a(?=b)",
			"a(?!b)",
			"(?<=a)b",
			"(?<!a)b",
			"(a)\\1",
			"(?<name>a)(?<other>b)",
			"\\x41",
			"\\u0041",
			"\\t\\n\\r\\f",
			"[\\]\\[]",
			"[]a]"
		}
	)
	@DisplayName("agrees on the constructs both engines write the same way")
	void agreesOnSharedSyntax(String pattern) {
		assertMatches(pattern, "abc", "aaa", "", "xyz", "]a[", "A");
	}

	@Test
	@DisplayName("refuses a brace that is not a count, which the runtime refuses too")
	void refusesABadRepetition() {
		for (String pattern : List.of("a{,3}", "a{}", "a{", "a{b}")) {
			assertThrows(
				PatternSyntaxException.class,
				() -> java.util.regex.Pattern.compile(pattern),
				"the runtime should refuse " + pattern
			);
			assertThrows(PatternSyntaxException.class, () -> ours(pattern), pattern);
		}
	}

	// #endregion

	// #region the constructs that look the same and are not

	/**
	 * Java's whitespace is six characters. The platform's is those plus a vertical tab, a no-break
	 * space, the line and paragraph separators and more, so passing {@code \s} through would match
	 * things a JVM does not.
	 */
	@Test
	@DisplayName("agrees on what whitespace is, which is six characters and not the platform's set")
	void agreesOnWhitespace() {
		assertMatches("\\s", " ", "\t", "\n", "\u000B", "\f", "\r", "\u00A0", "\u2028", "x");
		assertMatches("\\S", " ", "\u00A0", "x");
		assertMatches("[\\s]", "\u00A0", " ");
		assertMatches("[a\\s]+", "a \u00A0");
	}

	/** {@code \v} is vertical whitespace in Java and a vertical tab in the platform's engine. */
	@Test
	@DisplayName(
		"agrees on vertical and horizontal whitespace, which the platform spells differently"
	)
	void agreesOnVerticalWhitespace() {
		assertMatches("\\v", "\n", "\u000B", "\f", "\r", "\u0085", "\u2028", " ", "x");
		assertMatches("\\V", "\n", "x");
		assertMatches("\\h", " ", "\t", "\u00A0", "\u2000", "x");
		assertMatches("\\H", " ", "x");
		assertMatches("\\R", "\r\n", "\n", "\u0085", "x");
	}

	/** Java's {@code .} excludes {@code \u0085}; the platform's does not. */
	@Test
	@DisplayName("agrees on what a dot excludes, which is one character more than the platform's")
	void agreesOnTheDot() {
		assertMatches(".", "a", "\n", "\r", "\u0085", "\u2028", "");
		assertMatches("a.b", "a\u0085b", "axb", "a\nb");
		assertMatches(".", Pattern.DOTALL, "a", "\n", "\u0085");
		assertMatches(".", Pattern.UNIX_LINES, "a", "\n", "\u0085", "\r");
	}

	/**
	 * Java's {@code $} without {@code MULTILINE} matches at the end and also just before a terminator
	 * that ends the input. The platform's matches only at the end.
	 */
	@Test
	@DisplayName("agrees on where a line ends, which is not only the end of the input")
	void agreesOnLineEnd() {
		assertMatches("a$", "a", "a\n", "a\r\n", "a\r", "a\u0085", "a\nb", "ab");
		assertMatches("a\\z", "a", "a\n");
		assertMatches("a\\Z", "a", "a\n", "a\r\n", "a\nb");
		assertMatches("a$", Pattern.MULTILINE, "a\nb", "a\r\nb", "a\rb", "a\u0085b", "a");
		assertMatches("a$", Pattern.UNIX_LINES, "a\n", "a\r");
		assertMatches("a$", Pattern.MULTILINE | Pattern.UNIX_LINES, "a\nb", "a\rb");
	}

	@Test
	@DisplayName("agrees on where a line starts, which counts a carriage-return pair as one break")
	void agreesOnLineStart() {
		assertMatches("^b", "ab", "b", "a\nb", "a\r\nb", "a\rb", "a\u0085b");
		assertMatches("^b", Pattern.MULTILINE, "a\nb", "a\r\nb", "a\rb", "a\u0085b", "ab");
		assertMatches("\\Ab", "b", "a\nb");
		assertMatches("^b", Pattern.MULTILINE | Pattern.UNIX_LINES, "a\nb", "a\rb");
	}

	@Test
	@DisplayName("agrees on a literal run and on a pattern that is all literal")
	void agreesOnLiterals() {
		assertMatches("\\Qa.b\\E", "a.b", "axb");
		assertMatches("x\\Qa+b\\Ey", "xa+by", "xaby");
		assertMatches("\\Qa.b", "a.b", "axb");
		assertMatches("a.b", Pattern.LITERAL, "a.b", "axb");
		assertMatches(Pattern.quote("a.b[c"), "a.b[c", "axb[c");
	}

	@Test
	@DisplayName("agrees on the POSIX named classes, which are expanded rather than passed through")
	void agreesOnPosixClasses() {
		assertMatches("\\p{Alpha}+", "abcXYZ", "a1b");
		assertMatches("\\p{Digit}+", "123", "a1");
		assertMatches("\\p{Alnum}+", "a1B2", "a-1");
		assertMatches("\\p{Upper}+", "ABC", "abc");
		assertMatches("\\p{Lower}+", "abc", "ABC");
		assertMatches("\\p{XDigit}+", "0aF", "g");
		assertMatches("\\p{Blank}+", " \t", "\n");
		assertMatches("\\p{ASCII}+", "abc", "\u00A0");
		assertMatches("\\P{Digit}+", "abc1", "123");
		assertMatches("[\\p{Digit}a]+", "1a2b");
	}

	@Test
	@DisplayName("agrees under the flag that ignores whitespace and comments in the pattern")
	void agreesUnderComments() {
		assertMatches("a b c", Pattern.COMMENTS, "abc", "a b c");
		assertMatches("a # this is a comment\nb", Pattern.COMMENTS, "ab", "a b");
	}

	@Test
	@DisplayName("agrees with case folding, on the ASCII the two engines both fold")
	void agreesOnCaseFolding() {
		assertMatches("abc", Pattern.CASE_INSENSITIVE, "ABC", "abc", "AbC", "xyz");
		assertMatches("[a-z]+", Pattern.CASE_INSENSITIVE, "ABC", "abc");
	}

	// #endregion

	// #region what the matcher does itself

	@ParameterizedTest
	@CsvSource({ "a, 'banana'", "'', 'ab'", "'a*', 'baaac'", "'\\d+', 'a12b345'", "'b?', 'abc'" })
	@DisplayName("replaces every match the way the runtime does, including a match of nothing")
	void replacesEveryMatch(String pattern, String input) {
		assertEquals(
			java.util.regex.Pattern.compile(pattern).matcher(input).replaceAll("X"),
			ours(pattern).matcher(input).replaceAll("X"),
			pattern + " over " + input
		);
	}

	@Test
	@DisplayName(
		"expands a group reference in a replacement, in Java's spelling and not the platform's"
	)
	void expandsGroupReferences() {
		assertEquals("b-a", ours("(a)-(b)").matcher("a-b").replaceAll("$2-$1"));
		assertEquals("b-a", ours("(?<one>a)-(?<two>b)").matcher("a-b").replaceAll("${two}-${one}"));
		assertEquals("$1", ours("a").matcher("a").replaceAll("\\$1"));
		assertEquals("a$b", ours("x").matcher("x").replaceAll(Matcher.quoteReplacement("a$b")));
		assertEquals("a\\b", ours("x").matcher("x").replaceAll(Matcher.quoteReplacement("a\\b")));
		// a run of digits is read as far as it names a group, so this is group 1 and then a 2
		assertEquals("a2", ours("(a)").matcher("a").replaceAll("$12"));
	}

	@Test
	@DisplayName("agrees with the runtime on every replacement in the corpus")
	void agreesOnReplacements() {
		for (String[] each : new String[][] {
			{ "(a)(b)", "ab", "$2$1" },
			{ "(a)", "aaa", "[$1]" },
			{ "a", "abc", "" },
			{ "(?<x>a)", "a", "${x}${x}" },
			{ "b?", "abc", "-" }
		}) {
			assertEquals(
				java.util.regex.Pattern.compile(each[0]).matcher(each[1]).replaceAll(each[2]),
				ours(each[0]).matcher(each[1]).replaceAll(each[2]),
				each[0] + " over " + each[1] + " with " + each[2]
			);
			assertEquals(
				java.util.regex.Pattern.compile(each[0]).matcher(each[1]).replaceFirst(each[2]),
				ours(each[0]).matcher(each[1]).replaceFirst(each[2]),
				"first only: " + each[0]
			);
		}
	}

	@Test
	@DisplayName("refuses a replacement that ends in the middle of a reference")
	void refusesABadReplacement() {
		assertThrows(IllegalArgumentException.class, () -> ours("a").matcher("a").replaceAll("$"));
		assertThrows(IllegalArgumentException.class, () -> ours("a").matcher("a").replaceAll("\\"));
		assertThrows(IllegalArgumentException.class, () ->
			ours("(a)").matcher("a").replaceAll("${x")
		);
		assertThrows(IllegalArgumentException.class, () -> ours("a").matcher("a").replaceAll("$x"));
	}

	@Test
	@DisplayName("appends a replacement and then the tail, the way the runtime does")
	void appendsAReplacementAndTail() {
		Matcher matcher = ours("(\\d+)").matcher("a1b22c");
		StringBuilder into = new StringBuilder();
		while (matcher.find()) matcher.appendReplacement(into, "[$1]");
		assertEquals("a[1]b[22]c", matcher.appendTail(into).toString());
	}

	@ParameterizedTest
	@CsvSource({
		"',', 'a,b,c', 3",
		"',', 'a,,b', 3",
		"',', 'a,b,,,', 2",
		"',', ',a', 2",
		"',', '', 1",
		"'\\d', 'a1b2c', 3",
		"'x', 'abc', 1"
	})
	@DisplayName("splits the way the runtime does, down to which empty pieces survive")
	void splitsTheSameWay(String pattern, String input, int pieces) {
		assertArrayEquals(
			java.util.regex.Pattern.compile(pattern).split(input),
			ours(pattern).split(input),
			pattern + " over " + input
		);
		assertEquals(pieces, ours(pattern).split(input).length, pattern + " over " + input);
	}

	@Test
	@DisplayName("splits with a limit the way the runtime does")
	void splitsWithALimit() {
		for (int limit : new int[] { -1, 0, 1, 2, 3, 10 }) {
			assertArrayEquals(
				java.util.regex.Pattern.compile(",").split("a,b,c,,", limit),
				ours(",").split("a,b,c,,", limit),
				"limit " + limit
			);
		}
	}

	@Test
	@DisplayName(
		"resumes a search from where it was told to, and refuses a position outside the input"
	)
	void resumesFromAPosition() {
		Matcher matcher = ours("a").matcher("aXa");
		assertTrue(matcher.find(1));
		assertEquals(2, matcher.start());
		assertFalse(matcher.find(3));

		assertThrows(IndexOutOfBoundsException.class, () -> ours("a").matcher("a").find(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> ours("a").matcher("a").find(2));
	}

	@Test
	@DisplayName("keeps a match after the matcher moved on")
	void keepsAMatch() {
		Matcher matcher = ours("(?<letter>[a-z])(\\d)").matcher("a1 b2");
		assertTrue(matcher.find());
		java.util.regex.MatchResult first = matcher.toMatchResult();
		assertTrue(matcher.find());

		assertEquals("a1", first.group());
		assertEquals("a", first.group(1));
		assertEquals("a", first.group("letter"));
		assertEquals("1", first.group(2));
		assertEquals(0, first.start());
		assertEquals(2, first.end());
		assertEquals(0, first.start(1));
		assertEquals(1, first.end(1));
		assertEquals(0, first.start("letter"));
		assertEquals(1, first.end("letter"));
		assertEquals(2, first.groupCount());
		assertEquals(Map.of("letter", 1), first.namedGroups());
		assertThrows(IllegalArgumentException.class, () -> first.group("nothing"));

		assertEquals(2, matcher.results().count());
	}

	@Test
	@DisplayName("answers null for a group that took part in no match")
	void answersNullForAnUnmatchedGroup() {
		Matcher matcher = ours("(a)|(b)").matcher("a");
		assertTrue(matcher.find());
		assertEquals("a", matcher.group(1));
		assertNull(matcher.group(2));
		assertEquals(-1, matcher.start(2));
		assertEquals(-1, matcher.end(2));
	}

	@Test
	@DisplayName("refuses a group that is not there, and a reading before anything matched")
	void refusesABadGroup() {
		Matcher matcher = ours("(a)").matcher("a");
		assertThrows(IllegalStateException.class, matcher::group);
		assertThrows(IllegalStateException.class, () -> matcher.start(0));
		assertThrows(IllegalStateException.class, () -> matcher.end(0));

		assertTrue(matcher.find());
		assertThrows(IndexOutOfBoundsException.class, () -> matcher.group(2));
		assertThrows(IndexOutOfBoundsException.class, () -> matcher.group(-1));
		assertThrows(IllegalArgumentException.class, () -> matcher.group("nothing"));
	}

	@Test
	@DisplayName("forgets everything when it is reset")
	void forgetsWhenReset() {
		Matcher matcher = ours("a").matcher("aa");
		assertTrue(matcher.find());
		assertEquals(0, matcher.start());
		matcher.reset();
		assertTrue(matcher.find());
		assertEquals(0, matcher.start());
		assertTrue(matcher.reset("ba").find());
	}

	// #endregion

	// #region inputs chosen to hurt

	/**
	 * A run of digits after {@code $} was accumulated into an int, and a long enough run wrapped it
	 * negative, which slipped past the check that the group exists.
	 */
	@Test
	@DisplayName("a group number too long to hold does not wrap past the group check")
	void aHugeGroupNumberDoesNotWrap() {
		// the first digit has to name a group, so this is refused rather than read as group 9999...
		assertThrows(IndexOutOfBoundsException.class, () ->
			java.util.regex.Pattern.compile("(a)").matcher("a").replaceAll("$99999999999")
		);
		assertThrows(IndexOutOfBoundsException.class, () ->
			ours("(a)").matcher("a").replaceAll("$99999999999")
		);
		// and the digits after it are taken only while they still name one, so this is group 1 then text
		assertEquals(
			java.util.regex.Pattern.compile("(a)").matcher("a").replaceAll("$12147483648"),
			ours("(a)").matcher("a").replaceAll("$12147483648")
		);
	}

	@Test
	@DisplayName("an empty input and an empty pattern are not a special case anywhere")
	void handlesEmptyThings() {
		assertMatches("", "", "a");
		assertMatches("a", "");
		assertMatches("a*", "");
		assertArrayEquals(java.util.regex.Pattern.compile("a").split(""), ours("a").split(""));
		assertArrayEquals(
			java.util.regex.Pattern.compile("").split("", -1),
			ours("").split("", -1)
		);
		assertEquals("", ours("a").matcher("").replaceAll("X"));
	}

	@Test
	@DisplayName("an input far longer than the pattern behaves the same as a short one")
	void handlesAVeryLongInput() {
		String long1 = "a".repeat(50_000);

		assertEquals(
			java.util.regex.Pattern.compile("a+").split(long1 + "b").length,
			ours("a+").split(long1 + "b").length
		);
		assertTrue(ours("a+").matcher(long1).matches());
		assertEquals(50_000, ours("a").matcher(long1).results().count());
	}

	@Test
	@DisplayName("a search over a pattern that matches nothing terminates at the end of the input")
	void terminatesOnAMatchOfNothing() {
		Matcher matcher = ours("").matcher("abc");
		int found = 0;
		while (matcher.find()) found++;
		assertEquals(4, found, "a match of nothing at each position and one at the end");

		java.util.regex.Matcher runtime = java.util.regex.Pattern.compile("").matcher("abc");
		int expected = 0;
		while (runtime.find()) expected++;
		assertEquals(expected, found);
	}

	// #endregion

	// #region the pattern as an object

	@Test
	@DisplayName("carries what it was compiled from, and names its groups")
	void carriesItsPattern() {
		Pattern pattern = ours("(?<one>a)(?<two>b)", Pattern.CASE_INSENSITIVE);

		assertEquals("(?<one>a)(?<two>b)", pattern.pattern());
		assertEquals("(?<one>a)(?<two>b)", pattern.toString());
		assertEquals(Pattern.CASE_INSENSITIVE, pattern.flags());
		assertEquals(Map.of("one", 1, "two", 2), pattern.namedGroups());
		assertEquals(pattern, pattern.matcher("ab").pattern());
	}

	@Test
	@DisplayName("answers whether a whole input matches, and offers the same as a predicate")
	void answersWholeMatches() {
		assertTrue(Pattern.compile("a+", 0, Native.ENGINE).asMatchPredicate().test("aaa"));
		assertFalse(Pattern.compile("a+", 0, Native.ENGINE).asMatchPredicate().test("aab"));
		assertTrue(Pattern.compile("a+", 0, Native.ENGINE).asPredicate().test("xaax"));
		assertFalse(Pattern.compile("a+", 0, Native.ENGINE).asPredicate().test("xxx"));
		assertEquals(3, ours(",").splitAsStream("a,b,c").count());
	}

	// #endregion

	// #region refusals

	@ParameterizedTest
	@CsvSource(
		delimiter = ';',
		value = {
			"(?>a);atomic group",
			"a*+;possessive quantifier",
			"a++;possessive quantifier",
			"a?+;possessive quantifier",
			"a{2}+;possessive quantifier",
			"[a[bc]];character class inside another",
			"[a&&[b]];intersection",
			"\\p{L};property syntax",
			"\\p{IsAlphabetic};property syntax",
			"\\pL;one-letter character property",
			"\\G a;end of the previous match",
			"\\X;grapheme cluster",
			"\\N{X};character by name",
			"a\\;trailing backslash",
			"[a;unclosed character class",
			"\\b{g};grapheme boundary"
		}
	)
	@DisplayName("refuses what it cannot express, naming what it was")
	void refusesWhatItCannotExpress(String pattern, String reason) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(
			refused.getMessage().contains(reason),
			"for " + pattern + " the message was: " + refused.getMessage()
		);
	}

	@Test
	@DisplayName(
		"refuses a flag written inside the pattern, whether it applies to all of it or not"
	)
	void refusesInlineFlags() {
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?i)abc"))
				.getMessage()
				.contains("pass it to Pattern.compile")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("a(?i)bc"))
				.getMessage()
				.contains("applies from where it is written")
		);
		assertThrows(PatternSyntaxException.class, () -> ours("(?i:abc)"));
	}

	@Test
	@DisplayName("refuses the two flags the platform cannot honour")
	void refusesTheImpossibleFlags() {
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("a", Pattern.CANON_EQ))
				.getMessage()
				.contains("CANON_EQ")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () ->
				ours("a", Pattern.UNICODE_CHARACTER_CLASS)
			)
				.getMessage()
				.contains("UNICODE_CHARACTER_CLASS")
		);
	}

	@Test
	@DisplayName("refuses a negated class or a wide escape inside a character class")
	void refusesWideEscapesInAClass() {
		assertThrows(PatternSyntaxException.class, () -> ours("[\\S]"));
		assertThrows(PatternSyntaxException.class, () -> ours("[\\V]"));
		assertThrows(PatternSyntaxException.class, () -> ours("[\\H]"));
		assertThrows(PatternSyntaxException.class, () -> ours("[\\P{Digit}]"));
		assertThrows(PatternSyntaxException.class, () -> ours("[\\R]"));
	}

	@Test
	@DisplayName("accepts the flag that only says what the platform already does")
	void acceptsUnicodeCase() {
		assertMatches("abc", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "ABC", "abc");
	}

	// #endregion
}
