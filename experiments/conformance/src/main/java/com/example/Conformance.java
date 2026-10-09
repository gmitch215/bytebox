package com.example;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prints a deterministic transcript of results that a JVM and this platform must agree on.
 *
 * <p>Only {@code java.*} is used, so the same source runs on a JVM directly and compiled into a
 * Worker. Differences between the two transcripts are the measurement.
 */
public final class Conformance {

	private Conformance() {}

	/** A pattern and the inputs it is run against. */
	private static final String[][] PATTERNS = {
		{ "a+b", "aaab", "b", "aaa", "" },
		{ "(a|b)*c", "abbac", "c", "abab" },
		{ "^\\d{3}-\\d{4}$", "555-1234", "5551234", "555-12345" },
		{ "(\\w+)@(\\w+)\\.com", "user@example.com", "no-at-sign" },
		{ "a{2,4}", "a", "aa", "aaaaa" },
		{ "[^abc]+", "xyz", "abc", "axc" },
		{ "(foo)(bar)?", "foo", "foobar" },
		{ "a*?b", "aaab", "b" },
		{ "(?i)HeLLo", "hello", "HELLO", "help" },
		{ "\\bword\\b", "a word here", "sword" },
		{ "(?=.*x).*", "axb", "ab" },
		{ "(?!foo)\\w+", "bar", "foo" },
		{ "(a)\\1", "aa", "ab" },
		{ "[a-z&&[^aeiou]]+", "bcd", "abc" },
		{ "\\Qa+b\\E", "a+b", "aab" },
		{ "x(?:yz)+", "xyzyz", "xy" },
		{ "^$", "", "a" },
		{ "\\s+", " \t ", "a b" },
		{ "\\d+(?:\\.\\d+)?", "3.14", "42", "." },
		{ "(?<name>\\d+)-(?<rest>\\w+)", "12-ab", "x-y" }
	};

	/** Values whose shortest round-tripping decimal is the thing being compared. */
	private static double[] doubles() {
		double[] fixed = {
			0.0,
			-0.0,
			1.0,
			-1.0,
			0.1,
			0.2,
			0.3,
			0.35,
			1.0 / 3.0,
			2.0 / 3.0,
			1e-3,
			1e-7,
			1e7,
			1e21,
			1e-22,
			1e300,
			1e-300,
			123456789.0,
			0.000123456,
			Double.MIN_VALUE,
			Double.MAX_VALUE,
			Double.MIN_NORMAL,
			Math.PI,
			Math.E,
			4.9e-324,
			1.7976931348623157e308,
			9007199254740992.0,
			9007199254740993.0,
			0.5,
			0.25,
			1.5e-10
		};
		double[] all = new double[fixed.length + 512];
		System.arraycopy(fixed, 0, all, 0, fixed.length);
		// a fixed LCG so both sides see the same bits
		long seed = 0x5DEECE66DL;
		for (int i = 0; i < 512; i++) {
			seed = (seed * 0x5DEECE66DL + 0xB) & ((1L << 48) - 1);
			long hi = seed;
			seed = (seed * 0x5DEECE66DL + 0xB) & ((1L << 48) - 1);
			double d = Double.longBitsToDouble((hi << 16) ^ seed);
			all[fixed.length + i] = Double.isNaN(d) || Double.isInfinite(d) ? i : d;
		}
		return all;
	}

	/** Prints the double transcript. */
	public static void doublesTranscript() {
		for (double d : doubles()) {
			System.out.println("D " + Double.toString(d) + " " + Double.toHexString(d));
		}
		System.out.println("D NaN " + Double.toString(Double.NaN));
		System.out.println("D INF " + Double.toString(Double.POSITIVE_INFINITY));
		System.out.println("D FLT " + Float.toString(0.1f) + " " + Float.toString(1f / 3f));
		subnormalsTranscript();
	}

