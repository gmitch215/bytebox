import { describe, expect, it } from 'vitest';
import { EntryPointError, ImportError, JavaError, LoadError } from '../src/errors.js';
import {
	alignHeap,
	compileError,
	load,
	requiredModules,
	withoutCodegen,
	type LoadOptions,
	type TeaVMRuntime
} from '../src/loader.js';
import argvBytes from './fixtures/argv.wasm?bin';
import directBytes from './fixtures/direct.wasm?bin';
import * as runtime from './fixtures/hello.wasm-runtime.js';
import bytes from './fixtures/hello.wasm?bin';
import importedBytes from './fixtures/imported.wasm?bin';
import streamsBytes from './fixtures/streams.wasm?bin';
import throwerBytes from './fixtures/thrower.wasm?bin';

// `@cloudflare/workers-types` declares the constructor abstract, so hand-assembled modules in the
// cases below need the same cast the loader makes
const Wasm = WebAssembly as unknown as {
	Module: new (bytes: BufferSource) => WebAssembly.Module;
};

const HEADER = [0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00];
/** An import section declaring one global, `"m"."g"` of type i32. */
const ONE_GLOBAL_IMPORT = [0x02, 0x08, 0x01, 0x01, 0x6d, 0x01, 0x67, 0x03, 0x7f, 0x00];

/** Assembles a module carrying one custom section, sized rather than hand-counted. */
function withSection(name: string, payload: string, ...sections: number[]): Uint8Array {
	const encoded = [...new TextEncoder().encode(name)];
	const body = [encoded.length, ...encoded, ...new TextEncoder().encode(payload)];
	return new Uint8Array([...HEADER, 0x00, body.length, ...body, ...sections]);
}

/** Loads a fixture, collecting whatever it printed. */
function run(options: Omit<LoadOptions, 'runtime'>) {
	const lines: string[] = [];
	const module = load({ ...options, runtime, print: (line) => lines.push(line) });
	return { module, lines };
}

describe('loading a compiled module', () => {
	it('instantiates the real module', () => {
		const module = load({ runtime, bytes });

		expect(module.instance).toBeDefined();
		expect(module.module).toBeDefined();
	});

	it('satisfies the js-string imports from the engine rather than from JavaScript', () => {
		const module = load({ runtime, bytes });
		const unsatisfied = WebAssembly.Module.imports(module.module).filter(
			(entry) => entry.module === 'wasm:js-string'
		);

		// two of the seven take WebAssembly GC arrays, so a JavaScript fallback cannot supply them
		expect(unsatisfied).toHaveLength(0);
	});

	it('exposes the entry point TeaVM exported through a global', () => {
		const module = load({ runtime, bytes });

		expect(typeof module.exports['main']).toBe('function');
	});

	it('runs the entry point and collects what it printed', () => {
		const { module, lines } = run({ bytes });

		module.call('main', []);

		expect(lines).toEqual(['hello world!']);
	});

	it('accepts an ArrayBuffer as readily as a view', () => {
		const { module, lines } = run({ bytes: bytes.slice().buffer });

		module.call('main', []);

		expect(lines).toEqual(['hello world!']);
	});

	it('separates stdout from stderr', () => {
		const out: string[] = [];
		const err: string[] = [];
		load({
			runtime,
			bytes: streamsBytes,
			print: (l) => out.push(l),
			printErr: (l) => err.push(l)
		}).call('main', []);

		expect(out).toEqual(['to stdout']);
		expect(err).toEqual(['to stderr', 'second stderr line']);
	});

	it('collects stderr on its own when only that sink is given', () => {
		const err: string[] = [];
		load({ runtime, bytes: streamsBytes, printErr: (l) => err.push(l) }).call('main', []);

		expect(err).toEqual(['to stderr', 'second stderr line']);
	});

	it('discards output when no sink is given', () => {
		expect(() => load({ runtime, bytes: streamsBytes }).call('main', [])).not.toThrow();
	});

	it('starts the heap past the data segment, 256-aligned like TeaVM does', () => {
		expect(alignHeap(0)).toBe(0);
		expect(alignHeap(1)).toBe(256);
		expect(alignHeap(256)).toBe(256);
		expect(alignHeap(257)).toBe(512);
		// the direct-buffer fixture records dataSize 619
		expect(alignHeap(619)).toBe(768);
	});

	it('cannot run a program that allocates a direct ByteBuffer, which is upstream', () => {
		// reproduced against TeaVM 0.15.0's own generated loader as well, so this pins an upstream
		// limitation rather than a bytebox defect. `minDirectBuffersSize` does not move the
		// recorded requirement either.
		const module = load({ runtime, bytes: directBytes });

		expect(() => module.call('main', [])).toThrow(JavaError);
	});

	it('merges caller imports over everything it installed', () => {
		let seen = 0;
		const module = load({
			runtime,
			bytes,
			imports: {
				teavmConsole: { putcharStdout: () => seen++, putcharStderr: () => {} }
			}
		});

		module.call('main', []);

		expect(seen).toBeGreaterThan(0);
	});

	it('reads the memory requirements the compiler recorded', () => {
		const module = load({ runtime, bytes });

		expect(
			WebAssembly.Module.customSections(module.module, 'teavm.memoryRequirements')
		).toHaveLength(1);
	});

	it('gives the compiled program the scheduler clock', () => {
		const module = load({ runtime, bytes });

		expect(module.scheduler.now()).toBeGreaterThan(0);
		expect(module.scheduler.pending()).toBe(0);
	});
});

