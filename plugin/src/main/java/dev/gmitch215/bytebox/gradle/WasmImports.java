package dev.gmitch215.bytebox.gradle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which JavaScript modules a compiled program imports, read from the section the compiler writes.
 *
 * <p>A specifier reached only from inside the WebAssembly lives in a custom section, so no bundler
 * can follow it and the loader cannot invent it. The generated entry point has to import it
 * statically and hand it to {@code load}, and this is where the generator finds out which.
 */
final class WasmImports {

	/** The custom section the compiler writes its import table into. */
	private static final String SECTION = "teavm.imports";

	/** Specifiers the runtime supplies, which a Worker imports by name and cannot declare as a package. */
	private static final String PLATFORM = "cloudflare:";

	private static final Pattern MODULE = Pattern.compile(
		"\"module\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""
	);

	private WasmImports() {}

	/**
	 * The platform modules a program imports.
	 *
	 * <p>Only these are derived from the module. An npm package a project forgot to declare stays the
	 * project's own error, because declaring it also writes it into the manifest, and the loader names
	 * the one it was not given. A {@code cloudflare:} specifier has nowhere to be declared.
	 *
	 * @param wasm the compiled module
	 * @return the specifiers, in the order the section lists them, without duplicates
	 */
	static List<String> platformModules(byte[] wasm) {
		List<String> found = new ArrayList<>();
		for (String specifier : modules(wasm)) {
			if (specifier.startsWith(PLATFORM) && !found.contains(specifier)) found.add(specifier);
		}
		return found;
	}

	/**
	 * Every specifier the import table names.
	 *
	 * @param wasm the compiled module
	 * @return the specifiers, including the compiler's own internal ones
	 */
	static List<String> modules(byte[] wasm) {
		String table = section(wasm);
		if (table == null) return List.of();
		List<String> modules = new ArrayList<>();
		Matcher found = MODULE.matcher(table);
		while (found.find()) modules.add(unescape(found.group(1)));
		return modules;
	}

	/** {@return the import table as text, or null when the module carries no such section} */
	private static String section(byte[] wasm) {
		// 8 is past the magic number and the version, which every module begins with
		int cursor = 8;
		if (wasm.length < cursor) return null;
		while (cursor < wasm.length) {
			int[] at = { cursor };
			int id = wasm[at[0]++] & 0xFF;
			int size = leb(wasm, at);
			int start = at[0];
			int end = start + size;
			if (end > wasm.length || end < start) return null;
			if (id == 0) {
				int length = leb(wasm, at);
				if (at[0] + length <= end) {
					String name = new String(wasm, at[0], length, StandardCharsets.UTF_8);
					if (SECTION.equals(name)) {
						int from = at[0] + length;
						return new String(wasm, from, end - from, StandardCharsets.UTF_8);
					}
				}
			}
			cursor = end;
		}
		return null;
	}

	/** Reads an unsigned LEB128 and advances the cursor past it. */
	private static int leb(byte[] wasm, int[] at) {
		int result = 0;
		int shift = 0;
		while (at[0] < wasm.length) {
			int b = wasm[at[0]++] & 0xFF;
			result |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) break;
			shift += 7;
		}
		return result;
	}

	private static String unescape(String text) {
		if (text.indexOf('\\') < 0) return text;
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\\' && i + 1 < text.length()) c = text.charAt(++i);
			out.append(c);
		}
		return out.toString();
	}
}