	/** Every power-of-two subnormal, the ends of the range and a drawn sample. */
	private static double[] subnormals() {
		double[] all = new double[52 + 52 + 10 + 200];
		int at = 0;
		for (int bit = 0; bit < 52; bit++) {
			all[at++] = Double.longBitsToDouble(1L << bit);
			all[at++] = Double.longBitsToDouble((1L << bit) + 1);
		}
		long[] ends = {
			2L,
			3L,
			0x000ffffffffffffeL,
			0x000fffffffffffffL,
			0x0010000000000000L,
			0x0010000000000001L,
			0x000ffffffffffffdL,
			0x0008000000000000L,
			0x0000000000000064L,
			0x00000000000003e8L
		};
		for (long bits : ends) all[at++] = Double.longBitsToDouble(bits);
		long seed = 0x5DEECE66DL;
		for (int i = 0; i < 200; i++) {
			seed = (seed * 0x5DEECE66DL + 0xB) & ((1L << 48) - 1);
			all[at++] = Double.longBitsToDouble(1 + ((seed >>> (i % 40)) & 0xfffffffffffeL));
		}
		return all;
	}

	/**
	 * The subnormal rows. {@code T} is Double.toString itself, which this platform takes from its class
	 * library; {@code S} goes through the formatter, which does not.
	 */
	public static void subnormalsTranscript() {
		for (double d : subnormals()) {
			String bits = Long.toHexString(Double.doubleToRawLongBits(d));
			System.out.println(
				"T " + bits + " " + Double.toString(d) + " " + Double.toHexString(d)
			);
			for (String format : new String[] { "%e", "%g", "%.5f", "%.17e", "%a", "%.3a", "%A" }) {
				formatRow("S " + bits, format, d);
			}
		}
	}

	/** One format against one argument, written as the text or as the exception's class. */
	private static void formatRow(String tag, String format, Object argument) {
		String result;
		try {
			result = String.format(format, argument);
		} catch (Throwable t) {
			result = "THREW " + t.getClass().getName();
		}
		System.out.println(tag + " " + format + " " + result);
	}

	/** The conversions that are not a plain number: hexadecimal floating point and big integers. */
	public static void formatTranscript() {
		double[] hex = {
			0.0,
			-0.0,
			1.0,
			-1.5,
			0.1,
			Math.PI,
			1e300,
			Double.MAX_VALUE,
			Double.MIN_NORMAL,
			Double.NaN,
			Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY
		};
		String[] hexFormats = {
			"%a",
			"%A",
			"%.0a",
			"%.1a",
			"%.12a",
			"%.14a",
			"%20a",
			"%-20a|",
			"%020a",
			"%+a",
			"% a",
			"%+020a",
			"%(a"
		};
		for (double d : hex) {
			String bits = Long.toHexString(Double.doubleToRawLongBits(d));
			for (String format : hexFormats) formatRow("A " + bits, format, d);
		}
		java.math.BigInteger[] big = {
			java.math.BigInteger.ZERO,
			java.math.BigInteger.ONE.negate(),
			new java.math.BigInteger("255"),
			new java.math.BigInteger("-255"),
			java.math.BigInteger.ONE.shiftLeft(64),
			new java.math.BigInteger("-123456789012345678901234567890"),
			java.math.BigInteger.TEN.pow(40)
		};
		String[] radixFormats = {
			"%x",
			"%X",
			"%o",
			"%#x",
			"%#o",
			"%020x",
			"%#020x",
			"%(x",
			"%+o",
			"%30x",
			"%-30o|",
			"%,x"
		};
		for (java.math.BigInteger b : big) {
			for (String format : radixFormats) formatRow("X " + b, format, b);
		}
		formatRow("X 255L", "%#010x", 255L);
		formatRow("X 255", "%#010X", 255);
		formatRow("X -1", "%#08o", -1);
		formatRow("X BigDecimal", "%x", new java.math.BigDecimal("255"));
	}

