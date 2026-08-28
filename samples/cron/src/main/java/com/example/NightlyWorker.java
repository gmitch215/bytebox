package com.example;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Cron;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Scheduled;
import dev.gmitch215.bytebox.builtin.Clock;

/**
 * A Worker with no HTTP handler at all.
 *
 * <p>Implementing only {@code Scheduled} means the generated Worker exports only {@code scheduled},
 * and the generated configuration carries only the trigger. Nothing is exported for a trigger the
 * handler does not implement.
 *
 * <p>An account gets 5 Cron Triggers on the free plan and 250 on paid, counted across every Worker
 * rather than per Worker. A scheduled invocation gets 15 minutes rather than a request's allowance,
 * and a throw is logged without a retry.
 */
public class NightlyWorker implements Scheduled {

	@Override
	public void scheduled(Cron cron, Env env, ExecutionCtx ctx) {
		Bytebox.log("fired for " + cron.expression() + ", due at " + Clock.iso(cron.scheduledAt()));
		env.kv().put("last-run", Clock.isoNow());
	}
}
