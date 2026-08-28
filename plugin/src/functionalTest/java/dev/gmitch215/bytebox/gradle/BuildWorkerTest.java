package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * Runs the whole pipeline, compiler included.
 *
 * <p>Slower than the rest, and the only thing that proves the generated entry point compiles against
 * the handler it was derived from. Everything before it checks what was written; this checks that a
 * compiler accepts it.
 */
@DisplayName("buildWorker")
class BuildWorkerTest {

	@Test
	@DisplayName("compiles a handler into a Worker with the module beside it")
	void buildsAWorker(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "end-to-end"
					compatibilityDate = "2026-08-22"
				}
				bindings {
					kv()
					d1()
				}
			}

			teavm {
				wasmGC {
					modularRuntime = true
					minDirectBuffersSize = 0
					obfuscated = true
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":packWasm").getOutcome());
		Path worker = root.resolve("build/bytebox/worker");
		assertTrue(Files.exists(worker.resolve("src/app.wasmbin")), "the module");
		assertTrue(Files.exists(worker.resolve("src/app.wasm-runtime.js")), "the runtime");
		assertTrue(Files.exists(worker.resolve("src/index.ts")), "the entry point");
		assertTrue(Files.exists(worker.resolve("wrangler.jsonc")), "the configuration");
		assertTrue(Files.exists(worker.resolve("package.json")), "the manifest");

		// the module is real WebAssembly rather than a placeholder
		byte[] wasm = Files.readAllBytes(worker.resolve("src/app.wasmbin"));
		assertTrue(wasm.length > 10_000, "the module measured " + wasm.length + " bytes");
		assertEquals(0x00, wasm[0]);
		assertEquals('a', wasm[1]);
		assertEquals('s', wasm[2]);
		assertEquals('m', wasm[3]);
	}

	@Test
	@DisplayName("writes the bindings it was given, with their default names")
	void writesTheBindings(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				bindings {
					kv()
					kv()
					d1("ANALYTICS_DB")
					r2()
					ai()
					durableObject("Counter")
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		String wrangler = Files.readString(root.resolve("build/bytebox/worker/wrangler.jsonc"));
		assertTrue(wrangler.contains("\"binding\": \"KV\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"KV_2\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"ANALYTICS_DB\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"BLOB\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"AI\""), wrangler);
		assertTrue(wrangler.contains("\"name\": \"DO_COUNTER\""), wrangler);
	}

	@Test
	@DisplayName("takes the short form of the bindings block")
	void takesTheShortForm(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			import dev.gmitch215.bytebox.gradle.BindingType

			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				bindings(BindingType.KV, BindingType.D1, BindingType.D1, BindingType.KV)
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		Fixtures.runner(root, "generateWorkerScaffold").build();

		String wrangler = Files.readString(root.resolve("build/bytebox/worker/wrangler.jsonc"));
		assertTrue(wrangler.contains("\"binding\": \"KV\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"KV_2\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"DB\""), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"DB_2\""), wrangler);
	}

	@Test
	@DisplayName("refuses two bindings with the same name")
	void refusesADuplicateName(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				bindings {
					kv("CACHE")
					r2("CACHE")
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").buildAndFail();

		assertTrue(result.getOutput().contains("both named CACHE"), result.getOutput());
	}

	@Test
	@DisplayName("refuses a second binding of a type there can only be one of")
	void refusesASecondSingleton(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				bindings {
					ai()
					ai()
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "generateWorkerScaffold").buildAndFail();

		assertTrue(result.getOutput().contains("only declare one AI"), result.getOutput());
	}

	@Test
	@DisplayName("fails the build when the Worker grows past its budget")
	void failsPastTheBudget(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				size { budget = "1KiB" }
			}

			teavm { wasmGC { modularRuntime = true; obfuscated = true } }
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "packWasm").buildAndFail();

		assertTrue(result.getOutput().contains("past the budget of 1024"), result.getOutput());
	}

	@Test
	@DisplayName("reports every binding and where it points")
	void reportsTheBindings(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
				bindings {
					kv("SESSIONS") { id = "abc123" }
					d1()
				}
			}
			""",
			Map.of("fixture/Handler.java", Fixtures.fetchHandler())
		);

		BuildResult result = Fixtures.runner(root, "bindingsReport").build();

		assertTrue(result.getOutput().contains("SESSIONS"), result.getOutput());
		assertTrue(result.getOutput().contains("id=abc123"), result.getOutput());
		assertTrue(result.getOutput().contains("provisioned by Wrangler"), result.getOutput());
	}
}
