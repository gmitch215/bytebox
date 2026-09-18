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

	/** Runs a pattern that backtracks badly, at a size given by the caller. */
	public static void backtrack(int n) {
		StringBuilder input = new StringBuilder();
		for (int i = 0; i < n; i++) input.append('a');
		long started = System.nanoTime();
		boolean matched = Pattern.compile("(a+)+b").matcher(input.toString()).matches();
		System.out.println("B n=" + n + " matched=" + matched);
	}
}
