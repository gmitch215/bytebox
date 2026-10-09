import * as email from 'cloudflare:email';
import * as sockets from 'cloudflare:sockets';
import * as workers from 'cloudflare:workers';
import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coreBytes from '../fixtures/core.wasm';
import { counterClass } from '../fixtures/counter.js';
import * as runtime from '../fixtures/hello.wasm-runtime.js';

/**
 * A Worker that calls a Durable Object it hosts itself, wired the way the generated `index.ts` is
 * for a project with a Durable Object: the handlers on one instance of the module and one gate, the
 * Durable Object classes on a second instance and a second gate. With one gate, the handler parked
 * on its Durable Object holds the gate the Durable Object's call needs.
 */
const modules = {
	'cloudflare:sockets': sockets,
	'cloudflare:email': email,
	'cloudflare:workers': workers
};
const options = { runtime, bytes: coreBytes, print: (line: string) => console.log(line), modules };

const java = load(options);
java.call('main', []);
const gate = createGate();

const durable = load(options);
durable.call('main', []);
const durableGate = createGate();

export const Counter = counterClass(durable, durableGate);

/** A Durable Object whose call holds its gate while it awaits another Durable Object. */
export class Relay extends workers.DurableObject {
	fetch(): Promise<Response> {
		const env = this.env as unknown as { DO_COUNTER: DurableObjectNamespace };
		return durableGate.run(() =>
			env.DO_COUNTER.get(env.DO_COUNTER.idFromName('relayed')).fetch('https://counter/built')
		);
	}
}

export default {
	fetch(request: Request, env: { DO_RELAY: DurableObjectNamespace }, ctx: ExecutionContext) {
		const url = new URL(request.url);
		if (url.pathname === '/relay') {
			return env.DO_RELAY.get(env.DO_RELAY.idFromName('relay')).fetch(request);
		}
		return gate.run(async () => {
			const promise = java.call('fetch', request, env, ctx) as Promise<Response>;
			java.drain();
			return await promise;
		});
	}
};