	/** Prints the regex transcript. */
	public static void regexTranscript() {
		for (String[] row : PATTERNS) {
			String pattern = row[0];
			for (int i = 1; i < row.length; i++) {
				String input = row[i];
				StringBuilder line = new StringBuilder("R ")
					.append(pattern)
					.append(" | ")
					.append(input)
					.append(" | ");
				try {
					Matcher m = Pattern.compile(pattern).matcher(input);
					boolean found = m.find();
					line.append(found);
					if (found) {
						line.append(' ').append(m.start()).append(':').append(m.end());
						for (int g = 1; g <= m.groupCount(); g++) {
							line.append(" g").append(g).append('=').append(m.group(g));
						}
					}
					line.append(" matches=").append(Pattern.matches(pattern, input));
					line.append(" split=").append(Pattern.compile(pattern).split("axbxc").length);
				} catch (Throwable t) {
					line.append("THREW ").append(t.getClass().getName());
				}
				System.out.println(line);
			}
		}
	}

	/** Prints every transcript, which a JVM and this platform must agree on line for line. */
	public static void transcript() {
		doublesTranscript();
		formatTranscript();
		regexTranscript();
		regexFlagsTranscript();
		regexBlocksTranscript();
		regexCaseTranscript();
		regexFoldSweep();
		regexAtomicTranscript();
		regexGroupsTranscript();
		regexClassesTranscript();
		regexPairTranscript();
		regexBackreferenceTranscript();
	}

	/**
	 * A search that a JVM begins inside a surrogate pair: after an empty match, from {@code find(int)},
	 * and by trying every UTF-16 unit. Each row lists every match and then the match from each position.
	 */
	public static void regexPairTranscript() {
		String pair = "😀";
		String[] inputs = {
			"",
			pair,
			"a" + pair + "b",
			pair + pair,
			"a" + pair,
			pair + "b",
			"ab" + pair + pair + "cd",
			"a b " + pair + " c\n" + pair,
			"\ude00",
			"\ud83dx",
			"\ude00\ud83dx",
			"x\ude00y"
		};
		String[] patterns = {
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
		};
		for (String pattern : patterns) {
			for (String input : inputs) {
				StringBuilder line = new StringBuilder("S ")
					.append(shown(pattern))
					.append(" | ")
					.append(shown(input))
					.append(" | ");
				try {
					Pattern p = Pattern.compile(pattern);
					Matcher m = p.matcher(input);
					line.append("all=");
					while (m.find()) line.append(m.start()).append(':').append(m.end()).append(',');
					line.append(" from=");
					for (int from = 0; from <= input.length(); from++) {
						m = p.matcher(input);
						line.append(m.find(from) ? m.start() + ":" + m.end() : "-").append(',');
					}
				} catch (Throwable t) {
					line.append("THREW ").append(t.getClass().getName());
				}
				System.out.println(line);
			}
		}
	}

	/**
	 * A backreference to a group that is still open, which a JVM reads before the group has closed and
	 * this platform refuses, next to the references it keeps.
	 */
	public static void regexBackreferenceTranscript() {
		String[] runs = { "a", "aa", "aaa", "ab", "aba", "abab", "bb", "abb", "" };
		String[][] table = {
			{ "(a\\1)" },
			{ "(a\\1)+" },
			{ "(a\\1)?" },
			{ "(a\\1)*" },
			{ "(a|b\\1)+" },
			{ "(?<n>a\\k<n>)" },
			{ "(?<n>a\\k<n>)+" },
			{ "(?<n>a)(?<m>b\\k<m>)" },
			{ "((a\\1))" },
			{ "(x(a\\1)y)" },
			{ "(?:(a\\1))+" },
			{ "((a)\\1)" },
			{ "(a(b\\2))" },
			{ "(a(?=\\1))" },
			{ "(a(?>b)\\1)" },
			{ "(a)(b\\1)" },
			{ "((a)\\2)" },
			{ "(a(b)\\2)" },
			{ "(a)(?:(b)\\1\\2)+" },
			{ "(?<n>a)(?<m>b\\k<n>)" },
			{ "(a)(?=\\1)" },
			{ "(a)\\1+" }
		};
		for (String[] pattern : table) {
			String[] row = new String[runs.length + 1];
			row[0] = pattern[0];
			System.arraycopy(runs, 0, row, 1, runs.length);
			rows(0, new String[][] { row });
		}
	}