describe('the modules a program imports', () => {
	it('lists them from the custom section the compiler wrote', () => {
		const module = load({
			runtime,
			bytes: importedBytes,
			modules: { 'bytebox-test-module': { twice: (n: number) => n * 2 } }
		});

		expect(requiredModules(module.module)).toEqual(
			expect.arrayContaining([{ module: 'bytebox-test-module', name: '__self__' }])
		);
	});

	it('resolves a whole-namespace import from what the caller supplied', () => {
		const { module, lines } = run({
			bytes: importedBytes,
			modules: { 'bytebox-test-module': { twice: (n: number) => n * 2 } }
		});

		module.call('main', []);

		expect(lines).toEqual(['twice(21) = 42']);
	});

	it('names the specifier when nothing supplied it', () => {
		try {
			load({ runtime, bytes: importedBytes });
			expect.unreachable('the module imports a namespace nothing supplied');
		} catch (error) {
			expect(error).toBeInstanceOf(ImportError);
			expect((error as ImportError).code).toBe('bytebox.missing_import');
			expect((error as ImportError).modules).toEqual(['bytebox-test-module']);
			expect((error as ImportError).message).toContain('bytebox-test-module');
		}
	});

	it('lets caller imports satisfy a module directly', () => {
		const { module, lines } = run({
			bytes: importedBytes,
			imports: {
				'bytebox-test-module': {
					__self__: new WebAssembly.Global(
						{ value: 'externref', mutable: false },
						{ twice: (n: number) => n + n }
					)
				}
			}
		});

		module.call('main', []);

		expect(lines).toEqual(['twice(21) = 42']);
	});

	it('falls back to the import table when the section is absent', () => {
		const module = new Wasm.Module(new Uint8Array([...HEADER, ...ONE_GLOBAL_IMPORT]));

		expect(requiredModules(module)).toEqual([{ module: 'm', name: 'g' }]);
	});

	it('reports nothing rather than throwing when the section is malformed', () => {
		const module = new Wasm.Module(withSection('teavm.imports', '{'));

		expect(requiredModules(module)).toEqual([]);
	});
});

describe('a Java exception crossing back out', () => {
	it('arrives as a JavaError carrying the Java message', () => {
		const { module, lines } = run({ bytes: throwerBytes });

		try {
			module.call('main', []);
			expect.unreachable('the fixture throws');
		} catch (error) {
			expect(error).toBeInstanceOf(JavaError);
			expect((error as JavaError).code).toBe('bytebox.java');
			expect((error as JavaError).message).toBe('java said no');
			expect((error as JavaError).cause).toBeInstanceOf(Error);
		}
		expect(lines).toEqual(['about to throw']);
	});
});

