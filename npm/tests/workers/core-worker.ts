import * as email from 'cloudflare:email';
import * as sockets from 'cloudflare:sockets';
import * as workers from 'cloudflare:workers';
import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coreBytes from '../fixtures/core.wasm';
import { bindings } from '../fixtures/fakes.js';
import * as runtime from '../fixtures/hello.wasm-runtime.js';
import { triggerHandlers } from '../fixtures/triggers.js';

/**
 * The Java worker from `tests/fixtures/CoreWorker.java`, wired the way a generated scaffold wires
 * one. Module scope, because workerd only compiles wasm during module evaluation.
 */
const java = load({
	runtime,
	bytes: coreBytes,
	print: (line) => console.log(line),
	modules: {
		'cloudflare:sockets': sockets,
		'cloudflare:email': email,
		'cloudflare:workers': workers
	}
});

// runs the program's own static initialisation; nothing about the globals needs warming, because the
// loader resolves those without code generation
java.call('main', []);

// one wasm heap serves every request the isolate takes, and a handler that suspends has yielded to
// the event loop. Without this, a second request resolves the first one's promise from its own
// context and workerd cancels it.
const gate = createGate();

const triggers = triggerHandlers(java, gate, bindings);

export default {
	...triggers.handlers,
	async fetch(request: Request, env: unknown, ctx: ExecutionContext): Promise<Response> {
		// the services with no local emulation are merged over the ones miniflare does provide
		const resolved = { ...(env as object), ...bindings };
		const path = new URL(request.url).pathname;
		if (path.startsWith('/__trigger/')) {
			return triggers.drive(path.slice('/__trigger/'.length), resolved, ctx);
		}
		return gate.run(async () => {
			const promise = java.call('fetch', request, resolved, ctx) as Promise<Response>;
			// starts the handler fiber; anything it queues later drains itself
			java.drain();
			return await promise;
		});
	}
};
