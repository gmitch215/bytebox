package dev.gmitch215.bytebox.workflow;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.concurrent.Deferred;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.function.Supplier;
import org.teavm.jso.core.JSPromise;

/**
 * What the generated Workflow exports call.
 *
 * <p>Not part of the surface a project writes against. It is public because generated code in
 * another package calls it.
 *
 * @since 1.0.3
 */
public final class WorkflowHandlers {

	private WorkflowHandlers() {}

	/**
	 * Runs one Workflow instance on a fiber.
	 *
	 * @param flow the Workflow
	 * @param event what started the instance
	 * @param step the durable steps
	 * @param env the bindings
	 * @return the output, once {@code run} has returned
	 */
	public static JSPromise<TSObject> run(
		WorkflowEntrypoint flow,
		WorkflowEvent event,
		Step step,
		Env env
	) {
		return run(() -> flow, event, step, env);
	}

	/**
	 * Builds one Workflow instance and runs it, both on the same fiber.
	 *
	 * <p>The form the generated entry point calls, so a constructor that locks, joins or waits works. A
	 * constructor that throws rejects the promise.
	 *
	 * @param factory how to build the Workflow
	 * @param event what started the instance
	 * @param step the durable steps
	 * @param env the bindings
	 * @return the output, once {@code run} has returned
	 * @since 1.0.3
	 */
	public static JSPromise<TSObject> run(
		Supplier<? extends WorkflowEntrypoint> factory,
		WorkflowEvent event,
		Step step,
		Env env
	) {
		Deferred<TSObject> deferred = Deferred.create();
		new Thread(() -> {
			try {
				deferred.resolve(factory.get().run(event, step, env));
			} catch (Throwable failure) {
				deferred.reject(failure);
			}
		}).start();
		return deferred.promise();
	}
}
