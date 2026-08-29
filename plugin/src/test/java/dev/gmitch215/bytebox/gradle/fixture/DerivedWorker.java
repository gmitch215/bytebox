package dev.gmitch215.bytebox.gradle.fixture;

import dev.gmitch215.bytebox.Cron;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.ExecutionCtx;
import dev.gmitch215.bytebox.Scheduled;

/** Inherits fetch from a base class, which is what makes the hierarchy walk matter. */
public class DerivedWorker extends FetchWorker implements Scheduled {

	@Override
	public void scheduled(Cron cron, Env env, ExecutionCtx ctx) {}
}
