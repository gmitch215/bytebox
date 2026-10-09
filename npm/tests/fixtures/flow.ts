import { WorkerEntrypoint, WorkflowEntrypoint } from 'cloudflare:workers';
import type { Gate } from '../../src/gate.js';
import type { ByteboxModule } from '../../src/loader.js';

/**
 * The Workflow class the plugin's scaffold writes, built here so the lane drives the same shape.
 *
 * `run` goes through the gate and into the export the entry point declares for the class, which is
 * what the generated `index.ts` does. It lives in a Worker of its own, as the Durable Object does,
 * because the engine calls it from outside the Worker that started the instance.
 */
export function flowClass(java: ByteboxModule, gate: Gate) {
	return class Flow extends WorkflowEntrypoint {
		run(event: unknown, step: unknown): Promise<unknown> {
			return gate.run(async () => {
				const answer = java.call('workflowFlowRun', event, step, this.env);
				java.drain();
				return await answer;
			});
		}
	};
}

/**
 * Stands in for a Workflow binding that takes the options form of `createBatch`, which the local
 * engine does not yet.
 *
 * Reached as a service binding, so Java calls it as it would the real one. Each error carries what
 * the call received, JSON-encoded in `message`, so a test reads back exactly what crossed.
 */
export class BatchFake extends WorkerEntrypoint {
	createBatch(options: { count?: number; instances?: { id: string }[] }) {
		const received = JSON.stringify(options);
		if (options.instances) {
			const [first, ...rest] = options.instances;
			return {
				created: rest.map((entry) => ({ id: entry.id })),
				errors: [{ index: 0, id: first?.id, code: 10405, message: received }]
			};
		}
		const count = options.count ?? 0;
		return {
			created: Array.from({ length: count - 1 }, (_, i) => ({ id: `fake-${i}` })),
			errors: [{ index: count - 1, code: 10415, message: received }]
		};
	}
}