	/** A class inside a class, and an intersection of classes, which need the v flag here. */
	public static void regexClassesTranscript() {
		String[] runs = {
			"abc",
			"xyz",
			"a-b",
			"..",
			"]a",
			"{}",
			"a&b",
			"AB",
			"a😀b",
			"😀",
			"éα",
			"a b",
			""
		};
		String[] patterns = {
			"[a[b]]+",
			"[^a[b]]+",
			"[a[^b]]+",
			"[a-c[x-z]]+",
			"[a-z&&[^b]]+",
			"[a-z&&[^aeiou]]+",
			"[^a-z&&[^b]]+",
			"[a-z&&b-d&&[^c]]+",
			"[a-c[x-z]&&[^b]]+",
			"[abc&&def]+",
			"[\\w&&[^\\d]]+",
			"[\\p{Alpha}&&[^\\p{Lower}]]+",
			"[(){}|/[a]]+",
			"[!#%&,:;<=>@`~[a]]+",
			"[$*+.?^[a]]+",
			"[a\\-b[c]]+",
			"[a-[b]]+",
			"[--a[b]]+",
			"[\\p{Punct}[a]]+",
			"[\\p{InGreek}[x]]+",
			"[\\P{InGreek}[x]]+",
			"[^\\x00-\\x7F&&[^é]]+",
			"a\\-b[[x]]",
			"x}[[a]]",
			"[[a]]]",
			"[a[b]]*",
			"[a[b]]*+b",
			"(?>[a[b]]+)c",
			"([a[b]])\\1",
			"(?<=[a[b]])c",
			".[[a]]",
			"[^\\n[a]]+",
			"[&&a]",
			"[a&&]",
			"[a&&&b]",
			"[a&&b&c]",
			"[a-\\Qz\\E[b]]",
			"[\\S[a]]"
		};
		for (String pattern : patterns) {
			String[] row = new String[runs.length + 1];
			row[0] = pattern;
			System.arraycopy(runs, 0, row, 1, runs.length);
			rows(0, new String[][] { row });
		}
		String[][] folded = {
			{ "[a-c&&[^b]]+", "aBcD", "ABC" },
			{ "[^a[b]]+", "aBcD", "xyz" },
			{ "[a[^b]]+", "aBcD" },
			{ "[x-z&&[^y]]+", "XyZ" },
			{ "[a-z&&[^aeiou]]+", "AbcE" },
			{ "[\\d-z[a]]+", "1-zA" },
			{ "([a[b]])\\1", "aa" }
		};
		rows(Pattern.CASE_INSENSITIVE, folded);
		rows(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, new String[][] { { "[a[b]]", "A" } });
		rows(Pattern.MULTILINE, new String[][] { { "^[[a]]$", "a\na", "ab" } });
		rows(Pattern.DOTALL, new String[][] { { ".[[a]]", "\na" } });
		rows(Pattern.COMMENTS, new String[][] {
			{ "[a b]", "a b" },
			{ "[a #c\nb]", "a b #c\n" },
			{ "[a[b c]]", "a b c" }
		});
		for (String pattern : new String[] { "(?<n>a)(?<n>b)", "[a-\\d[b]]" }) {
			try {
				Pattern.compile(pattern);
				System.out.println("E " + pattern + " compiled");
			} catch (Throwable t) {
				System.out.println("E " + pattern + " threw " + t.getClass().getName());
			}
		}
	}

