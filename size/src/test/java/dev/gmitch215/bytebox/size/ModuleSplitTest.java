package dev.gmitch215.bytebox.size;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
