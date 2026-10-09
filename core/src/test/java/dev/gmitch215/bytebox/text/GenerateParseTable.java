package dev.gmitch215.bytebox.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes {@code parse-grammar.tsv}: what the running JDK's {@code Double.parseDouble} and
 * {@code Float.parseFloat} answer for a few hundred strings.
 *
 * <p>The answer is the bits of the value or, for a refusal, the message. The grammar and the
 * messages are those of JDK 25 and later (JDK 21 words 74 of these refusals differently), so run it
 * on one, from the repository root:
 *
 * <pre>
 * java core/src/test/java/dev/gmitch215/bytebox/text/GenerateParseTable.java \
 *     core/src/test/resources/dev/gmitch215/bytebox/text/parse-grammar.tsv
 * </pre>
 *
 * <p>Each line is the string, the double and the float, separated by tabs. A string is written with
 * {@code \t}, {@code \n}, {@code \\} and {@code \}{@code uXXXX} escapes for anything not in
 * printable ASCII. A value is {@code D} and 16 hexadecimal digits or {@code F} and 8; a refusal is
 * {@code !} and the message, escaped the same way.
 */
public final class GenerateParseTable {

	private GenerateParseTable() {}

	/**
	 * @param text a string as the table writes it
	 * @return the string
	 */
	public static String unescape(String text) {
		StringBuilder out = new StringBuilder();
		for (int at = 0; at < text.length(); at++) {
			char c = text.charAt(at);
			if (c != '\\') {
				out.append(c);
				continue;
			}
			char next = text.charAt(++at);
			if (next == 't') out.append('\t');
			else if (next == 'n') out.append('\n');
			else if (next == 'u') {
				out.append((char) Integer.parseInt(text.substring(at + 1, at + 5), 16));
				at += 4;
			} else out.append(next);
		}
		return out.toString();
	}

	static String escape(String text) {
		StringBuilder out = new StringBuilder();
		for (int at = 0; at < text.length(); at++) {
			char c = text.charAt(at);
			if (c == '\\') out.append("\\\\");
			else if (c == '\t') out.append("\\t");
			else if (c == '\n') out.append("\\n");
			else if (c < ' ' || c > '~') out.append(String.format("\\u%04x", (int) c));
			else out.append(c);
		}
		return out.toString();
	}

	static String ofDouble(String text) {
		try {
			return String.format("D%016x", Double.doubleToRawLongBits(Double.parseDouble(text)));
		} catch (NumberFormatException e) {
			return "!" + escape(e.getMessage());
		}
	}

	static String ofFloat(String text) {
		try {
			return String.format("F%08x", Float.floatToRawIntBits(Float.parseFloat(text)));
		} catch (NumberFormatException e) {
			return "!" + escape(e.getMessage());
		}
	}

