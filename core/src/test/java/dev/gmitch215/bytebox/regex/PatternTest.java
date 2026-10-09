package dev.gmitch215.bytebox.regex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
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
	@DisplayName(
		"agrees that a line does not start at the end of the input, which a JVM never reports"
	)
	void agreesThatNoLineStartsAtTheEnd() {
		String[] inputs = { "", "\n", "a\n", "a\n\n", "a\r\n", "a\r", "a\u0085", "\n\n" };
		assertMatches("^", Pattern.MULTILINE, inputs);
		assertMatches("^", Pattern.MULTILINE | Pattern.UNIX_LINES, inputs);
		assertMatches("^$", Pattern.MULTILINE, inputs);
		assertMatches("(?m)^", inputs);
		assertMatches("^a*", Pattern.MULTILINE, inputs);
		assertMatches("^", inputs);
		assertMatches("\\A", inputs);
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
	@DisplayName("reads whitespace and the end of a comment under the flag as a JVM does")
	void agreesOnWhitespaceAndCommentEndsUnderComments() {
		int comments = Pattern.COMMENTS;
		String vertical = chr(0x0B);
		String next = chr(0x85);
		String separator = chr(0x2028);
		assertMatches("a\fb", comments, "ab", "a\fb");
		assertMatches("a" + vertical + "b", comments, "ab", "a" + vertical + "b");
		assertMatches("a # c\rb", comments, "ab", "a\rb", "a");
		assertMatches("a # c\r\nb", comments, "ab", "a\r\nb", "a");
		assertMatches("a # c" + separator + "b", comments, "a" + separator + "b", "ab", "a");
		assertMatches("a # c" + next + "b", comments, "a" + next + "b", "ab", "a");
		assertMatches("a # c\rb", comments | Pattern.UNIX_LINES, "ab", "a\rb", "a");
		assertMatches("a # c" + separator + "b", comments | Pattern.UNIX_LINES, "ab", "a");
		assertMatches("a #\n+", comments, "a", "aa", "+");
	}

	private static String chr(int code) {
		return String.valueOf((char) code);
	}

	@Test
	@DisplayName("agrees with case folding, on the ASCII the two engines both fold")
	void agreesOnCaseFolding() {
		assertMatches("abc", Pattern.CASE_INSENSITIVE, "ABC", "abc", "AbC", "xyz");
		assertMatches("[a-z]+", Pattern.CASE_INSENSITIVE, "ABC", "abc");
	}

	// #endregion

	// #region case folding that stops at ASCII, as it does without UNICODE_CASE

	private static final String[] FOLDING_INPUTS = {
		"abcABC",
		"xyzXYZ",
		"AB",
		"ab",
		"aB",
		"-",
		"]",
		"a-z",
		"\u00e9\u00c9",
		"\u00e0\u00c0",
		"\u03c3\u03a3\u03c2",
		"\u017f",
		"\u212a",
		"\u0130\u0131",
		"s",
		"S",
		"k",
		"K",
		"i",
		"I",
		"3",
		"_"
	};

	@Test
	@DisplayName(
		"folds only ASCII without UNICODE_CASE, where the platform's own folding goes further"
	)
	void foldsOnlyAscii() {
		int flags = Pattern.CASE_INSENSITIVE;
		assertMatches("\u00e9", flags, "\u00e9", "\u00c9", "e", "E");
		assertMatches("\u00c9", flags, "\u00e9", "\u00c9", "e", "E");
		assertMatches("\u03c3", flags, "\u03c3", "\u03a3", "\u03c2");
		assertMatches("\u03c2", flags, "\u03c3", "\u03a3", "\u03c2");
		assertMatches("[\u00e0-\u00e9]", flags, "\u00e0", "\u00c0", "\u00e9", "\u00c9");
		assertMatches("[\u00e0-\u00e9]+", flags, "\u00e0\u00c0\u00e9\u00c9");
		assertMatches("[^\u00e0-\u00e9]", flags, "\u00e0", "\u00c0", "a");
		assertMatches("[a-\u00e9]", flags, "a", "A", "\u00c0", "\u00e0", "\u00c9");
		assertMatches("s", flags, "s", "S", "\u017f");
		assertMatches("k", flags, "k", "K", "\u212a");
		assertMatches("\u212a", flags, "k", "K", "\u212a");
		assertMatches("i", flags, "i", "I", "\u0130", "\u0131");
		assertMatches("[a-z]+", flags, "k\u212a\u017fAz");
		assertMatches("[^a-z]", flags, "k", "K", "\u017f", "\u212a", "1");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"abc",
			"a|b",
			"x(?:yz)+",
			"(?<n>ab)",
			"a{2}",
			"a+?b",
			"(?=a)a",
			"(?<=a)b",
			"\\x41",
			"\\x41\\x42",
			"\\u0041",
			"\\u0041b",
			"\\Qab\\E",
			"\\Qab\\Ec",
			"[abc]",
			"[a-c]+",
			"[A-C]+",
			"[a-cX]+",
			"[^a-c]",
			"[^A-C]+",
			"[\\x41-\\x43]",
			"[a-c\\x44]",
			"[\\Qab\\E]",
			"[a\\-z]",
			"[-a]",
			"[a-]",
			"[a-z-]",
			"[]a]",
			"[+\\-z]",
			"[\\w]",
			"[\\d_a]",
			"[^\\w]",
			"[\\t-a]",
			"[Z-a]",
			"[W-b]",
			"[^Z-a]",
			"\\p{Lower}",
			"\\p{Upper}+",
			"\\P{Lower}",
			"\\P{Upper}+",
			"[\\p{Lower}]",
			"[^\\p{Lower}]",
			"[\\p{Upper}\\d]+",
			"\\p{Alpha}+",
			"\\p{InBasicLatin}+",
			"\\P{InBasicLatin}+",
			"[\\p{InGreek}a]+",
			"[\\P{InGreek}]+",
			"[A-Fa-f0-9]+",
			"\\w+",
			"\\bK\\b"
		}
	)
	@DisplayName("folds ASCII in every position a letter can be written, as a JVM does")
	void foldsAsciiEverywhere(String pattern) {
		assertMatches(pattern, Pattern.CASE_INSENSITIVE, FOLDING_INPUTS);
	}

	@Test
	@DisplayName("folds a pattern that is all literal, and one that quotes its letters")
	void foldsLiterals() {
		assertMatches("a.B", Pattern.CASE_INSENSITIVE | Pattern.LITERAL, "a.b", "A.B", "axb");
		assertMatches("\\Qa.B\\E", Pattern.CASE_INSENSITIVE, "a.b", "A.B", "axb");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(a)\\1",
			"(?<n>a)\\k<n>",
			"(?i)(a)\\1",
			"(a)(?:b|\\1)",
			"(a)[b]\\1",
			"x\\0101(a)\\1",
			"(a)\\cA\\1",
			"(a)(?>b)\\1",
			"(?>a)(b)\\1"
		}
	)
	@DisplayName(
		"refuses a backreference under CASE_INSENSITIVE alone, which a pair of cases cannot fold as a JVM does"
	)
	void refusesABackreferenceUnderAsciiFolding(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern, Pattern.CASE_INSENSITIVE)
		);
		assertTrue(refused.getMessage().contains("backreference"), refused.getMessage());
		assertTrue(refused.getMessage().contains("UNICODE_CASE"), refused.getMessage());
		assertEquals(pattern, refused.getPattern());
	}

	@Test
	@DisplayName("takes a backreference wherever the platform's folding is the one a JVM uses")
	void keepsABackreferenceThatNeedsNoAsciiFolding() {
		int unicode = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		assertMatches("(a)\\1", unicode, "aa", "aA", "Aa", "AA", "ab");
		assertMatches("(?<n>a)\\k<n>", unicode, "aA", "aa", "ab");
		assertMatches("(?iu)(a)\\1", 0, "aA", "ab");
		assertMatches("(a)\\1", 0, "aa", "aA");
		assertMatches("(a)\\1", Pattern.CASE_INSENSITIVE | Pattern.LITERAL, "(a)\\1", "aa");
		assertMatches("(?i)(a)\\1", Pattern.CASE_INSENSITIVE | Pattern.LITERAL, "(?i)(a)\\1");
		assertEquals("i", Translate.translate("(a)\\1", unicode).jsFlags);
		assertEquals("", Translate.translate("(a)\\1", 0).jsFlags);
		assertEquals("", Translate.translate("a\\0101", Pattern.CASE_INSENSITIVE).jsFlags);
	}

	@Test
	@DisplayName("keeps the platform's flag with UNICODE_CASE and drops it without")
	void choosesTheFlag() {
		int ascii = Pattern.CASE_INSENSITIVE;
		assertEquals("", Translate.translate("abc", ascii).jsFlags);
		assertEquals("", Translate.translate("(?i)abc", 0).jsFlags);
		assertEquals("i", Translate.translate("abc", ascii | Pattern.UNICODE_CASE).jsFlags);
		assertEquals("i", Translate.translate("(?iu)abc", 0).jsFlags);
		assertEquals("", Translate.translate("abc", 0).jsFlags);
		assertEquals("[aA][bB][cC]", Translate.translate("abc", ascii).source);
		assertEquals("abc", Translate.translate("abc", ascii | Pattern.UNICODE_CASE).source);
	}

	@Test
	@DisplayName("agrees with UNICODE_CASE on the folds the platform also makes")
	void agreesWithUnicodeCase() {
		int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		assertMatches("\u00e9", flags, "\u00e9", "\u00c9", "e");
		assertMatches("\u03c3", flags, "\u03c3", "\u03a3", "\u03c2");
		assertMatches("[\u00e0-\u00e9]+", flags, "\u00e0\u00c0\u00e9\u00c9");
		assertMatches("(\u00e9)\\1", flags, "\u00e9\u00c9", "\u00e9\u00e9");
		assertMatches("abc", flags, "ABC");
	}

	// #endregion

	// #region a flag that opens the pattern

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(?i)HeLLo",
			"(?s).",
			"(?m)^b",
			"(?x) a b # c",
			"(?is)",
			"(?i)(?s)x",
			"(?i)",
			"(?-i)x",
			"(?i-s)a.",
			"(?ii)x",
			"(?i-i)x",
			"(?d).",
			"(?iu)\u00e9",
			"(?u)a",
			"(?)a",
			"(?-)a",
			"(?i-)a",
			"(?m)a$",
			"(?mi)^HELLO$"
		}
	)
	@DisplayName("reads a flag that opens the pattern, as the flags it sets")
	void readsALeadingFlag(String pattern) {
		assertMatches(
			pattern,
			"hello",
			"HELLO",
			"help",
			"a\nb",
			"ab",
			"a b",
			"A\n",
			"a\r",
			"x",
			"X",
			"",
			"\u00c9",
			"\u00e9",
			"a",
			"A",
			"hello\nHELLO\n"
		);
		assertEquals(
			java.util.regex.Pattern.compile(pattern).flags(),
			ours(pattern).flags(),
			"flags of " + pattern
		);
	}

	@Test
	@DisplayName("applies a leading flag on top of the flags passed in, and takes one away")
	void mergesALeadingFlag() {
		assertMatches("(?-i)x", Pattern.CASE_INSENSITIVE, "x", "X");
		assertMatches("(?)x", Pattern.CASE_INSENSITIVE, "x", "X");
		assertMatches("(?-s).", Pattern.DOTALL, "\n", "a");
		assertMatches("(?-x) a", Pattern.COMMENTS, " a", "a");
		assertMatches("(?s)a.", Pattern.CASE_INSENSITIVE, "A\n", "ab");
		assertMatches("(?i)a", Pattern.MULTILINE, "A");
		assertEquals(
			java.util.regex.Pattern.compile("(?-i)x", Pattern.CASE_INSENSITIVE).flags(),
			ours("(?-i)x", Pattern.CASE_INSENSITIVE).flags()
		);
		assertEquals(
			java.util.regex.Pattern.compile("(?s)x", Pattern.CASE_INSENSITIVE).flags(),
			ours("(?s)x", Pattern.CASE_INSENSITIVE).flags()
		);
	}

	@Test
	@DisplayName("does not read a flag in a pattern that is a literal")
	void leavesALiteralAlone() {
		assertMatches("(?i)a", Pattern.LITERAL, "(?i)a", "A");
	}

	@Test
	@DisplayName("refuses a flag anywhere but the start, or one that covers only a group")
	void refusesAFlagThatDoesNotOpenThePattern() {
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("a(?i)bc"))
				.getMessage()
				.contains("applies from where it is written")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?i)a(?s)b"))
				.getMessage()
				.contains("applies from where it is written")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?i:abc)"))
				.getMessage()
				.contains("applies to one group")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?i)(?-i:abc)"))
				.getMessage()
				.contains("applies to one group")
		);
		assertThrows(PatternSyntaxException.class, () -> ours("(?i"));
		assertThrows(PatternSyntaxException.class, () -> ours("(?y)a"));
	}

	@Test
	@DisplayName(
		"refuses the flags the platform cannot honour when they are written in the pattern"
	)
	void refusesTheImpossibleFlagsWrittenInThePattern() {
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?U)a"))
				.getMessage()
				.contains("UNICODE_CHARACTER_CLASS")
		);
		assertTrue(
			assertThrows(PatternSyntaxException.class, () -> ours("(?c)a"))
				.getMessage()
				.contains("CANON_EQ")
		);
	}

	// #endregion

	// #region the Unicode blocks, written as ranges

	private static final String[] BLOCK_INPUTS = {
		"e\u0301",
		"caf\u00e9",
		"a\u0300\u0301b",
		"abc",
		"abc \u03b1\u03b2 d",
		"\u03b1",
		"x\u03b1y",
		"ab\u00e9\u00ff",
		"ab\u0431\u0432",
		"ab\u4e2d\u6587",
		"ab\uac00",
		"a\ue000",
		"a\ufffd",
		"\u0000\uffff",
		"AbC"
	};

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\p{InCombiningDiacriticalMarks}+",
			"\\p{InGreek}+",
			"\\P{InGreek}+",
			"[\\p{InGreek}x]+",
			"[^\\p{InGreek}]+",
			"[\\P{InGreek}]+",
			"[a\\P{InGreek}]+",
			"[\\P{InGreek}\\p{InGreek}]+",
			"\\p{blk=Greek}+",
			"\\p{block=greek and coptic}+",
			"\\P{blk=Greek}+",
			"\\p{BLK=Greek}+",
			"[\\p{block=Cyrillic}]+",
			"\\p{InBasic_Latin}+",
			"\\p{InBasicLatin}+",
			"\\P{InBasicLatin}+",
			"\\p{InLatin-1Supplement}+",
			"\\p{InLatin-1 Supplement}+",
			"\\p{InCyrillic}+",
			"\\p{InCJKUnifiedIdeographs}+",
			"\\p{InHangulSyllables}+",
			"\\p{InPrivateUseArea}",
			"\\p{InSpecials}",
			"\\P{InSpecials}+",
			"[\\P{InBasicLatin}]+",
			"[^\\P{InBasicLatin}]+"
		}
	)
	@DisplayName("agrees on the Unicode blocks, inside a class and out, positive and negated")
	void agreesOnBlocks(String pattern) {
		assertMatches(pattern, BLOCK_INPUTS);
	}

	/** Characters above U+FFFF, which a pattern read as UTF-16 units would take apart. */
	private static final String EMOJI = new String(Character.toChars(0x1F600));

	private static final String MATH = new String(Character.toChars(0x1D7D8));

	private static final String ALPHA = String.valueOf((char) 0x3b1);

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\P{InGreek}",
			"\\P{InGreek}+",
			"\\P{InGreek}{2}",
			"\\P{blk=Greek}",
			"\\P{InBasicLatin}+",
			"\\P{InSpecials}",
			"[\\P{InGreek}]",
			"[\\P{InGreek}x]+",
			"[a\\P{InBasicLatin}]+",
			"[^\\P{InBasicLatin}]+",
			"x\\P{InGreek}y",
			"^\\P{InGreek}$",
			"\\P{InGreek}*+b",
			"(?>\\P{InGreek}+)b"
		}
	)
	@DisplayName("takes a character above U+FFFF whole under a negated block, as a JVM does")
	void agreesOnNegatedBlocksOverSupplementaryCharacters(String pattern) {
		assertMatches(
			pattern,
			EMOJI,
			"a" + EMOJI + "b",
			EMOJI + EMOJI,
			"x" + MATH + "y",
			MATH + "b",
			ALPHA + EMOJI,
			EMOJI + ALPHA,
			"\ud83d",
			"\ude00",
			"a\ud83d",
			"\ud83dx",
			"\ude00\ud83d",
			"ab",
			ALPHA
		);
		assertMatches(pattern, Pattern.CASE_INSENSITIVE, "a" + EMOJI + "A", EMOJI + "b");
		assertMatches(pattern, Pattern.DOTALL, "a" + EMOJI + "b");
	}

	@Test
	@DisplayName("takes a backreference to a negated block over a character above U+FFFF")
	void agreesOnABackreferenceToANegatedBlock() {
		assertMatches(
			"(\\P{InGreek})\\1",
			EMOJI + EMOJI,
			EMOJI + MATH,
			"aa",
			"a" + EMOJI + EMOJI,
			ALPHA + ALPHA
		);
	}

	@Test
	@DisplayName("reads a pattern with a negated block as code points, and no other block")
	void readsANegatedBlockAsCodePoints() {
		assertEquals("v", Translate.translate("\\P{InGreek}", 0).jsFlags);
		assertEquals("[^\\u0370-\\u03ff]", Translate.translate("\\P{InGreek}", 0).source);
		assertEquals("v", Translate.translate("\\P{blk=Greek}+", 0).jsFlags);
		assertEquals("v", Translate.translate("a[\\P{InGreek}]", 0).jsFlags);
		assertEquals(
			"[\\u0000-\\u036f\\u0400-\\u{10ffff}]",
			Translate.translate("[\\P{InGreek}]", 0).source
		);
		assertEquals("v", Translate.translate("\\P{InGreek}", Pattern.CASE_INSENSITIVE).jsFlags);
		assertEquals("v", Translate.translate("(?i)\\P{InGreek}", 0).jsFlags);
		assertEquals("", Translate.translate("\\p{InGreek}", 0).jsFlags);
		assertEquals("", Translate.translate("[\\p{InGreek}x]", 0).jsFlags);
		assertEquals("", Translate.translate("[^\\p{InGreek}]", 0).jsFlags);
		assertEquals("", Translate.translate("\\P{Lower}", 0).jsFlags);
		assertEquals(
			"i",
			Translate.translate(
				"\\p{InGreek}",
				Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE
			).jsFlags
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\\P{InGreek}", "[\\P{InGreek}]", "x\\P{blk=Greek}+" })
	@DisplayName(
		"refuses a negated block under UNICODE_CASE, where the flag it needs changes folding"
	)
	void refusesANegatedBlockUnderPlatformFolding(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
		);
		assertTrue(refused.getMessage().contains("negated"), refused.getMessage());
		assertTrue(refused.getMessage().contains("UNICODE_CASE"), refused.getMessage());
		assertEquals(pattern, refused.getPattern());
		assertMatches(pattern, "a", ALPHA, EMOJI);
	}

	@Test
	@DisplayName("finds the combining marks that strip the accents off decomposed text")
	void findsCombiningMarks() {
		String pattern = "\\p{InCombiningDiacriticalMarks}+";
		for (String text : List.of(
			"e\u0301cole\u0300",
			"cafe\u0301",
			"plain",
			"a\u0300\u0301\u0302b"
		)) {
			assertEquals(expected(pattern, 0, text), found(pattern, 0, text), text);
			assertEquals(
				java.util.regex.Pattern.compile(pattern).matcher(text).replaceAll(""),
				ours(pattern).matcher(text).replaceAll(""),
				text
			);
		}
	}

	@Test
	@DisplayName("takes a block under a flag the way a JVM does")
	void agreesOnBlocksUnderFlags() {
		assertMatches("\\p{InBasicLatin}+", Pattern.CASE_INSENSITIVE, "aB\u00e9");
		assertMatches("\\p{InGreek}+", Pattern.CASE_INSENSITIVE, "ab\u03b1\u03a3");
		assertMatches(
			"\\p{InGreek}+",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE,
			"ab\u03b1\u03a3"
		);
		assertMatches("\\p{InGreek}+", Pattern.COMMENTS, "ab\u03b1\u03a3");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\p{InEmoticons}",
			"\\p{InMathematicalAlphanumericSymbols}",
			"\\p{InHighSurrogates}",
			"\\p{InLowSurrogates}",
			"\\p{InNoSuchBlock}",
			"\\p{In}",
			"\\p{blk=Emoticons}",
			"\\P{InEmoticons}",
			"[\\p{InEmoticons}]"
		}
	)
	@DisplayName("refuses a block above U+FFFF or made of surrogates, naming it")
	void refusesABlockItCannotHold(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(
			refused.getMessage().contains("basic multilingual plane"),
			"for " + pattern + " the message was: " + refused.getMessage()
		);
		String name = pattern.substring(pattern.indexOf('{') + 1, pattern.indexOf('}'));
		assertTrue(refused.getMessage().contains(name), "the message does not name " + name);
	}

	@Test
	@DisplayName("still refuses a category, a script and a negated POSIX class inside a class")
	void stillRefusesCategories() {
		for (String pattern : List.of(
			"\\p{L}",
			"\\p{Lu}",
			"\\p{IsGreek}",
			"\\p{sc=Greek}",
			"\\p{gc=L}"
		)) {
			assertTrue(
				assertThrows(PatternSyntaxException.class, () -> ours(pattern))
					.getMessage()
					.contains("the platform's own property syntax"),
				pattern
			);
		}
		assertThrows(PatternSyntaxException.class, () -> ours("[\\P{Digit}]"));
	}

	@Test
	@DisplayName("holds the table the generator writes from this JDK, spelling for spelling")
	void blockTableIsWhatTheGeneratorWrites() throws Exception {
		Path blocks = Path.of("src/main/java/dev/gmitch215/bytebox/regex/Blocks.java");
		Process generator = new ProcessBuilder(
			Path.of(System.getProperty("java.home"), "bin", "java").toString(),
			"--add-opens",
			"java.base/java.lang=ALL-UNNAMED",
			"-cp",
			System.getProperty("java.class.path"),
			GenerateBlocks.class.getName(),
			blocks.toString()
		)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start();
		String generated = new String(
			generator.getInputStream().readAllBytes(),
			StandardCharsets.UTF_8
		);
		assertEquals(0, generator.waitFor());
		assertEquals(Files.readString(blocks), generated);
	}

	@Test
	@DisplayName("holds every block of the basic multilingual plane as this JDK has it")
	void blockTableMatchesTheJdk() {
		String[] owner = new String[65536];
		for (String entry : Blocks.TABLE) {
			String[] parts = entry.split(" ", 3);
			int low = Integer.parseInt(parts[0], 16);
			int high = Integer.parseInt(parts[1], 16);
			String[] names = parts[2].split("\\|");
			for (int unit = low; unit <= high; unit++) {
				assertNull(owner[unit], "U+" + Integer.toHexString(unit) + " is in two entries");
				owner[unit] = names[0];
			}
			for (String name : names) {
				assertEquals(
					names[0],
					Character.UnicodeBlock.forName(name).toString(),
					name + " is a spelling of another block"
				);
				assertArrayEquals(new int[] { low, high }, Blocks.find(name), name);
				assertArrayEquals(
					new int[] { low, high },
					Blocks.find(name.toLowerCase(java.util.Locale.ROOT)),
					name
				);
			}
		}
		for (int unit = 0; unit <= 0xFFFF; unit++) {
			Character.UnicodeBlock block = Character.UnicodeBlock.of(unit);
			boolean surrogate = unit >= 0xD800 && unit <= 0xDFFF;
			assertEquals(
				block == null || surrogate ? null : block.toString(),
				owner[unit],
				"U+" + Integer.toHexString(unit)
			);
		}
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

	// #region the escapes a JavaScript engine spells differently

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\a",
			"\\e",
			"[\\a]",
			"[\\e]",
			"[\\h]",
			"[\\v]",
			"a\\Zb",
			"a\\Z",
			"\\Qa+b\\E",
			"[\\Qa-b\\E]"
		}
	)
	@DisplayName("rewrites an escape a JavaScript engine spells differently, or not at all")
	void escapes(String pattern) {
		assertMatches(pattern, "a", "\u0007", "\u001B", "a+b", "a-b", "a\nb", "a b", "a\tb");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"Alpha",
			"Digit",
			"Alnum",
			"Punct",
			"Graph",
			"Print",
			"Blank",
			"Cntrl",
			"XDigit",
			"Space",
			"Lower",
			"Upper",
			"ASCII"
		}
	)
	@DisplayName("writes each POSIX class out as the range it stands for")
	void posixClasses(String name) {
		assertMatches("\\p{" + name + "}", "a", "Z", "7", "!", " ", "\t", "\u0000", "\u007F", "~");
		assertMatches("[x\\p{" + name + "}]", "a", "Z", "7", "!", " ", "x");
	}

	@Test
	@DisplayName("refuses a property it has no range for, rather than passing it through")
	void unknownProperty() {
		assertThrows(PatternSyntaxException.class, () -> ours("\\p{IsGreek}"));
		assertThrows(PatternSyntaxException.class, () -> ours("[\\p{IsGreek}]"));
	}

	@Test
	@DisplayName("refuses a quotation that never opened, and a backslash with nothing after it")
	void danglingEscapes() {
		assertThrows(PatternSyntaxException.class, () -> ours("a\\E"));
		assertThrows(PatternSyntaxException.class, () -> ours("a\\"));
		assertThrows(PatternSyntaxException.class, () -> ours("[a\\"));
	}

	@Test
	@DisplayName("escapes a control character in a quoted run rather than writing it raw")
	void quotedControlCharacters() {
		assertMatches("\\Qa\nb\\E", "a\nb", "ab");
		assertMatches("\\Qa\rb\\E", "a\rb");
		assertMatches("\\Qa\u0001b\\E", "a\u0001b");
		assertMatches("\\Qa\u007Fb\\E", "a\u007Fb");
		assertMatches("\\Qa\u0010b\\E", "a\u0010b");
	}

	// #endregion

	// #region the escapes that stand for one character, read the way a JVM reads them

	@ParameterizedTest
	@CsvSource(
		delimiter = ';',
		value = {
			"\\x{41};\\x41",
			"\\x{0041};\\x41",
			"\\x{000000041};\\x41",
			"\\x{e9};\\xe9",
			"\\x{0};\\x00",
			"\\x{100};\\u0100",
			"\\x{FFFF};\\uffff",
			"\\x{1F600};(?:\\ud83d\\ude00)",
			"\\x{10FFFF};(?:\\udbff\\udfff)",
			"\\x{1F600}+;(?:\\ud83d\\ude00)+",
			"a\\x{42}c;a\\x42c",
			"[\\x{41}];[\\x41]",
			"[a\\x{42}-\\x{44}];[a\\x42-\\x44]",
			"[\\x{100}-\\x{200}];[\\u0100-\\u0200]",
			"\\0101;\\x41",
			"\\0101+;\\x41+",
			"\\0101{2};\\x41{2}",
			"\\012;\\x0a",
			"\\07;\\x07",
			"\\00;\\x00",
			"\\0377;\\xff",
			"\\0400;\\x200",
			"\\0777;\\x3f7",
			"[\\0101-\\0132];[\\x41-\\x5a]",
			"[\\0153];[\\x6b]",
			"\\cA;\\x01",
			"\\ca;\\x21",
			"\\c1;\\x71",
			"\\c?;\\x7f",
			"\\c@;\\x00",
			"\\c[;\\x1b",
			"[\\cA-\\cZ];[\\x01-\\x1a]",
			"\\x41;\\x41",
			"\\u0041;\\u0041",
			"[\\x41-\\x5A];[\\x41-\\x5A]",
			"\\uD83D\\uDE00;(?:\\ud83d\\ude00)",
			"\\uD83D\\uDE00+;(?:\\ud83d\\ude00)+",
			"\\uD83D;\\uD83D"
		}
	)
	@DisplayName("writes an escape for one character as one the platform reads the same way")
	void writesAnEscapeForOneCharacterPlainly(String pattern, String source) {
		assertEquals(source, Translate.translate(pattern, 0).source);
		assertEquals("", Translate.translate(pattern, 0).jsFlags);
	}

	@Test
	@DisplayName("writes an escape that stands for a letter as the pair of its cases under folding")
	void writesAnEscapedLetterAsItsPairUnderAsciiFolding() {
		int ascii = Pattern.CASE_INSENSITIVE;
		assertEquals("[kK]", Translate.translate("\\x{6b}", ascii).source);
		assertEquals("[kK]", Translate.translate("\\x{4B}", ascii).source);
		assertEquals("[kK]", Translate.translate("\\0153", ascii).source);
		assertEquals("[kK]", Translate.translate("\\c+", ascii).source);
		assertEquals("[kK]", Translate.translate("\\x6b", ascii).source);
		assertEquals("[\\x6bK]", Translate.translate("[\\0153]", ascii).source);
		assertEquals("[\\x6bK]", Translate.translate("[\\x{6b}]", ascii).source);
		assertEquals("[aA][aA]", Translate.translate("a\\0101", ascii).source);
		for (String pattern : new String[] { "\\x{6b}", "\\0153", "\\c+", "[\\0153]", "a\\0101" }) {
			assertEquals("", Translate.translate(pattern, ascii).jsFlags, pattern);
		}
	}

	private static final String[] ESCAPE_INPUTS = {
		"A",
		"a",
		"B",
		"k",
		"K",
		"!",
		"q",
		chr(0x00),
		chr(0x01),
		chr(0x07),
		chr(0x1b),
		chr(0x7f),
		chr(0xe9),
		chr(0xc9),
		chr(0xff),
		chr(0x100),
		chr(0x212a),
		"x{41}",
		"AAA",
		"aBc",
		"ab",
		" ",
		EMOJI,
		"a" + EMOJI + "b"
	};

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\x{41}",
			"\\x{0041}+",
			"\\x{e9}",
			"\\x{100}",
			"\\x{1F600}",
			"\\x{1F600}+",
			"[\\x{41}]",
			"[^\\x{41}]",
			"a\\x{42}c",
			"\\x{41}*b",
			"(\\x{41})(b)",
			"\\0101",
			"\\0153",
			"\\0377",
			"\\0400",
			"\\07",
			"\\0101+",
			"[\\0101-\\0132]",
			"[^\\0101]",
			"\\cA",
			"\\ca",
			"\\c1",
			"\\c!",
			"[\\ca]",
			"\\x41\\u0042",
			"\\uD83D\\uDE00",
			"\\uD83D\\uDE00+",
			"[\\uD83D\\uDE00]",
			"\\x{20}[\\x{20}-\\x{7e}]+"
		}
	)
	@DisplayName("matches what a JVM matches for an escape that stands for one character")
	void agreesOnEscapesForOneCharacter(String pattern) {
		assertMatches(pattern, ESCAPE_INPUTS);
		assertMatches(pattern, Pattern.CASE_INSENSITIVE, ESCAPE_INPUTS);
		assertMatches(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL, ESCAPE_INPUTS);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\t",
			"\\n",
			"\\r",
			"\\f",
			"\\a",
			"\\e",
			"\\t+",
			"[\\t\\n\\r\\f\\a\\e]+",
			"[^\\t\\n\\r\\f\\a\\e]",
			"\\Qa.b\\E",
			"\\Qa.b",
			"\\Q\\E",
			"\\Q.\\E+",
			"a\\Q*\\E+",
			"\\Q\\E\\x{41}",
			"[\\Q^-\\E]"
		}
	)
	@DisplayName("matches what a JVM matches for the rest of the single-character escapes")
	void agreesOnTheRestOfTheEscapeList(String pattern) {
		assertMatches(
			pattern,
			"\t",
			"\n",
			"\r",
			"\f",
			chr(0x07),
			chr(0x1b),
			"a.b",
			"axb",
			"a*",
			"a**",
			"...",
			"^",
			"-",
			"A",
			"ab"
		);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\x",
			"\\x4",
			"\\xg1",
			"\\x{",
			"\\x{}",
			"\\x{41",
			"\\x{4g}",
			"\\x{110000}",
			"\\u",
			"\\u41",
			"\\u004",
			"\\u00g1",
			"\\c",
			"\\0",
			"\\08",
			"\\y",
			"\\i",
			"\\C",
			"\\k",
			"\\k<a>",
			"(?<a>x)\\k<b>",
			"[\\x4]",
			"[\\u41]",
			"[\\c]",
			"[\\0]",
			"[\\b]",
			"[\\B]",
			"[\\A]",
			"[\\Z]",
			"[\\z]",
			"[\\1]",
			"[\\9]",
			"[\\k]",
			"[\\y]"
		}
	)
	@DisplayName(
		"refuses an escape that a JVM refuses, where the platform would read it as a letter"
	)
	void refusesAnEscapeThatAJvmRefuses(String pattern) {
		assertThrows(PatternSyntaxException.class, () -> java.util.regex.Pattern.compile(pattern));
		assertThrows(PatternSyntaxException.class, () -> Translate.translate(pattern, 0));
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"\\x4 1",
			"\\x{4 1}",
			"\\x{41 }",
			"\\x{ 41}",
			"\\c A",
			"\\0 12",
			"\\01 2",
			"\\u00 41",
			"\\uD83D \\uDE00"
		}
	)
	@DisplayName("refuses whitespace inside an escape under COMMENTS, which a JVM reads past")
	void refusesWhitespaceInsideAnEscapeUnderComments(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern, Pattern.COMMENTS)
		);
		assertTrue(refused.getMessage().contains("COMMENTS"), refused.getMessage());
	}

	@Test
	@DisplayName("takes an escape that ends beside whitespace under COMMENTS")
	void keepsAnEscapeThatEndsBesideWhitespaceUnderComments() {
		int comments = Pattern.COMMENTS;
		assertMatches("\\x41 b", comments, "Ab");
		assertMatches("\\x{41} b # c", comments, "Ab");
		assertMatches("\\0101 b", comments, "Ab");
		assertMatches("\\cA b", comments, chr(0x01) + "b");
		assertMatches("\\u0041 b", comments, "Ab");
		assertMatches("[\\x{41} \\x{42}]+", comments, "ABBA", "AB B");
	}

	// #endregion

	// #region a character above U+FFFF, which a pattern read as UTF-16 units takes apart

	/** A constant, for the annotations: the same character as {@link #EMOJI}. */
	private static final String PAIR = "" + (char) 0xD83D + (char) 0xDE00;

	private static final String HIGH = "" + (char) 0xD83D;

	private static final String LOW = "" + (char) 0xDE00;

	@ParameterizedTest
	@ValueSource(
		strings = {
			".",
			".+",
			"a.c",
			"(?s).",
			"[^x]",
			"[^a-z]+",
			"\\S",
			"\\D",
			"\\W",
			"\\H",
			"\\V",
			"\\P{Alpha}",
			"[\\W]",
			"[\\D]",
			"[\\u0100-\\uFFFF]",
			"[\\uFFFF]",
			"\\uD83D",
			"[\\uDE00]",
			"\\x{D83D}",
			"[^\\x{D83D}]"
		}
	)
	@DisplayName(
		"gives a pattern that reads half of a character above U+FFFF a source for the v flag"
	)
	void givesASecondSourceToAPatternThatTakesHalvesApart(String pattern) {
		Translate.Result result = Translate.translate(pattern, 0);
		assertNotNull(result.sensitive, pattern);
		assertNotNull(result.codePointSource, pattern);
		assertNull(result.codePointRefusal, pattern);
		assertEquals("", result.jsFlags, pattern);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"abc",
			"a+",
			"\\d+",
			"\\w\\s\\h\\v\\R",
			"[a-z]+",
			"\\p{InGreek}",
			"\\p{Alpha}",
			"[\\u0100-\\ud7ff]",
			"[\\u0100-\\u1000]",
			"\\x{1F600}",
			"\\x{FF}",
			"\\x{41}",
			"[abc]",
			"a|b",
			"(?>a+)b",
			"a\\Q\\E",
			"\\P{InGreek}.",
			"[a[b]].",
			"[\\x{1F600}]"
		}
	)
	@DisplayName("gives no second source to a pattern that reads every character whole already")
	void givesNoSecondSourceToAPatternThatIsExactAlready(String pattern) {
		Translate.Result result = Translate.translate(pattern, 0);
		assertNull(result.sensitive, pattern);
		assertNull(result.codePointSource, pattern);
		assertNull(result.codePointRefusal, pattern);
	}

	@Test
	@DisplayName(
		"says what has no second source under UNICODE_CASE, where the v flag changes folding"
	)
	void refusesTheSecondSourceUnderUnicodeCase() {
		int unicode = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		Translate.Result result = Translate.translate(".", unicode);
		assertEquals("a dot", result.sensitive);
		assertNull(result.codePointSource);
		assertTrue(result.codePointRefusal.contains("UNICODE_CASE"), result.codePointRefusal);
		assertEquals("i", result.jsFlags);
		assertNull(Translate.translate("[a-z]+", unicode).sensitive);
	}

	@Test
	@DisplayName("takes a quantifier over the whole of a character above U+FFFF written as itself")
	void takesAQuantifierOverAWholeCharacter() {
		assertEquals("(?:" + PAIR + ")+", Translate.translate(PAIR + "+", 0).source);
		assertEquals(
			"a(?:" + PAIR + "){2}",
			Translate.translate("a\\Q" + PAIR + "\\E{2}", 0).source
		);
		assertEquals("(?:" + PAIR + ")*b", Translate.translate(PAIR + "*b", 0).source);
		assertEquals("ab" + PAIR, Translate.translate("ab" + PAIR, 0).source);
		assertEquals(
			"(?:" + PAIR + ")+",
			Translate.translate(PAIR + " # why\n +", Pattern.COMMENTS).source
		);
		assertEquals(PAIR + "+[a[b]]", Translate.translate(PAIR + "+[a[b]]", 0).source);
		for (String pattern : new String[] { PAIR + "+", "a\\Q" + PAIR + "\\E+", PAIR + "{2}" }) {
			assertMatches(pattern, PAIR, PAIR + PAIR, "a" + PAIR + PAIR, HIGH, LOW);
		}
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"[" + PAIR + "]",
			"[^" + PAIR + "]",
			"[a-" + PAIR + "]",
			"[\\x{1F600}]",
			"[\\x{1F600}-\\x{1F64F}]",
			"[\\uD83D\\uDE00]",
			"[a\\Q" + PAIR + "\\E]"
		}
	)
	@DisplayName("reads a class that holds a character above U+FFFF as code points, which it needs")
	void readsAClassWithAWideCharacterAsCodePoints(String pattern) {
		Translate.Result result = Translate.translate(pattern, 0);
		assertEquals("v", result.jsFlags, pattern);
		assertNull(result.sensitive, pattern);
		assertSameSet(pattern, 0);
		assertThrows(PatternSyntaxException.class, () ->
			ours(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
		);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			".",
			"[^x]",
			"[^\\x{D83D}]",
			"\\S",
			"\\D",
			"\\W",
			"\\H",
			"\\V",
			"\\P{Alpha}",
			"[\\W]",
			"[\\u0100-\\uFFFF]",
			"[\\uD800-\\uDFFF]",
			"\\uD83D",
			"\\x{DE00}",
			"[\\uD83D]"
		}
	)
	@DisplayName("takes a character above U+FFFF whole wherever the pattern would read half of it")
	void agreesOnTheSetsOverWideCharacters(String pattern) {
		assertSameSet(pattern, 0);
		assertSameSet(pattern, Pattern.DOTALL);
		assertSameSet(pattern, Pattern.CASE_INSENSITIVE);
	}

	private static final String[] WIDE_INPUTS = {
		"",
		"a",
		PAIR,
		"a" + PAIR + "b",
		PAIR + PAIR,
		PAIR + "a" + PAIR,
		"x" + MATH,
		MATH + "x",
		"ab" + PAIR + "cd",
		"a\n" + PAIR,
		PAIR + "\n",
		HIGH,
		LOW,
		HIGH + "x",
		"x" + LOW,
		LOW + HIGH,
		PAIR + LOW,
		chr(0xe9) + PAIR + chr(0xe9),
		PAIR + PAIR + PAIR + PAIR
	};

	@ParameterizedTest
	@ValueSource(
		strings = {
			".",
			".+",
			".*",
			"^.$",
			"^..$",
			"(.)(.)",
			".{2}",
			"a.c",
			".x",
			"[^x]+",
			"[^a-z]",
			"\\S+",
			"\\D+",
			"\\W+",
			"\\H",
			"\\V",
			"\\P{Alpha}+",
			"[\\W]+",
			"[\\u0100-\\uFFFF]+",
			"\\uD83D",
			"[\\uD83D]",
			"[^\\uD83D]+",
			"\\x{D83D}\\x{DE00}",
			"(?>.+)x",
			".*+x",
			".+?x",
			"a|.",
			"(?<=.)x",
			"(?:.)+",
			"(.)|(x)"
		}
	)
	@DisplayName("finds the same matches as a JVM over text that holds characters above U+FFFF")
	void agreesOverInputsWithWideCharacters(String pattern) {
		assertMatches(pattern, WIDE_INPUTS);
		assertMatches(pattern, Pattern.DOTALL, WIDE_INPUTS);
		assertMatches(pattern, Pattern.UNIX_LINES, WIDE_INPUTS);
	}

	/** The engine of the JVM lane, noting which flags each expression it runs was compiled with. */
	private static final class Recording implements Engine {

		final List<String> used = new ArrayList<>();

		private static final class Compiled {

			final String flags;
			final Object expression;

			Compiled(String flags, Object expression) {
				this.flags = flags;
				this.expression = expression;
			}
		}

		@Override
		public Object compile(String source, String flags) {
			return new Compiled(flags, Native.ENGINE.compile(source, flags));
		}

		@Override
		public Object find(Object expression, String input, int from) {
			Compiled compiled = (Compiled) expression;
			used.add(compiled.flags);
			return Native.ENGINE.find(compiled.expression, input, from);
		}

		@Override
		public int count(Object match) {
			return Native.ENGINE.count(match);
		}

		@Override
		public String group(Object match, int index) {
			return Native.ENGINE.group(match, index);
		}

		@Override
		public String named(Object match, String name) {
			return Native.ENGINE.named(match, name);
		}

		@Override
		public int start(Object match, int index) {
			return Native.ENGINE.start(match, index);
		}

		@Override
		public int end(Object match, int index) {
			return Native.ENGINE.end(match, index);
		}

		@Override
		public int namedStart(Object match, String name) {
			return Native.ENGINE.namedStart(match, name);
		}

		@Override
		public int namedEnd(Object match, String name) {
			return Native.ENGINE.namedEnd(match, name);
		}
	}

	@Test
	@DisplayName(
		"runs the source for the v flag over text with a character above U+FFFF and no other"
	)
	void runsTheSourceThatSuitsTheInput() {
		Recording engine = new Recording();
		Pattern dot = Pattern.compile(".", 0, engine);
		for (String plain : new String[] { "abc", HIGH, LOW + HIGH, HIGH + "x" + LOW, "" }) {
			dot.matcher(plain).find();
			dot.matcher(plain).matches();
			dot.matcher(plain).lookingAt();
		}
		assertEquals(Collections.nCopies(15, ""), engine.used);

		engine.used.clear();
		dot.matcher("a" + PAIR).find();
		dot.matcher(PAIR).matches();
		dot.matcher(PAIR + "a").lookingAt();
		dot.matcher("x" + PAIR)
			.reset()
			.find();
		assertEquals(List.of("v", "v", "v", "v"), engine.used);

		engine.used.clear();
		Pattern exact = Pattern.compile("[a-z]+\\d", 0, engine);
		exact.matcher("ab1" + PAIR).find();
		exact.matcher(PAIR).matches();
		assertEquals(List.of("", ""), engine.used);

		engine.used.clear();
		Pattern wide = Pattern.compile("[\\u0100-\\uFFFF]+", 0, engine);
		wide.matcher(chr(0x101) + PAIR).find();
		wide.matcher(chr(0x101) + chr(0x102)).find();
		assertEquals(List.of("v", ""), engine.used);
	}

	/**
	 * The platform's engine for the {@code v} flag, as far as the matcher can tell: it reads code points,
	 * backs up to the start of the pair when it is given a position in the middle of one, and when the
	 * search reaches the middle of a pair it stops there with an empty match, which is the worst thing it
	 * does (V8 reads nothing at that point, so {@code (?![\s\S])} holds there). The JVM lane's own engine
	 * does none of that, so this is what lets a test see the matcher's handling of such a search.
	 */
	private static final class CodePoints implements Engine {

		static final CodePoints ENGINE = new CodePoints();

		private static final class Compiled {

			final java.util.regex.Pattern expression;
			final boolean codePoints;
			final boolean anchored;

			Compiled(java.util.regex.Pattern expression, boolean codePoints, boolean anchored) {
				this.expression = expression;
				this.codePoints = codePoints;
				this.anchored = anchored;
			}
		}

		@Override
		public Object compile(String source, String flags) {
			return new Compiled(
				(java.util.regex.Pattern) Native.ENGINE.compile(source, flags),
				flags.indexOf('v') >= 0,
				source.startsWith("^(?:")
			);
		}

		@Override
		public Object find(Object compiled, String input, int from) {
			Compiled held = (Compiled) compiled;
			if (!held.codePoints) return Native.ENGINE.find(held.expression, input, from);
			java.util.regex.Matcher matcher = held.expression.matcher(input);
			matcher.useTransparentBounds(true);
			matcher.useAnchoringBounds(false);
			int at = from;
			if (pairStartsBefore(input, at)) at--;
			for (; at <= input.length(); at++) {
				if (at > from && pairStartsBefore(input, at) && !held.anchored) {
					java.util.regex.Matcher nothing = java.util.regex.Pattern.compile("").matcher(
						input
					);
					nothing.find(at);
					return nothing;
				}
				matcher.region(at, input.length());
				if (matcher.lookingAt()) return matcher;
				if (held.anchored) return null;
			}
			return null;
		}

		private static boolean pairStartsBefore(String input, int index) {
			return (
				index > 0 &&
				index < input.length() &&
				Character.isLowSurrogate(input.charAt(index)) &&
				Character.isHighSurrogate(input.charAt(index - 1))
			);
		}

		@Override
		public int count(Object match) {
			return Native.ENGINE.count(match);
		}

		@Override
		public String group(Object match, int index) {
			return Native.ENGINE.group(match, index);
		}

		@Override
		public String named(Object match, String name) {
			return Native.ENGINE.named(match, name);
		}

		@Override
		public int start(Object match, int index) {
			return Native.ENGINE.start(match, index);
		}

		@Override
		public int end(Object match, int index) {
			return Native.ENGINE.end(match, index);
		}

		@Override
		public int namedStart(Object match, String name) {
			return Native.ENGINE.namedStart(match, name);
		}

		@Override
		public int namedEnd(Object match, String name) {
			return Native.ENGINE.namedEnd(match, name);
		}
	}

	private static final String[] MIDDLE_INPUTS = {
		"",
		PAIR,
		"a" + PAIR + "b",
		PAIR + PAIR,
		"a" + PAIR,
		PAIR + "b",
		"ab" + PAIR + PAIR + "cd",
		"a b " + PAIR + " c\n" + PAIR,
		LOW,
		HIGH + "x",
		LOW + HIGH + "x",
		"x" + LOW + "y"
	};

	/** Every match as {@code start:end:groups}, and every {@code find(int)} from each position. */
	private static List<String> walk(Pattern pattern, String input) {
		List<String> seen = new ArrayList<>();
		Matcher matcher = pattern.matcher(input);
		while (matcher.find()) seen.add(record(matcher.start(), matcher.end(), matcher));
		for (int from = 0; from <= input.length(); from++) {
			Matcher again = pattern.matcher(input);
			seen.add(
				again.find(from)
					? "@" + from + " " + record(again.start(), again.end(), again)
					: "-"
			);
		}
		return seen;
	}

	private static String record(int start, int end, MatchResult result) {
		StringBuilder record = new StringBuilder().append(start).append(':').append(end);
		for (int group = 1; group <= result.groupCount(); group++) {
			record.append(':').append(result.group(group)).append('@').append(result.start(group));
		}
		return record.toString();
	}

	private static List<String> jvmWalk(String pattern, int flags, String input) {
		java.util.regex.Pattern compiled = java.util.regex.Pattern.compile(pattern, flags);
		List<String> seen = new ArrayList<>();
		java.util.regex.Matcher matcher = compiled.matcher(input);
		while (matcher.find()) seen.add(record(matcher.start(), matcher.end(), matcher));
		for (int from = 0; from <= input.length(); from++) {
			java.util.regex.Matcher again = compiled.matcher(input);
			seen.add(
				again.find(from)
					? "@" + from + " " + record(again.start(), again.end(), again)
					: "-"
			);
		}
		return seen;
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"[a[b]]*",
			"[a[b]]?",
			"[a[b]]*|x",
			"x*[a[b]]*",
			"[a[b]]*$",
			"[a-z&&[^b]]*",
			"[a[b]]+",
			"(?=.)",
			"(?=\\S)",
			"(?!a)",
			"(?<!a)",
			"(?<=a)",
			"(?<=.)",
			"(?<!.)",
			"\\B",
			"\\b",
			"\\B.",
			"(?<!a).",
			"(?<=\\W).",
			"(?<!a)\\S",
			"(?<!a)[^x]",
			"(?<!a)[a[^x]]",
			"\\P{InGreek}*",
			"\\P{InGreek}*?",
			"(?<!a)\\P{InGreek}",
			"[^x]*",
			"[^x]*?",
			".*?",
			".*",
			"\\S*",
			"[\\uDC00-\\uDFFF]*",
			"[\\uDC00-\\uDFFF]*|a",
			"[\\u0100-\\uFFFF]*",
			"(?:\\x{1F600}|[^a])*",
			"[^\\x{1F600}]*",
			"x*|.",
			"$",
			"\\z",
			"(?=.$)",
			"^",
			"^.",
			"^[a[b]]*",
			"\\A[a[b]]*",
			"(?:^|x)[a[b]]*",
			"(?m)^",
			"(?m)^.",
			"(?m)^[a[b]]*",
			"(?m)$",
			"(?<=^)",
			"(?<![a-z])",
			"((?<!a)[a[b]]*)",
			"(a)?(?<!a).",
			"(?<=\\uD83D)",
			"(?<![\\uD800-\\uDBFF])"
		}
	)
	@DisplayName(
		"finds the same matches as a JVM, including one that starts inside a surrogate pair"
	)
	void startsInsideAPairAsAJvmDoes(String pattern) {
		for (String input : MIDDLE_INPUTS) {
			List<String> want = jvmWalk(pattern, 0, input);
			assertEquals(
				want,
				walk(Pattern.compile(pattern, 0, CodePoints.ENGINE), input),
				"code points, " + pattern + " over " + show(input)
			);
			assertEquals(
				want,
				walk(ours(pattern), input),
				"jvm engine, " + pattern + " over " + show(input)
			);
		}
	}

	@Test
	@DisplayName("starts the next search inside a pair after an empty match, as a JVM does")
	void startsTheNextSearchInsideAPair() {
		String text = "a" + PAIR + "b";
		assertEquals(
			List.of("0:1", "1:1", "2:2", "3:4", "4:4"),
			spans(Pattern.compile("[a[b]]*", 0, CodePoints.ENGINE), text)
		);
		assertEquals(
			List.of("0:0", "1:1", "2:2", "3:3"),
			spans(Pattern.compile("(?=.)", 0, CodePoints.ENGINE), text)
		);
		assertEquals(
			List.of("0:0", "1:1", "2:2"),
			spans(Pattern.compile("[a[b]]*", 0, CodePoints.ENGINE), PAIR)
		);
	}

	private static List<String> spans(Pattern pattern, String input) {
		List<String> spans = new ArrayList<>();
		Matcher matcher = pattern.matcher(input);
		while (matcher.find()) spans.add(matcher.start() + ":" + matcher.end());
		return spans;
	}

	@Test
	@DisplayName(
		"refuses to match a character above U+FFFF where it has no source that is exact there"
	)
	void refusesAnInputItCannotMatchExactly() {
		int unicode = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		Pattern dot = ours(".", unicode);
		assertTrue(dot.matcher("a").matches());
		assertTrue(dot.matcher(HIGH).matches());
		UnsupportedOperationException refused = assertThrows(
			UnsupportedOperationException.class,
			() -> dot.matcher("a" + PAIR)
		);
		assertTrue(refused.getMessage().contains("UNICODE_CASE"), refused.getMessage());
		assertTrue(refused.getMessage().contains("a dot"), refused.getMessage());
		assertThrows(UnsupportedOperationException.class, () -> dot.split("a" + PAIR));
		assertThrows(UnsupportedOperationException.class, () -> dot.matcher("a").reset("a" + PAIR));
		// text that has none still goes through, and so does a pattern that reads every character whole
		assertArrayEquals(
			java.util.regex.Pattern.compile(".", unicode).split("abc", -1),
			dot.split("abc", -1)
		);
		assertTrue(
			ours("[a-z]+", unicode)
				.matcher("ab" + PAIR)
				.find()
		);
	}

	// #endregion

	// #region named groups

	@Test
	@DisplayName("reads a group by the name the pattern gave it")
	void namedGroups() {
		Matcher matcher = ours("(?<year>\\d{4})-(?<month>\\d{2})").matcher("on 2026-08 ok");

		assertTrue(matcher.find());
		assertEquals("2026-08", matcher.group());
		assertEquals("2026", matcher.group("year"));
		assertEquals("08", matcher.group("month"));
		assertEquals(3, matcher.start("year"));
		assertEquals(7, matcher.end("year"));
		assertEquals(8, matcher.start("month"));
		assertEquals(10, matcher.end("month"));
		assertEquals(Map.of("year", 1, "month", 2), matcher.namedGroups());
	}

	@Test
	@DisplayName("names a group the pattern does not have")
	void unknownGroupName() {
		Matcher matcher = ours("(?<year>\\d{4})").matcher("2026");
		assertTrue(matcher.find());

		assertUnknownName(() -> matcher.group("month"));
		assertUnknownName(() -> matcher.start("month"));
		assertUnknownName(() -> matcher.end("month"));
	}

	private static void assertUnknownName(org.junit.jupiter.api.function.Executable call) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, call);

		assertTrue(failure.getMessage().contains("no group called month"), failure.getMessage());
	}

	@Test
	@DisplayName(
		"counts the groups before anything has matched, which is when only the pattern knows"
	)
	void countsBeforeMatching() {
		assertEquals(2, ours("(?<a>x)(?<b>y)").matcher("").groupCount());
		assertEquals(0, ours("xy").matcher("").groupCount());
	}

	@Test
	@DisplayName(
		"counts plain groups before anything has matched, and numbers a named one by place"
	)
	void countsPlainGroupsAndNumbersNamedOnes() {
		assertEquals(2, ours("(a)(b)").matcher("").groupCount());
		assertEquals(
			java.util.regex.Pattern.compile("(a)(?<n>b)(c)").namedGroups(),
			ours("(a)(?<n>b)(c)").namedGroups()
		);
		assertEquals(Map.of("n", 2), ours("(a)(?<n>b)").matcher("ab").namedGroups());
	}

	// #endregion

	// #region atomic groups and possessive quantifiers, written as a lookahead and a backreference

	private static final String[] RUNS = {
		"abc",
		"ab",
		"aab",
		"abcabc",
		"aaa",
		"a.a.",
		"123",
		"xaaa",
		"abd",
		"bbc",
		"",
		"a",
		"abcd",
		"abcdefghijkll"
	};

	private static String source(String pattern, int flags) {
		return Translate.translate(pattern, flags).source;
	}

	/**
	 * Every group the caller can read, in every way it can be read: the count, each group's text and
	 * span, the named ones, a replacement that names every group, and the results kept as a stream.
	 */
	private static void assertSameGroups(String pattern, int flags, String... inputs) {
		java.util.regex.Pattern expected = java.util.regex.Pattern.compile(pattern, flags);
		Pattern actual = ours(pattern, flags);
		assertEquals(
			expected.matcher("").groupCount(),
			actual.matcher("").groupCount(),
			"groupCount of " + pattern + " before a match"
		);
		assertEquals(expected.namedGroups(), actual.namedGroups(), "named groups of " + pattern);

		StringBuilder replacement = new StringBuilder("<");
		for (int group = 0; group <= expected.matcher("").groupCount(); group++) {
			replacement.append('$').append(group).append('|');
		}
		for (String input : inputs) {
			String where = pattern + " over " + show(input);
			java.util.regex.Matcher want = expected.matcher(input);
			Matcher got = actual.matcher(input);
			while (want.find()) {
				assertTrue(got.find(), where);
				assertEquals(want.groupCount(), got.groupCount(), where);
				for (int group = 0; group <= want.groupCount(); group++) {
					assertEquals(
						want.group(group),
						got.group(group),
						"group " + group + " of " + where
					);
					assertEquals(
						want.start(group),
						got.start(group),
						"start " + group + " of " + where
					);
					assertEquals(want.end(group), got.end(group), "end " + group + " of " + where);
				}
				for (String name : expected.namedGroups().keySet()) {
					assertEquals(
						want.group(name),
						got.group(name),
						"group " + name + " of " + where
					);
					assertEquals(
						want.start(name),
						got.start(name),
						"start " + name + " of " + where
					);
					assertEquals(want.end(name), got.end(name), "end " + name + " of " + where);
				}
			}
			assertFalse(got.find(), where);
			assertEquals(
				expected.matcher(input).replaceAll(replacement.toString()),
				actual.matcher(input).replaceAll(replacement.toString()),
				"replacement over " + where
			);
			List<String> kept = new ArrayList<>();
			expected
				.matcher(input)
				.results()
				.forEach(result -> kept.add(spans(result)));
			List<String> keptHere = new ArrayList<>();
			actual
				.matcher(input)
				.results()
				.forEach(result -> keptHere.add(spans(result)));
			assertEquals(kept, keptHere, "results of " + where);
		}
	}

	private static String spans(java.util.regex.MatchResult result) {
		StringBuilder spans = new StringBuilder();
		for (int group = 0; group <= result.groupCount(); group++) {
			spans
				.append(result.group(group))
				.append('@')
				.append(result.start(group))
				.append('-')
				.append(result.end(group))
				.append(' ');
		}
		return spans.toString();
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(?>a|ab)c",
			"(?>a|ab)",
			"(?>a|ab)$",
			"x(?>a+)a",
			"(?>a*)a",
			"(?>a)*",
			"(?>a|b)+c",
			"(?>a)?b",
			"(?>a){2}",
			"(?>(?>a|ab)c|abd)",
			"(?:(?>a|ab)c)+",
			"(?>[ab]+)b",
			"(?>\\d+)\\d",
			"(?>\\w+)x",
			"(?>\\Qa.\\E)+",
			"(?>)",
			"(?>a)(?>b)",
			"a(?>b|bc)c",
			"(?>a|ab)(?>c|cd)d",
			"(?>(?>a)b)c",
			"(?=(?>a|ab)c)ab",
			"(?!(?>a|ab)c)a",
			"(?>a|b)*+c"
		}
	)
	@DisplayName("agrees on an atomic group, which never gives back what it took")
	void agreesOnAtomicGroups(String pattern) {
		assertMatches(pattern, RUNS);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"a*+a",
			"a++a",
			"a?+a",
			"a{1,2}+a",
			"a{2}+a",
			"a{2,}+a",
			"[ab]*+b",
			"\\d++\\d",
			"\\w*+x",
			".*+c",
			"a.*+b",
			"(?:ab)*+b",
			"(?:ab)*+c",
			"(?:a|ab)*+c",
			"\\Qab\\E*+b",
			"\\Qa.\\E++",
			"x*+y*+z",
			"(?=a*+b)",
			"(?!a*+b)a",
			"(?>a)*+",
			"(?>a*+)b",
			"^a*+$",
			"\\b\\w++\\b",
			"[a-c]++c",
			"\\s*+$",
			"a*+",
			"b++"
		}
	)
	@DisplayName("agrees on a possessive quantifier, which never gives back what it took")
	void agreesOnPossessiveQuantifiers(String pattern) {
		assertMatches(pattern, RUNS);
		assertMatches(pattern, "aaa  ", " ", "aaab", "a.a.a.");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(a)(?>bc)(d)",
			"(?<n>a)(?>b)(?<m>c)",
			"(?>b)(?<x>a)(b)",
			"(a)(?>b)\\1",
			"(?>a)(b)\\1",
			"(a)(?>b|c)(?>d)",
			"(?:(a)(?>b))*",
			"((a)(?>b))c",
			"(?>b)((a)c)",
			"(?:(a)|(b))(?>c)(d)",
			"(?<one>a)(?>b)\\k<one>",
			"(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(?>k)(l)\\11",
			"(a)(?>b+)(?>c+)(d)",
			"(?>a+)(?<x>b)(?<y>a)",
			"(?>a)*+(b)(?>c)*+(d)"
		}
	)
	@DisplayName("keeps every group number, name, span and replacement the caller sees")
	void keepsTheGroupNumbersTheCallerSees(String pattern) {
		assertSameGroups(pattern, 0, RUNS);
		assertSameGroups(
			pattern,
			0,
			"abcd",
			"abcabcd",
			"aabcdd",
			"abbcc",
			"aba",
			"abab",
			"abcabcab"
		);
	}

	@Test
	@DisplayName("reads the groups of a pattern with a plain group before and after an atomic one")
	void readsGroupsAroundAnAtomicGroup() {
		Matcher matcher = ours("(a)(?>b)(c)(?>d)(e)").matcher("abcde");
		assertEquals(3, matcher.groupCount());
		assertTrue(matcher.find());
		assertEquals("a", matcher.group(1));
		assertEquals("c", matcher.group(2));
		assertEquals("e", matcher.group(3));
		assertEquals(4, matcher.start(3));
		assertEquals(5, matcher.end(3));
		assertThrows(IndexOutOfBoundsException.class, () -> matcher.group(4));
		assertEquals("e-a", ours("(a)(?>b)(c)(?>d)(e)").matcher("abcde").replaceAll("$3-$1"));
		assertEquals(Map.of("n", 2), ours("(a)(?>b)(?<n>c)").namedGroups());
		assertEquals(
			java.util.regex.Pattern.compile("(a)(?>b)(?<n>c)").namedGroups(),
			ours("(a)(?>b)(?<n>c)").namedGroups()
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { "(?>a|ab)c", "(?>a)*", "(a)(?>b)", "a*+a", "(?>a)(b)" })
	@DisplayName("agrees under case folding, both the ASCII pairs and the platform's own")
	void agreesOnAtomicGroupsUnderFolding(String pattern) {
		for (int flags : new int[] {
			Pattern.CASE_INSENSITIVE,
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE,
			Pattern.COMMENTS,
			Pattern.DOTALL
		}) {
			assertMatches(pattern, flags, "abc", "ABC", "aBc", "aab", "AAB", "abab", "ABAB", "aA");
			assertSameGroups(pattern, flags, "abc", "ABC", "aBc", "abab");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "(a)(?>b)\\1", "(?>a)(b)\\1", "(?<n>a)(?>b)\\k<n>" })
	@DisplayName(
		"agrees on a backreference after an atomic group where the platform's folding is a JVM's"
	)
	void agreesOnAtomicGroupsAroundABackreference(String pattern) {
		for (int flags : new int[] {
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE,
			Pattern.COMMENTS,
			Pattern.DOTALL
		}) {
			assertMatches(pattern, flags, "abc", "aba", "abb", "aBa", "aBA", "ABA", "abab");
			assertSameGroups(pattern, flags, "aba", "abb", "aBA", "abab");
		}
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(?>(a)b)",
			"(?>(?<x>a))",
			"(?>a(?:(b)))",
			"(?>(?=(a))a)",
			"(?>(a)|(b))c",
			"(?>(a)(?>(b)c))(d)",
			"(a)(?>(b)(c))(d)",
			"(?<n>a)(?>b|(c))",
			"(a)*+",
			"(a)?+b",
			"(ab)*+b",
			"(a|ab)*+c",
			"((a)b)*+c",
			"(?:(a)b)*+c",
			"(?:x(?<n>a))++",
			"(?:a|(b)c){2}+",
			"(?:(?=(a))a)*+",
			"(?>a)*+(b)*+"
		}
	)
	@DisplayName(
		"refuses a capturing group inside an atomic group or a possessive atom, which a JVM reads differently"
	)
	void refusesAGroupInsideAnAtom(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(refused.getMessage().contains("capturing group"), refused.getMessage());
		assertTrue(
			refused.getMessage().contains("atomic group") ||
				refused.getMessage().contains("possessive"),
			refused.getMessage()
		);
		assertEquals(pattern, refused.getPattern());
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(a)(?>b)(c)",
			"((a)(?>b))c",
			"(?>b)((a)c)",
			"(?:(a)(?>b))*",
			"(?:(?>a)b)*+",
			"(a)*",
			"(a)*?b",
			"(?:(a)b)*c",
			"(?=(a))a*+",
			"(?<=(a))b*+",
			"(a)?(?>b)",
			"(?>a)*+(b)",
			"(?:a)*+(b)",
			"(?>(?:a|b))(c)",
			"(?>a|b)(?<n>c)\\k<n>"
		}
	)
	@DisplayName(
		"takes a capturing group beside an atomic group or a possessive atom, and around one"
	)
	void keepsAGroupBesideAnAtom(String pattern) {
		assertMatches(pattern, RUNS);
		assertSameGroups(pattern, 0, "abc", "abcabc", "aab", "abab", "abcd", "ba");
	}

	@Test
	@DisplayName("reads a possessive or lazy mark past the whitespace that comments mode drops")
	void readsASuffixPastWhitespace() {
		assertMatches("a* +a", Pattern.COMMENTS, "aaa", "aab", "a");
		assertMatches("a *+ a", Pattern.COMMENTS, "aaa", "aab");
		assertMatches("a* ?", Pattern.COMMENTS, "aaa");
		assertMatches("(?>a b) c", Pattern.COMMENTS, "abc", "ab");
	}

	@Test
	@DisplayName("writes an atomic group and a possessive atom as a lookahead and a backreference")
	void writesTheLookahead() {
		assertEquals("(?:(?=(a|ab))\\1)c", source("(?>a|ab)c", 0));
		assertEquals("(?:(?=(a*))\\1)a", source("a*+a", 0));
		assertEquals("(?:(?=(a+))\\1)", source("a++", 0));
		assertEquals("(?:(?=(a?))\\1)", source("a?+", 0));
		assertEquals("(?:(?=(a{1,2}))\\1)", source("a{1,2}+", 0));
		assertEquals("(?:(?=([ab]*))\\1)b", source("[ab]*+b", 0));
		assertEquals("(?:(?=(a))\\1)*", source("(?>a)*", 0));
		assertEquals("(?:(?=((?:(?=(a))\\2)*))\\1)", source("(?>a)*+", 0));
		assertEquals("(?:(?=(a))\\1)(?:(?=(b))\\2)", source("(?>a)(?>b)", 0));
		assertEquals("aa(?:(?=(b*))\\1)", source("a\\Qab\\E*+", 0));
	}

	@Test
	@DisplayName("writes the group numbers the platform will count, shifted by the groups added")
	void shiftsTheNumbers() {
		assertEquals("(a)(?:(?=(bc))\\2)(d)", source("(a)(?>bc)(d)", 0));
		assertEquals("(a)(?:(?=(b))\\2)\\1", source("(a)(?>b)\\1", 0));
		assertEquals("(?:(?=(a))\\1)(b)\\2", source("(?>a)(b)\\1", 0));
		assertEquals("((a)(?:(?=(b))\\3))c", source("((a)(?>b))c", 0));
		assertEquals("(?:(?=(a*))\\1)(b)\\2", source("a*+(b)\\1", 0));
		assertEquals("(?<n>a)(?:(?=(b))\\2)(?<m>c)\\k<n>", source("(?<n>a)(?>b)(?<m>c)\\k<n>", 0));
	}

	@Test
	@DisplayName("reads a backreference as a JVM does, to as many digits as name a group so far")
	void readsBackreferenceDigits() {
		assertEquals("(a)(?:\\1)0", source("(a)\\10", 0));
		assertEquals("(a)(?:\\1)2", source("(a)\\12", 0));
		assertEquals("(a)(?:\\1)0", source("(a)\\1 0", Pattern.COMMENTS));
		assertEquals("(a)(?:\\1)1", source("(a)\\1\\Q1\\E", 0));
		assertEquals("(a)\\1b", source("(a)\\1b", 0));
		assertMatches("(a)\\10", "aa0", "a\n", "a");
		assertMatches("(a)\\12", "aa2", "a\n");
		assertMatches("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\10", "abcdefghijj", "abcdefghij\u0008");
	}

	@ParameterizedTest
	@ValueSource(strings = { "(?<=(?>a))b", "(?<!a++)b", "(?<=a(?=(?>b)))c", "(?<=x(?:(?>a)))b" })
	@DisplayName("refuses an atomic group or a possessive quantifier in a lookbehind")
	void refusesAtomicInALookbehind(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(refused.getMessage().contains("lookbehind"), refused.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "(a)\\2", "(?:\\1(a))", "\\1(a)", "(a\\2)(b)" })
	@DisplayName(
		"refuses a backreference to a group that is not open yet, which a JVM never matches"
	)
	void refusesAForwardBackreference(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(refused.getMessage().contains("not open yet"), refused.getMessage());
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"(a\\1)",
			"(a\\1)+",
			"(a|b\\1)+",
			"(a\\1)?",
			"(?<n>a\\k<n>)",
			"(?<n>a\\k<n>)*",
			"(?<n>a)(?<m>b\\k<m>)",
			"((a\\1))",
			"(x(a\\1)y)",
			"(?:(a\\1))+",
			"((a)\\1)",
			"(a(b\\2))",
			"(a(?=\\1))",
			"(a(?>b)\\1)"
		}
	)
	@DisplayName("refuses a backreference inside the group it names, which has not closed yet")
	void refusesABackreferenceToAnOpenGroup(String pattern) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(refused.getMessage().contains("has not closed yet"), refused.getMessage());
		assertEquals(pattern, refused.getPattern());
	}

	@Test
	@DisplayName("keeps a backreference to a group that has closed, however deep it sits")
	void keepsABackreferenceToAClosedGroup() {
		assertMatches("(a)(b\\1)", "aba", "ab");
		assertMatches("((a)\\2)", "aa", "a");
		assertMatches("(a(b)\\2)", "abb", "ab");
		assertMatches("(a)(?:(b)\\1\\2)+", "abab", "ababab", "abaa");
		assertMatches("(?<n>a)(?<m>b\\k<n>)", "aba", "ab");
		assertMatches("(a)(?=\\1)", "aa", "ab");
	}

	// #endregion

	// #region a class inside a class, and an intersection of classes, which need the v flag

	/**
	 * The set of single code units a pattern matches in full, which is the whole question for a class.
	 * Translated nested syntax is also valid Java syntax with the same meaning, so on a JVM this checks
	 * that the rewrite (the escapes, the brackets around each operand, the hyphens) kept the meaning.
	 * What V8 does with it is for the transcript.
	 */
	private static void assertSameSet(String pattern, int flags) {
		java.util.regex.Matcher want = java.util.regex.Pattern.compile(pattern, flags).matcher("");
		Pattern compiled = ours(pattern, flags);
		for (int unit = 0; unit <= 0xFFFF; unit++) {
			String text = String.valueOf((char) unit);
			boolean expected = want.reset(text).matches();
			if (expected != compiled.matcher(text).matches()) {
				throw new AssertionError(
					pattern + " under " + flags + " disagrees at U+" + Integer.toHexString(unit)
				);
			}
		}
		for (int point : new int[] { 0x10000, 0x1F600, 0x10FFFF }) {
			String text = new String(Character.toChars(point));
			assertEquals(
				want.reset(text).matches(),
				compiled.matcher(text).matches(),
				pattern + " at U+" + Integer.toHexString(point)
			);
		}
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"[a[b]]",
			"[^a[b]]",
			"[a[^b]]",
			"[a-c[x-z]]",
			"[[a-c]x]",
			"[a[b[c]]]",
			"[^a[b[^c]]]",
			"[a-z&&[^b]]",
			"[a-z&&[^aeiou]]",
			"[^a-z&&[^b]]",
			"[a-z&&b-d&&[^c]]",
			"[a-c[x-z]&&[^b]]",
			"[abc&&def]",
			"[a-d&&b-e]",
			"[[a-c]&&[b-d]]",
			"[\\w&&[^\\d]]",
			"[\\p{Alpha}&&[^\\p{Lower}]]",
			"[a-c&&b-d[x]]",
			"[\\Qab\\E&&b]",
			"[a-z&&[^b][^c]]",
			"[a[^b]&&[^a]]",
			"[^\\x00-\\x7F&&[^é]]",
			"[(){}|/[a]]",
			"[!#%&,:;<=>@`~[a]]",
			"[$*+.?^[a]]",
			"[a\\-b[c]]",
			"[a-[b]]",
			"[--a[b]]",
			"[\\d-z[a]]",
			"[a-c-e[x]]",
			"[\\]\\[[a]]",
			"[ [a]]",
			"[\\x41-\\x43[x]]",
			"[\\x{41}-\\x{43}[x]]",
			"[\\cA[a]]",
			"[\\0101[a]]",
			"[\\0101-\\0103&&[^\\0102]]",
			"[\\x{1F600}[a]]",
			"[\\x{1F600}-\\x{1F64F}&&[^\\x{1F610}]]",
			"[\\u0041-\\u0043&&[^B]]",
			"[a&b[c]]",
			"[a&[b]]",
			"[[a]&]",
			"[\\p{Punct}[a]]",
			"[\\p{InGreek}[x]]",
			"[\\p{InGreek}&&[^α]]",
			"[\\s[a]]",
			"[\\h&&[^ ]]",
			"[\\v[a]]",
			"[\\a[b]]",
			"[\\e[b]]",
			"[\\t\\n[b]]",
			"[\\Q.*+\\E[b]]",
			"[\\Q-\\E[b]]",
			"[a\\Q-\\Ec[d]]",
			"[^]a[b]]",
			"[a[]b]]"
		}
	)
	@DisplayName(
		"matches the same characters as a JVM does for a class inside a class, or an intersection"
	)
	void agreesOnNestedClasses(String pattern) {
		assertSameSet(pattern, 0);
		assertMatches(pattern + "+", "abc", "xyz", "a-b", "..", "]a", "{}", "a&b", "AB");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"a\\-b[[x]]",
			"[[a]]]",
			"x}[[a]]",
			"\\@[[a]]",
			"(?>[a[b]]+)c",
			"([a[b]])\\1",
			"[a[b]]*+b",
			"(?<n>[a-c&&[^b]])\\k<n>",
			".[[a]]",
			"\\.[[a]]",
			"[[a]]{2,3}",
			"^[[a]]$",
			"\\Qa-b\\E[[x]]",
			"a]b}[[x]]",
			"\\s[[x]]\\S",
			"\\p{Alpha}[[x]]",
			"\\p{Punct}[[x]]",
			"\\P{InGreek}[[x]]",
			"[a&&[b]]|[c[d]]"
		}
	)
	@DisplayName("keeps the rest of the pattern exact when a class inside a class is in it")
	void agreesAroundANestedClass(String pattern) {
		assertMatches(
			pattern,
			"a-b-x",
			"a@a",
			"x}a]",
			"abab",
			"a.a",
			"aa",
			"aaa",
			"b",
			"cd",
			"x]",
			"a\n"
		);
		assertMatches(pattern, Pattern.MULTILINE, "a\na", "aa\n");
		assertMatches(pattern, Pattern.DOTALL, "a\na");
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"[a-c&&[^b]]",
			"[^a[b]]",
			"[a[^b]]",
			"[x-z&&[^y]]",
			"[a-z&&[^aeiou]]",
			"[A[b]]",
			"[a-c[x-z]&&[^B]]",
			"[\\x41-\\x43[x]]",
			"[\\Qab\\E[c]]",
			"[\\p{Lower}[x]]",
			"[\\d-z[a]]",
			"[a-c-e[x]]",
			"[\\w-a&&[^b]]"
		}
	)
	@DisplayName("folds the ASCII letters in each part of a nested class, as a JVM does")
	void foldsEachPartOfANestedClass(String pattern) {
		assertSameSet(pattern, Pattern.CASE_INSENSITIVE);
	}

	@Test
	@DisplayName("writes a nested class and an intersection for the v flag, and only then uses it")
	void writesTheUnicodeSetsSource() {
		assertEquals("[a[b]]", source("[a[b]]", 0));
		assertEquals("[[a-z]&&[^aeiou]]", source("[a-z&&[^aeiou]]", 0));
		assertEquals("[^[a-z]&&[^b]]", source("[^a-z&&[^b]]", 0));
		assertEquals("[[a-z]&&[b-d]&&[^c]]", source("[a-z&&b-d&&[^c]]", 0));
		assertEquals("[[a-c[x-z]]&&[^b]]", source("[a-c[x-z]&&[^b]]", 0));
		assertEquals("[\\{\\(\\|\\)\\}[a]]", source("[{(|)}[a]]", 0));
		assertEquals("[a\\-[b]]", source("[a-[b]]", 0));
		assertEquals("[a\\-b[c]]", source("[a\\-b[c]]", 0));
		assertEquals(
			"[\\!\\#\\%\\&\\,\\:\\;\\<\\=\\>\\@\\`\\~[a]]",
			source("[!#%&,:;<=>@`~[a]]", 0)
		);
		assertEquals("[\\$\\*\\+\\.\\?\\^[a]]", source("[$*+.?^[a]]", 0));
		assertEquals("a-b[[x]]", source("a\\-b[[x]]", 0));
		assertEquals("[[a]]\\]", source("[[a]]]", 0));
		assertEquals("[\\u0000-\\u036f\\u0400-\\u{10ffff}[x]]", source("[\\P{InGreek}[x]]", 0));
		assertEquals("[\\!-\\/\\:-\\@\\[-\\`\\{-\\~[a]]", source("[\\p{Punct}[a]]", 0));
		assertEquals("[[a-cA-C]&&[^bB]]", source("[a-c&&[^b]]", Pattern.CASE_INSENSITIVE));

		assertEquals("v", Translate.translate("[a[b]]", 0).jsFlags);
		assertEquals("v", Translate.translate("x[a&&b]", 0).jsFlags);
		assertEquals("v", Translate.translate("[a[b]]", Pattern.CASE_INSENSITIVE).jsFlags);
		assertEquals("", Translate.translate("[a-z]+[^b]\\Q[[\\E", 0).jsFlags);
		assertEquals("", Translate.translate("[\\Q&&\\E]", 0).jsFlags);
		assertEquals("", Translate.translate("\\[a[b]", 0).jsFlags);
	}

	@ParameterizedTest
	@CsvSource(
		delimiter = ';',
		value = {
			"[&&a];empty",
			"[a&&];empty",
			"[a&&&b];&&&",
			"[a&&b&c];single &",
			"[a-\\Qz\\E[b]];quotation",
			"[\\Qa\\E-z[b]];quotation",
			"[\\S[a]];\\S",
			"[\\1[a]];escape",
			"[\\y[a]];escape",
			"[a-\\d[b]];range",
			"[z-a[b]];range",
			"[a[b];unclosed character class"
		}
	)
	@DisplayName("refuses a nested class it cannot read exactly, naming what it was")
	void refusesAnAmbiguousNestedClass(String pattern, String reason) {
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours(pattern)
		);
		assertTrue(
			refused.getMessage().contains(reason),
			"for " + pattern + " the message was: " + refused.getMessage()
		);
	}

	@Test
	@DisplayName("refuses a nested class where the platform's own case folding would be in play")
	void refusesANestedClassUnderPlatformFolding() {
		int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			ours("[a[b]]", flags)
		);
		assertTrue(refused.getMessage().contains("UNICODE_CASE"), refused.getMessage());
		assertTrue(refused.getMessage().contains("inside another"), refused.getMessage());
		refused = assertThrows(PatternSyntaxException.class, () -> ours("[a&&b]", flags));
		assertTrue(refused.getMessage().contains("an intersection"), refused.getMessage());
	}

	@Test
	@DisplayName(
		"reports what the platform refuses as a PatternSyntaxException on the pattern as written"
	)
	void reportsAPlatformRefusal() {
		Engine refusing = (Engine) java.lang.reflect.Proxy.newProxyInstance(
			Engine.class.getClassLoader(),
			new Class<?>[] { Engine.class },
			(proxy, method, args) -> {
				throw new IllegalStateException("Duplicate capture group name");
			}
		);
		PatternSyntaxException refused = assertThrows(PatternSyntaxException.class, () ->
			Pattern.compile("(?<n>a)(?<n>b)", 0, refusing)
		);
		assertEquals("(?<n>a)(?<n>b)", refused.getPattern());
		assertTrue(refused.getDescription().contains("Duplicate capture group name"));
	}

	@Test
	@DisplayName("drops whitespace and a comment inside a class under COMMENTS, as a JVM does")
	void dropsSpaceInAClassUnderComments() {
		assertMatches("[a b]", Pattern.COMMENTS, "a b", " ", "ab", "#");
		assertMatches("[a #c\nb]", Pattern.COMMENTS, "a b #c\n", "c");
		assertMatches("[^a b]", Pattern.COMMENTS, "a b c");
		assertMatches("[a[b c]]", Pattern.COMMENTS, "a b c ");
		assertMatches("[a-z &&[^ b]]", Pattern.COMMENTS, "a b c ");
		assertMatches("[a - c [x]]", Pattern.COMMENTS, "a b c - x ");
	}

	// #endregion

	// #region the sources that must not move

	/**
	 * Recorded from the translator before atomic groups and group numbering were added. Anything in
	 * this list that is neither an atomic group, a possessive quantifier nor a nested class is
	 * translated to the same text and flags as then.
	 */
	private static final Object[][] RECORDED = {
		{ "abc", 0, "abc", "" },
		{ "a|b", 0, "a|b", "" },
		{ "a*", 0, "a*", "" },
		{ "a+?", 0, "a+?", "" },
		{ "a{2,3}", 0, "a{2,3}", "" },
		{ "a{2,3}?", 0, "a{2,3}?", "" },
		{ "(a)(b)", 0, "(a)(b)", "" },
		{ "(?:ab)+", 0, "(?:ab)+", "" },
		{ "[a-z]+", 0, "[a-z]+", "" },
		{ "[^a-z]+", 0, "[^a-z]+", "" },
		{ "\\d+", 0, "\\d+", "" },
		{ "\\w+", 0, "\\w+", "" },
		{ "\\bword\\b", 0, "\\bword\\b", "" },
		{ "a(?=b)", 0, "a(?=b)", "" },
		{ "a(?!b)", 0, "a(?!b)", "" },
		{ "(?<=a)b", 0, "(?<=a)b", "" },
		{ "(?<!a)b", 0, "(?<!a)b", "" },
		{ "(a)\\1", 0, "(a)\\1", "" },
		{ "(?<name>a)(?<other>b)", 0, "(?<name>a)(?<other>b)", "" },
		{ "(?<n>a)\\k<n>", 0, "(?<n>a)\\k<n>", "" },
		{ "\\x41", 0, "\\x41", "" },
		{ "\\u0041", 0, "\\u0041", "" },
		{ "\\t\\n\\r\\f", 0, "\\t\\n\\r\\f", "" },
		{ "[\\]\\[]", 0, "[\\]\\[]", "" },
		{ "[]a]", 0, "[\\]a]", "" },
		{
			"\\s+\\S+\\h\\H\\v\\V\\R",
			0,
			"[ \\t\\n\\x0B\\f\\r]+[^ \\t\\n\\x0B\\f\\r]+[ \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000][^ \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000][\\n\\x0B\\f\\r\\u0085\\u2028\\u2029][^\\n\\x0B\\f\\r\\u0085\\u2028\\u2029](?:\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])",
			""
		},
		{
			"[\\s\\h\\v]",
			0,
			"[ \\t\\n\\x0B\\f\\r \\t\\xA0\\u1680\\u180e\\u2000-\\u200a\\u202f\\u205f\\u3000\\n\\x0B\\f\\r\\u0085\\u2028\\u2029]",
			""
		},
		{ ".", 0, "[^\\n\\r\\u0085\\u2028\\u2029]", "" },
		{ ".", Pattern.DOTALL, "[\\s\\S]", "" },
		{ ".", Pattern.UNIX_LINES, "[^\\n]", "" },
		{ "^a$", 0, "^a(?=(?:\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])?(?![\\s\\S]))", "" },
		{
			"^a$",
			Pattern.MULTILINE,
			"(?:(?=[\\s\\S])(?:^|(?<=[\\n\\u0085\\u2028\\u2029])|(?<=\\r)(?!\\n)))a(?=(?![\\s\\S])|\\r|(?<!\\r)[\\n\\u0085\\u2028\\u2029])",
			""
		},
		{
			"^a$",
			Pattern.UNIX_LINES | Pattern.MULTILINE,
			"(?:(?=[\\s\\S])(?:^|(?<=\\n)))a(?=(?![\\s\\S])|\\n)",
			""
		},
		{ "^a$", Pattern.UNIX_LINES, "^a(?=\\n?(?![\\s\\S]))", "" },
		{
			"\\Aa\\z\\Z",
			0,
			"^a(?![\\s\\S])(?=(?:\\r\\n|[\\n\\r\\u0085\\u2028\\u2029])?(?![\\s\\S]))",
			""
		},
		{ "\\a\\e", 0, "\\x07\\x1B", "" },
		{ "[\\a\\e]", 0, "[\\x07\\x1B]", "" },
		{ "\\Qa+b\\E", 0, "a\\+b", "" },
		{ "[\\Qa-b\\E]", 0, "[a\\-b]", "" },
		{ "\\Qa\nb\\E", 0, "a\\nb", "" },
		{ "a.b", 0, "a[^\\n\\r\\u0085\\u2028\\u2029]b", "" },
		{ "\\p{Alpha}\\p{Punct}\\P{Digit}", 0, "[a-zA-Z][!-\\/:-@\\[-`{-~][^0-9]", "" },
		{ "[x\\p{Lower}\\p{Upper}]", 0, "[xa-zA-Z]", "" },
		{ "[\\p{Punct}]", 0, "[!-\\/:-@\\[-`{-~]", "" },
		{ "\\p{InGreek}", 0, "[\\u0370-\\u03ff]", "" },
		{ "[\\p{InGreek}x]", 0, "[\\u0370-\\u03ffx]", "" },
		{ "hello", Pattern.CASE_INSENSITIVE, "[hH][eE][lL][lL][oO]", "" },
		{ "hello", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "hello", "i" },
		{ "[a-z]+", Pattern.CASE_INSENSITIVE, "[a-zA-Z]+", "" },
		{ "[^a-z]", Pattern.CASE_INSENSITIVE, "[^a-zA-Z]", "" },
		{ "[a-z]+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "[a-z]+", "i" },
		{ "(a)\\1", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "(a)\\1", "i" },
		{ "[\\w-a]", Pattern.CASE_INSENSITIVE, "[\\w-a]", "i" },
		{ "\\x41\\u0062", Pattern.CASE_INSENSITIVE, "[aA][bB]", "" },
		{ "[\\x41-\\x5A]", Pattern.CASE_INSENSITIVE, "[\\x41-\\x5Aa-z]", "" },
		{ "\\p{Lower}", Pattern.CASE_INSENSITIVE, "[a-zA-Z]", "" },
		{ "(?i)abc", 0, "[aA][bB][cC]", "" },
		{ "(?iu)(a)\\1", 0, "(a)\\1", "i" },
		{ "a b # c\n d", Pattern.COMMENTS, "abd", "" },
		{ "a b", Pattern.LITERAL, "a b", "" },
		{
			"(?m)^a$",
			0,
			"(?:(?=[\\s\\S])(?:^|(?<=[\\n\\u0085\\u2028\\u2029])|(?<=\\r)(?!\\n)))a(?=(?![\\s\\S])|\\r|(?<!\\r)[\\n\\u0085\\u2028\\u2029])",
			""
		},
		{ "(?s).", 0, "[\\s\\S]", "" },
		{ "(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11", 0, "(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)\\11", "" },
		{ "x{3}", 0, "x{3}", "" },
		{ "x*?y+?z??", 0, "x*?y+?z??", "" },
		{ "a\\-b", 0, "a\\-b", "" },
		{ "a\\@b", 0, "a\\@b", "" },
		{ "a]b}", 0, "a]b}", "" },
		{ "[a\\-b]", 0, "[a\\-b]", "" },
		{ "[\\d\\w\\D\\W]", 0, "[\\d\\w\\D\\W]", "" },
		{ "\\b\\B", 0, "\\b\\B", "" },
		{ "(?<=ab)c(?<!d)", 0, "(?<=ab)c(?<!d)", "" },
		{ "(?:a|b)*c", 0, "(?:a|b)*c", "" },
		{ "[a-c&]", 0, "[a-c&]", "" }
	};

	@Test
	@DisplayName(
		"translates a pattern with none of the new constructs exactly as it did before them"
	)
	void leavesEveryOtherSourceAlone() {
		for (Object[] row : RECORDED) {
			Translate.Result translated = Translate.translate((String) row[0], (Integer) row[1]);
			assertEquals(row[2], translated.source, "source of " + row[0] + " under " + row[1]);
			assertEquals(row[3], translated.jsFlags, "flags of " + row[0] + " under " + row[1]);
		}
	}

	// #endregion
}
