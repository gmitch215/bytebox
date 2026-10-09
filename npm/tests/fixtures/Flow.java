package fixture;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.workflow.Step;
import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
import dev.gmitch215.bytebox.workflow.WorkflowEvent;
import java.util.List;

/** A Workflow with two steps, a sleep, and an optional step that fails once before it succeeds. */
public class Flow implements WorkflowEntrypoint {

	private static int attempts;

	private final List<String> built = LockRoutes.blocked();

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		String name = event.payload().get("name").asString();

		TSObject greeting = step.run("greet", () -> TSObject.of("hello " + name));
		step.sleep("pause", "1 second");
		TSObject shout = step.run("shout", () ->
			TSObject.of(greeting.asString().toUpperCase() + " from " + event.instanceId())
		);

		TSObject flaky = TSObject.undefined();
		if (event.payload().get("flaky").asBoolean()) {
			TSObject retries = TSObject.fromJson(
				"{\"retries\":{\"limit\":2,\"delay\":\"1 second\",\"backoff\":\"constant\"}}"
			);
			flaky = step.run("flaky", retries, () -> {
				if (++attempts < 2) throw new IllegalStateException("not yet, attempt " + attempts);
				return TSObject.of("succeeded on attempt " + attempts);
			});
		}

		TSObject output = TSObject.object();
		output.set("greeting", greeting);
		output.set("shout", shout);
		output.set("instance", TSObject.of(event.instanceId()));
		output.set("started", TSObject.of(event.timestamp() > 0));
		output.set("flaky", flaky);
		output.set("built", TSObject.of(String.join("|", built)));
		return output;
	}
}
