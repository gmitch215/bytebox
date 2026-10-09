import * as email from 'cloudflare:email';
import * as sockets from 'cloudflare:sockets';
import * as workers from 'cloudflare:workers';
import { createGate } from '../../src/gate.js';
import { load } from '../../src/loader.js';
import coreBytes from '../fixtures/core.wasm';
import { flowClass } from '../fixtures/flow.js';
import * as runtime from '../fixtures/hello.wasm-runtime.js';

/** The Workflow's own isolate, holding its own heap and its own gate. */
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

java.call('main', []);

const gate = createGate();

export const Flow = flowClass(java, gate);

export { BatchFake } from '../fixtures/flow.js';

export default {
	fetch(): Response {
		return new Response('this worker only hosts the Workflow', { status: 404 });
	}
};
