import * as email from 'cloudflare:email';
import * as sockets from 'cloudflare:sockets';
import * as workers from 'cloudflare:workers';
import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coverageBytes from '../fixtures/core-coverage.wasm';
import { bindings } from '../fixtures/fakes.js';
import * as runtime from '../fixtures/hello.wasm-runtime.js';
import { triggerHandlers } from '../fixtures/triggers.js';

/**
 * The same worker `tests/workers/core-worker.ts` runs, compiled from an instrumented core.
 *
 * One extra route reads the probes back out. It sits ahead of the gate because it records nothing
 * and waits on nothing, and it has to answer after the handler fibers have finished rather than
 * queue behind them.
 */
// the platform's own modules, not stand-ins: the socket route dials a real mail server with them
const java = load({
	runtime,
	bytes: coverageBytes,
	print: (line) => console.log(line),
	modules: {
		'cloudflare:sockets': sockets,
		'cloudflare:email': email,
		'cloudflare:workers': workers
	}
});

java.call('main', []);

const gate = createGate();

const triggers = triggerHandlers(java, gate, bindings);

export default {
	...triggers.handlers,
	async fetch(request: Request, env: unknown, ctx: ExecutionContext): Promise<Response> {
		const pathname = new URL(request.url).pathname;
		// the Durable Object holds its own instance, so its probes are read through it
		if (pathname === '/__durableprobes') {
			const counter = (env as { COUNTER: Fetcher }).COUNTER;
			return counter.fetch('http://counter/__probes');
		}
		if (pathname === '/__probes') {
			// only the main class's own exports land at the top level; anything else is grouped under
			// the class that declared it, so this is `Probes.dump` rather than a bare `dump`
			const probes = java.exports.Probes as { dump(): string };
			return new Response(probes.dump(), {
				headers: { 'content-type': 'text/plain' }
			});
		}
		// the services with no local emulation are merged over the ones miniflare does provide
		const resolved = { ...(env as object), ...bindings };
		if (pathname.startsWith('/__trigger/')) {
			return triggers.drive(pathname.slice('/__trigger/'.length), resolved, ctx);
		}
		return gate.run(async () => {
			const promise = java.call('fetch', request, resolved, ctx) as Promise<Response>;
			java.drain();
			return await promise;
		});
	}
};
