package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.JSArrays;
import dev.gmitch215.bytebox.js.TSObject;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSPromise;

/**
 * A Queue producer. Declared with {@code queue()}, named {@code QUEUE} by default.
 *
 * <p>Sending is the producer half; {@link dev.gmitch215.bytebox.Consumer} is the other. A body is
 * capped at 128 KB and has to be a structured-clone value, so a JavaScript function or a class
 * instance cannot be sent.
 *
 * @since 1.0.0
 */
public interface Queue extends JSObject {
	/**
	 * Sends one message.
	 *
	 * @param body the body, at most 128 KB once serialised
	 */
	default void send(TSObject body) {
		Async.awaitVoid(sendMessage(body));
	}

	/**
	 * Sends one message as JSON.
	 *
	 * @param json the body, serialised
	 */
	default void sendJson(String json) {
		send(TSObject.fromJson(json));
	}

	/**
	 * Sends one message after a delay.
	 *
	 * @param body the body
	 * @param delaySeconds how long to hold it before delivery
	 */
	default void send(TSObject body, int delaySeconds) {
		Async.awaitVoid(sendMessage(body, Options.delaySeconds(delaySeconds)));
	}

	/**
	 * Sends several messages in one call, which counts as one write rather than several.
	 *
	 * @param bodies the bodies
	 */
	default void sendBatch(TSObject... bodies) {
		TSObject[] messages = new TSObject[bodies.length];
		for (int i = 0; i < bodies.length; i++) {
			TSObject message = TSObject.object();
			message.set("body", bodies[i]);
			messages[i] = message;
		}
		Async.awaitVoid(sendMessages(JSArrays.of(messages)));
	}

	@JSMethod("send")
	JSPromise<JSObject> sendMessage(TSObject body);

	@JSMethod("send")
	JSPromise<JSObject> sendMessage(TSObject body, JSObject options);

	@JSMethod("sendBatch")
	JSPromise<JSObject> sendMessages(JSArray<TSObject> messages);
}
