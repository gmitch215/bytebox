package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSPromise;

/**
 * A Pipelines binding. Declared with {@code pipeline()}, named {@code PIPELINE} by default.
 *
 * <p>Ingests records for batching into R2. Send, and the pipeline handles the rest.
 *
 * @since 1.0.0
 */
public interface Pipeline extends JSObject {
	/**
	 * Sends records.
	 *
	 * @param records the records
	 */
	default void send(TSObject... records) {
		Async.awaitVoid(sendRecords(JSArray.of(records)));
	}

	/**
	 * Sends one record given as JSON.
	 *
	 * @param json the record, serialised
	 */
	default void sendJson(String json) {
		send(TSObject.fromJson(json));
	}

	@JSMethod("send")
	JSPromise<JSObject> sendRecords(JSArray<TSObject> records);
}
