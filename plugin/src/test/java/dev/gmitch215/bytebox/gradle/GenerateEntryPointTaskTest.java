package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
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
	@DisplayName("starts the handler and the codecs on a fiber, so module-start work can block")
	void startsOnAFiber() {
		String source = generate(FIXTURE + "FetchWorker");

		int main = source.indexOf("public static void main(");
		int thread = source.indexOf("new Thread(", main);
		assertTrue(main > 0 && thread > main, source);
		assertTrue(source.indexOf("HANDLER = new", thread) > thread, source);
		assertTrue(source.indexOf(GenerateCodecsTask.REGISTRY + ".register();") > thread, source);
		assertFalse(source.contains("static final " + FIXTURE + "FetchWorker"), source);
		assertFalse(source.contains("static {"), source);
	}

	@Test
	@DisplayName("exports read the handler through a check, never a static initialiser")
	void exportsReadThroughACheck() {
		String source = generate(FIXTURE + "EveryTrigger");

		assertTrue(source.contains("Handlers.fetch(handler(), request, env, ctx)"), source);
		assertTrue(source.contains("Handlers.alarm(handler(), env, ctx)"), source);
		assertFalse(source.contains("(HANDLER,"), source);
		assertTrue(source.contains("private static " + FIXTURE + "EveryTrigger handler()"), source);
	}

	@Test
	@DisplayName("keeps the failure of a start that threw, and rethrows it on the first request")
	void failedStart() {
		String source = generate(FIXTURE + "FetchWorker");

		assertTrue(source.contains("catch (Throwable failure)"), source);
		assertTrue(source.contains("FAILURE = failure;"), source);
		assertTrue(source.contains("new IllegalStateException("), source);
		assertTrue(source.contains("\"module start failed: \" + FAILURE"), source);
	}

	@Test
	@DisplayName("reads a handler with a private constructor from its static instance")
	void kotlinObject() {
		String source = generate(FIXTURE + "KotlinObject");

		assertTrue(source.contains("HANDLER = " + FIXTURE + "KotlinObject.INSTANCE;"), source);
		assertFalse(source.contains("new " + FIXTURE + "KotlinObject"), source);
	}

	@Test
	@DisplayName("still constructs a handler that has a public constructor and an instance field")
	void publicConstructorWins() {
		String source = generate(FIXTURE + "OpenInstance");

		assertTrue(source.contains("HANDLER = new " + FIXTURE + "OpenInstance();"), source);
	}

	@Test
	@DisplayName("refuses a Durable Object that is a singleton, because there is one per id")
	void singletonDurableObject() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generate(FIXTURE + "FetchWorker", FIXTURE + "KotlinObjectDurable")
		);

		assertTrue(failure.getMessage().contains("one instance per"), failure.getMessage());
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
	@DisplayName("builds a Durable Object inside the export's fiber, not before it")
	void durableObjectBuiltOnTheFiber() {
		String source = generate(FIXTURE + "FetchWorker", FIXTURE + "FullObject");
		String type = FIXTURE + "FullObject::new";

		assertTrue(source.contains("DurableHandlers.fetch(id, " + type + ", request, state, env)"));
		assertTrue(source.contains("DurableHandlers.alarm(id, " + type + ", state, env)"));
		assertTrue(source.contains("DurableHandlers.message(id, " + type + ", socket, text,"));
		assertTrue(source.contains("DurableHandlers.message(id, " + type + ", socket, bytes,"));
		assertTrue(source.contains("DurableHandlers.closed(id, " + type + ", socket, code,"));
		assertTrue(source.contains("DurableHandlers.failed(id, " + type + ", socket, message,"));
		assertFalse(source.contains("DurableHandlers.instance("), source);
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

	// #region the classlib block

	@Test
	@DisplayName("registers nothing of the class library unless a project asks")
	void classlibOffByDefault() {
		String source = generate(FIXTURE + "FetchWorker");

		assertFalse(source.contains("ZoneHistory"), source);
		assertFalse(source.contains("Dates"), source);
		assertFalse(source.contains("static {"), source);
		assertFalse(source.contains("import dev.gmitch215.bytebox.time"), source);
	}

	@Test
	@DisplayName("registers the zone history before the handler is constructed")
	void registersZoneHistoryFirst() {
		String source = generate(true, FIXTURE + "FetchWorker");

		assertTrue(source.contains("import " + GenerateEntryPointTask.ZONE_HISTORY + ";"), source);
		int registered = source.indexOf("ZoneHistory.register();");
		assertTrue(registered > source.indexOf("new Thread("), source);
		assertTrue(registered < source.indexOf("HANDLER = new"), source);
	}

	@Test
	@DisplayName("changes nothing but the import and the registration when the zone history is on")
	void changesOnlyWhatItNames() {
		String off = generate(false, FIXTURE + "FetchWorker");
		String on = generate(true, FIXTURE + "FetchWorker");

		assertEquals(
			off,
			on
				.replace("import " + GenerateEntryPointTask.ZONE_HISTORY + ";\n", "")
				.replace("\t\t\tZoneHistory.register();\n", "")
		);
	}

	@Test
	@DisplayName("installs the date conversions before the handler is constructed")
	void registersDatesFirst() {
		String source = generateDates(FIXTURE + "FetchWorker");

		assertTrue(source.contains("import " + GenerateEntryPointTask.DATES + ";"), source);
		int registered = source.indexOf("Dates.register();");
		assertTrue(registered > source.indexOf("new Thread("), source);
		assertTrue(registered < source.indexOf("HANDLER = new"), source);
	}

	@Test
	@DisplayName(
		"changes nothing but the import and the registration when the date conversions are on"
	)
	void changesOnlyWhatDatesNames() {
		String off = generate(false, FIXTURE + "FetchWorker");

		assertEquals(
			off,
			generateDates(FIXTURE + "FetchWorker")
				.replace("import " + GenerateEntryPointTask.DATES + ";\n", "")
				.replace("\t\t\tDates.register();\n", "")
		);
	}

	// #endregion

	// #region Workflows

	@Test
	@DisplayName("exports one run per Workflow class, starting a new instance each time")
	void workflowExport() {
		String source = generateFlows(FIXTURE + "FetchWorker", FIXTURE + "PlainFlow");

		assertTrue(source.contains("workflowPlainFlowRun("), source);
		assertTrue(source.contains("WorkflowEvent event"), source);
		assertTrue(source.contains("import dev.gmitch215.bytebox.workflow.Step;"), source);
		assertTrue(source.contains("import dev.gmitch215.bytebox.js.TSObject;"), source);
	}

	@Test
	@DisplayName("builds a Workflow inside the export's fiber, not before it")
	void workflowBuiltOnTheFiber() {
		String source = generateFlows(FIXTURE + "FetchWorker", FIXTURE + "PlainFlow");

		assertTrue(
			source.contains(
				"WorkflowHandlers.run(" + FIXTURE + "PlainFlow::new, event, step, env)"
			),
			source
		);
		assertFalse(source.contains("run(new "), source);
	}

	@Test
	@DisplayName("reaches a Workflow that is a Kotlin object through its static instance")
	void workflowObject() {
		String source = generateFlows(FIXTURE + "FetchWorker", FIXTURE + "ObjectFlow");

		assertTrue(
			source.contains("WorkflowHandlers.run(() -> " + FIXTURE + "ObjectFlow.INSTANCE,"),
			source
		);
	}

	@Test
	@DisplayName("imports TSObject once when a queue handler and a Workflow both need it")
	void workflowWithQueue() {
		String source = generateFlows(FIXTURE + "EveryTrigger", FIXTURE + "PlainFlow");

		assertEquals(1, source.split("import dev.gmitch215.bytebox.js.TSObject;", -1).length - 1);
	}

	@Test
	@DisplayName("refuses a class named as a Workflow that is not one")
	void notAWorkflow() {
		GradleException failure = assertThrows(GradleException.class, () ->
			generateFlows(FIXTURE + "FetchWorker", FIXTURE + "NoTrigger")
		);

		assertTrue(failure.getMessage().contains("workflows"), failure.getMessage());
	}

	@Test
	@DisplayName("writes the same entry point as before when no Workflow is declared")
	void noWorkflowsChangesNothing() {
		String without = generate(FIXTURE + "EveryTrigger");
		String with = generateFlows(FIXTURE + "EveryTrigger", FIXTURE + "PlainFlow");

		assertFalse(without.toLowerCase().contains("workflow"), without);
		assertEquals(
			without,
			with
				.replace("import dev.gmitch215.bytebox.workflow.Step;\n", "")
				.replace("import dev.gmitch215.bytebox.workflow.WorkflowEvent;\n", "")
				.replace("import dev.gmitch215.bytebox.workflow.WorkflowHandlers;\n", "")
				.replaceFirst(
					"(?s)\n\t@JSExport\n\tpublic static JSPromise<TSObject> workflowPlainFlowRun\\(.*?\n\t}\n",
					""
				)
		);
	}

	// #endregion

	private String generateFlows(String handler, String... flows) {
		return generate(task -> task.getWorkflowClasses().set(List.of(flows)), handler);
	}

	private String generate(String handler, String... objects) {
		return generate(task -> {}, handler, objects);
	}

	private String generateDates(String handler) {
		return generate(task -> task.getDateFormat().set(true), handler);
	}

	private String generate(boolean zoneHistory, String handler) {
		return generate(task -> task.getZoneHistory().set(zoneHistory), handler);
	}

	private String generate(
		Consumer<GenerateEntryPointTask> configure,
		String handler,
		String... objects
	) {
		Project project = Projects.project(directory);
		GenerateEntryPointTask task = Projects.task(project, GenerateEntryPointTask.class);
		task.getHandlerClass().set(handler);
		configure.accept(task);
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
