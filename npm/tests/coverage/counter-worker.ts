import * as email from 'cloudflare:email';
import * as sockets from 'cloudflare:sockets';
import * as workers from 'cloudflare:workers';
import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coverageBytes from '../fixtures/core-coverage.wasm';
import { counterClass } from '../fixtures/counter.js';
import * as runtime from '../fixtures/hello.wasm-runtime.js';

/**
 * The Durable Object's own instrumented module.
 *
 * Its own isolate means its own probes, so the lane reads this one's dump as well and the report
 * merges the two: JaCoCo keys execution data by class id, so the same class recorded in both
 * instances is one entry with the probes ORed together.
 */
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

export const Counter = counterClass(java, gate);

export default {
	fetch(request: Request): Response {
		if (new URL(request.url).pathname === '/__probes') {
			const probes = java.exports.Probes as { dump(): string };
			return new Response(probes.dump(), { headers: { 'content-type': 'text/plain' } });
		}
		return new Response('this worker only hosts the Durable Object', { status: 404 });
	}
};
