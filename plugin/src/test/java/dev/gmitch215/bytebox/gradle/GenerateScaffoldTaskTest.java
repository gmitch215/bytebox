package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("generating the Worker project")
class GenerateScaffoldTaskTest {

	@TempDir
	Path directory;

	// #region wrangler.jsonc

	@Test
	@DisplayName("writes the keys every Worker needs")
	void skeleton() {
		String config = wrangler(task());

		assertTrue(config.contains("\"name\": \"api\""), config);
		assertTrue(config.contains("\"main\": \"src/index.ts\""), config);
		assertTrue(config.contains("\"compatibility_date\": \"2026-08-22\""), config);
		assertTrue(config.contains("\"observability\": { \"enabled\": true }"), config);
		assertTrue(config.contains("\"type\": \"Data\""), config);
		assertTrue(config.contains("**/*.wasmbin"), config);
	}

	@Test
	@DisplayName("leaves observability out when it is turned off")
	void observabilityOff() {
		GenerateScaffoldTask task = task();
		task.getObservability().set(false);

		assertFalse(wrangler(task).contains("observability"));
	}

	@Test
	@DisplayName("turns Node compatibility off, which a modern date turns on")
	void disablesNodeCompatibility() {
		String config = wrangler(task());

		assertTrue(config.contains("no_nodejs_compat"), config);
		assertTrue(config.contains("no_nodejs_compat_v2"), config);
	}

	@Test
	@DisplayName("leaves it alone for a date from before it was the default")
	void oldDate() {
		GenerateScaffoldTask task = task();
		task.getCompatibilityDate().set("2024-01-01");

		assertFalse(wrangler(task).contains("no_nodejs_compat"));
	}

	@Test
	@DisplayName("does not contradict a project that asked for Node compatibility")
	void explicitNodeCompatibility() {
		GenerateScaffoldTask task = task();
		task.getCompatibilityFlags().set(List.of("nodejs_compat"));

		String config = wrangler(task);
		assertTrue(config.contains("\"nodejs_compat\""), config);
		assertFalse(config.contains("\"no_nodejs_compat\""), config);
	}

	@Test
	@DisplayName("refuses a date that is not a date")
	void badDate() {
		GenerateScaffoldTask task = task();
		task.getCompatibilityDate().set("august");

		assertThrows(GradleException.class, task::generate);
	}

	@Test
	@DisplayName("writes routes, crons and tail consumers only when there are some")
	void triggersAndRoutes() {
		assertFalse(wrangler(task()).contains("routes"));

		GenerateScaffoldTask task = task();
		task.getRoutes().set(List.of("example.com/*"));
		task.getCrons().set(List.of("*/5 * * * *"));
		task.getTailConsumers().set(List.of("logger"));

		String config = wrangler(task);
		assertTrue(config.contains("\"routes\": [\"example.com/*\"]"), config);
		assertTrue(config.contains("\"crons\": [\"*/5 * * * *\"]"), config);
		assertTrue(config.contains("\"tail_consumers\": [{ \"service\": \"logger\" }]"), config);
	}

	@Test
	@DisplayName("groups bindings under the key each type is written at")
	void bindingKeys() {
		Bindings bindings = new Bindings();
		bindings.kv();
		bindings.kv("SESSIONS");
		bindings.d1();
		bindings.ai();
		bindings.assets("./public");
		bindings.durableObject("Counter");

		GenerateScaffoldTask task = task();
		task.getBindings().set(bindings.getAll());

		String config = wrangler(task);
		assertTrue(config.contains("\"kv_namespaces\": [{ \"binding\": \"KV\" }"), config);
		assertTrue(config.contains("\"binding\": \"SESSIONS\""), config);
		assertTrue(config.contains("\"d1_databases\": ["), config);
		assertTrue(config.contains("\"ai\": { \"binding\": \"AI\" }"), config);
		assertTrue(config.contains("\"assets\": {"), config);
		assertTrue(config.contains("\"durable_objects\": { \"bindings\": ["), config);
	}

	@Test
	@DisplayName("declares each Java Durable Object class in a migration")
	void migrations() {
		GenerateScaffoldTask task = task();
		task.getDurableObjects().set(
			List.of(new DurableObjects("com.example.Counter", false, false))
		);

		assertTrue(
			wrangler(task).contains(
				"\"migrations\": [{ \"tag\": \"v1\", \"new_sqlite_classes\": [\"Counter\"] }]"
			)
		);
	}

	@Test
	@DisplayName("escapes what a JSON string cannot carry raw")
	void escaping() {
		GenerateScaffoldTask task = task();
		task.getWorkerName().set(
			"q\"b" + (char) 92 + "s" + (char) 10 + (char) 9 + (char) 13 + (char) 1
		);

		String config = wrangler(task);
		assertTrue(config.contains("q" + (char) 92 + "\"b"), config);
		assertTrue(config.contains((char) 92 + "" + (char) 92 + "s"), config);
		assertTrue(config.contains((char) 92 + "n" + (char) 92 + "t" + (char) 92 + "r"), config);
		assertTrue(config.contains((char) 92 + "u0001"), config);
	}

