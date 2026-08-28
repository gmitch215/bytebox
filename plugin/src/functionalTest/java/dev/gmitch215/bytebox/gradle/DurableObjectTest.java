package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

@DisplayName("a Durable Object written in Java")
class DurableObjectTest {

	private static final String COUNTER = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.durable.DurableObject;
	import dev.gmitch215.bytebox.durable.DurableState;

	public class Counter implements DurableObject {
		@Override
		public Response fetch(Request request, DurableState state, Env env) {
			long count = state.getLong("count", 0) + 1;
			state.put("count", count);
			return Bytebox.response(String.valueOf(count));
		}
	}
	""";

	private static final String ROOM = """
	package fixture;

	import dev.gmitch215.bytebox.Bytebox;
	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.Request;
	import dev.gmitch215.bytebox.Response;
	import dev.gmitch215.bytebox.durable.AlarmObject;
	import dev.gmitch215.bytebox.durable.DurableState;
	import dev.gmitch215.bytebox.durable.SocketObject;
	import dev.gmitch215.bytebox.socket.WebSocket;
	import dev.gmitch215.bytebox.socket.WebSocketPair;

	public class Room implements SocketObject, AlarmObject {
		@Override
		public Response fetch(Request request, DurableState state, Env env) {
			WebSocketPair pair = WebSocketPair.create();
			state.acceptWebSocket(pair.server());
			return Bytebox.upgrade(pair.client());
		}

		@Override
		public void message(WebSocket socket, String text, DurableState state, Env env) {
			state.broadcast(text);
		}

		@Override
		public void alarm(DurableState state, Env env) {
			state.broadcast("tick");
		}
	}
	""";

	@Test
	@DisplayName("exports only the handlers it implements")
	void exportsWhatItImplements(@TempDir Path root) throws IOException {
		BuildResult result = build(
			root,
			"fixture.Counter",
			Map.of("fixture/Counter.java", COUNTER)
		);

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateEntryPoint").getOutcome());
		String entry = generated(root, "ByteboxEntry");
		assertTrue(entry.contains("durableCounterFetch("), entry);

		// no alarm and no sockets, so nothing for them is exported at all
		assertFalse(entry.contains("durableCounterAlarm("), entry);
		assertFalse(entry.contains("durableCounterMessageText("), entry);
	}

	@Test
	@DisplayName("exports the socket and alarm handlers when the class takes them")
	void exportsSocketsAndAlarms(@TempDir Path root) throws IOException {
		build(root, "fixture.Room", Map.of("fixture/Room.java", ROOM));

		String entry = generated(root, "ByteboxEntry");
		assertTrue(entry.contains("durableRoomFetch("), entry);
		assertTrue(entry.contains("durableRoomAlarm("), entry);
		assertTrue(entry.contains("durableRoomMessageText("), entry);
		assertTrue(entry.contains("durableRoomMessageBytes("), entry);
		assertTrue(entry.contains("durableRoomClosed("), entry);
		assertTrue(entry.contains("durableRoomFailed("), entry);

		// one Java instance per Durable Object instance, paired by the instance's own identifier
		assertTrue(entry.contains("DurableHandlers.instance(id, fixture.Room::new)"), entry);
	}

	@Test
	@DisplayName("writes the JavaScript class the runtime instantiates")
	void writesTheJavaScriptClass(@TempDir Path root) throws IOException {
		build(root, "fixture.Room", Map.of("fixture/Room.java", ROOM));

		String index = worker(root, "src/index.ts");
		assertTrue(index.contains("import { DurableObject } from 'cloudflare:workers';"), index);
		assertTrue(index.contains("export class Room extends DurableObject {"), index);
		assertTrue(index.contains("this.id = ctx.id.toString();"), index);
		assertTrue(index.contains("webSocketMessage(socket: WebSocket"), index);
		assertTrue(index.contains("webSocketClose(socket: WebSocket"), index);
		assertTrue(index.contains("alarm(): Promise<void>"), index);

		// through the same gate as a request: one heap serves the isolate, so a Durable Object
		// method arriving while a request is parked is the same reentrancy
		assertTrue(index.contains("return gate.run(() =>"), index);
	}

	@Test
	@DisplayName("binds itself and declares its migration")
	void bindsItselfAndMigrates(@TempDir Path root) throws IOException {
		build(root, "fixture.Counter", Map.of("fixture/Counter.java", COUNTER));

		String wrangler = worker(root, "wrangler.jsonc");
		assertTrue(wrangler.contains("\"durable_objects\""), wrangler);
		assertTrue(wrangler.contains("\"DO_COUNTER\""), wrangler);
		assertTrue(wrangler.contains("\"class_name\": \"Counter\""), wrangler);
		assertTrue(
			wrangler.contains("\"migrations\"") && wrangler.contains("\"new_sqlite_classes\""),
			wrangler
		);
	}

	@Test
	@DisplayName("refuses a class that is not one")
	void refusesAClassThatIsNotOne(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.put(
			"fixture/NotOne.java",
			"""
			package fixture;

			public class NotOne {}
			"""
		);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				durableObjects("fixture.NotOne")
				wrangler {
					name = "durable"
					compatibilityDate = "2026-08-22"
				}
			}
			""",
			sources
		);

		BuildResult result = Fixtures.runner(root, "generateEntryPoint").buildAndFail();

		assertTrue(
			result.getOutput().contains("does not implement DurableObject"),
			result.getOutput()
		);
	}

	private static BuildResult build(Path root, String objectClass, Map<String, String> object)
		throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.putAll(object);

		Fixtures.project(
			root,
			"""
			bytebox {
				handlerClass = "fixture.Handler"
				durableObjects("%s")
				wrangler {
					name = "durable"
					compatibilityDate = "2026-08-22"
				}
			}
			""".formatted(objectClass),
			sources
		);
		return Fixtures.runner(root, "buildWorker").build();
	}

	private static String generated(Path root, String name) throws IOException {
		return Files.readString(
			root
				.resolve("build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated")
				.resolve(name + ".java")
		);
	}

	private static String worker(Path root, String path) throws IOException {
		return Files.readString(root.resolve("build/bytebox/worker").resolve(path));
	}
}
