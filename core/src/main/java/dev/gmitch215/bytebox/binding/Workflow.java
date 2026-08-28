package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

/**
 * A Workflows binding. Declared with {@code workflow()}, named {@code WORKFLOW} by default.
 *
 * <p>A workflow outlives the request that started it and survives a restart, so this binding starts
 * one and asks after it rather than running it.
 *
 * @since 1.0.0
 */
public interface Workflow extends JSObject {
	/**
	 * Starts an instance, letting Cloudflare pick the id.
	 *
	 * @param params the workflow's input
	 * @return the new instance
	 */
	default Instance start(TSObject params) {
		TSObject options = TSObject.object();
		options.set("params", params);
		return Async.await(create(options));
	}

	/**
	 * Starts an instance with an id of your own, which makes the call idempotent.
	 *
	 * @param id the instance id
	 * @param params the workflow's input
	 * @return the new instance
	 */
	default Instance start(String id, TSObject params) {
		TSObject options = TSObject.object();
		options.set("id", TSObject.of(id));
		options.set("params", params);
		return Async.await(create(options));
	}

	/**
	 * Looks up a running or finished instance.
	 *
	 * @param id the instance id
	 * @return the instance
	 */
	default Instance instance(String id) {
		return Async.await(fetchInstance(id));
	}

	@JSMethod("create")
	JSPromise<Instance> create(TSObject options);

	@JSMethod("get")
	JSPromise<Instance> fetchInstance(String id);

	/**
	 * One workflow instance.
	 *
	 * @since 1.0.0
	 */
	interface Instance extends JSObject {
		/** {@return the instance id} */
		@org.teavm.jso.JSProperty
		String getId();

		/** {@return the instance's status and output} */
		default TSObject status() {
			return Async.await(readStatus());
		}

		/** Pauses the instance. */
		default void pause() {
			Async.awaitVoid(doPause());
		}

		/** Resumes a paused instance. */
		default void resume() {
			Async.awaitVoid(doResume());
		}

		/** Stops the instance for good. */
		default void terminate() {
			Async.awaitVoid(doTerminate());
		}

		/**
		 * Delivers an event the workflow is waiting for.
		 *
		 * @param type the event type the workflow named
		 * @param payload the event's payload
		 */
		default void sendEvent(String type, TSObject payload) {
			TSObject event = TSObject.object();
			event.set("type", TSObject.of(type));
			event.set("payload", payload);
			Async.awaitVoid(doSendEvent(event));
		}

		@JSMethod("status")
		JSPromise<TSObject> readStatus();

		@JSMethod("pause")
		JSPromise<JSObject> doPause();

		@JSMethod("resume")
		JSPromise<JSObject> doResume();

		@JSMethod("terminate")
		JSPromise<JSObject> doTerminate();

		@JSMethod("sendEvent")
		JSPromise<JSObject> doSendEvent(TSObject event);

		/** {@return the instance id, for a caller that only wants the string} */
		default String id() {
			JSString value = JSString.valueOf(getId());
			return value.stringValue();
		}
	}
}