	// #endregion

	// #region index.ts

	@Test
	@DisplayName("exports one handler per trigger, each through the gate")
	void handlers() {
		GenerateScaffoldTask task = task();
		task.getTriggers().set(List.of("fetch", "scheduled", "email", "queue", "tail", "alarm"));

		String index = index(task);
		assertTrue(index.contains("async fetch(request: Request"), index);
		assertTrue(index.contains("async scheduled(controller: ScheduledController"), index);
		assertTrue(index.contains("async email(message: ForwardableEmailMessage"), index);
		assertTrue(index.contains("async queue(batch: MessageBatch"), index);
		assertTrue(index.contains("async tail(events: TraceItem[]"), index);
		assertTrue(index.contains("async alarm(env: unknown"), index);
		assertTrue(index.contains("java.call('alarm', env, ctx)"), index);
		assertEquals(6, index.split("gate\\.run", -1).length - 1, index);
	}

	@Test
	@DisplayName("loads the module at module scope, which is the only place it may compile")
	void moduleScope() {
		String index = index(task());

		assertTrue(index.contains("import { createGate, load } from 'bytebox';"), index);
		assertTrue(index.contains("import bytes from './app.wasmbin';"), index);
		assertTrue(index.contains("const java = load({ runtime, bytes });"), index);
		assertTrue(index.contains("java.call('main', []);"), index);
	}

	@Test
	@DisplayName("imports each npm package statically, which is what makes it resolvable")
	void npmImports() {
		GenerateScaffoldTask task = task();
		task.getNPMPackages().set(
			List.of("nanoid@^5.0.9", "@noble/hashes@^1.4.0", "3d-view@^2.0.0")
		);

		String index = index(task);
		assertTrue(index.contains("import * as nanoid from 'nanoid';"), index);
		assertTrue(index.contains("import * as _noble_hashes from '@noble/hashes';"), index);
		assertTrue(index.contains("import * as _3d_view from '3d-view';"), index);
		assertTrue(index.contains("\"nanoid\": nanoid"), index);
		assertTrue(index.contains("modules: {"), index);
	}

	@Test
	@DisplayName("writes a JavaScript class per Durable Object, with what its interfaces call for")
	void durableClasses() {
		GenerateScaffoldTask task = task();
		task.getDurableObjects().set(
			List.of(
				new DurableObjects("com.example.Room", true, true),
				new DurableObjects("com.example.Plain", false, false)
			)
		);

		String index = index(task);
		assertTrue(index.contains("import { DurableObject } from 'cloudflare:workers';"), index);
		assertTrue(index.contains("export class Room extends DurableObject"), index);
		assertTrue(index.contains("durableRoomFetch"), index);
		assertTrue(index.contains("durableRoomAlarm"), index);
		assertTrue(index.contains("durableRoomMessageText"), index);
		assertTrue(index.contains("durableRoomMessageBytes"), index);
		assertTrue(index.contains("durableRoomClosed"), index);
		assertTrue(index.contains("durableRoomFailed"), index);

		assertTrue(index.contains("export class Plain extends DurableObject"), index);
		assertFalse(index.contains("durablePlainAlarm"), index);
		assertFalse(index.contains("durablePlainMessageText"), index);
	}

	// #endregion

	@Test
	@DisplayName("writes a manifest naming bytebox, the decoder and every npm package")
	void manifest() {
		GenerateScaffoldTask task = task();
		task.getDecoder().set("fzstd");
		task.getDecoderVersion().set("^0.1.1");
		task.getNPMPackages().set(List.of("@noble/hashes@^1.4.0"));
		task.generate();

		String manifest = Projects.read(directory.resolve("package.json"));
		assertTrue(manifest.contains("\"bytebox\": \"^1.0.0\""), manifest);
		assertTrue(manifest.contains("\"fzstd\": \"^0.1.1\""), manifest);
		assertTrue(manifest.contains("\"@noble/hashes\": \"^1.4.0\""), manifest);
		assertTrue(manifest.contains("\"type\": \"module\""), manifest);
		assertTrue(manifest.contains("\"wrangler\""), manifest);
	}

	private GenerateScaffoldTask task() {
		GenerateScaffoldTask task = Projects.task(
			Projects.project(directory.resolve("project")),
			GenerateScaffoldTask.class
		);
		task.getWorkerName().set("api");
		task.getCompatibilityDate().set("2026-08-22");
		task.getByteboxVersion().set("^1.0.0");
		task.getTriggers().set(List.of("fetch"));
		task.getOutputDirectory().set(directory.toFile());
		return task;
	}

	private String wrangler(GenerateScaffoldTask task) {
		task.generate();
		return Projects.read(directory.resolve("wrangler.jsonc"));
	}

	private String index(GenerateScaffoldTask task) {
		task.generate();
		return Projects.read(directory.resolve("src/index.ts"));
	}
}
