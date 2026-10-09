package dev.gmitch215.bytebox.workflow;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.concurrent.Deferred;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.concurrent.Callable;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * The durable steps of a Workflow, which is the {@code step} argument of {@code run}.
 *
 * <p>Each call blocks the instance's fiber until the platform has the step's result. A step the
 * platform has already finished returns its saved result and its body does not run, which is what
 * makes a replay cheap. A body that throws fails the attempt, and the platform retries it according
 * to the config.
 *
 * @since 1.0.3
 */
public interface Step extends JSObject {
	/**
	 * Runs a body as a durable step.
	 *
	 * <p>The body runs on its own fiber, so it may call any binding. What it returns is stored, so it
	 * has to be a structured-cloneable value of at most 1 MiB.
	 *
	 * @param name the step's name; use the same one on every replay, at most 256 characters
	 * @param body the work
	 * @return what the body returned
	 */
	default TSObject run(String name, Callable<TSObject> body) {
		return Async.await(doStep(name, Body.of(body)));
	}

	/**
	 * Runs a body as a durable step with retries and a timeout.
	 *
	 * @param name the step's name
	 * @param config Cloudflare's step config, such as
	 *     {@code {"retries": {"limit": 3, "delay": "5 seconds"}, "timeout": "1 minute"}}
	 * @param body the work
	 * @return what the body returned
	 */
	default TSObject run(String name, TSObject config, Callable<TSObject> body) {
		return Async.await(doStep(name, config, Body.of(body)));
	}

	/**
	 * Sleeps without holding the instance.
	 *
	 * @param name the step's name
	 * @param duration a duration the platform reads, such as {@code "30 seconds"} or {@code "1 day"}
	 */
	default void sleep(String name, String duration) {
		Async.awaitVoid(doSleep(name, duration));
	}

	@JSMethod("do")
	JSPromise<TSObject> doStep(String name, Body body);

	@JSMethod("do")
	JSPromise<TSObject> doStep(String name, TSObject config, Body body);

	@JSMethod("sleep")
	JSPromise<JSObject> doSleep(String name, String duration);

	/**
	 * The callback the platform calls for a step.
	 *
	 * <p>JavaScript calls this with a JavaScript frame on the stack, where a call that suspends cannot
	 * run. So it starts the body on a fiber and answers with a promise, the way a trigger's entry
	 * point does.
	 */
	@JSFunctor
	interface Body extends JSObject {
		JSPromise<TSObject> run(JSObject context);

		/**
		 * @param body the Java body
		 * @return a callback running it
		 */
		static Body of(Callable<TSObject> body) {
			return context -> {
				Deferred<TSObject> deferred = Deferred.create();
				new Thread(() -> {
					try {
						deferred.resolve(body.call());
					} catch (Throwable failure) {
						deferred.reject(failure);
					}
				}).start();
				return deferred.promise();
			};
		}
	}
}
