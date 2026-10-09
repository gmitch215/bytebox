package dev.gmitch215.bytebox.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("reading a Workflow class")
class WorkflowClassesTest {

	private static final String FIXTURE = "dev.gmitch215.bytebox.gradle.fixture.";

	@Test
	@DisplayName("names the export, the binding and the Workflow from the class's own name")
	void namesDerivedFromTheClass() {
		WorkflowClasses flow = WorkflowClasses.of(
			List.of(FIXTURE + "PlainFlow"),
			Projects.classpath()
		).get(0);

		assertEquals("PlainFlow", flow.simpleName());
		assertEquals("workflowPlainFlow", flow.exportPrefix());
		assertEquals("WORKFLOW_PLAIN_FLOW", flow.bindingName());
		assertEquals("plain-flow", flow.workflowName());
		assertFalse(flow.singleton());
	}

	@Test
	@DisplayName("reads a Kotlin object as a singleton")
	void singleton() {
		assertTrue(
			WorkflowClasses.of(List.of(FIXTURE + "ObjectFlow"), Projects.classpath())
				.get(0)
				.singleton()
		);
	}

	@Test
	@DisplayName("reads nothing when no Workflow is declared")
	void none() {
		assertEquals(List.of(), WorkflowClasses.of(List.of(), Projects.classpath()));
	}

	@Test
	@DisplayName("refuses a class named as a Workflow that is not one")
	void notAWorkflow() {
		GradleException failure = assertThrows(GradleException.class, () ->
			WorkflowClasses.of(List.of(FIXTURE + "NoTrigger"), Projects.classpath())
		);

		assertTrue(
			failure.getMessage().contains("does not implement WorkflowEntrypoint"),
			failure.getMessage()
		);
	}

	@Test
	@DisplayName("says which setting to check when a Workflow class is not there")
	void missing() {
		GradleException failure = assertThrows(GradleException.class, () ->
			WorkflowClasses.of(List.of("com.example.Absent"), Projects.classpath())
		);

		assertTrue(failure.getMessage().contains("workflows"), failure.getMessage());
	}

	@Test
	@DisplayName("declares the Workflow with the binding Wrangler requires")
	void bindingKeys() {
		Bindings bindings = new Bindings();
		bindings.workflow("com.example.OrderFlow", "WORKFLOW_ORDER_FLOW", "order-flow");

		Binding declared = bindings.getAll().get(0);
		assertEquals("WORKFLOW_ORDER_FLOW", declared.name());
		assertEquals(
			java.util.Map.of(
				"name",
				"order-flow",
				"binding",
				"WORKFLOW_ORDER_FLOW",
				"class_name",
				"OrderFlow"
			),
			declared.toConfig()
		);
	}

	@Test
	@DisplayName(
		"writes the one-argument form with the keys it always had, and numbers a second one"
	)
	void defaultBinding() {
		Bindings bindings = new Bindings();
		bindings.workflow("com.example.First");
		bindings.workflow("com.example.Second");

		assertEquals(
			java.util.List.of("name", "class_name"),
			java.util.List.copyOf(bindings.getAll().get(0).toConfig().keySet())
		);
		assertEquals("WORKFLOW", bindings.getAll().get(0).toConfig().get("name"));
		assertEquals("First", bindings.getAll().get(0).toConfig().get("class_name"));
		assertEquals("WORKFLOW_2", bindings.getAll().get(1).toConfig().get("name"));
		assertEquals("WORKFLOW_2", bindings.getAll().get(1).name());
	}
}
