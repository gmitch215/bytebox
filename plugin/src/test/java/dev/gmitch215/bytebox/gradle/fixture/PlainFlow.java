package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.workflow.Step;
import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
import dev.gmitch215.bytebox.workflow.WorkflowEvent;

/** A Workflow with a public constructor. */
public class PlainFlow implements WorkflowEntrypoint {

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		return null;
	}
}
