package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("the generated Worker project")
class ScaffoldTest {

	@Test
	@DisplayName("derives its handlers from the interfaces the handler implements")
	void derivesHandlers(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "derived"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWorkerScaffold").getOutcome());
		String index = read(root, "build/bytebox/worker/src/index.ts");
		assertTrue(index.contains("async fetch("), index);
		assertFalse(index.contains("async scheduled("), "an unimplemented trigger is not exported");
	}

	@Test
	@DisplayName("exports every trigger the handler implements")
	void exportsEveryTrigger(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					compatibilityDate = "2026-08-22"
					crons("*/5 * * * *")
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.twoTriggerHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		String index = read(root, "build/bytebox/worker/src/index.ts");
		assertTrue(index.contains("async fetch("), index);
		assertTrue(index.contains("async scheduled("), index);
		String wrangler = read(root, "build/bytebox/worker/wrangler.jsonc");
		assertTrue(wrangler.contains("\"crons\": [\"*/5 * * * *\"]"), wrangler);
	}

	@Test
	@DisplayName("disables Node compatibility, which a modern date turns on by default")
	void disablesNodeCompat(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		String wrangler = read(root, "build/bytebox/worker/wrangler.jsonc");
		assertTrue(wrangler.contains("no_nodejs_compat"), wrangler);
		assertTrue(wrangler.contains("no_nodejs_compat_v2"), wrangler);
	}

	@Test
	@DisplayName("leaves Node compatibility alone for a date before it was default-on")
	void leavesOlderDatesAlone(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-01-01" }
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		assertFalse(read(root, "build/bytebox/worker/wrangler.jsonc").contains("no_nodejs_compat"));
	}

	@Test
	@DisplayName("carries the module as a Data module rather than one the platform compiles")
	void carriesTheModuleAsData(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		String wrangler = read(root, "build/bytebox/worker/wrangler.jsonc");
		// a CompiledWasm module is compiled by the platform, which offers no way to pass the JS
		// String Builtins option the compiler's output needs
		assertTrue(wrangler.contains("\"type\": \"Data\""), wrangler);
		assertTrue(wrangler.contains("**/*.wasmbin"), wrangler);
	}

	@Test
	@DisplayName("refuses a compatibility date that is not a real date")
	void refusesABadDate(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-02-30" }
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").buildAndFail();

		assertTrue(result.getOutput().contains("not a real calendar date"), result.getOutput());
	}

	@Test
	@DisplayName("refuses a date that is not in yyyy-mm-dd form")
	void refusesABadFormat(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "August 2026" }
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").buildAndFail();

		assertTrue(result.getOutput().contains("yyyy-mm-dd"), result.getOutput());
	}

	@Test
	@DisplayName("names a handler that implements no trigger at all")
	void refusesAHandlerWithNoTriggers(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			Map.of("fixture/Handler.java", "package fixture;\n\npublic class Handler {}\n")
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").buildAndFail();

		assertTrue(
			result.getOutput().contains("implements none of the bytebox trigger interfaces"),
			result.getOutput()
		);
	}

	private static String read(Path root, String path) throws IOException {
		return Files.readString(root.resolve(path));
	}
}
