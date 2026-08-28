import type { Plugin } from 'esbuild';

/** Matches TeaVM's generated runtime, whatever the compiled module was named. */
const TEAVM_RUNTIME = /wasm-runtime\.[cm]?js$/;

const STUB = 'export default {};';

/**
 * Stubs the Node-only imports inside TeaVM's generated runtime.
 *
 * The runtime reads a module off disk with `import('node:fs/promises')` on a branch bytebox never
 * takes, since it drives `defaults` and never the generated `load`. esbuild resolves a dynamic
 * import's literal specifier whether the branch can run or not, so a Workers target with no Node
 * builtins fails the whole bundle on unreachable code.
 *
 * Only imports made by the runtime itself are stubbed, so a project that does use `nodejs_compat`
 * keeps its own Node imports.
 *
 * @example
 * ```ts
 * import { teavmStubs } from 'bytebox/bundler';
 * import { build } from 'esbuild';
 *
 * await build({
 * 	entryPoints: ['src/index.ts'],
 * 	outfile: 'dist/index.mjs',
 * 	bundle: true,
 * 	format: 'esm',
 * 	external: ['*.wasm'],
 * 	plugins: [teavmStubs()]
 * });
 * ```
 */
export function teavmStubs(): Plugin {
	return {
		name: 'bytebox:teavm-stubs',
		setup(build) {
			build.onResolve({ filter: /^node:/ }, (args) => {
				if (!TEAVM_RUNTIME.test(args.importer)) return null;
				return { path: args.path, namespace: 'bytebox-stub' };
			});
			build.onLoad({ filter: /.*/, namespace: 'bytebox-stub' }, () => ({
				contents: STUB,
				loader: 'js'
			}));
		}
	};
}
