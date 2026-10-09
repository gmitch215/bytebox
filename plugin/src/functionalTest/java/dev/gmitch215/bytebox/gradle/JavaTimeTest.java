package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A program using {@code java.time} compiled to WebAssembly.
 *
 * <p>The class library has none of it, so this is the only check that says whether the substitution
 * holds: the compiler either resolves every reference or reports the ones it cannot, and nothing in
 * between. The zone path is the interesting half, because that is where the compiled timezone database
 * would be pulled in, and the assertion that it was not is that the module carries no trace of it.
 */
@DisplayName("java.time")
class JavaTimeTest {

	private static final String HANDLER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import java.time.Duration;
	import java.time.Instant;
	import java.time.LocalDate;
	import java.time.ZoneId;
	import java.time.ZonedDateTime;
	import java.time.format.DateTimeFormatter;
	import java.time.temporal.ChronoUnit;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			Instant now = Instant.now().plus(Duration.ofHours(3));
			LocalDate date = LocalDate.of(2026, 8, 28).plusDays(5);
			ZonedDateTime zoned = now.atZone(ZoneId.of("America/New_York"));
			return Bytebox.response(
				zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) +
				" " +
				ChronoUnit.DAYS.between(date, date.plusWeeks(1)) +
				" " +
				zoned.getZone().getRules().getOffset(now).getTotalSeconds()
			);
		}
	}
	""";

	private static final String HISTORY = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import java.time.ZoneId;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			return Bytebox.response(
				"" + ZoneId.of("Europe/London").getRules().getTransitions().size()
			);
		}
	}
	""";

	// #region the classlib extras

	@Test
	@DisplayName(
		"leaves the timezone database out of a module that calls for the history without asking"
	)
	void historyOffByDefault(@TempDir Path root) throws IOException {
		byte[] module = build(root, "", HISTORY);

		assertTrue(!contains(module, "TZDB"), "the database should not be in the module");
		String entry = Files.readString(entryPoint(root));
		assertTrue(!entry.contains("ZoneHistory"), entry);
	}

	@Test
	@DisplayName(
		"copies the timezone database in when the project asks for the history and calls it"
	)
	void historyOn(@TempDir Path off, @TempDir Path on) throws IOException {
		byte[] without = build(off, "", HISTORY);
		byte[] with = build(on, "classlib { zoneHistory = true }", HISTORY);

		assertTrue(contains(with, "TZDB"), "the database should be in the module");
		assertTrue(
			with.length - without.length > 100_000,
			"the module grew by " + (with.length - without.length) + " bytes"
		);
		assertTrue(Files.readString(entryPoint(on)).contains("ZoneHistory.register();"));
	}

	@Test
	@DisplayName("adds the database to nothing that never calls for the history, even when asked")
	void historyOnButUnused(@TempDir Path root) throws IOException {
		byte[] module = build(root, "classlib { zoneHistory = true }", HANDLER);

		assertTrue(!contains(module, "TZDB"), "the database should not be in the module");
	}

	private static final String FORMAT = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;

	public class Handler implements Worker {
		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			return Bytebox.response(String.format("%tF %<tA", System.currentTimeMillis()));
		}
	}
	""";

	@Test
	@DisplayName(
		"links the date conversions only when the project asks and a program formats a date"
	)
	void dateFormatOnlyWhenAsked(
		@TempDir Path off,
		@TempDir Path on,
		@TempDir Path unused,
		@TempDir Path plainRoot
	) throws IOException {
		byte[] without = build(off, "", FORMAT);
		byte[] with = build(on, "classlib { dateFormat = true }", FORMAT);
		byte[] idle = build(unused, "classlib { dateFormat = true }", HANDLER);

		assertTrue(!Files.readString(entryPoint(off)).contains("Dates"));
		assertTrue(Files.readString(entryPoint(on)).contains("Dates.register();"));
		assertTrue(
			with.length - without.length > 10_000,
			"the module grew by " + (with.length - without.length) + " bytes"
		);
		byte[] plain = build(plainRoot, "", HANDLER);
		assertTrue(
			idle.length - plain.length < 2_000,
			"asking and never formatting a date grew the module by " + (idle.length - plain.length)
		);
	}

	private static byte[] build(Path root, String classlib, String handler) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				%s
				wrangler {
					name = "javatime"
					compatibilityDate = "2026-08-22"
				}
			}
			""".formatted(classlib),
			Map.of("fixture/Handler.java", handler)
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();
		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		return Files.readAllBytes(root.resolve("build/bytebox/worker/src/app.wasmbin"));
	}

	private static Path entryPoint(Path root) {
		return root.resolve(
			"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated/ByteboxEntry.java"
		);
	}

	private static boolean contains(byte[] module, String text) {
		return new String(module, java.nio.charset.StandardCharsets.ISO_8859_1).contains(text);
	}

	// #endregion

	@Test
	@DisplayName("compiles, and carries no copy of the timezone database")
	void compilesAProgramUsingDatesAndZones(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", HANDLER);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "javatime"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		Path module = root.resolve("build/bytebox/worker/src/app.wasmbin");
		assertTrue(Files.exists(module), "the module should have been written");

		String bytes = new String(
			Files.readAllBytes(module),
			java.nio.charset.StandardCharsets.ISO_8859_1
		);
		assertTrue(
			!bytes.contains("TZDB") && !bytes.contains("TzdbZoneRules"),
			"the compiled timezone database should not be in the module"
		);
	}
}
