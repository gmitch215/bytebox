package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("generating the entry point")
class GenerateEntryPointTaskTest {

	private static final String FIXTURE = "dev.gmitch215.bytebox.gradle.fixture.";

	@TempDir
	Path directory;

	@Test
	@DisplayName("exports one handler per trigger the class implements")
	void everyTrigger() {
		String source = generate(FIXTURE + "EveryTrigger");

		for (String export : List.of("fetch", "scheduled", "email", "queue", "tail", "alarm")) {
			assertTrue(source.contains(" " + export + "("), export + " is missing from " + source);
		}
		assertTrue(source.contains("import dev.gmitch215.bytebox.Request;"));
		assertTrue(source.contains("import dev.gmitch215.bytebox.ScheduledController;"));
		assertTrue(source.contains("import dev.gmitch215.bytebox.MailMessage;"));
		assertTrue(source.contains("import dev.gmitch215.bytebox.QueueBatch;"));
		assertTrue(source.contains("import org.teavm.jso.core.JSArrayReader;"));
	}

	@Test
	@DisplayName("exports nothing for a trigger the class does not implement")
	void onlyFetch() {
		String source = generate(FIXTURE + "FetchWorker");

		assertTrue(source.contains(" fetch("));
		assertFalse(source.contains(" scheduled("));
		assertFalse(source.contains("import dev.gmitch215.bytebox.ScheduledController;"));
		assertFalse(source.contains("import dev.gmitch215.bytebox.TraceItem;"));
	}

	@Test
	@DisplayName("reads a trigger the class inherits rather than declares")
	void throughABaseClass() {
		String source = generate(FIXTURE + "DerivedWorker");

		assertTrue(source.contains(" fetch("));
		assertTrue(source.contains(" scheduled("));
	}

	@Test
	@DisplayName("constructs the handler once and registers both codec sets")
	void singleton() {
		String source = generate(FIXTURE + "FetchWorker");

		assertTrue(source.contains("HANDLER = new " + FIXTURE + "FetchWorker()"));
		assertTrue(source.contains(GenerateCodecsTask.REGISTRY + ".register();"));
		assertTrue(source.contains(GenerateSerialCodecsTask.REGISTRY + ".register();"));
	}

	@Test
	@DisplayName("refuses a handler that implements no trigger, rather than exporting nothing")
	void noTrigger() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generate(FIXTURE + "NoTrigger")
		);

		assertTrue(failure.getMessage().contains("implements none of"), failure.getMessage());
	}

	@Test
	@DisplayName("names the setting to check when the handler class is not there")
	void missingHandler() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generate("com.example.Absent")
		);

		assertTrue(failure.getMessage().contains("bytebox.handlerClass"), failure.getMessage());
	}

	@Test
	@DisplayName("gives a Durable Object every export its interfaces call for")
	void durableObjectWithEverything() {
		String source = generate(FIXTURE + "FetchWorker", FIXTURE + "FullObject");

		assertTrue(source.contains("durableFullObjectFetch("));
		assertTrue(source.contains("durableFullObjectAlarm("));
		assertTrue(source.contains("durableFullObjectMessageText("));
		assertTrue(source.contains("durableFullObjectMessageBytes("));
		assertTrue(source.contains("durableFullObjectClosed("));
		assertTrue(source.contains("durableFullObjectFailed("));
		assertTrue(source.contains("import dev.gmitch215.bytebox.socket.WebSocket;"));
		assertTrue(source.contains("import org.teavm.jso.typedarrays.ArrayBuffer;"));
	}

	@Test
	@DisplayName("gives one that only takes requests nothing else")
	void durableObjectWithOnlyFetch() {
		String source = generate(FIXTURE + "FetchWorker", FIXTURE + "PlainObject");

		assertTrue(source.contains("durablePlainObjectFetch("));
		assertFalse(source.contains("durablePlainObjectAlarm("));
		assertFalse(source.contains("import dev.gmitch215.bytebox.socket.WebSocket;"));
	}

	@Test
	@DisplayName("imports Request for a Durable Object even when the Worker takes no requests")
	void durableObjectWithoutAFetchHandler() {
		String source = generate(FIXTURE + "DerivedWorker", FIXTURE + "PlainObject");

		assertTrue(source.contains("import dev.gmitch215.bytebox.Request;"));
		assertTrue(source.contains("import dev.gmitch215.bytebox.durable.DurableState;"));
	}

	@Test
	@DisplayName("refuses a class named as a Durable Object that is not one")
	void notADurableObject() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generate(FIXTURE + "FetchWorker", FIXTURE + "NoTrigger")
		);

		assertTrue(
			failure.getMessage().contains("does not implement DurableObject"),
			failure.getMessage()
		);
	}

	@Test
	@DisplayName("says which setting to check when a Durable Object class is not there")
	void missingDurableObject() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generate(FIXTURE + "FetchWorker", "com.example.Absent")
		);

		assertTrue(failure.getMessage().contains("durableObjects"), failure.getMessage());
	}

	@Test
	@DisplayName("names the exports and the binding from the class's own name")
	void namesDerivedFromTheClass() {
		DurableObjects object = DurableObjects.of(
			List.of(FIXTURE + "FullObject"),
			Projects.classpath()
		).get(0);

		assertEquals("FullObject", object.simpleName());
		assertEquals("DO_FULL_OBJECT", object.bindingName());
		assertEquals("durableFullObject", object.exportPrefix());
		assertTrue(object.sockets());
		assertTrue(object.alarms());
	}

	@Test
	@DisplayName("reads nothing when no Durable Object is declared")
	void noDurableObjects() {
		assertEquals(List.of(), DurableObjects.of(List.of(), Projects.classpath()));
	}

	@Test
	@DisplayName("pairs each trigger interface with the name the Worker exports")
	void triggerNames() {
		assertEquals("dev.gmitch215.bytebox.Worker", Triggers.FETCH.interfaceName());
		assertEquals("fetch", Triggers.FETCH.exportName());
		assertEquals("dev.gmitch215.bytebox.Alarm", Triggers.ALARM.interfaceName());
		assertEquals("alarm", Triggers.ALARM.exportName());
	}

	private String generate(String handler, String... objects) {
		Project project = Projects.project(directory);
		GenerateEntryPointTask task = Projects.task(project, GenerateEntryPointTask.class);
		task.getHandlerClass().set(handler);
		task.getDurableObjectClasses().set(List.of(objects));
		task.getHandlerClasspath().from(Projects.classpath());
		task.getOutputDirectory().set(directory.resolve("generated").toFile());
		task.generate();

		return Projects.read(
			directory
				.resolve("generated")
				.resolve(GenerateEntryPointTask.PACKAGE.replace('.', '/'))
				.resolve(GenerateEntryPointTask.CLASS_NAME + ".java")
		);
	}
}
