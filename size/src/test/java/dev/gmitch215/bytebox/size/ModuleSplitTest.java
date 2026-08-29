package dev.gmitch215.bytebox.size;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleSplitTest {

	private static byte[] wasm;

	@BeforeAll
	static void readFixture() throws IOException {
		try (InputStream in = ModuleSplitTest.class.getResourceAsStream("/hello.wasm")) {
			wasm = in.readAllBytes();
		}
	}

	@Test
	void attributesEveryFunctionInTheCodeSection() {
		ModuleSplit split = ModuleSplit.of(wasm, "probe.");

		assertTrue(split.total() > 0, "the code section should not be empty");
		assertEquals(
			split.total(),
			split.bytes().values().stream().mapToLong(Long::longValue).sum(),
			"the buckets should account for the whole code section"
		);
	}

	@Test
	void chargesAHelloWorldMostlyToTheRuntime() {
		ModuleSplit split = ModuleSplit.of(wasm, "probe.");
		Map<ModuleSplit.Bucket, Long> bytes = split.bytes();

		long runtime = bytes.getOrDefault(ModuleSplit.Bucket.RUNTIME, 0L);
		long user = bytes.getOrDefault(ModuleSplit.Bucket.USER_CODE, 0L);

		// the runtime is close to a fixed cost, so it dominates until a program does real work
		assertTrue(runtime > user, "the runtime should outweigh a hello world's own code");
		assertTrue(user > 0, "the program's own code should be attributed to it");
	}

	@Test
	void namesTheLargestFunctions() {
		ModuleSplit split = ModuleSplit.of(wasm, "probe.");

		assertFalse(split.largest().isEmpty(), "the name section should yield function names");
		split.largest().forEach((name, size) -> assertTrue(size > 0, name + " should have a size"));
	}

	@Test
	void rendersATable() {
		String table = ModuleSplit.of(wasm, "probe.").toString();

		assertTrue(table.contains("bucket"));
		assertTrue(table.contains("total"));
	}

	@Test
	void countsTheFunctionsInEachBucket() {
		ModuleSplit split = ModuleSplit.of(wasm, "probe.");

		assertEquals(
			split.functions().values().stream().mapToInt(Integer::intValue).sum(),
			split.largest().size() + countUnnamed(split),
			"every function should be counted once"
		);
		assertTrue(split.functions().get(ModuleSplit.Bucket.RUNTIME) > 0);
	}

	@Test
	void readsAModuleFromDisk(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("hello.wasm");
		Files.write(file, wasm);

		assertEquals(
			ModuleSplit.of(wasm, "probe.").total(),
			ModuleSplit.of(file, "probe.").total()
		);
	}

	/**
	 * The import section is walked to find where the module's own functions start, so every kind of
	 * import has to be skipped by exactly its own length. A module that imports a memory is the
	 * ordinary case rather than an exotic one.
	 */
	@Test
	void skipsEveryKindOfImport() {
		ModuleSplit split = ModuleSplit.of(imports(), "probe.");

		assertEquals(0, split.total(), "the fixture has no code section to attribute");
	}

	@Test
	void refusesAnImportKindThatDoesNotExist() {
		byte[] module = module(section(2, new byte[] { 1, 1, 'm', 1, 'x', 7 }));

		assertThrows(IllegalStateException.class, () -> ModuleSplit.of(module, "probe."));
	}

	private static int countUnnamed(ModuleSplit split) {
		int named = split.largest().size();
		int total = split.functions().values().stream().mapToInt(Integer::intValue).sum();
		return total - named;
	}

	/** One import of each kind, including a garbage-collected reference type and an empty code section. */
	private static byte[] imports() {
		byte[] entries = {
			6,
			// a function, whose type index is all that follows the kind
			1,
			'm',
			1,
			'f',
			0,
			0,
			// a table of funcref, with a minimum and no maximum
			1,
			'm',
			1,
			't',
			1,
			0x70,
			0,
			1,
			// a memory, with both a minimum and a maximum
			1,
			'm',
			1,
			'm',
			2,
			1,
			1,
			2,
			// an immutable i32 global
			1,
			'm',
			1,
			'g',
			3,
			0x7F,
			0,
			// a tag, which carries an attribute byte before its type index
			1,
			'm',
			1,
			'e',
			4,
			0,
			0,
			// a table of a reference type, whose heap index follows it as a signed LEB128
			1,
			'm',
			1,
			'r',
			1,
			0x64,
			(byte) 0x81,
			0,
			0,
			1
		};
		byte[] code = { 0 };
		return module(concat(section(2, entries), section(10, code)));
	}

	private static byte[] section(int id, byte[] payload) {
		byte[] out = new byte[payload.length + 2];
		out[0] = (byte) id;
		out[1] = (byte) payload.length;
		System.arraycopy(payload, 0, out, 2, payload.length);
		return out;
	}

	private static byte[] module(byte[] sections) {
		byte[] header = { 0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00 };
		return concat(header, sections);
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] out = new byte[first.length + second.length];
		System.arraycopy(first, 0, out, 0, first.length);
		System.arraycopy(second, 0, out, first.length, second.length);
		return out;
	}
}
