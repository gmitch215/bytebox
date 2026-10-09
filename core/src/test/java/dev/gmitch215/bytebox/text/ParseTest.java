package dev.gmitch215.bytebox.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.instructions.InvokeInstruction;

@DisplayName("the strings Double.parseDouble and Float.parseFloat accept and refuse")
class ParseTest {

	/** Stands for the platform's reader of a decimal, which a JVM test cannot call. */
	private static final ToDoubleFunction<String> PLATFORM = Double::parseDouble;

	private static String ofDouble(String text) {
		try {
			return String.format(
				"D%016x",
				Double.doubleToRawLongBits(Parse.parseDouble(text, PLATFORM))
			);
		} catch (NumberFormatException e) {
			return "!" + GenerateParseTable.escape(e.getMessage());
		}
	}

	private static String ofFloat(String text) {
		try {
			return String.format(
				"F%08x",
				Float.floatToRawIntBits(Parse.parseFloat(text, PLATFORM))
			);
		} catch (NumberFormatException e) {
			return "!" + GenerateParseTable.escape(e.getMessage());
		}
	}

	private static long state = 0x9E3779B97F4A7C15L;

	private static long next() {
		state ^= state << 13;
		state ^= state >>> 7;
		state ^= state << 17;
		return state;
	}

	@Test
	@DisplayName("answers every string in the grammar table as JDK 27 does, value or message")
	void agreesWithTheTable() throws IOException {
		String path = "/dev/gmitch215/bytebox/text/parse-grammar.tsv";
		String table;
		try (InputStream in = ParseTest.class.getResourceAsStream(path)) {
			assertNotNull(in);
			table = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		List<String> wrong = new ArrayList<>();
		int rows = 0;
		for (String line : table.split("\n")) {
			String[] cells = line.split("\t", -1);
			String text = GenerateParseTable.unescape(cells[0]);
			rows++;
			if (!cells[1].equals(ofDouble(text))) {
				wrong.add(
					"double " + cells[0] + " expected " + cells[1] + " got " + ofDouble(text)
				);
			}
			if (!cells[2].equals(ofFloat(text))) {
				wrong.add("float " + cells[0] + " expected " + cells[2] + " got " + ofFloat(text));
			}
		}
		assertTrue(rows > 1500, "the table has " + rows + " rows");
		assertEquals(List.of(), wrong.subList(0, Math.min(wrong.size(), 10)));
	}

	@Test
	@DisplayName("refuses what the class library's lexer let through after an exponent")
	void refusesText() {
		for (String text : new String[] { "1e5.5", "1e1e1", "1e1_0", "0e0e0" }) {
			NumberFormatException failure = assertThrows(NumberFormatException.class, () ->
				Parse.parseDouble(text, PLATFORM)
			);
			assertEquals("For input string: \"" + text + "\"", failure.getMessage());
			assertThrows(NumberFormatException.class, () -> Parse.parseFloat(text, PLATFORM));
		}
		assertEquals(
			"empty String",
			assertThrows(NumberFormatException.class, () ->
				Parse.parseDouble("  ", PLATFORM)
			).getMessage()
		);
		assertEquals(
			"multiple points",
			assertThrows(NumberFormatException.class, () ->
				Parse.parseDouble("1.2.3", PLATFORM)
			).getMessage()
		);
		assertThrows(NullPointerException.class, () -> Parse.parseDouble(null, PLATFORM));
		assertThrows(NullPointerException.class, () -> Parse.parseFloat(null, PLATFORM));
	}

	@Test
	@DisplayName("shortens a long message from the middle, at a thousand characters")
	void shortensTheMessage() {
		String text = "1".repeat(1100) + "x";
		String message = assertThrows(NumberFormatException.class, () ->
			Parse.parseDouble(text, PLATFORM)
		).getMessage();
		assertEquals(
			"For input string: \"" + "1".repeat(497) + " ... " + "1".repeat(497) + "x\"",
			message
		);
	}

	@Test
	@DisplayName(
		"hands the platform the unsigned digits, point and exponent and nothing around them"
	)
	void handsOverTheDecimal() {
		List<String> seen = new ArrayList<>();
		ToDoubleFunction<String> record = text -> {
			seen.add(text);
			return 5.0;
		};

		assertEquals(-5.0, Parse.parseDouble(" \t-1.5e+3d\n", record));
		assertEquals(5.0, Parse.parseDouble("+00.25E-1F", record));
		assertEquals(-5.0f, Parse.parseFloat("-.5e0", record));
		assertEquals(5.0f, Parse.parseFloat("2.", record));
		assertEquals(List.of("1.5e+3", "00.25E-1", ".5e0", "2."), seen);

		// a zero, a symbol or a hexadecimal literal is not the platform's to read
		seen.clear();
		assertEquals(-0.0, Parse.parseDouble("-0e99", record));
		assertEquals(Double.POSITIVE_INFINITY, Parse.parseDouble("Infinity", record));
		assertTrue(Double.isNaN(Parse.parseDouble(" NaN ", record)));
		assertEquals(12.0, Parse.parseDouble("0xCp0", record));
		assertEquals(List.of(), seen);
	}

	@Test
	@DisplayName(
		"reads a hexadecimal literal to the double a JVM reads, at every rounding boundary"
	)
	void readsHexDoubles() {
		for (int at = 0; at < 4000; at++) {
			long bits = next();
			// a quarter of the draws are subnormal, a few land at the top
			if (at % 4 == 0) bits &= 0x800FFFFFFFFFFFFFL;
			if (at % 16 == 1) bits |= 0x7FE0000000000000L;
			double value = Double.longBitsToDouble(bits);
			if (Double.isNaN(value) || Double.isInfinite(value)) continue;
			for (String text : halfwayHex(bits, false)) {
				assertEquals(
					Double.doubleToRawLongBits(Double.parseDouble(text)),
					Double.doubleToRawLongBits(Parse.parseDouble(text, PLATFORM)),
					text
				);
			}
			String own = Double.toHexString(value);
			assertEquals(
				Double.doubleToRawLongBits(Double.parseDouble(own)),
				Double.doubleToRawLongBits(Parse.parseDouble(own, PLATFORM)),
				own
			);
		}
	}

	@Test
	@DisplayName("reads a hexadecimal literal to the float a JVM reads, rounding once")
	void readsHexFloats() {
		for (int at = 0; at < 4000; at++) {
			int bits = (int) next();
			if (at % 4 == 0) bits &= 0x807FFFFF;
			if (at % 16 == 1) bits |= 0x7F000000;
			float value = Float.intBitsToFloat(bits);
			if (Float.isNaN(value) || Float.isInfinite(value)) continue;
			for (String text : halfwayHex(bits & 0xFFFFFFFFL, true)) {
				assertEquals(
					Float.floatToRawIntBits(Float.parseFloat(text)),
					Float.floatToRawIntBits(Parse.parseFloat(text, PLATFORM)),
					text
				);
			}
			String own = Float.toHexString(value);
			assertEquals(
				Float.floatToRawIntBits(Float.parseFloat(own)),
				Float.floatToRawIntBits(Parse.parseFloat(own, PLATFORM)),
				own
			);
		}
	}

	/** Hexadecimal text exactly halfway to the next value, and a hair either side of it. */
	private static List<String> halfwayHex(long bits, boolean single) {
		long fraction;
		long significand;
		int binary;
		if (single) {
			int field = (int) (bits >>> 23) & 0xFF;
			fraction = bits & 0x7FFFFF;
			significand = field == 0 ? fraction : fraction | (1L << 23);
			binary = field == 0 ? -149 : field - 150;
		} else {
			int field = (int) (bits >>> 52) & 0x7FF;
			fraction = bits & 0xFFFFFFFFFFFFFL;
			significand = field == 0 ? fraction : fraction | (1L << 52);
			binary = field == 0 ? -1074 : field - 1075;
		}
		String half = Long.toHexString(2 * significand + 1);
		String sign = bits >>> (single ? 31 : 63) != 0 ? "-" : "";
		List<String> out = new ArrayList<>();
		out.add(sign + "0x" + half + "p" + (binary - 1));
		out.add(sign + "0x" + half + "0000000001p" + (binary - 1 - 40));
		out.add(sign + "0x" + half + "0000000000p" + (binary - 1 - 40));
		out.add(
			sign + "0x" + Long.toHexString(2 * significand) + "ffffffffffp" + (binary - 1 - 40)
		);
		out.add(sign + "0x" + half + "." + "0".repeat(40) + "1p" + (binary - 1));
		out.add(sign + "0x" + half + "." + "0".repeat(300) + "p" + (binary - 1));
		return out;
	}

	@Test
	@DisplayName(
		"rounds a decimal of any length to the float a JVM reads, where a double sits halfway"
	)
	void readsLongFloats() {
		for (int at = 0; at < 3000; at++) {
			int bits = (int) next() & 0x7FFFFFFF;
			if (at % 3 == 0) bits &= 0x007FFFFF;
			float value = Float.intBitsToFloat(bits);
			if (Float.isNaN(value) || Float.isInfinite(value)) continue;
			// the exact decimal of the double halfway between this float and the next
			double midway = ((double) value + (double) Math.nextUp(value)) / 2;
			String exact = new BigDecimal(midway).toPlainString();
			int tail = 1 + ((int) (next() >>> 40) % 150);
			List<String> texts = List.of(
				exact,
				exact + "0".repeat(tail) + "1",
				exact + "0".repeat(tail),
				exact.contains(".") ? exact + "0".repeat(tail) + "e0" : exact + ".0e0",
				"-" + exact,
				exact + "0".repeat(130) + "1"
			);
			for (String text : texts) {
				assertEquals(
					Float.floatToRawIntBits(Float.parseFloat(text)),
					Float.floatToRawIntBits(Parse.parseFloat(text, PLATFORM)),
					text
				);
			}
		}
	}

	@Test
	@DisplayName("takes the decimals a float is made from, whatever their length or exponent")
	void readsFloatsOfAnyShape() {
		for (String text : new String[] {
			"16777217",
			"16777217.0000000000000000000000000000001",
			"3.4028235677973366E38",
			"3.4028235677973366E38000",
			"1e-46",
			"1e-99999999999",
			"1e99999999999",
			"0." + "0".repeat(60) + "7006492321624085354618647916449580656401309",
			"7.0064923216240853546186479164495806564013091e-46",
			"7.0064923216240853546186479164495806564013091" + "0".repeat(400) + "1e-46",
			"1" + "0".repeat(300) + ".5e-300",
			"123456789012345678901234567890123456789e-30"
		}) {
			assertEquals(
				Float.floatToRawIntBits(Float.parseFloat(text)),
				Float.floatToRawIntBits(Parse.parseFloat(text, PLATFORM)),
				text
			);
		}
	}

	@Test
	@DisplayName("points Double.parseDouble and Float.parseFloat at Parse and no other method")
	void pointsTheTwoMethods() {
		ParsePlugin plugin = new ParsePlugin();
		ClassHierarchy hierarchy = new ClassHierarchy(name -> null);
		ClassHolderTransformerContext context =
			(ClassHolderTransformerContext) Proxy.newProxyInstance(
				ParseTest.class.getClassLoader(),
				new Class<?>[] { ClassHolderTransformerContext.class },
				(proxy, method, args) -> hierarchy
			);

		for (Object[] row : new Object[][] {
			{ "java.lang.Double", "parseDouble", double.class },
			{ "java.lang.Float", "parseFloat", float.class }
		}) {
			ClassHolder cls = new ClassHolder((String) row[0]);
			MethodDescriptor parse = new MethodDescriptor(
				(String) row[1],
				String.class,
				(Class<?>) row[2]
			);
			MethodHolder method = new MethodHolder(parse);
			method.getModifiers().add(ElementModifier.STATIC);
			cls.addMethod(method);
			MethodHolder other = new MethodHolder(
				new MethodDescriptor("valueOf", String.class, Object.class)
			);
			cls.addMethod(other);

			plugin.transformClass(cls, context);

			InvokeInstruction call = (InvokeInstruction) method
				.getProgram()
				.basicBlockAt(1)
				.getFirstInstruction();
			assertEquals("dev.gmitch215.bytebox.text.Parse", call.getMethod().getClassName());
			assertEquals(row[1], call.getMethod().getName());
			assertEquals(parse, call.getMethod().getDescriptor());
			assertNull(other.getProgram());
		}

		ClassHolder untouched = new ClassHolder("java.lang.Integer");
		MethodHolder parse = new MethodHolder(
			new MethodDescriptor("parseDouble", String.class, double.class)
		);
		untouched.addMethod(parse);
		plugin.transformClass(untouched, context);
		assertNull(parse.getProgram());
	}
}
