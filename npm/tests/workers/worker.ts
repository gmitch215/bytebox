import { createInterpreter } from '../../src/cartridge.js';
import { ImportError, isByteboxError } from '../../src/errors.js';
import { createGate } from '../../src/gate.js';
import { load, requiredModules } from '../../src/loader.js';
import argvBytes from '../fixtures/argv.wasm';
import helloBytes from '../fixtures/hello.wasm';
import * as runtime from '../fixtures/hello.wasm-runtime.js';
import importedBytes from '../fixtures/imported.wasm';
import queuedBytes from '../fixtures/queued.wasm';
import sleeperBytes from '../fixtures/sleeper.wasm';
import throwerBytes from '../fixtures/thrower.wasm';

// module scope, because workerd allows wasm compilation only during module evaluation. A spec file
// in the workers pool is itself evaluated inside a request, so this is the only place a bytebox
// module can be built.
const out: string[] = [];
const collect = (line: string) => out.push(line);

const hello = load({ runtime, bytes: helloBytes, print: collect });
const thrower = load({ runtime, bytes: throwerBytes, print: collect });
const imported = load({
	runtime,
	bytes: importedBytes,
	print: collect,
	modules: { 'bytebox-test-module': { twice: (n: number) => n * 2 } }
});

const java = createInterpreter({ runtime, bytes: argvBytes });
const interpreter = java.instantiate({ print: collect, printErr: collect });

// each fixture gets its own instance per policy, because a drained queue cannot be re-armed
// without re-entering main
const virtualSleeper = load({ runtime, bytes: sleeperBytes, print: collect });
const realSleeper = load({ runtime, bytes: sleeperBytes, print: collect, time: 'real' });
const queued = load({ runtime, bytes: queuedBytes, print: collect });

let missingImport: string[] | null = null;
try {
	load({ runtime, bytes: importedBytes });
} catch (error) {
	if (error instanceof ImportError) missingImport = [...error.modules];
}

const gate = createGate();

function json(body: unknown): Response {
	return new Response(JSON.stringify(body), {
		headers: { 'content-type': 'application/json' }
	});
}

const routes: Record<string, () => unknown | Promise<unknown>> = {
	'/hello'() {
		out.length = 0;
		hello.call('main', []);
		return { out: [...out] };
	},

	'/thrower'() {
		out.length = 0;
		try {
			thrower.call('main', []);
			return { threw: false };
		} catch (error) {
			return {
				threw: true,
				code: isByteboxError(error) ? error.code : null,
				message: (error as Error).message,
				out: [...out]
			};
		}
	},

	'/imported'() {
		out.length = 0;
		imported.call('main', []);
		return {
			out: [...out],
			required: requiredModules(imported.module),
			missing: missingImport
		};
	},

	'/queued'() {
		out.length = 0;
		queued.call('main', []);
		const beforeDrain = { out: [...out], pending: queued.scheduler.pending() };
		const result = queued.drain();
		return { beforeDrain, afterDrain: { out: [...out] }, result };
	},

	'/sleeper/virtual'() {
		out.length = 0;
		const wallBefore = Date.now();
		const javaBefore = virtualSleeper.scheduler.now();
		virtualSleeper.call('main', []);
		const result = virtualSleeper.drain();
		return {
			out: [...out],
			result,
			wallElapsed: Date.now() - wallBefore,
			// the clock the compiled program reads moves with the queue under `virtual`
			javaElapsed: virtualSleeper.scheduler.now() - javaBefore
		};
	},

	async '/sleeper/real'() {
		out.length = 0;
		realSleeper.call('main', []);
		const sync = realSleeper.drain();
		const async = await realSleeper.drainAsync();
		return { out: [...out], sync, async };
	},

	'/request-time-load'() {
		try {
			load({ runtime, bytes: helloBytes });
			return { refused: false };
		} catch (error) {
			return { refused: true, code: isByteboxError(error) ? error.code : null };
		}
	},

	'/cartridge'() {
		out.length = 0;
		java.fs.mkdir('/cartridge');
		java.fs.writeFile('/cartridge/main.txt', 'from the cartridge');
		const status = interpreter.callMain(['java', '/cartridge/main.txt']);
		return { status, out: [...out], files: java.fs.list() };
	},

	async '/gate'() {
		const order: string[] = [];
		const first = gate.run(async () => {
			order.push('first in');
			await scheduler.wait(1);
			order.push('first out');
		});
		const second = gate.run(() => {
			order.push('second in');
		});
		await Promise.all([first, second]);
		return { order, stats: gate.stats() };
	},

	'/gate/reentrant'() {
		try {
			gate.enter(() => gate.enter(() => 'never'));
			return { refused: false };
		} catch (error) {
			return { refused: true, code: isByteboxError(error) ? error.code : null };
		}
	}
};

export default {
	async fetch(request: Request): Promise<Response> {
		const route = routes[new URL(request.url).pathname];
		if (!route) return new Response('not found', { status: 404 });
		return json(await route());
	}
};
