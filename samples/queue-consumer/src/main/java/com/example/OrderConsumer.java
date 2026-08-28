package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Consumer;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Message;
import dev.gmitch215.bytebox.MessageBatch;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * A Queue consumer that acknowledges each message separately.
 *
 * <p>An uncaught exception retries the whole batch, which is rarely what a partial failure wants.
 * Acknowledging and retrying per message is how one bad message stops holding up the rest.
 *
 * <p>A body is any structured-clone value, so it arrives as a {@code TSObject}. A body with a known
 * shape can be read with a codec instead; see the {@code @JSONType} annotation.
 */
public class OrderConsumer implements Consumer<TSObject> {

	@Override
	public void queue(MessageBatch<TSObject> batch, Env env, ExecutionCtx ctx) {
		var insert = env.d1().prepare("insert into orders (id, sku) values (?, ?)");

		for (Message<TSObject> message : batch.messages()) {
			try {
				TSObject body = message.body();
				insert.bind(message.id(), body.get("sku").asString()).run();
				message.ack();
			} catch (RuntimeException failure) {
				Bytebox.log("order " + message.id() + " failed: " + failure.getMessage());
				// a fourth attempt is unlikely to go differently, so give up rather than loop
				if (message.attempts() >= 3) message.ack();
				else message.retry(30);
			}
		}
	}
}
