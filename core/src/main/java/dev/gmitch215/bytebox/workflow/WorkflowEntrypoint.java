package dev.gmitch215.bytebox.workflow;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * A Workflow written in Java.
 *
 * <p>Name the class with {@code workflows(...)} and the build writes the JavaScript
 * {@code WorkflowEntrypoint} subclass the platform instantiates, and the binding that starts it.
 * Each invocation of {@link #run} gets a new instance of this class.
 *
 * <p>The platform replays {@code run} from the top to resume an instance, and a step it has already
 * finished returns its saved result without running its body. Everything outside a step runs again
 * on every resume, so keep side effects, the clock and randomness inside {@link Step#run} and name
 * steps the same way every time. Fields do not survive a resume, because the instance can be
 * rebuilt in a fresh isolate.
 *
 * {@snippet lang = "java":
 * public class Onboarding implements WorkflowEntrypoint {
 * 	@Override
 * 	public TSObject run(WorkflowEvent event, Step step, Env env) {
 * 		String id = event.payload().get("id").asString();
 * 		TSObject user = step.run("load user", () -> env.kv().getJson(id));
 * 		step.sleep("wait a day", "1 day");
 * 		return step.run("greet", () -> TSObject.of("welcome " + user.get("name").asString()));
 * 	}
 * }
 *}
 *
 * @since 1.0.3
 */
public interface WorkflowEntrypoint {
	/**
	 * Runs one instance.
	 *
	 * @param event what started the instance
	 * @param step the durable steps
	 * @param env the bindings
	 * @return the instance's output, visible through {@code Instance.status()}; may be {@code null}
	 */
	TSObject run(WorkflowEvent event, Step step, Env env);
}
