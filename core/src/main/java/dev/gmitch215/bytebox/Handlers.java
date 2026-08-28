package dev.gmitch215.bytebox;

import dev.gmitch215.bytebox.concurrent.Deferred;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSPromise;

/**
 * What a generated entry point calls.
 *
 * <p>The plugin emits one exported static method per trigger the handler class implements, and each
 * is a single call into here. Nothing in this class is meant to be called by hand, but the shape is
 * worth knowing because it explains how a blocking handler works at all.
 *
 * <p>Each method starts the handler on a fiber and hands JavaScript a promise straight away. The
 * fiber is what makes suspension legal, since a stack with a JavaScript frame in it cannot suspend,
 * and the promise is what tells the host when the handler has genuinely finished rather than merely
 * parked. The host drains its queue to start the fiber, then awaits the promise.
 *
 * @since 1.0.0
 */
public final class Handlers {

	private Handlers() {}

	/**
	 * Runs a {@link Worker}.
	 *
	 * @param worker the handler
	 * @param request the incoming request
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return the response, once the handler has produced one
	 */
	public static JSPromise<Response> fetch(
		Worker worker,
		Request request,
		Env env,
		ExecutionCtx ctx
	) {
		Deferred<Response> deferred = Deferred.create();
		start(deferred, () -> worker.fetch(request, env, ctx));
		return deferred.promise();
	}

	/**
	 * Runs a {@link Scheduled}.
	 *
	 * @param scheduled the handler
	 * @param controller the Cron Trigger that fired
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> scheduled(
		Scheduled scheduled,
		ScheduledController controller,
		Env env,
		ExecutionCtx ctx
	) {
		Cron cron = new Cron(controller.getCron(), (long) controller.getScheduledTime());
		return complete(() -> scheduled.scheduled(cron, env, ctx));
	}

	/**
	 * Runs a {@link Mail}.
	 *
	 * <p>Raises when the handler returns without acting on the message, because Cloudflare drops an
	 * unacted message silently. {@link InboundMail#drop()} is how a handler says the silence was
	 * meant.
	 *
	 * @param mail the handler
	 * @param message the incoming message
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> email(
		Mail mail,
		MailMessage message,
		Env env,
		ExecutionCtx ctx
	) {
		InboundMailImpl inbound = new InboundMailImpl(message);
		return complete(() -> {
			mail.email(inbound, env, ctx);
			if (inbound.disposition() == MailAction.NONE) {
				throw new IllegalStateException(
					"the email handler returned without forwarding, replying, rejecting or dropping" +
						" the message, which would drop it silently"
				);
			}
		});
	}

	/**
	 * Runs a {@link Consumer}.
	 *
	 * @param consumer the handler
	 * @param batch the delivered batch
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> queue(
		Consumer<TSObject> consumer,
		QueueBatch batch,
		Env env,
		ExecutionCtx ctx
	) {
		return complete(() -> consumer.queue(new MessageBatchImpl(batch), env, ctx));
	}

	/**
	 * Runs a {@link Tail}.
	 *
	 * @param tail the handler
	 * @param events the trace events
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> tail(
		Tail tail,
		org.teavm.jso.core.JSArrayReader<TraceItem> events,
		Env env,
		ExecutionCtx ctx
	) {
		List<TraceItem> items = new java.util.ArrayList<>(events.getLength());
		for (int i = 0; i < events.getLength(); i++) items.add(events.get(i));
		return complete(() -> tail.tail(items, env, ctx));
	}

	/**
	 * Runs an {@link Alarm}.
	 *
	 * @param alarm the handler
	 * @param env the bindings
	 * @param ctx the invocation context
	 * @return a promise that settles when the handler returns
	 */
	public static JSPromise<JSObject> alarm(Alarm alarm, Env env, ExecutionCtx ctx) {
		return complete(() -> alarm.alarm(env, ctx));
	}

	private static JSPromise<JSObject> complete(Runnable work) {
		Deferred<JSObject> deferred = Deferred.create();
		start(deferred, () -> {
			work.run();
			return null;
		});
		return deferred.promise();
	}

	private static <T extends JSObject> void start(
		Deferred<T> deferred,
		java.util.function.Supplier<T> work
	) {
		new Thread(() -> {
			try {
				deferred.resolve(work.get());
			} catch (Throwable failure) {
				deferred.reject(failure);
			}
		}).start();
	}
}
