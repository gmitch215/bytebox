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

/** Runs the pipeline over a project written in Kotlin, with the Kotlin plugin applied. */
@DisplayName("a Kotlin Worker")
class KotlinTest {

	@Test
	@DisplayName("compiles an object handler into a module, with the Kotlin standard library in it")
	void buildsAnObjectHandler(@TempDir Path root) throws IOException {
		project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			Map.of(
				"fixture/Handler.kt",
				"""
				package fixture

				import dev.gmitch215.bytebox.Bytebox
				import dev.gmitch215.bytebox.Env
				import dev.gmitch215.bytebox.ExecutionCtx
				import dev.gmitch215.bytebox.Request
				import dev.gmitch215.bytebox.Response
				import dev.gmitch215.bytebox.Worker

				data class Greeting(val name: String, val count: Int)

				object Handler : Worker {
					override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response {
						val greetings = listOf("a", "bb").map { Greeting(it, it.length) }
						return Bytebox.response("hello ${greetings.joinToString { "${it.name}=${it.count}" }}")
					}
				}
				"""
			)
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin").getOutcome());
		assertEquals(TaskOutcome.SUCCESS, result.task(":packWasm").getOutcome());
		String entry = Files.readString(
			root.resolve(
				"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated/ByteboxEntry.java"
			)
		);
		assertTrue(entry.contains("HANDLER = fixture.Handler.INSTANCE;"), entry);
		byte[] wasm = Files.readAllBytes(root.resolve("build/bytebox/worker/src/app.wasmbin"));
		assertTrue(wasm.length > 10_000, "the module measured " + wasm.length + " bytes");
		assertEquals(0x00, wasm[0]);
		assertEquals('a', wasm[1]);
		assertEquals('s', wasm[2]);
		assertEquals('m', wasm[3]);
	}

	@Test
	@DisplayName("finds a Kotlin Durable Object and a Kotlin JSON type in the compiled classes")
	void readsKotlinClasses(@TempDir Path root) throws IOException {
		project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				durableObjects("fixture.Counter")
				wrangler { compatibilityDate = "2026-08-22" }
			}
			""",
			Map.of(
				"fixture/Handler.kt",
				"""
				package fixture

				import dev.gmitch215.bytebox.Bytebox
				import dev.gmitch215.bytebox.Env
				import dev.gmitch215.bytebox.ExecutionCtx
				import dev.gmitch215.bytebox.Request
				import dev.gmitch215.bytebox.Response
				import dev.gmitch215.bytebox.Worker
				import dev.gmitch215.bytebox.durable.DurableObject
				import dev.gmitch215.bytebox.durable.DurableState
				import dev.gmitch215.bytebox.json.JSONType

				@JSONType
				class Config {
					@JvmField var host: String = ""
					@JvmField var port: Int = 0
				}

				class Handler : Worker {
					override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response =
						Bytebox.response("ok")
				}

				class Counter : DurableObject {
					override fun fetch(request: Request, state: DurableState, env: Env): Response =
						Bytebox.response("counted")
				}
				"""
			)
		);

		BuildResult result = Fixtures.runner(root, "byteboxClasses").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":compileByteboxJava").getOutcome());
		Path generated = root.resolve(
			"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated"
		);
		String entry = Files.readString(generated.resolve("ByteboxEntry.java"));
		assertTrue(entry.contains("HANDLER = new fixture.Handler();"), entry);
		assertTrue(entry.contains("durableCounterFetch("), entry);
		assertTrue(entry.contains("fixture.Counter::new"), entry);
		assertTrue(Files.exists(generated.resolve("fixture_ConfigCodec.java")), "the Config codec");
	}

	/** Writes a Fixtures project and applies the Kotlin plugin next to the bytebox one. */
	private static void project(Path root, String buildScript, Map<String, String> sources)
		throws IOException {
		Fixtures.project(root, buildScript, Map.of());
		Path build = root.resolve("build.gradle.kts");
		Files.writeString(
			build,
			Files.readString(build).replace(
				"id(\"dev.gmitch215.bytebox\")",
				"id(\"dev.gmitch215.bytebox\")\n\tkotlin(\"jvm\") version \"2.4.20\""
			)
		);
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			Path file = root.resolve("src/main/kotlin").resolve(entry.getKey());
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry.getValue());
		}
	}
}
