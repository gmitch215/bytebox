package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;
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

	/**
	 * Starts up to 100 instances in one call, from the list form Cloudflare deprecates.
	 *
	 * <p>An id that already exists, or that repeats inside the batch, is skipped without a word. Use
	 * {@link #createBatch(TSObject)} to hear about those.
	 *
	 * @param instances one {@link #entry} per instance
	 * @return the instances that were created, in order
	 */
	default List<Instance> createBatch(List<TSObject> instances) {
		TSObject created = Async.await(rawCreateBatch(TSObject.array(instances)));
		List<Instance> all = new ArrayList<>(created.length());
		for (int i = 0; i < created.length(); i++) all.add(instanceOf(created.at(i)));
		return all;
	}

	/**
	 * Starts up to 100 instances in one call, from the options form.
	 *
	 * <p>The object is Cloudflare's own: {@code {count, params, retention}} to start that many
	 * instances with one input, or {@code {instances: [...]}} to give each its own. A bad entry throws
	 * before anything is created; an id that already exists is reported in
	 * {@link BatchResult#errors()} and the rest are created.
	 *
	 * @param options the options object
	 * @return what was created and what was not
	 */
	default BatchResult createBatch(TSObject options) {
		TSObject result = Async.await(rawCreateBatch(options));
		List<Instance> created = new ArrayList<>();
		TSObject made = result.get("created");
		for (int i = 0; i < made.length(); i++) created.add(instanceOf(made.at(i)));
		List<BatchError> errors = new ArrayList<>();
		TSObject failed = result.get("errors");
		for (int i = 0; i < failed.length(); i++) {
			TSObject error = failed.at(i);
			TSObject id = error.get("id");
			errors.add(
				new BatchError(
					error.get("index").asInt(),
					id.isUndefined() || id.isNull() ? null : id.asString(),
					error.get("code").asInt(),
					error.get("message").asString()
				)
			);
		}
		return new BatchResult(created, errors);
	}

	/**
	 * Starts {@code count} instances that share one input.
	 *
	 * @param count how many, from 1 to 100
	 * @param params the input every instance gets
	 * @return what was created and what was not
	 */
	default BatchResult createBatch(int count, TSObject params) {
		TSObject options = TSObject.object();
		options.set("count", TSObject.of(count));
		options.set("params", params);
		return createBatch(options);
	}

	/**
	 * Builds one entry of a batch.
	 *
	 * @param id the instance id
	 * @param params the instance's input
	 * @return an entry for {@link #createBatch(List)} or the {@code instances} of an options object
	 */
	static TSObject entry(String id, TSObject params) {
		TSObject entry = TSObject.object();
		entry.set("id", TSObject.of(id));
		entry.set("params", params);
		return entry;
	}

	private static Instance instanceOf(TSObject value) {
		return (Instance) (JSObject) value;
	}

	@JSMethod("create")
	JSPromise<Instance> create(TSObject options);

	@JSMethod("createBatch")
	JSPromise<TSObject> rawCreateBatch(TSObject batch);

	@JSMethod("get")
	JSPromise<Instance> fetchInstance(String id);

	/**
	 * What {@code createBatch} made.
	 *
	 * @param created the new instances, in input order
	 * @param errors the entries that were not created
	 * @since 1.0.3
	 */
	record BatchResult(List<Instance> created, List<BatchError> errors) {}

	/**
	 * One entry a batch did not create.
	 *
	 * @param index the entry's position in the input
	 * @param id the entry's id, or {@code null} for a count batch
	 * @param code Cloudflare's error code, 10405 for an id that exists and 10415 for one the batch repeats
	 * @param message the reason
	 * @since 1.0.3
	 */
	record BatchError(int index, String id, int code, String message) {}

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
