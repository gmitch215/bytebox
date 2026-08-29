import { build } from 'esbuild';
import { Miniflare, convertV4MiniflareOptions } from 'miniflare';
import { mkdirSync, writeFileSync } from 'node:fs';
import { connect } from 'node:net';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { teavmStubs } from '../../src/bundler.js';
import { ECHO_WORKER } from '../fixtures/echo.js';

const here = dirname(fileURLToPath(import.meta.url));
const tests = join(here, '..');
const fixtures = join(tests, 'fixtures');
const bundle = join(here, 'worker.build.mjs');
const counter = join(here, 'counter-worker.build.mjs');

/**
 * Routes whose outbound requests need a base, which this lane serves over a real port.
 *
 * The gate reaches its echo through `outboundService`, which cannot be used here: pointing a
 * Worker's outbound at another Worker routes `connect()` through it as an HTTP CONNECT, and every
 * socket route fails. So the echo runs as its own server and the fixture is told where it is.
 */
const OUTBOUND = ['/fetch', '/urlconnection', '/httpclient'];

/** Routes that answer a 101 rather than a body, which need the header that asks for one. */
const UPGRADE = ['/durablesocket'];

/** Every route the fixture answers, which is what decides how much of core gets recorded. */
const ROUTES = [
	'/url?a=1&b=two',
	'/vars',
	'/kv',
	'/r2',
	'/d1',
	'/async',
	'/resolve',
	'/reject',
	'/builtin',
	'/tsobject',
	'/numbers',
	'/collections',
	'/json',
	'/bindings',
	'/regex',
	'/socket',
	'/jdksocket',
	'/bytes',
	'/rejects',
	'/tls',
	'/hyperdrive',
	'/durable',
	'/durablesocket',
	'/durableseen',
	...OUTBOUND,
	'/services',
	'/overloads',
	'/futures',
	'/__trigger/scheduled',
	'/__trigger/email',
	'/__trigger/queue',
	'/__trigger/tail',
	'/__trigger/alarm',
	'/fired',
	'/missing',
	'/nothing-routed-here'
];

let worker: Miniflare;
let echo: Miniflare;
let echoUrl: string;

/** Each server is a container, so none is listening at the moment docker reports it started. */
async function waitForPort(port: number, what: string): Promise<void> {
	const deadline = Date.now() + 60_000;
	for (;;) {
		const reached = await new Promise<boolean>((resolve) => {
			const probe = connect({ host: '127.0.0.1', port });
			probe.once('connect', () => (probe.destroy(), resolve(true)));
			probe.once('error', () => resolve(false));
		});
		if (reached) return;
		if (Date.now() > deadline) {
			throw new Error(
				`nothing is listening on ${port} for ${what}; run docker/compose.yml first`
			);
		}
		await new Promise((resolve) => setTimeout(resolve, 500));
	}
}

/**
 * Drives the instrumented build and writes out what it recorded.
 *
 * <p>Miniflare is started here rather than through the pool, because the pool runs a spec inside
 * workerd and the probes have to land in a file. This lane is opt-in: without the instrumented
 * fixture there is nothing to drive, so the project is not registered at all rather than skipped.
 */
