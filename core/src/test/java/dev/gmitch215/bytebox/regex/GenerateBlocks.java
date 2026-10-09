package dev.gmitch215.bytebox.regex;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the table in {@code Blocks.java} from the running JDK's {@code Character.UnicodeBlock}.
 *
 * <p>The private name map is read by reflection, because it holds every spelling {@code forName}
 * accepts and the public API lists none of them, so the JDK must open {@code java.lang}. To regenerate
 * after a JDK adds a block, from the repository root:
 *
 * <pre>
 * java --add-opens java.base/java.lang=ALL-UNNAMED \
 *     core/src/test/java/dev/gmitch215/bytebox/regex/GenerateBlocks.java \
 *     core/src/main/java/dev/gmitch215/bytebox/regex/Blocks.java /tmp/Blocks.java
 * </pre>
 *
 * <p>then move the output over {@code Blocks.java}. With no second argument the result goes to
 * standard output, which is how {@code PatternTest} compares it with the committed file.
 */
public final class GenerateBlocks {

	private static final String START = "\tstatic final String[] TABLE = {\n";

	private static final String END = "\n\t};\n";

	private GenerateBlocks() {}

	/**
	 * @param source the current text of {@code Blocks.java}
	 * @return that text with its table replaced by the one this JDK has
	 */
	static String generate(String source) throws ReflectiveOperationException {
		int from = source.indexOf(START);
		int to = source.indexOf(END, from);
		if (from < 0 || to < 0) throw new IllegalArgumentException("no table to replace");

		Field names = Character.UnicodeBlock.class.getDeclaredField("map");
		names.setAccessible(true);
		Map<?, ?> spellings = (Map<?, ?>) names.get(null);

		Map<Character.UnicodeBlock, int[]> ranges = new LinkedHashMap<>();
		for (int unit = 0; unit <= 0xFFFF; unit++) {
			Character.UnicodeBlock block = Character.UnicodeBlock.of(unit);
			if (block == null) continue;
			int[] range = ranges.get(block);
			if (range == null) ranges.put(block, new int[] { unit, unit });
			else if (range[1] == unit - 1) range[1] = unit;
			else throw new IllegalStateException(block + " is not one run, it breaks at " + unit);
		}

		List<String> lines = new ArrayList<>();
		for (Map.Entry<Character.UnicodeBlock, int[]> entry : ranges.entrySet()) {
			Character.UnicodeBlock block = entry.getKey();
			int[] range = entry.getValue();
			// the surrogate blocks are refused, since a half of a pair cannot be told from the whole
			if (range[0] >= 0xD800 && range[1] <= 0xDFFF) continue;
			List<String> others = new ArrayList<>();
			for (Map.Entry<?, ?> spelling : spellings.entrySet()) {
				String name = (String) spelling.getKey();
				if (spelling.getValue() == block && !name.equals(block.toString())) others.add(
					name
				);
			}
			Collections.sort(others);
			others.add(0, block.toString());
			lines.add(
				String.format("\t\t\"%04X %04X %s\"", range[0], range[1], String.join("|", others))
			);
		}
		return (
			source.substring(0, from + START.length()) +
			String.join(",\n", lines) +
			source.substring(to)
		);
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1 || args.length > 2) {
			System.err.println("usage: GenerateBlocks <Blocks.java> [output]");
			System.exit(2);
		}
		String written = generate(Files.readString(Path.of(args[0])));
		if (args.length == 2) {
			Files.writeString(Path.of(args[1]), written);
		} else {
			System.out.write(written.getBytes(StandardCharsets.UTF_8));
			System.out.flush();
		}
	}
}
