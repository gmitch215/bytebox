import { readFileSync } from 'node:fs';
import { beforeAll, describe, expect, it } from 'vitest';
import { EntryPointError, LoadError } from '../src/errors.js';
import { load, type TeaVMRuntime } from '../src/loader.js';

// a real module compiled by TeaVM 0.15.0 for the WebAssembly GC target, and its generated runtime
let bytes: Uint8Array;
let runtime: TeaVMRuntime;

beforeAll(async () => {
	bytes = new Uint8Array(readFileSync('tests/fixtures/hello.wasm'));
	runtime = (await import('./fixtures/hello.wasm-runtime.js')) as unknown as TeaVMRuntime;
});

describe('loading a compiled module', () => {
	it('instantiates the real module', () => {
		const module = load({ runtime, bytes });

		expect(module.instance).toBeDefined();
		expect(module.module).toBeDefined();
	});

	it('satisfies the js-string imports from the engine rather than from JavaScript', () => {
		const module = load({ runtime, bytes });
		const unsatisfied = WebAssembly.Module.imports(module.module).filter(
			(i) => i.module === 'wasm:js-string'
		);

		// two of the seven take WebAssembly GC arrays, so a JavaScript fallback cannot supply them
		expect(unsatisfied).toHaveLength(0);
	});

	it('exposes the entry point TeaVM exported through a global', () => {
		const module = load({ runtime, bytes });

		expect(typeof module.exports['main']).toBe('function');
	});

	it('runs the entry point and collects what it printed', () => {
		const lines: string[] = [];
		const module = load({ runtime, bytes, print: (line) => lines.push(line) });

		module.call('main', []);

		expect(lines).toEqual(['hello world!']);
	});

	it('accepts an ArrayBuffer as readily as a view', () => {
		const buffer = bytes.slice().buffer;
		const lines: string[] = [];

		load({ runtime, bytes: buffer, print: (line) => lines.push(line) }).call('main', []);

		expect(lines).toEqual(['hello world!']);
	});

	it('merges caller imports over the defaults', () => {
		let seen = 0;
		const module = load({
			runtime,
			bytes,
			imports: {
				teavmConsole: {
					putcharStdout: () => seen++,
					putcharStderr: () => {}
				}
			}
		});

		module.call('main', []);

		expect(seen).toBeGreaterThan(0);
	});

	it('reads the memory requirements the compiler recorded', () => {
		const module = load({ runtime, bytes });
		const sections = WebAssembly.Module.customSections(
			module.module,
			'teavm.memoryRequirements'
		);

		expect(sections).toHaveLength(1);
	});
});

describe('driving the fiber queue', () => {
	it('exposes a pump bounded by its budget', () => {
		const module = load({ runtime, bytes });

		// teavm_stopped stays false once main has returned, so the budget is what ends the loop
		expect(module.pump(7)).toBe(7);
	});

	it('does nothing when asked for no iterations', () => {
		expect(load({ runtime, bytes }).pump(0)).toBe(0);
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

	it('reports an instantiation failure separately from a compile failure', () => {
		const empty: TeaVMRuntime = {
			defaults: () => ({ supplyExports: () => {} })
		};

		try {
			load({ runtime: empty, bytes });
			expect.unreachable('the module needs imports the empty runtime does not supply');
		} catch (error) {
			expect(error).toBeInstanceOf(LoadError);
			expect((error as LoadError).code).toBe('bytebox.instantiate');
		}
	});
});