	static List<String> inputs() {
		Set<String> all = new LinkedHashSet<>();

		// decimals a JVM accepts
		for (String s : new String[] {
			"0",
			"-0",
			"+0",
			"0.0",
			"-0.0",
			"00",
			"007",
			"1",
			"-1",
			"+1",
			"1.",
			".5",
			"-.5",
			"+.5",
			"5.e0",
			"1.5",
			"1e5",
			"1E5",
			"1e+5",
			"1e-5",
			"1E-5",
			"1.0e+1",
			"1e00005",
			".1e1",
			"1.e1",
			"0e0",
			"0.e0",
			"1e0000000000000000000005",
			"1e99999999999",
			"1e99999999999999999999",
			"1e-99999999999999999999",
			"0e99999999999999999999",
			"1e308",
			"1e309",
			"-1e309",
			"1e-324",
			"1e-325",
			"1e-400",
			"2e-324",
			"4.9E-324",
			"4.9406564584124654E-324",
			"2.4703282292062327E-324",
			"2.4703282292062328E-324",
			"1.7976931348623157E308",
			"1.7976931348623158E308",
			"1.797693134862315807E308",
			"2.2250738585072011E-308",
			"2.2250738585072012E-308",
			"0.1",
			"0.3",
			"0.30000000000000004",
			"4.35",
			"0.001",
			"100",
			"1e23",
			"9.999999999999999E22",
			"3.687639494787356E-165",
			"3.6876394947873562E-165",
			"-1e-400",
			"-1e-46",
			"-7.0E-46",
			"-4.9E-325",
			"-0e5",
			"-0.0e-99",
			"-0x0.0p5",
			"-0x1p-1075",
			"-0x1p-150",
			"1.4E-45",
			"1.17549435E-38",
			"3.4028235E38",
			"3.4028236E38",
			"3.4028235677973366E38",
			"3.4028235677973365E38",
			"3.4028235677973367E38",
			"7.0E-46",
			"7.1E-46",
			"7.0064923216240854E-46",
			"7.0064923216240853E-46",
			"7.0064923216240855E-46",
			"1e-45",
			"1e-46",
			"1e39",
			"1e38",
			"16777217",
			"16777219",
			"33554433",
			"33554435",
			"8.388609E6",
			"1.00000006",
			"1.00000012",
			"1.000000178813934326",
			"1.000000178813934327",
			"1.000000178813934328",
			"1.0000000596046447754",
			"1.0000000596046447752",
			"1.0000000596046447756",
			"9007199254740993",
			"9007199254740993.0000000000000000000001",
			"9007199254740993.0000000000000000000000",
			"9007199254740992.9999999999999999999999",
			"9007199254740991.5",
			"0.00000000000000000000000000001",
			"123456789012345678901234567890",
			"-123456789012345678901234567890e-30",
			"123456789e-9",
			"00.5",
			"0000.0000",
			"0.0000e5"
		})
			all.add(s);

		// suffixes
		for (String s : new String[] {
			"1f",
			"1F",
			"1d",
			"1D",
			"1.5f",
			"0.5d",
			"1e1f",
			"1e1d",
			"1e1D",
			"1e1F",
			".5f",
			"5.d",
			"1ff",
			"1fd",
			"1dd",
			"1.5ff",
			"1ef",
			"1f ",
			"1 f",
			"f",
			"d",
			".f",
			"1e1ff",
			"NaNf",
			"NaNd",
			"Infinityf",
			"Infinityd"
		})
			all.add(s);

		// whitespace, signs and symbols
		for (String s : new String[] {
			"",
			" ",
			"  ",
			"\\t",
			"\\n",
			"\\u0000",
			"\\u0001",
			"\\u001f",
			" 1",
			"1 ",
			" 1 ",
			"\\t1\\n",
			"\\n1",
			"1\\t",
			"\\u00001",
			"1\\u0000",
			"\\u00a01",
			"1\\u00a0",
			"\\u20001",
			"1\\u2003",
			"\\u001f1\\u001f",
			"1 2",
			"1\\t2",
			"- 1",
			"-\\t1",
			"+ 1",
			"1 e5",
			"1e 5",
			"1e5 5",
			"- ",
			"+ ",
			"-",
			"+",
			"--1",
			"+-1",
			"-+1",
			"++1",
			"1-",
			"1+",
			"NaN",
			"-NaN",
			"+NaN",
			" NaN ",
			"NaN ",
			"nan",
			"NAN",
			"NaNx",
			"NaN1",
			"NaN.0",
			"Na",
			"N",
			"NN",
			"Infinity",
			"-Infinity",
			"+Infinity",
			" Infinity ",
			"infinity",
			"INFINITY",
			"Infinit",
			"Infinityx",
			"Infinity1",
			"I",
			"Inf",
			"-NaN ",
			"- NaN",
			"NaN-",
			"0NaN",
			"1NaN",
			"-I",
			"-N"
		})
			all.add(unescape(s));

		// refused decimals
		for (String s : new String[] {
			".",
			"-.",
			"+.",
			"..",
			"1..2",
			"1.2.3",
			".1.",
			"1.e",
			"1.e+",
			".e1",
			"e",
			"E",
			"e1",
			"E5",
			"1e",
			"1E",
			"1e+",
			"1e-",
			"1e+-1",
			"1e-+1",
			"1ee1",
			"1e1e1",
			"0e0e0",
			"1e5.5",
			"1e1_0",
			"1_0",
			"1,5",
			"1;",
			"1e1,0",
			"0e",
			"1.5e",
			"1e5x",
			"1x",
			"x1",
			"1ex",
			"1e5.",
			"1e.5",
			"1.5.5e1",
			"e1e1",
			"1e1 e1",
			"1e 1",
			"1 e1",
			"1e+ 1",
			"\\u0661\\u0662\\u0663",
			"\\uff11",
			"1\\u0662",
			"\\u00bd",
			"1\\u2028",
			"\\u20281",
			"1\\ufeff",
			"\\ufeff1",
			"\\u0660",
			"0b1",
			"0o7",
			"07x",
			"1_000",
			"1e1l",
			"1l",
			"1L",
			"1h",
			"1\\u0000e5",
			"1e\\u00005",
			"1.0\\u0000"
		})
			all.add(unescape(s));

		// hexadecimal floating point
		for (String s : new String[] {
			"0x1p3",
			"0x1.8p3",
			"0X1P-1",
			"0x1p0",
			"0x1P+0",
			"0x0p0",
			"-0x0p0",
			"0x0.0p0",
			"0x00p5",
			"0x.8p1",
			"0x8.p-3",
			"0x1.p1",
			"0x.1p4",
			"0xAp0",
			"0xap0",
			"0XAP0",
			"0xA.Bp0",
			"0xFFp0",
			"0xffp0",
			"0xabcdefp0",
			"0xABCDEFP0",
			"0x1p1f",
			"0x1p1d",
			"0x1p1F",
			"0x1p1D",
			"0x1p1ff",
			"0x1p1 ",
			" 0x1p1",
			" 0x1p1 ",
			"-0x1p1",
			"+0x1p1",
			"0x1.fffffffffffffp1023",
			"0x1.fffffffffffff7ffp1023",
			"0x1.fffffffffffff8p1023",
			"0x1.fffffffffffff80001p1023",
			"0x1.fffffffffffffp1024",
			"0x1p1024",
			"0x1p1023",
			"0x0.fffffffffffff8p1024",
			"0x0.fffffffffffff8p1023",
			"0x1p-1022",
			"0x1p-1023",
			"0x0.8p-1022",
			"0x1p-1074",
			"0x1p-1075",
			"0x1.0000000000001p-1075",
			"0x1.8p-1075",
			"0x1.8p-1074",
			"0x1.0p-1075",
			"0x0.0000000000001p-1022",
			"0x0.0000000000001p-1023",
			"0x0.00000000000008p-1022",
			"0x0.00000000000008000001p-1022",
			"0x0.00000000000018p-1022",
			"0x1.fffffep127",
			"0x1.fffffefp127",
			"0x1.ffffffp127",
			"0x1.ffffff0000001p127",
			"0x1.fffffe8p127",
			"0x1p128",
			"0x1p127",
			"0x1p-126",
			"0x1p-149",
			"0x1p-150",
			"0x1.000002p-150",
			"0x1.000001p-149",
			"0x1.8p-149",
			"0x1.800001p-149",
			"0x1.fffffcp-127",
			"0x1.fffffep-127",
			"0x1.ffffffp-127",
			"0x0.fffffep-126",
			"0x1.0000008p0",
			"0x1.00000080000000000000000001p0",
			"0x1.0000018p0",
			"0x1.00000000000008p0",
			"0x1.00000000000008000000001p0",
			"0x1.00000000000018p0",
			"0x1.000000000000080p0",
			"0x123456789abcdef0123456789abcdefp0",
			"0x123456789abcdef0123456789abcdefp-200",
			"0x0.000000000000000000000000000000001p0",
			"0x00000000000000000000000001p0",
			"0x1p99999999999",
			"0x1p-99999999999",
			"0x0p99999999999",
			"0x1p99999999999999999999",
			"0x1p-99999999999999999999",
			"0x1p00000000000000000000003",
			"0x1p9999999999",
			"0x1p10000000000",
			"0x1p-10000000000",
			"0x1p+9999999999"
		})
			all.add(s);

		// refused hexadecimal
		for (String s : new String[] {
			"0x",
			"0X",
			"0x ",
			"0x1",
			"0x1.8",
			"0x.",
			"0x.p1",
			"0x..p1",
			"0x1p",
			"0x1p+",
			"0x1p-",
			"0x1pp1",
			"0x1p1p1",
			"0x1p1.5",
			"0x1.8.8p1",
			"0x1.8p",
			"0xp1",
			"0xgp1",
			"0x1gp1",
			"0x1p1g",
			"0x-1p1",
			"-0x-1p1",
			"0 x1p1",
			"0x 1p1",
			"0x1 p1",
			"0x1p 1",
			"0x1p1 1",
			"00x1p1",
			"0xx1p1",
			"0x0x1p1",
			"0x1e1",
			"0x1e1p1e",
			"0x1e1p0",
			"0x1.p",
			"0x.8",
			"0x1.8f",
			"0x1.8d",
			"0x1.8fd",
			"0x1P",
			"0X1",
			"0x1p1fd",
			"x1p1",
			"0x1p\\u0661",
			"0x\\u0661p1",
			"0xAp",
			"0xhp1",
			"0x1pe1",
			"0x1p1e1",
			"0xp",
			"-0x",
			"+0x",
			"-0x.p1"
		})
			all.add(unescape(s));

		// refusals that echo a long string
		all.add("1".repeat(1000) + "x");
		all.add("1".repeat(1001) + "x");
		all.add("1".repeat(1100) + "x");
		all.add("x" + "1".repeat(1100));
		all.add(" " + "1".repeat(1100) + "x ");
		all.add("1.5".repeat(400));
		all.add("0x" + "f".repeat(600) + "q");
		all.add("1".repeat(600) + "." + "1".repeat(600));
		all.add("\n".repeat(1200) + "x");

		// long decimals that need every digit
		all.add("1" + "0".repeat(50) + "1");
		all.add("0." + "0".repeat(50) + "1");
		all.add("1." + "0".repeat(200) + "1e0");
		all.add("1.00000000000000011102230246251565404236316680908203125");
		all.add("1.00000000000000011102230246251565404236316680908203126");
		all.add("1.00000000000000011102230246251565404236316680908203124");
		all.add("1.000000059604644775390625");
		all.add("1.000000059604644775390626");
		all.add("1.000000059604644775390624");
		all.add("1.00000005960464477539062500000000000000000000000000000000000000001");
		all.add("1.000000059604644775390625" + "0".repeat(120) + "1");
		all.add("1.000000059604644775390625" + "0".repeat(120));
		all.add("1.000000059604644775390624" + "9".repeat(120));
		all.add("3.4028235677973366765e38");
		all.add("3.4028235677973362e38");
		all.add("3.40282356779733661637539395458142568447e38");
		all.add("3.40282356779733661637539395458142568448e38");
		all.add("3.40282356779733661637539395458142568446e38");
		all.add(
			"1.7976931348623158079372897140530341507993413271003782693617377898044496829276475094664901797758720709633028641669288791094655554785194040263065748867150582068190890200070838367627385484581771153176447573027006985557136695962284291481986083493647529271907416844436551070434271155969950934438677132800e308"
		);
		all.add(
			"1.797693134862315807937289714053034150799341327100378269361737789804449682927647509466490179775872070963302864166928879109465555478519404026306574886715058206819089020007083836762738548458177115317644757302700698555713669596228429148198608349364752927190741684443655107043427115596995093443867713280000000001e308"
		);
		all.add("2.4703282292062327208e-324");
		all.add("2.4703282292062327208" + "0".repeat(300) + "1e-324");
		all.add("2.4703282292062327208" + "0".repeat(300) + "e-324");

		// mutations of valid text, which find what no one thought to write
		String[] seeds = {
			"1.5e10",
			"-0.25",
			"123.456e-7",
			"0x1.8p3",
			"0X.Ap-2",
			"Infinity",
			"NaN",
			"1e+5f",
			"00.100d",
			"0x1p1",
			".5",
			"5.",
			"1E5",
			" 2 ",
			"9.99e999"
		};
		String alphabet = "0123456789.+-eEpPxXfFdDnNaAiIyY \t_,";
		long state = 0x9E3779B97F4A7C15L;
		for (int at = 0; at < 1500; at++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			StringBuilder text = new StringBuilder(seeds[(int) ((state >>> 8) % seeds.length)]);
			int edits = 1 + (int) ((state >>> 24) % 3);
			for (int edit = 0; edit < edits; edit++) {
				state ^= state << 13;
				state ^= state >>> 7;
				state ^= state << 17;
				int kind = (int) ((state >>> 5) % 3);
				int where = (int) ((state >>> 20) % (text.length() + 1));
				char c = alphabet.charAt((int) ((state >>> 40) % alphabet.length()));
				if (kind == 0 || text.length() == 0) text.insert(where, c);
				else if (where == text.length()) text.deleteCharAt(where - 1);
				else if (kind == 1) text.deleteCharAt(where);
				else text.setCharAt(where, c);
			}
			all.add(text.toString());
		}
		return new ArrayList<>(all);
	}

	public static void main(String[] args) throws IOException {
		StringBuilder out = new StringBuilder();
		for (String text : inputs()) {
			out.append(escape(text))
				.append('\t')
				.append(ofDouble(text))
				.append('\t')
				.append(ofFloat(text))
				.append('\n');
		}
		if (args.length == 0) System.out.print(out);
		else {
			Path path = Path.of(args[0]);
			Files.createDirectories(path.getParent());
			Files.writeString(path, out, StandardCharsets.UTF_8);
		}
	}
}