describe('refusing bad input', () => {
	it('reports a module that is not WebAssembly at all', () => {
		expect(() => load({ runtime, bytes: new Uint8Array([1, 2, 3, 4]) })).toThrow(LoadError);
	});

	it('uses the compile code for a malformed module', () => {
		try {
			load({ runtime, bytes: new Uint8Array([0, 0, 0, 0]) });
			expect.unreachable('a malformed module should not load');
		} catch (error) {
			expect(error).toBeInstanceOf(LoadError);
			expect((error as LoadError).code).toBe('bytebox.compile');
		}
	});

	it('names an entry point the module does not export', () => {
		const module = load({ runtime, bytes });

		expect(() => module.call('absent')).toThrow(EntryPointError);
	});

	it('reads through a malformed memory-requirements section rather than failing on it', () => {
		// the load still fails, but on the unsatisfied import rather than on the bad section,
		// which is what proves the section was skipped instead of fatal
		const malformed = withSection('teavm.memoryRequirements', '{', ...ONE_GLOBAL_IMPORT);

		try {
			load({ runtime, bytes: malformed });
			expect.unreachable('the module imports a global nothing supplies');
		} catch (error) {
			expect((error as LoadError).code).toBe('bytebox.missing_import');
		}
	});

	it('tells a request-time refusal apart from any other compile failure', () => {
		const refusal = compileError(
			new Error('CompileError: Wasm code generation disallowed by embedder')
		);
		const other = compileError(new Error('CompileError: expected magic word'));

		expect(refusal.code).toBe('bytebox.request_time_codegen');
		expect(refusal.message).toContain('module scope');
		expect(other.code).toBe('bytebox.compile');
		expect(other.message).toContain('expected magic word');
	});

	it('reports an instantiation failure separately from a compile failure', () => {
		const empty: TeaVMRuntime = { defaults: () => ({ supplyExports: () => {} }) };

		try {
			load({ runtime: empty, bytes });
			expect.unreachable('the module needs imports the empty runtime does not supply');
		} catch (error) {
			expect(error).toBeInstanceOf(LoadError);
			expect((error as LoadError).code).toBe('bytebox.instantiate');
		}
	});
});

describe('driving the queue through the module', () => {
	it('reports an empty queue as drained', () => {
		const module = load({ runtime, bytes: argvBytes, modules: { 'bytebox:fs': {} } });

		expect(module.drain()).toMatchObject({ ran: 0, drained: true });
	});

	it('drains asynchronously as readily as synchronously', async () => {
		const module = load({ runtime, bytes: argvBytes, modules: { 'bytebox:fs': {} } });

		await expect(module.drainAsync()).resolves.toMatchObject({ drained: true });
	});
});

describe('resolving JavaScript globals without code generation', () => {
	interface Jso {
		global(name: string): unknown;
		getProperty(target: Record<string, unknown> | null, name: string): unknown;
		apply(target: Record<string, unknown> | null, method: string, args: unknown[]): unknown;
	}

	/** Patches a bare interop object the way `load` patches the runtime's. */
	function patched(): Jso {
		const imports: Record<string, unknown> = { teavmJso: {} };
		withoutCodegen(imports);
		return imports.teavmJso as unknown as Jso;
	}

	it('resolves a plain global by name', () => {
		expect(patched().global('JSON')).toBe(JSON);
	});

	it('walks a dotted name, which is why the string form existed', () => {
		expect(patched().global('JSON.stringify')).toBe(JSON.stringify);
		expect(patched().global('Intl.NumberFormat')).toBe(Intl.NumberFormat);
	});

	it('names a global the runtime does not have', () => {
		try {
			patched().global('NoSuchGlobalHere');
			expect.unreachable('an absent global should be reported');
		} catch (error) {
			expect(error).toBeInstanceOf(LoadError);
			expect((error as LoadError).code).toBe('bytebox.unknown_global');
			expect((error as LoadError).message).toContain('NoSuchGlobalHere');
		}
	});

	it('stops walking rather than throwing past a missing segment', () => {
		expect(() => patched().global('JSON.nope.deeper')).toThrow(LoadError);
	});

	it('reads a property off a target, and a global when there is none', () => {
		const jso = patched();

		expect(jso.getProperty({ port: 8787 }, 'port')).toBe(8787);
		expect(jso.getProperty(null, 'JSON')).toBe(JSON);
	});

	it('calls a method on a target, and a global function when there is none', () => {
		const jso = patched();
		const target = { twice: (n: number) => n * 2 };

		expect(jso.apply(target, 'twice', [21])).toBe(42);
		expect(jso.apply(null, 'JSON.stringify', [{ a: 1 }])).toBe('{"a":1}');
	});

	it('does nothing when the runtime installed no interop', () => {
		const imports: Record<string, unknown> = {};

		expect(() => withoutCodegen(imports)).not.toThrow();
		expect(imports).toEqual({});
	});
});
