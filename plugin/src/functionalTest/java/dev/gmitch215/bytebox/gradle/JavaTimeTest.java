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