	/** Atomic groups and possessive quantifiers, which are a lookahead and a backreference here. */
	public static void regexAtomicTranscript() {
		String[] runs = { "abc", "ab", "aab", "abcabc", "aaa", "a.a.", "", "abcd", "aaab", "xaaa" };
		String[] patterns = {
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
			"(?>\\Qa.\\E)+",
			"(?>)",
			"a*+a",
			"a++a",
			"a?+a",
			"a{1,2}+a",
			"[ab]*+b",
			"\\w*+x",
			".*+c",
			"(?:ab)*+c",
			"\\Qab\\E*+b",
			"x*+y*+z",
			"(?=a*+b)",
			"(?!a*+b)a",
			"(?>a)*+",
			"(?>a*+)b",
			"^a*+$",
			"\\b\\w++\\b",
			"(?<=a)b*+",
			"(?<=(?>a))b",
			"(?<!a++)b"
		};
		for (String pattern : patterns) {
			String[] row = new String[runs.length + 1];
			row[0] = pattern;
			System.arraycopy(runs, 0, row, 1, runs.length);
			rows(0, new String[][] { row });
		}
		rows(Pattern.CASE_INSENSITIVE, new String[][] {
			{ "(?>a|ab)c", "abc", "ABC", "aBc" },
			{ "a*+A", "aaA", "AAa" }
		});
		rows(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, new String[][] {
			{ "(?>a|ab)c", "abc", "ABC" },
			{ "A++a", "aA", "Aa" }
		});
		rows(Pattern.COMMENTS, new String[][] {
			{ "a* +a", "aaa", "aab" },
			{ "a *+ a", "aaa" },
			{ "(?>a b) c", "abc" }
		});
	}

	/** The group numbers a caller reads when an atomic group or a possessive quantifier is in the pattern. */
	public static void regexGroupsTranscript() {
		String[] runs = { "abcd", "abcabcd", "aabcdd", "abbcc", "aba", "abcdefghijkll" };
		String[] patterns = {
			"(?>(a)b)",
			"(a)(?>(b)(c))(d)",
			"(?<n>a)(?>b)(?<m>c)",
			"(?>(?<x>a))(b)",
			"(a)(?>b)\\1",
			"(?>(a)b\\1)",
			"(?>(a))(b)\\2",
			"(?>a)(b)\\1",
			"(a)*+(b)",
			"(?>(a)(?>(b)c))(d)",
			"(?<one>a)(?>b)\\k<one>",
			"(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(?>k)(l)\\11",
			"(a)(?>(b)|(c))(?>(d))",
			"(?>a+)(?<x>b)(?<y>a)",
			"(a)\\10",
			"(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\10",
			"(?>(a)|(b))c",
			"(ab)*+b",
			"(a)\\2",
			"(?:\\1(a))",
			"(a\\1)"
		};
		for (String pattern : patterns) {
			for (String input : runs) {
				StringBuilder line = new StringBuilder("G ")
					.append(shown(pattern))
					.append(" | ")
					.append(shown(input))
					.append(" | ");
				try {
					Pattern p = Pattern.compile(pattern);
					line.append("count=").append(p.matcher("").groupCount());
					line.append(" names=").append(new java.util.TreeMap<>(p.namedGroups()));
					StringBuilder replacement = new StringBuilder("<");
					for (int g = 0; g <= p.matcher("").groupCount(); g++) {
						replacement.append('$').append(g).append('|');
					}
					Matcher m = p.matcher(input);
					while (m.find()) {
						line.append(" [");
						for (int g = 0; g <= m.groupCount(); g++) {
							line.append(g == 0 ? "" : " ")
								.append(shown(m.group(g)))
								.append('@')
								.append(m.start(g))
								.append('-')
								.append(m.end(g));
						}
						line.append(']');
					}
					line.append(" replaced=").append(
						shown(p.matcher(input).replaceAll(replacement.toString()))
					);
				} catch (Throwable t) {
					line.append("THREW ").append(t.getClass().getName());
				}
				System.out.println(line);
			}
		}
	}

