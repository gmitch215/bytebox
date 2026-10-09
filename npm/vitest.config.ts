import { cloudflareTest } from '@cloudflare/vitest-pool-workers';
import { join } from 'node:path';
import { defineConfig } from 'vitest/config';
import { ECHO_WORKER } from './tests/fixtures/echo.js';
import { wasmBytes } from './vite-wasm-bytes.js';

const workers = join(import.meta.dirname, 'tests/workers');
const fixtures = join(import.meta.dirname, 'tests/fixtures');
// the tests root, so a bundle and the fixtures share an ancestor and the bundle's relative
// `../fixtures/*.wasm` specifiers resolve to the registered module names
const modulesRoot = join(import.meta.dirname, 'tests');

const WASM = ['hello', 'thrower', 'imported', 'queued', 'sleeper', 'argv', 'core'];

// the installed workerd (1.20261006) serves up to today or its build date plus 7 days; later is refused
const COMPATIBILITY_DATE = '2026-08-22';
const DEFAULT_FLAGS = ['no_nodejs_compat', 'no_nodejs_compat_v2'];
const COMPAT_FLAGS = ['nodejs_compat', 'nodejs_compat_v2'];

const COMPAT_PROBE_WORKER = `export default {
	fetch: () => Response.json({ process: typeof process, nodeVersion: process?.versions?.node ?? null })
}`;

const coreFixture = {
	// the default names the plugin assigns, so the Java accessors resolve without any name being
	// written down
	bindings: { GREETING: 'hello from the environment' },
	kvNamespaces: ['KV'],
	r2Buckets: ['BLOB'],
	d1Databases: ['DB'],
	durableObjects: {
		DO_COUNTER: { className: 'Counter', scriptName: 'bytebox-counter' }
	},
	serviceBindings: {
		SERVICE: 'bytebox-echo',
		MTLS: 'bytebox-echo',
		BROWSER: 'bytebox-echo'
	},
	// everything the fixture fetches lands here rather than on the internet, which is what makes the
	// HTTP surfaces gate-testable
	outboundService: 'bytebox-echo'
};

/**
 * Workers under test, each as its own miniflare service.
 *
 * Not the pool's `main`: the pool evaluates `main` inside the test request, and workerd allows wasm
 * compilation only during a worker's module evaluation. An auxiliary worker has its own startup,
 * which is the shape a deployed bytebox Worker has. `tests/workers/setup.ts` bundles them.
 */
function auxiliary(name: string, bundle: string, extra: Record<string, unknown> = {}) {
	return {
		name,
		compatibilityDate: COMPATIBILITY_DATE,
		// what the generated scaffold emits: Java needs no Node polyfills, and both are default-on
		// from 2026-08-04
		compatibilityFlags: DEFAULT_FLAGS,
		modulesRoot,
		modules: [
			{ type: 'ESModule' as const, path: join(workers, bundle) },
			...WASM.map((wasm) => ({
				type: 'Data' as const,
				path: join(fixtures, `${wasm}.wasm`)
			}))
		],
		...extra
	};
}

/**
 * The lane that records Java coverage from inside workerd, registered only when it was asked for.
 *
 * It drives an instrumented build that Gradle produces, so it cannot run from a checkout with no
 * Java toolchain. Registering it conditionally rather than skipping it keeps a lane that did not run
 * from reading as a lane that passed.
 */
const coverage =
	process.env.BYTEBOX_COVERAGE === '1'
		? [
				{
					test: {
						name: 'coverage',
						include: ['tests/coverage/*.spec.ts'],
						environment: 'node',
						// an instrumented module is twice the size and the socket route dials a
						// real server, so this lane is nothing like the gate's budget
						testTimeout: 60_000,
						execArgv: ['--experimental-wasm-exnref']
					}
				}
			]
		: [];

export default defineConfig({
	test: {
		coverage: {
			// istanbul rather than v8: the v8 provider reads the Node inspector, so it attributes
			// zero from inside workerd while every test in that lane passes
			provider: 'istanbul',
			reporter: ['text', 'json', 'lcov', 'clover'],
			reportsDirectory: './coverage',
			include: ['src/**/*.ts'],
			exclude: ['**/*.d.ts']
		},
		projects: [
			...coverage,
			{
				plugins: [wasmBytes()],
				test: {
					name: 'node',
					include: ['tests/*.spec.ts'],
					environment: 'node',
					// the fiber continuations a threaded module compiles to use the exception
					// handling proposal's try_table, which Node still gates behind a flag
					execArgv: ['--experimental-wasm-exnref']
				}
			},
			{
				plugins: [
					cloudflareTest({
						miniflare: {
							compatibilityDate: COMPATIBILITY_DATE,
							compatibilityFlags: DEFAULT_FLAGS,
							serviceBindings: {
								FIXTURE: 'bytebox-fixture',
								CORE: 'bytebox-core-fixture',
								CORE_COMPAT: 'bytebox-core-compat-fixture',
								COMPAT_PROBE: 'bytebox-compat-probe',
								SELF: 'bytebox-self'
							},
							workers: [
								auxiliary('bytebox-fixture', 'worker.build.mjs'),
								auxiliary('bytebox-core-fixture', 'core-worker.build.mjs', {
									...coreFixture,
									serviceBindings: {
										...coreFixture.serviceBindings,
										FLOW_FAKE: {
											name: 'bytebox-workflow',
											entrypoint: 'BatchFake'
										}
									},
									workflows: {
										FLOW: {
											name: 'bytebox-flow',
											className: 'Flow',
											scriptName: 'bytebox-workflow'
										}
									}
								}),
								// hosts the Workflow class, the way the Durable Object has a Worker of its own
								auxiliary('bytebox-workflow', 'workflow-worker.build.mjs'),
								// the same Worker as a user who opts in to Node compatibility gets it
								auxiliary('bytebox-core-compat-fixture', 'core-worker.build.mjs', {
									...coreFixture,
									compatibilityFlags: COMPAT_FLAGS
								}),
								{
									name: 'bytebox-compat-probe',
									compatibilityDate: COMPATIBILITY_DATE,
									compatibilityFlags: COMPAT_FLAGS,
									modules: true,
									script: COMPAT_PROBE_WORKER
								},
								auxiliary('bytebox-counter', 'counter-worker.build.mjs', {
									// the hosting worker declares the namespace; the caller references it by script
									durableObjects: {
										DO_COUNTER: { className: 'Counter', useSQLite: true }
									}
								}),
								// calls the Durable Objects it hosts itself, as a generated project does
								auxiliary('bytebox-self', 'self-worker.build.mjs', {
									durableObjects: {
										DO_COUNTER: { className: 'Counter', useSQLite: true },
										DO_RELAY: { className: 'Relay', useSQLite: true }
									}
								}),
								{
									name: 'bytebox-echo',
									compatibilityDate: COMPATIBILITY_DATE,
									modules: true,
									script: ECHO_WORKER
								}
							]
						}
					})
				],
				test: {
					name: 'workers',
					include: ['tests/workers/*.spec.ts'],
					globalSetup: ['tests/workers/setup.ts']
				}
			}
		]
	}
});
