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

@DisplayName("a Workflow written in Java")
class WorkflowTest {

	private static final String ONBOARDING = """
	package fixture;

	import dev.gmitch215.bytebox.Env;
	import dev.gmitch215.bytebox.js.TSObject;
	import dev.gmitch215.bytebox.workflow.Step;
	import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
	import dev.gmitch215.bytebox.workflow.WorkflowEvent;

	public class Onboarding implements WorkflowEntrypoint {
		@Override
		public TSObject run(WorkflowEvent event, Step step, Env env) {
			TSObject welcome = step.run("welcome", () -> TSObject.of("hello"));
			step.sleep("wait", "1 minute");
			return step.run("follow up", () -> TSObject.of(welcome.asString() + " again"));
		}
	}
	""";

	@Test
	@DisplayName("compiles, exports its run, and writes the class the platform instantiates")
	void exportsAndWritesTheClass(@TempDir Path root) throws IOException {
		BuildResult result = build(root, "", Map.of("fixture/Onboarding.java", ONBOARDING));

		assertEquals(TaskOutcome.SUCCESS, result.task(":generateEntryPoint").getOutcome());
		assertEquals(TaskOutcome.SUCCESS, result.task(":buildWorker").getOutcome());
		assertTrue(generated(root).contains("workflowOnboardingRun("), generated(root));

		String index = worker(root, "src/index.ts");
		assertTrue(
			index.contains("import { WorkflowEntrypoint } from 'cloudflare:workers';"),
			index
		);
		assertTrue(index.contains("export class Onboarding extends WorkflowEntrypoint {"), index);
		assertTrue(
			index.contains("java.call('workflowOnboardingRun', event, step, this.env)"),
			index
		);
	}

	@Test
	@DisplayName("binds itself, under the three keys Wrangler requires")
	void bindsItself(@TempDir Path root) throws IOException {
		build(root, "", Map.of("fixture/Onboarding.java", ONBOARDING));

		String wrangler = worker(root, "wrangler.jsonc");
		assertTrue(wrangler.contains("\"workflows\": [{ "), wrangler);
		assertTrue(wrangler.contains("\"binding\": \"WORKFLOW_ONBOARDING\""), wrangler);
		assertTrue(wrangler.contains("\"name\": \"onboarding\""), wrangler);
		assertTrue(wrangler.contains("\"class_name\": \"Onboarding\""), wrangler);
	}

	@Test
	@DisplayName("is not bound a second time when the project already bound the class")
	void notBoundTwice(@TempDir Path root) throws IOException {
		build(
			root,
			"bindings { workflow(\"fixture.Onboarding\") }",
			Map.of("fixture/Onboarding.java", ONBOARDING)
		);

		String wrangler = worker(root, "wrangler.jsonc");
		assertTrue(
			wrangler.contains("{ \"name\": \"WORKFLOW\", \"class_name\": \"Onboarding\" }"),
			wrangler
		);
		assertFalse(wrangler.contains("WORKFLOW_ONBOARDING"), wrangler);
		assertEquals(1, wrangler.split("\"class_name\"", -1).length - 1, wrangler);
	}

	@Test
	@DisplayName("refuses a class that is not one")
	void refusesAClassThatIsNotOne(@TempDir Path root) throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.put("fixture/NotOne.java", "package fixture;\n\npublic class NotOne {}\n");

		Fixtures.project(root, script("fixture.NotOne", ""), sources);
		BuildResult result = Fixtures.runner(root, "generateEntryPoint").buildAndFail();

		assertTrue(
			result.getOutput().contains("does not implement WorkflowEntrypoint"),
			result.getOutput()
		);
	}

	private static String script(String flow, String extra) {
		return """
		bytebox {
			handlerClass = "fixture.Handler"
			workflows("%s")
			%s
			wrangler {
				name = "flows"
				compatibilityDate = "2026-08-22"
			}
		}
		""".formatted(flow, extra);
	}

	private static BuildResult build(Path root, String extra, Map<String, String> flow)
		throws IOException {
		Map<String, String> sources = new LinkedHashMap<>();
		sources.put("fixture/Handler.java", Fixtures.fetchHandler());
		sources.putAll(flow);

		Fixtures.project(root, script("fixture.Onboarding", extra), sources);
		return Fixtures.runner(root, "buildWorker").build();
	}

	private static String generated(Path root) throws IOException {
		return Files.readString(
			root.resolve(
				"build/generated/sources/bytebox/java/dev/gmitch215/bytebox/generated/ByteboxEntry.java"
			)
		);
	}

	private static String worker(Path root, String path) throws IOException {
		return Files.readString(root.resolve("build/bytebox/worker").resolve(path));
	}
}