	/**
	 * Every character of the basic multilingual plane that has a case mapping, matched as a literal
	 * against each character it maps to, with and without {@code UNICODE_CASE}.
	 */
	public static void regexFoldSweep() {
		for (int flags : new int[] {
			Pattern.CASE_INSENSITIVE,
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
		}) {
			for (int c = 0; c <= 0xFFFF; c++) {
				if (c >= 0xD800 && c <= 0xDFFF) continue;
				char ch = (char) c;
				char[] partners = {
					Character.toLowerCase(ch),
					Character.toUpperCase(ch),
					Character.toTitleCase(ch),
					Character.toLowerCase(Character.toUpperCase(ch)),
					Character.toUpperCase(Character.toLowerCase(ch))
				};
				String hex = Integer.toHexString(c);
				String pattern = "\\u" + "0000".substring(hex.length()) + hex;
				Pattern compiled = Pattern.compile(pattern, flags);
				for (int i = 0; i < partners.length; i++) {
					char partner = partners[i];
					boolean seen = partner == ch;
					for (int j = 0; j < i; j++) seen |= partners[j] == partner;
					if (seen) continue;
					System.out.println(
						"F[" +
							flags +
							"] " +
							shown(String.valueOf(ch)) +
							" " +
							shown(String.valueOf(partner)) +
							" " +
							compiled.matcher(String.valueOf(partner)).matches()
					);
				}
			}
		}
	}

	/** Runs on a JVM as {@code java Conformance.java}, which is the reference side of the diff. */
	public static void main(String[] args) {
		transcript();
	}

	/** Leading inline flags: folded into the flags where they open the pattern, refused elsewhere. */
	public static void regexFlagsTranscript() {
		rows(0, new String[][] {
			{ "(?i)HeLLo", "hello", "HELLO", "help" },
			{ "(?s).", "\n", "a" },
			{ "(?m)^b", "a\nb", "ab" },
			{ "(?x) a b # c", "ab", "a b" },
			{ "(?is)", "", "x" },
			{ "(?i)(?s)x", "X", "x", "y" },
			{ "(?i)", "a", "" },
			{ "(?-i)x", "x", "X" },
			{ "(?i-s)a.", "A\n", "Ab" },
			{ "(?ii)x", "X" },
			{ "(?i-i)x", "X" },
			{ "(?d).", "\r", "\n" },
			{ "(?iu)\u00e9", "\u00c9", "\u00e9" },
			{ "(?)a", "a" },
			{ "(?-)a", "a" },
			{ "(?i-)a", "A" },
			{ "a(?i)b", "aB", "ab" },
			{ "(?i:x)", "X" },
			{ "(?U)a", "a" },
			{ "(?c)a", "a" },
			{ "(?i", "a" }
		});
		rows(Pattern.CASE_INSENSITIVE, new String[][] { { "(?-i)x", "x", "X" }, { "(?)x", "X" } });
		rows(Pattern.DOTALL, new String[][] { { "(?-s).", "\n", "a" } });
		rows(Pattern.COMMENTS, new String[][] { { "(?-x) a", " a", "a" } });
		rows(Pattern.LITERAL, new String[][] { { "(?i)a", "(?i)a", "A" } });
	}

