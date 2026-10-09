package com.example;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.workflow.Step;
import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
import dev.gmitch215.bytebox.workflow.WorkflowEvent;

/**
 * A Workflow with two steps and a sleep between them.
 *
 * <p>The platform replays {@code run} from the top whenever it resumes the instance, and a finished
 * step returns its saved result without running its body. So the clock, randomness and anything with
 * a side effect stay inside {@link Step#run}, and each step keeps the same name on every replay.
 */
public class OrderFlow implements WorkflowEntrypoint {

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		String item = event.payload().get("item").asString();

		TSObject reserved = step.run("reserve", () -> TSObject.of("reserved " + item));
		step.sleep("wait for payment", "2 seconds");
		TSObject receipt = step.run("ship", () ->
			TSObject.of(reserved.asString() + ", shipped for " + event.instanceId())
		);

		TSObject output = TSObject.object();
		output.set("receipt", receipt);
		return output;
	}
}
