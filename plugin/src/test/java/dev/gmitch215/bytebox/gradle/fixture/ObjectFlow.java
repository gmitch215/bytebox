package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.workflow.Step;
import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
import dev.gmitch215.bytebox.workflow.WorkflowEvent;

/** A Workflow that is a Kotlin {@code object}: a private constructor and a public static instance. */
public final class ObjectFlow implements WorkflowEntrypoint {

	public static final ObjectFlow INSTANCE = new ObjectFlow();

	private ObjectFlow() {}

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		return null;
	}
}