	/** Block properties, written as ranges. */
	public static void regexBlocksTranscript() {
		rows(0, new String[][] {
			{
				"\\p{InCombiningDiacriticalMarks}+",
				"e\u0301",
				"caf\u00e9",
				"a\u0300\u0301b",
				"abc"
			},
			{ "\\p{InGreek}+", "abc \u03b1\u03b2 d", "abc" },
			{ "\\P{InGreek}+", "abc \u03b1\u03b2 d", "\u03b1" },
			{ "[\\p{InGreek}x]+", "x\u03b1y", "abc" },
			{ "[^\\p{InGreek}]+", "ab\u03b1cd", "\u03b1" },
			{ "[\\P{InGreek}]+", "ab\u03b1cd", "\u03b1" },
			{ "[a\\P{InGreek}]+", "ab\u03b1cd", "\u03b1" },
			{ "\\p{blk=Greek}+", "ab\u03b1\u03b2", "ab" },
			{ "\\p{block=greek and coptic}+", "ab\u03b1\u03b2", "ab" },
			{ "\\P{blk=Greek}+", "ab\u03b1\u03b2", "\u03b1" },
			{ "\\p{InBasic_Latin}+", "ab\u00e9", "\u00e9" },
			{ "\\p{InBasicLatin}+", "ab\u00e9", "\u00e9" },
			{ "\\p{InLatin-1Supplement}+", "ab\u00e9\u00ff", "ab" },
			{ "\\p{InCyrillic}+", "ab\u0431\u0432", "ab" },
			{ "\\p{InCJKUnifiedIdeographs}+", "ab\u4e2d\u6587", "ab" },
			{ "\\p{InHangulSyllables}+", "ab\uac00", "ab" },
			{ "\\p{InPrivateUseArea}", "a\ue000" },
			{ "\\p{InSpecials}", "a\ufffd", "a" },
			{ "\\p{InEmoticons}", "a", "\ud83d\ude00" },
			{ "\\p{InHighSurrogates}", "a" },
			{ "\\p{InNoSuchBlock}", "a" },
			{ "\\P{InGreek}", "\ud83d\ude00", "a\ud83d\ude00b" },
			{ "[^x]", "\ud83d\ude00", "a\ud83d\ude00b" },
			{ "a.b", "a\ud83d\ude00b" },
			{ "\\p{L}", "a" },
			{ "\\p{Lower}+", "abC", "ABC" },
			{ "\\P{Lower}+", "abC", "ABC" }
		});
		String[] wide = {
			"x\ud835\udfd8y",
			"\ud83d\ude00b",
			"\ud83d",
			"\ude00",
			"\ud83d\ude00\ud83d\ude00",
			"a\ud83d\ude00\ud83d\ude00b"
		};
		for (String pattern : new String[] {
			"\\P{InGreek}+",
			"[\\P{InGreek}x]+",
			"\\P{InBasicLatin}+",
			"(\\P{InGreek})\\1",
			"\\P{InGreek}*+b",
			"\\P{InGreek}{2}",
			"\\P{InGreek}*?b",
			"(?>\\P{InGreek}+)b",
			"[^\\P{InGreek}]+",
			"[a-z&&\\P{InGreek}]+"
		}) {
			String[] row = new String[wide.length + 1];
			row[0] = pattern;
			System.arraycopy(wide, 0, row, 1, wide.length);
			rows(0, new String[][] { row });
		}
		rows(Pattern.CASE_INSENSITIVE, new String[][] {
			{ "\\P{InGreek}+", "x\ud835\udfd8y", "\ud83d\ude00" },
			{ "[a\\P{InGreek}]+", "A\ud83d\ude00" }
		});
		rows(Pattern.DOTALL, new String[][] {
			{ "\\P{InGreek}.", "x\ud83d\ude00", "\ud83d\ude00\n" }
		});
		rows(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, new String[][] {
			{ "\\P{InGreek}+", "x\ud835\udfd8y" },
			{ "[a[b]]", "A" }
		});
		rows(Pattern.CASE_INSENSITIVE, new String[][] {
			{ "\\p{InBasicLatin}+", "aB\u00e9" },
			{ "\\p{InGreek}+", "ab\u03b1\u03a3" },
			{ "\\p{Lower}+", "abC", "ABC" },
			{ "\\P{Lower}+", "abC", "ABC", "12" },
			{ "[\\p{Lower}]+", "abC", "12" },
			{ "[^\\p{Lower}]+", "abC", "12" }
		});
	}

