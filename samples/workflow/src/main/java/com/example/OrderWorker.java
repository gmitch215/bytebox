package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.Worker;
import dev.gmitch215.bytebox.binding.Workflow;
import dev.gmitch215.bytebox.js.TSObject;

/**
 * Starts the {@link OrderFlow} Workflow and reports on it.
 *
 * <p>{@code /start?item=lamp} creates an instance and answers its id at once, because a Workflow
 * outlives the request that started it. {@code /status?id=...} reads the instance back.
 *
 * <p>The Workflow and this handler share one Worker, so one WebAssembly heap and one gate, and a
 * request that arrives while an instance is inside {@code run} (a sleep included) waits for it. That
 * is fine for a sample and wrong for a Workflow that sleeps for hours, which belongs in a Worker that
 * serves nothing else.
 */
public class OrderWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return switch (request.path()) {
			case "/start" -> {
				TSObject params = TSObject.object();
				params.set("item", TSObject.of(request.query("item", "lamp")));
				Workflow.Instance order = env.workflow().start(params);
				yield Bytebox.response(order.id() + "\n");
			}
			case "/status" -> {
				TSObject status = env.workflow().instance(request.query("id", "")).status();
				yield Bytebox.json(status);
			}
			case "/ping" -> Bytebox.response("pong\n");
			default -> Bytebox.response("try /start?item=lamp, then /status?id=<id>\n", 404);
		};
	}
}
