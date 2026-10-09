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

/**
 * A handler that locks, joins and waits before its first request.
 *
 * <p>Whether such a module runs is a runtime question, answered by the {@code startup} program in
 * {@code experiments/lock-delegation}. This checks the part the plugin owns: the generated entry
 * builds the handler on a fiber and the compiler accepts what that makes asynchronous.
 */
@DisplayName("module start")
class ModuleStartTest {

	private static final String HANDLER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.ExecutionCtx;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.Worker;
	import java.util.concurrent.CompletableFuture;
	import java.util.concurrent.locks.ReentrantLock;

	public class Handler implements Worker {
		private static final ReentrantLock LOCK = new ReentrantLock();
		private static final String GREETING;

		static {
			LOCK.lock();
			try {
				GREETING = CompletableFuture.supplyAsync(() -> "hello").join();
			} finally {
				LOCK.unlock();
			}
		}

		private final int answer;

		public Handler() {
			answer = CompletableFuture.supplyAsync(() -> 42).join();
		}

		@Override
		public Response fetch(Request request, Env env, ExecutionCtx ctx) {
			return Bytebox.response(GREETING + " " + answer);
		}
	}
	""";

	private static final String ROOM = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.durable.DurableObject;
	import dev.gmitch215.bytebox.durable.DurableState;
	import java.util.concurrent.CompletableFuture;

	public class Room implements DurableObject {
		private final int answer;

		public Room() {
			answer = CompletableFuture.supplyAsync(() -> 7).join();
		}

		@Override
		public Response fetch(Request request, DurableState state, Env env) {
			return Bytebox.response("room " + answer);
		}
	}
	""";

	private static final String FLOW = """
	package fixture;

	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.js.TSObject;
	import dev.gmitch215.bytebox.workflow.Step;
	import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
	import dev.gmitch215.bytebox.workflow.WorkflowEvent;
	import java.util.concurrent.CompletableFuture;
	import java.util.concurrent.locks.ReentrantLock;

	public class Flow implements WorkflowEntrypoint {
		private static final ReentrantLock LOCK = new ReentrantLock();
		private final int answer;

		public Flow() {
			LOCK.lock();
			try {
				answer = CompletableFuture.supplyAsync(() -> 9).join();
			} finally {
				LOCK.unlock();
			}
		}

		@Override
		public TSObject run(WorkflowEvent event, Step step, Env env) {
			return TSObject.of("flow " + answer);
		}
	}
	""";

	@Test
	@DisplayName("compiles a Durable Object and a Workflow that block in their constructors")
	void compilesBlockingConstructors(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				durableObjects("fixture.Room")
				workflows("fixture.Flow")
				wrangler {
					name = "constructors"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			Map.of(
				"fixture/Handler.java",
				Fixtures.fetchHandler(),
				"fixture/Room.java",
				ROOM,
				"fixture/Flow.java",
				FLOW
			)
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		String entry = Files.readString(
			root.resolve(
				"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated/ByteboxEntry.java"
			)
		);
		assertTrue(entry.contains("DurableHandlers.fetch(id, fixture.Room::new,"), entry);
		assertTrue(entry.contains("WorkflowHandlers.run(fixture.Flow::new,"), entry);
		assertFalse(entry.contains("DurableHandlers.instance("), entry);
		byte[] module = Files.readAllBytes(root.resolve("build/bytebox/worker/src/app.wasmbin"));
		assertTrue(module.length > 10_000, "the module measured " + module.length + " bytes");
	}

	@Test
	@DisplayName("compiles a handler that blocks in a static initialiser and its constructor")
	void compilesABlockingStart(@TempDir Path root) throws IOException {
		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				wrangler {
					name = "modulestart"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			Map.of("fixture/Handler.java", HANDLER)
		);

		BuildResult result = Fixtures.runner(root, "buildWorker").build();

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateWasmGC").getOutcome());
		String entry = Files.readString(
			root.resolve(
				"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated/ByteboxEntry.java"
			)
		);
		assertTrue(entry.contains("new Thread(ByteboxEntry::start).start();"), entry);
		assertTrue(entry.contains("HANDLER = new fixture.Handler();"), entry);
		assertFalse(entry.contains("static final fixture.Handler"), entry);
		byte[] module = Files.readAllBytes(root.resolve("build/bytebox/worker/src/app.wasmbin"));
		assertTrue(module.length > 10_000, "the module measured " + module.length + " bytes");
	}
}
