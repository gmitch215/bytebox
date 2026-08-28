import { EntryPointError, LoadError } from './errors.js';

/**
 * The subset of TeaVM's generated `*.wasm-runtime.js` that bytebox drives. Build with
 * `modularRuntime = true` so the file is an ES module rather than a global assignment.
 */
export interface TeaVMRuntime {
	defaults(
		imports: Record<string, unknown>,
		userExports: Record<string, unknown>,
		stringBuiltinsSupported: boolean
	): { supplyExports(exports: WebAssembly.Exports): void };
}

export interface LoadOptions {
	/** TeaVM's generated runtime module, imported by the caller. */
	runtime: TeaVMRuntime;
	/** The compiled module's bytes, imported as a wrangler `Data` module. */
	bytes: ArrayBuffer | Uint8Array;
	/** Called once per line written to Java's `System.out`. */
	print?: (line: string) => void;
	/** Called once per line written to Java's `System.err`. */
	printErr?: (line: string) => void;
	/** Extra or replacement wasm imports, merged after the defaults are installed. */
	imports?: Record<string, unknown>;
}

export interface ByteboxModule {
	readonly instance: WebAssembly.Instance;
	readonly module: WebAssembly.Module;
	/** Entry points TeaVM exported, each read through its backing global. */
	readonly exports: Record<string, unknown>;
	/** Runs one entry point by name. */
	call(name: string, ...args: unknown[]): unknown;
	/**
	 * Drives TeaVM's fiber queue until it is idle or `budget` iterations elapse, and
	 * returns how many iterations ran.
	 */
	pump(budget?: number): number;
}

const MAX_SIZE = (1 << 31) - 1;

/**
 * `@cloudflare/workers-types` declares `WebAssembly.Module` as abstract and does not yet carry the
 * JS String Builtins compile option, so the two constructors this file needs are re-declared here.
 */
interface WasmConstructors {
	Module: new (bytes: BufferSource, options?: { builtins?: string[] }) => WebAssembly.Module;
	Instance: new (
		module: WebAssembly.Module,
		imports?: WebAssembly.Imports
	) => WebAssembly.Instance;
}

const Wasm = WebAssembly as unknown as WasmConstructors;

/**
 * Compiles and instantiates a TeaVM WebAssembly GC module.
 *
 * **Call this at module scope.** workerd permits WebAssembly compilation only during module
 * evaluation; the same call inside a request handler throws
 * `CompileError: Wasm code generation disallowed by embedder`. The whole path is synchronous
 * on purpose, because the asynchronous forms are unavailable at module scope: a
 * `WebAssembly.compile` promise never settles there, and `WebAssembly.compileStreaming`
 * refuses outright as asynchronous I/O.
 *
 * @example
 * ```ts
 * import * as runtime from './app.wasm-runtime.js';
 * import bytes from './app.wasmbin';
 * import { load } from 'bytebox/loader';
 *
 * const app = load({ runtime, bytes });
 *
 * export default {
 * 	fetch() {
 * 		app.call('main', []);
 * 		return new Response('ok');
 * 	}
 * };
 * ```
 */
export function load(options: LoadOptions): ByteboxModule {
	const bin = options.bytes instanceof Uint8Array ? options.bytes : new Uint8Array(options.bytes);

	let module: WebAssembly.Module;
	try {
		// `builtins` is not optional: TeaVM's output imports `wasm:js-string`, and two of the
		// seven members take WebAssembly GC arrays that no JavaScript fallback can address.
		module = new Wasm.Module(bin, { builtins: ['js-string'] });
	} catch (cause) {
		const message = String(cause);
		if (message.includes('code generation disallowed')) {
			throw new LoadError(
				'load() ran inside a request. workerd allows wasm compilation only during module evaluation, so call load() at module scope.',
				'bytebox.request_time_codegen',
				{ cause }
			);
		}
		throw new LoadError(`could not compile the module: ${message}`, 'bytebox.compile', {
			cause
		});
	}

	const imports: Record<string, unknown> = {};
	const userExports: Record<string, unknown> = {};
	// true: the engine satisfies wasm:js-string, so TeaVM's partial JS fallback is never installed
	const supply = options.runtime.defaults(imports, userExports, true);

	const requirements = memoryRequirements(module);
	const memory = new WebAssembly.Memory({
		initial: Math.max(1, requirements.min ?? 1),
		maximum: ((MAX_SIZE - 1) >> 16) + 1
	});

	imports.env = { memory };
	imports.teavmMemory = {
		linearMemory: () => memory.buffer,
		notifyHeapResized: () => {},
		heapOffset: new WebAssembly.Global({ value: 'i32', mutable: false }, 0),
		maxSize: new WebAssembly.Global({ value: 'i32', mutable: false }, MAX_SIZE)
	};

	if (options.print || options.printErr) {
		imports.teavmConsole = lineBuffered(options.print, options.printErr);
	}
	if (options.imports) Object.assign(imports, options.imports);

	let instance: WebAssembly.Instance;
	try {
		instance = new Wasm.Instance(module, imports as WebAssembly.Imports);
	} catch (cause) {
		throw new LoadError(`could not instantiate the module: ${cause}`, 'bytebox.instantiate', {
			cause
		});
	}
	supply.supplyExports(instance.exports);

	// TeaVM exports entry points as externref globals holding the callable, not as functions
	for (const [name, value] of Object.entries(instance.exports)) {
		if (value instanceof WebAssembly.Global) {
			Object.defineProperty(userExports, name, { get: () => value.value, enumerable: true });
		}
	}

	const processQueue = instance.exports['teavm_processQueue'] as (() => unknown) | undefined;
	const stopped = instance.exports['teavm_stopped'] as (() => number) | undefined;

	return {
		instance,
		module,
		exports: userExports,
		call(name, ...args) {
			const fn = userExports[name];
			if (typeof fn !== 'function') throw new EntryPointError(name);
			return (fn as (...a: unknown[]) => unknown)(...args);
		},
		pump(budget = 1000) {
			if (!processQueue) return 0;
			let n = 0;
			// teavm_stopped stays false after main returns, so the budget is the real bound
			while (n < budget && !stopped?.()) {
				processQueue();
				n++;
			}
			return n;
		}
	};
}

function memoryRequirements(module: WebAssembly.Module): { min?: number; dataSize?: number } {
	const sections = WebAssembly.Module.customSections(module, 'teavm.memoryRequirements');
	if (sections.length !== 1) return {};
	try {
		return JSON.parse(new TextDecoder().decode(sections[0]!)) as {
			min?: number;
			dataSize?: number;
		};
	} catch {
		return {};
	}
}

function lineBuffered(out?: (line: string) => void, err?: (line: string) => void) {
	let o = '';
	let e = '';
	return {
		putcharStdout(c: number) {
			if (c === 10) {
				out?.(o);
				o = '';
			} else o += String.fromCharCode(c);
		},
		putcharStderr(c: number) {
			if (c === 10) {
				err?.(e);
				e = '';
			} else e += String.fromCharCode(c);
		}
	};
}
