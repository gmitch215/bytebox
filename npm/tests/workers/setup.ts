import { build } from 'esbuild';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { teavmStubs } from '../../src/bundler.js';

const here = dirname(fileURLToPath(import.meta.url));

/**
 * Bundles the Workers under test.
 *
 * They run as auxiliary miniflare workers rather than as the pool's own `main`, because the pool
 * evaluates `main` inside the test request and workerd only allows wasm compilation during a
 * worker's module evaluation. An auxiliary worker is a separate service with its own startup, which
 * is the same shape a deployed bytebox Worker has.
 *
 * The `.wasm` imports stay external so miniflare resolves them as `Data` modules, again matching
 * what the generated scaffold does. Each bundle lands beside its source so those relative
 * specifiers still point at the fixtures.
 */
export default async function setup(): Promise<void> {
	await Promise.all(
		['worker', 'core-worker'].map((name) =>
			build({
				entryPoints: [join(here, `${name}.ts`)],
				outfile: join(here, `${name}.build.mjs`),
				bundle: true,
				format: 'esm',
				target: 'esnext',
				platform: 'neutral',
				external: ['*.wasm'],
				plugins: [teavmStubs()],
				logLevel: 'warning'
			})
		)
	);
}
