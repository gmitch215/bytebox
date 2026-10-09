package fixture;

import dev.gmitch215.bytebox.Bytebox;
import dev.gmitch215.bytebox.Env;
import dev.gmitch215.bytebox.Request;
import dev.gmitch215.bytebox.Response;
import dev.gmitch215.bytebox.binding.Workflow;
import dev.gmitch215.bytebox.concurrent.Async;
import dev.gmitch215.bytebox.js.TSObject;
import java.util.ArrayList;
import java.util.List;

/** The routes that start Workflows and read them back, kept apart from the rest of the fixture. */
final class WorkflowRoutes {

	private WorkflowRoutes() {}

	static Response route(Request request, Env env) {
		return switch (request.path()) {
			case "/workflowrun" -> run(request, env);
			case "/workflowbatch" -> batch(request, env);
			case "/workflowengine" -> engine(request, env);
			default -> engine(request, env);
		};
	}

	/**
	 * The options form of createBatch: a count with shared params, instances with ids, then the same
	 * ids again, and one that exists with one that does not.
	 */
	private static Response engine(Request request, Env env) {
		String id = request.query("id", "engine");
		Workflow flow = env.workflow("FLOW");
		TSObject params = TSObject.object();
		params.set("name", TSObject.of("lin"));
		params.set("flaky", TSObject.of(false));

		TSObject answer = TSObject.object();
		Workflow.BatchResult counted = flow.createBatch(2, params);
		answer.set("count", describe(counted));
		answer.set("countStatuses", statuses(counted.created()));

		TSObject instances = TSObject.array();
		instances.push(Workflow.entry(id + "-a", params));
		instances.push(Workflow.entry(id + "-b", params));
		TSObject options = TSObject.object();
		options.set("instances", instances);
		Workflow.BatchResult listed = flow.createBatch(options);
		answer.set("instances", describe(listed));
		answer.set("instanceStatuses", statuses(listed.created()));

		answer.set("again", describe(flow.createBatch(options)));

		TSObject mixed = TSObject.array();
		mixed.push(Workflow.entry(id + "-a", params));
		mixed.push(Workflow.entry(id + "-c", params));
		TSObject mixedOptions = TSObject.object();
		mixedOptions.set("instances", mixed);
		Workflow.BatchResult partial = flow.createBatch(mixedOptions);
		answer.set("mixed", describe(partial));
		answer.set("mixedStatuses", statuses(partial.created()));
		return Bytebox.json(answer);
	}

	private static TSObject statuses(List<Workflow.Instance> created) {
		TSObject out = TSObject.array();
		for (Workflow.Instance instance : created) out.push(settled(instance).get("status"));
		return out;
	}

	/** Starts an instance on the real engine and waits for it to finish. */
	private static Response run(Request request, Env env) {
		TSObject params = TSObject.object();
		params.set("name", TSObject.of("ada"));
		params.set("flaky", TSObject.of(request.query("flaky", "false").equals("true")));

		Workflow.Instance instance = env.workflow("FLOW").start(request.query("id", "run"), params);
		TSObject status = settled(instance);

		TSObject answer = TSObject.object();
		answer.set("id", TSObject.of(instance.id()));
		answer.set("status", status.get("status"));
		answer.set("output", status.get("output"));
		answer.set("error", status.get("error"));
		return Bytebox.json(answer);
	}

	/** The list form of createBatch, which the local engine accepts. */
	private static Response batch(Request request, Env env) {
		String id = request.query("id", "batch");
		TSObject params = TSObject.object();
		params.set("name", TSObject.of("grace"));
		params.set("flaky", TSObject.of(false));

		List<Workflow.Instance> created = env
			.workflow("FLOW")
			.createBatch(List.of(Workflow.entry(id + "-1", params), Workflow.entry(id + "-2", params)));

		TSObject ids = TSObject.array();
		for (Workflow.Instance instance : created) ids.push(TSObject.of(instance.id()));
		TSObject answer = TSObject.object();
		answer.set("created", ids);
		answer.set("finished", settled(created.get(0)).get("status"));
		return Bytebox.json(answer);
	}

	private static TSObject describe(Workflow.BatchResult result) {
		TSObject ids = TSObject.array();
		for (Workflow.Instance instance : result.created()) ids.push(TSObject.of(instance.id()));
		List<TSObject> errors = new ArrayList<>();
		for (Workflow.BatchError error : result.errors()) {
			TSObject one = TSObject.object();
			one.set("index", TSObject.of(error.index()));
			one.set("id", error.id() == null ? TSObject.nullValue() : TSObject.of(error.id()));
			one.set("code", TSObject.of(error.code()));
			one.set("message", TSObject.of(error.message()));
			errors.add(one);
		}
		TSObject described = TSObject.object();
		described.set("created", ids);
		described.set("errors", TSObject.array(errors));
		return described;
	}

	/** Polls until the instance leaves queued and running, for up to 20 seconds. */
	private static TSObject settled(Workflow.Instance instance) {
		TSObject status = instance.status();
		for (int tries = 0; tries < 80 && !finished(status); tries++) {
			Async.sleep(250);
			status = instance.status();
		}
		return status;
	}

	private static boolean finished(TSObject status) {
		String state = status.get("status").asString();
		return state.equals("complete") || state.equals("errored") || state.equals("terminated");
	}
}
