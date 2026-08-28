import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coreBytes from '../fixtures/core.wasm';
import * as runtime from '../fixtures/hello.wasm-runtime.js';

/**
 * The Java worker from `tests/fixtures/CoreWorker.java`, wired the way a generated scaffold wires
 * one. Module scope, because workerd only compiles wasm during module evaluation.
 */
const java = load({ runtime, bytes: coreBytes, print: (line) => console.log(line) });

// runs the program's own static initialisation; nothing about the globals needs warming, because the
// loader resolves those without code generation
java.call('main', []);

// one wasm heap serves every request the isolate takes, and a handler that suspends has yielded to
// the event loop. Without this, a second request resolves the first one's promise from its own
// context and workerd cancels it.
const gate = createGate();

export default {
	async fetch(request: Request, env: unknown, ctx: ExecutionContext): Promise<Response> {
		return gate.run(async () => {
			const promise = java.call('fetch', request, env, ctx) as Promise<Response>;
			// starts the handler fiber; anything it queues later drains itself
			java.drain();
			return await promise;
		});
	}
};
