import { build } from 'esbuild';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { teavmStubs } from '../src/bundler.js';

/** Bundles `files` for a Workers target and returns the output, or the error it failed with. */
async function bundle(
	files: Record<string, string>,
	plugins: ReturnType<typeof teavmStubs>[]
): Promise<{ ok: true; code: string } | { ok: false; message: string }> {
	const dir = await mkdtemp(join(tmpdir(), 'bytebox-bundler-'));
	for (const [name, contents] of Object.entries(files)) {
		await writeFile(join(dir, name), contents);
	}
	try {
		const result = await build({
			entryPoints: [join(dir, 'entry.js')],
			bundle: true,
			write: false,
			format: 'esm',
			platform: 'neutral',
			logLevel: 'silent',
			plugins
		});
		return { ok: true, code: result.outputFiles[0]!.text };
	} catch (error) {
		return { ok: false, message: String(error) };
	}
}

const RUNTIME = 'app.wasm-runtime.js';

// the shape of the branch bytebox never takes: a literal `node:` specifier inside a dynamic import
const runtimeSource = `
let cached;
async function readFile() {
	if (!cached) cached = import('node:fs/promises');
	return await cached;
}
export function defaults() { return { supplyExports() {} }; }
export const unreachable = readFile;
`;

describe('bundling TeaVM runtime for a Workers target', () => {
	it('fails without the plugin, because esbuild resolves a dead branch', async () => {
		const result = await bundle(
			{
				'entry.js': `export * from './${RUNTIME}';`,
				[RUNTIME]: runtimeSource
			},
			[]
		);

		expect(result.ok).toBe(false);
		expect(result.ok === false && result.message).toContain('node:fs/promises');
	});

	it('succeeds with the plugin', async () => {
		const result = await bundle(
			{
				'entry.js': `export * from './${RUNTIME}';`,
				[RUNTIME]: runtimeSource
			},
			[teavmStubs()]
		);

		expect(result.ok).toBe(true);
		expect(result.ok === true && result.code).toContain('defaults');
	});

	it('leaves a Node import made by the project itself alone', async () => {
		const result = await bundle(
			{
				'entry.js': "export const fs = import('node:fs/promises');",
				[RUNTIME]: 'export function defaults() {}'
			},
			[teavmStubs()]
		);

		// still refused, because the plugin only covers the generated runtime
		expect(result.ok).toBe(false);
		expect(result.ok === false && result.message).toContain('node:fs/promises');
	});

	it('covers a runtime emitted with either module extension', async () => {
		for (const name of ['app.wasm-runtime.mjs', 'app.wasm-runtime.cjs']) {
			const result = await bundle(
				{
					'entry.js': `export * from './${name}';`,
					[name]: runtimeSource
				},
				[teavmStubs()]
			);

			expect(result.ok, name).toBe(true);
		}
	});
});
