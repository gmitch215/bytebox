package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("packing and measuring the module")
class SizeTaskTest {

	@TempDir
	Path directory;

	@Test
	@DisplayName("puts the module where the Worker imports it, under a name no bundler claims")
	void packs() throws IOException {
		byte[] wasm = module(2048);
		PackWasmTask task = pack(wasm);
		task.pack();

		assertArrayEquals(wasm, Files.readAllBytes(directory.resolve("worker/src/app.wasmbin")));
		assertEquals(
			"// runtime",
			Files.readString(directory.resolve("worker/src/app.wasm-runtime.js"))
		);
	}

	@Test
	@DisplayName("carries a small module raw and a large one as a frame")
	void resolvesTheModuleType() throws IOException {
		PackWasmTask task = pack(module(1024));

		assertEquals(SizeSpec.ModuleType.DATA, task.resolve(1024));
		assertEquals(
			SizeSpec.ModuleType.DATA_COMPRESSED,
			task.resolve(Compression.COMPRESSION_CROSSOVER)
		);
	}

	@Test
	@DisplayName("keeps a declared module type rather than deciding on size")
	void honoursADeclaredType() throws IOException {
		PackWasmTask task = pack(module(1024));
		task.getModuleType().set(SizeSpec.ModuleType.DATA_COMPRESSED);

		assertEquals(SizeSpec.ModuleType.DATA_COMPRESSED, task.resolve(1024));
	}

	@Test
	@DisplayName("fails past the budget, and names the task that finds the growth")
	void enforcesTheBudget() throws IOException {
		PackWasmTask task = pack(module(64 * 1024));
		task.getBudget().set(16L);

		GradleException failure = assertThrows(GradleException.class, task::pack);

		assertTrue(failure.getMessage().contains("past the budget of 16"), failure.getMessage());
		assertTrue(failure.getMessage().contains("sizeReport"), failure.getMessage());
	}

	@Test
	@DisplayName("passes a budget it fits inside")
	void insideTheBudget() throws IOException {
		PackWasmTask task = pack(module(1024));
		task.getBudget().set(1024L * 1024);

		task.pack();
	}

	@Test
	@DisplayName("reports every compression axis, and the meter Cloudflare enforces")
	void reports() throws IOException {
		report(module(4096)).report();
		String out = String.join("\n", SizeReportTask.rows("app.wasm", module(4096), 1024L * 1024));

		assertTrue(out.contains("app.wasm"), out);
		assertTrue(out.contains("raw              4096"), out);
		assertTrue(out.contains("the meter Cloudflare enforces"), out);
		assertTrue(out.contains("gzip -9"), out);
		assertTrue(out.contains("to spare"), out);
		assertTrue(out.contains("free plan        3145728"), out);
		assertTrue(out.contains("paid plan        10485760"), out);
	}

	@Test
	@DisplayName("says how far over a budget the module is, and leaves the row out with none")
	void reportsAgainstABudget() {
		assertTrue(row(SizeReportTask.rows("a", module(4096), 8L), "budget").contains("over"));
		assertTrue(
			SizeReportTask.rows("a", module(4096), -1L)
				.stream()
				.noneMatch(row -> row.contains("budget"))
		);
	}

	@Test
	@DisplayName("says a small module is carried raw and a large one as a frame")
	void reportsTheCrossover() {
		assertTrue(
			row(SizeReportTask.rows("a", module(1024), -1L), "carried as").contains("raw bytes")
		);
		assertTrue(
			row(
				SizeReportTask.rows("a", module((int) Compression.COMPRESSION_CROSSOVER), -1L),
				"carried as"
			).contains("saves more than its decoder costs")
		);
	}

	@Test
	@DisplayName("compresses the way the meter does, and a higher level compresses harder")
	void compresses() throws IOException {
		byte[] raw = module(64 * 1024);

		byte[] six = Compression.gzip(raw);
		byte[] nine = Compression.gzip(raw, 9);

		assertArrayEquals(raw, ungzip(six));
		assertArrayEquals(raw, ungzip(nine));
		assertTrue(nine.length <= six.length, nine.length + " against " + six.length);
	}

	/** Compressible rather than random, so the levels differ and the figures mean something. */
	private static byte[] module(int size) {
		byte[] bytes = new byte[size];
		for (int i = 0; i < size; i++) bytes[i] = (byte) (i % 61);
		return bytes;
	}

	private static String row(List<String> rows, String label) {
		return rows
			.stream()
			.filter(row -> row.contains(label))
			.findFirst()
			.orElseThrow();
	}

	private static byte[] ungzip(byte[] compressed) throws IOException {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
			return in.readAllBytes();
		}
	}

	private PackWasmTask pack(byte[] wasm) throws IOException {
		PackWasmTask task = Projects.task(
			Projects.project(directory.resolve("project")),
			PackWasmTask.class
		);
		task.getWasm().set(write("app.wasm", wasm));
		task.getRuntime().set(
			write("app.wasm-runtime.js", "// runtime".getBytes(StandardCharsets.UTF_8))
		);
		task.getModuleType().set(SizeSpec.ModuleType.AUTO);
		task.getBudget().set(-1L);
		task.getOutputDirectory().set(directory.resolve("worker").toFile());
		return task;
	}

	private SizeReportTask report(byte[] wasm) throws IOException {
		SizeReportTask task = Projects.task(
			Projects.project(directory.resolve("project")),
			SizeReportTask.class
		);
		task.getWasm().set(write("app.wasm", wasm));
		return task;
	}

	private File write(String name, byte[] bytes) throws IOException {
		Path file = directory.resolve(name);
		Files.write(file, bytes);
		return file.toFile();
	}
}
