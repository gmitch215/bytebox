import { cloudflareTest } from '@cloudflare/vitest-pool-workers';
import { join } from 'node:path';
import { defineConfig } from 'vitest/config';
import { wasmBytes } from './vite-wasm-bytes.js';

const workers = join(import.meta.dirname, 'tests/workers');
const fixtures = join(import.meta.dirname, 'tests/fixtures');
// the tests root, so a bundle and the fixtures share an ancestor and the bundle's relative
// `../fixtures/*.wasm` specifiers resolve to the registered module names
const modulesRoot = join(import.meta.dirname, 'tests');

const WASM = ['hello', 'thrower', 'imported', 'queued', 'sleeper', 'argv', 'core'];

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
		compatibilityDate: '2026-08-22',
		// what the generated scaffold emits: Java needs no Node polyfills, and both are default-on
		// from 2026-08-04
		compatibilityFlags: ['no_nodejs_compat', 'no_nodejs_compat_v2'],
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
							compatibilityDate: '2026-08-22',
							compatibilityFlags: ['no_nodejs_compat', 'no_nodejs_compat_v2'],
							serviceBindings: {
								FIXTURE: 'bytebox-fixture',
								CORE: 'bytebox-core-fixture'
							},
							workers: [
								auxiliary('bytebox-fixture', 'worker.build.mjs'),
								auxiliary('bytebox-core-fixture', 'core-worker.build.mjs', {
									// the default names the plugin assigns, so the Java accessors
									// resolve without any name being written down
									bindings: { GREETING: 'hello from the environment' },
									kvNamespaces: ['KV'],
									r2Buckets: ['BLOB'],
									d1Databases: ['DB']
								})
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