describe('the instrumented build', () => {
	beforeAll(async () => {
		await Promise.all([
			waitForPort(3025, 'the mail server'),
			waitForPort(5432, 'the database'),
			waitForPort(8443, 'the tls endpoint')
		]);
		await build({
			entryPoints: [join(here, 'worker.ts'), join(here, 'counter-worker.ts')],
			outdir: here,
			outExtension: { '.js': '.build.mjs' },
			bundle: true,
			format: 'esm',
			target: 'esnext',
			platform: 'neutral',
			// the platform supplies these at runtime, so they stay unbundled
			external: ['*.wasm', 'cloudflare:*'],
			plugins: [teavmStubs()],
			logLevel: 'warning'
		});

		// the options are written the way the pool writes an auxiliary worker, and converted, because
		// miniflare 5 changed the shape and ships the converter for exactly this
		worker = new Miniflare(
			convertV4MiniflareOptions({
				workers: [
					{
						name: 'bytebox-core-coverage',
						modulesRoot: tests,
						modules: [
							{ type: 'ESModule', path: bundle },
							{ type: 'Data', path: join(fixtures, 'core-coverage.wasm') }
						],
						compatibilityDate: '2026-08-22',
						compatibilityFlags: ['no_nodejs_compat', 'no_nodejs_compat_v2'],
						// the default names the plugin assigns, matching the uninstrumented lane
						bindings: { GREETING: 'hello from the environment' },
						kvNamespaces: ['KV'],
						r2Buckets: ['BLOB'],
						d1Databases: ['DB'],
						durableObjects: {
							DO_COUNTER: {
								className: 'Counter',
								scriptName: 'bytebox-counter-coverage',
								useSQLite: true
							}
						},
						serviceBindings: {
							SERVICE: 'bytebox-echo',
							MTLS: 'bytebox-echo',
							BROWSER: 'bytebox-echo',
							COUNTER: 'bytebox-counter-coverage'
						}
					},
					{
						name: 'bytebox-counter-coverage',
						modulesRoot: tests,
						modules: [
							{ type: 'ESModule', path: counter },
							{ type: 'Data', path: join(fixtures, 'core-coverage.wasm') }
						],
						compatibilityDate: '2026-08-22',
						compatibilityFlags: ['no_nodejs_compat', 'no_nodejs_compat_v2'],
						durableObjects: { DO_COUNTER: { className: 'Counter', useSQLite: true } }
					},
					{
						name: 'bytebox-echo',
						compatibilityDate: '2026-08-22',
						modules: true,
						script: ECHO_WORKER
					}
				]
			})
		);
		await worker.ready;

		echo = new Miniflare(convertV4MiniflareOptions({ modules: true, script: ECHO_WORKER }));
		echoUrl = (await echo.ready).origin;
	}, 60_000);

	afterAll(async () => {
		await worker?.dispose();
		await echo?.dispose();
	});

	// one test per route, so a route that hangs or fails names itself rather than the whole loop
	it.each(ROUTES)('answers %s', async (route) => {
		const answer = await worker.dispatchFetch(
			OUTBOUND.includes(route)
				? `http://fixture${route}?base=${encodeURIComponent(echoUrl)}`
				: `http://fixture${route}`,
			UPGRADE.includes(route) ? { headers: { upgrade: 'websocket' } } : undefined
		);
		// an upgrade answers 101 with no body to read
		const body = UPGRADE.includes(route) ? '' : await answer.text();

		expect(answer.status, `${route} answered ${answer.status}: ${body}`).toBeLessThan(500);
	});

	// the platform's own socket API and the substituted `java.net.Socket` reach the same server, and
	// only the second one carries the bytes back through linear memory
	it('speaks SMTP through both socket surfaces', async () => {
		const platform = await worker.dispatchFetch('http://fixture/socket');
		const jdk = await worker.dispatchFetch('http://fixture/jdksocket');

		expect(((await platform.json()) as { conversation: string }).conversation).toContain(
			'GreenMail SMTP Service'
		);
		expect(((await jdk.json()) as { jdk: string }).jdk).toContain('GreenMail SMTP Service');
	});

	// the route list can only ask for the upgrade; the handlers behind it need a conversation
	it('carries a websocket conversation into the Durable Object', async () => {
		const upgraded = await worker.dispatchFetch('http://fixture/durablesocket', {
			headers: { upgrade: 'websocket' }
		});
		const socket = upgraded.webSocket!;
		socket.accept();

		const heard: unknown[] = [];
		const done = new Promise<void>((resolve) => {
			socket.addEventListener('message', (event) => {
				heard.push(event.data);
				if (heard.length === 2) resolve();
			});
		});
		socket.send('count');
		socket.send(new Uint8Array([9, 9]));
		await done;
		socket.close(1000, 'done');

		const seen = await (await worker.dispatchFetch('http://fixture/durableseen')).text();

		expect(seen).toContain('text:count');
		expect(seen).toContain('bytes:2');
		expect(seen).toContain('closed:1000');
	});

	it('reports an uncaught Java exception rather than hanging', async () => {
		const answer = await worker.dispatchFetch('http://fixture/throws');

		expect(answer.status).toBe(500);
	});

	it('records probes, and writes them where the report task reads them', async () => {
		const answer = await worker.dispatchFetch('http://fixture/__probes');
		// the Durable Object runs in its own instance, so its probes are its own; JaCoCo keys
		// execution data by class id and ORs two records of one class, so concatenating is the merge
		const inside = await (await worker.dispatchFetch('http://fixture/__durableprobes')).text();
		const dump = (await answer.text()) + inside;

		expect(answer.status, dump).toBe(200);
		const lines = dump.split('\n').filter((line) => line.length > 0);
		expect(lines.length, 'no class recorded a probe').toBeGreaterThan(0);
		expect(
			lines.some((line) => line.includes('1')),
			'every probe read as unexecuted, so nothing was recorded'
		).toBe(true);

		const out = join(tests, '..', 'coverage');
		mkdirSync(out, { recursive: true });
		writeFileSync(join(out, 'workers-probes.txt'), dump);
	});
});
