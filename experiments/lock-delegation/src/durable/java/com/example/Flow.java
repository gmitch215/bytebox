package com.example;

import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.workflow.Step;
import dev.gmitch215.bytebox.workflow.WorkflowEntrypoint;
import dev.gmitch215.bytebox.workflow.WorkflowEvent;
import java.util.concurrent.CompletableFuture;

/** A Workflow whose constructor joins a future. */
public class Flow implements WorkflowEntrypoint {

	private final int answer;

	public Flow() {
		answer = CompletableFuture.supplyAsync(() -> 9).join();
		System.out.println("flow built " + answer);
	}

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		return TSObject.of("flow " + answer);
	}
}