	/** What each case-folding mode does to a letter, the cross-script folds included. */
	public static void regexCaseTranscript() {
		String[][] table = {
			{ "e", "e", "E", "\u00e9" },
			{ "\u00e9", "\u00e9", "\u00c9", "e", "E" },
			{ "s", "s", "S", "\u017f" },
			{ "\u017f", "\u017f", "s", "S" },
			{ "k", "k", "K", "\u212a" },
			{ "K", "k", "K", "\u212a" },
			{ "\u212a", "\u212a", "k", "K" },
			{ "i", "i", "I", "\u0130", "\u0131" },
			{ "\u0130", "\u0130", "i", "I", "\u0131" },
			{ "\u03c3", "\u03c3", "\u03a3", "\u03c2" },
			{ "\u03c2", "\u03c2", "\u03c3", "\u03a3" },
			{ "[a-z]", "a", "A", "z", "Z", "\u017f", "\u212a" },
			{ "[^a-z]", "a", "A", "z", "Z", "\u017f", "\u212a", "1" },
			{ "[a-\u00e9]", "a", "A", "\u00c0", "\u00e0", "\u00c9" },
			{ "[\u00e0-\u00e9]", "\u00e0", "\u00c0", "\u00e9", "\u00c9" },
			{ "(a)\\1", "aa", "aA", "Aa", "AA" },
			{ "(\u00e9)\\1", "\u00e9\u00e9", "\u00e9\u00c9" },
			{ "\u00df", "\u00df", "\u1e9e", "ss", "SS" },
			{ "ss", "ss", "SS", "\u00df", "\u1e9e" },
			{ "\\x41", "a", "A" },
			{ "\\u0041", "a", "A" },
			{ "\\101", "a", "A" },
			{ "[\\x41-\\x43]", "a", "B", "d" },
			{ "\\Qab\\E", "AB", "ab" },
			{ "\\w+", "aB\u00e9", "\u017f" },
			{ "\\bk\\b", "k", "\u212a" },
			{ "(?<n>a)\\k<n>", "aA", "aa" },
			{ "[A-Za-z]+", "k\u212a\u017f", "abc" },
			{ "a|b", "A", "B" }
		};
		rows(Pattern.CASE_INSENSITIVE, table);
		rows(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, table);
		rows(0, table);
	}

	/** One line per pattern and input: the first match, every match, whole-input match, and flags. */
	private static void rows(int flags, String[][] table) {
		for (String[] row : table) {
			for (int i = 1; i < row.length; i++) {
				StringBuilder line = new StringBuilder("R")
					.append(flags == 0 ? "" : "[" + flags + "]")
					.append(' ')
					.append(shown(row[0]))
					.append(" | ")
					.append(shown(row[i]))
					.append(" | ");
				try {
					Pattern p = Pattern.compile(row[0], flags);
					Matcher m = p.matcher(row[i]);
					boolean found = m.find();
					line.append(found);
					if (found) {
						line.append(' ').append(m.start()).append(':').append(m.end());
						for (int g = 1; g <= m.groupCount(); g++) {
							line.append(" g")
								.append(g)
								.append('=')
								.append(shown(m.group(g)));
						}
					}
					line.append(" all=");
					m = p.matcher(row[i]);
					while (m.find()) line.append(m.start()).append(':').append(m.end()).append(',');
					line.append(" matches=").append(p.matcher(row[i]).matches());
					line.append(" flags=").append(p.flags());
				} catch (Throwable t) {
					line.append("THREW ").append(t.getClass().getName());
				}
				System.out.println(line);
			}
		}
	}

	/** A string with everything outside printable ASCII written as a code unit escape. */
	private static String shown(String text) {
		if (text == null) return "null";
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0x20 && c < 0x7F) {
				out.append(c);
			} else {
				String hex = Integer.toHexString(c);
				out.append("\\u").append("0000".substring(hex.length())).append(hex);
			}
		}
		return out.toString();
	}

	/** Runs a pattern that backtracks badly, at a size given by the caller. */
	public static void backtrack(int n) {
		StringBuilder input = new StringBuilder();
		for (int i = 0; i < n; i++) input.append('a');
		long started = System.nanoTime();
		boolean matched = Pattern.compile("(a+)+b").matcher(input.toString()).matches();
		System.out.println("B n=" + n + " matched=" + matched);
	}
}
