import { asJavaError, EntryPointError, ImportError, LoadError } from './errors.js';
import {
	createScheduler,
	type DrainOptions,
	type DrainResult,
	type Scheduler,
	type TimePolicy
} from './scheduler.js';

/**
 * The subset of TeaVM's generated `*.wasm-runtime.js` that bytebox drives. Build with
 * `modularRuntime = true` so the file is an ES module rather than a global assignment.
 *
 * Only `defaults` is used. The generated `load` is not: it probes for JS String Builtins by
 * compiling a module from bytes, which workerd refuses at request time; it takes the Node branch
 * on any compatibility date from 2026-08-04 because `nodejs_compat` defines `process`; and it
 * resolves the program's JavaScript imports with a dynamic `import()` of a specifier read out of
 * a wasm custom section, which no bundler can follow.
 */
export interface TeaVMRuntime {
	defaults(
		imports: Record<string, unknown>,
		userExports: Record<string, unknown>,
		stringBuiltinsSupported: boolean
	): { supplyExports(exports: WebAssembly.Exports): void };
}

/** One JavaScript module the compiled program imports. */
export interface RequiredImport {
	/** The specifier, as written in the Java source's `@JSBodyImport(fromModule = ...)`. */
	module: string;
	/** A named export, or `__self__` for the whole namespace. */
	name: string;
}

export interface LoadOptions {
	/** TeaVM's generated runtime module, imported by the caller. */
	runtime: TeaVMRuntime;
	/** The compiled module's bytes, imported as a wrangler `Data` module. */
	bytes: ArrayBuffer | Uint8Array;
	/**
	 * JavaScript modules the compiled program imports, keyed by specifier.
	 *
	 * Each value is the module namespace from a static `import`. Use {@link requiredModules} to
	 * find out which specifiers a binary asks for.
	 *
	 * @example
	 * ```ts
	 * import * as nanoid from 'nanoid';
	 *
	 * load({ runtime, bytes, modules: { nanoid } });
	 * ```
	 */
	modules?: Record<string, unknown>;
	/** Called once per line written to Java's `System.out`. */
	print?: (line: string) => void;
	/** Called once per line written to Java's `System.err`. */
	printErr?: (line: string) => void;
	/** How a fiber scheduled for the future is treated. Defaults to `virtual`. */
	time?: TimePolicy;
	/** Extra or replacement wasm imports, merged after everything else is installed. */
	imports?: Record<string, unknown>;
}

export interface ByteboxModule {
	readonly instance: WebAssembly.Instance;
	readonly module: WebAssembly.Module;
	/** Entry points TeaVM exported, each read through its backing global. */
	readonly exports: Record<string, unknown>;
	/** The fiber queue this module's threads and suspensions run on. */
	readonly scheduler: Scheduler;
	/** Runs one entry point by name, rewrapping a Java throwable as a `JavaError`. */
	call(name: string, ...args: unknown[]): unknown;
	/** Runs every fiber that is due. */
	drain(options?: DrainOptions): DrainResult;
	/** Runs every fiber, waiting real time for one that is not due yet. */
	drainAsync(options?: DrainOptions): Promise<DrainResult>;
}

const MAX_SIZE = (1 << 31) - 1;

/** The name TeaVM gives an import of a whole module namespace. */
const WHOLE_MODULE = '__self__';

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
 * The JavaScript modules a compiled program imports.
 *
 * Read from the `teavm.imports` custom section, which lists every imported wasm global. Some of
 * those are supplied by {@link load} itself; {@link LoadOptions.modules} covers the rest.
 */
export function requiredModules(module: WebAssembly.Module): RequiredImport[] {
	const sections = WebAssembly.Module.customSections(module, 'teavm.imports');
	if (sections.length !== 1) {
		return WebAssembly.Module.imports(module)
			.filter((entry) => entry.kind === 'global')
			.map((entry) => ({ module: entry.module, name: entry.name }));
	}
	try {
		return JSON.parse(new TextDecoder().decode(sections[0]!)) as RequiredImport[];
	} catch {
		return [];
	}
}

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
 * TeaVM's runtime has the same constraint for a second reason. Its JavaScript interop builds
 * `@JSBody` bodies with `new Function`, which workerd allows during module evaluation and answers
 * with `EvalError` in a request.
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
 * 		app.drain();
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
		throw compileError(cause);
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
		// the heap starts past the module's own data segment. Zero would hand linear memory the
		// data segment already occupies, and only a program reaching the heap would ever notice
		heapOffset: new WebAssembly.Global(
			{ value: 'i32', mutable: false },
			alignHeap(requirements.dataSize ?? 0)
		),
		maxSize: new WebAssembly.Global({ value: 'i32', mutable: false }, MAX_SIZE)
	};

	withoutCodegen(imports);

	const scheduler = createScheduler({ policy: options.time });
	imports.teavmAsync = scheduler.imports;
	// so `System.currentTimeMillis()` reads the same clock the queue is scheduled against; TeaVM's
	// own binding is `new Date().getTime()`, which under `virtual` would sit behind the queue
	const date = imports.teavmDate as Record<string, unknown> | undefined;
	if (date) date.currentTimeMillis = () => scheduler.now();

	if (options.print || options.printErr) {
		imports.teavmConsole = lineBuffered(options.print, options.printErr);
	}

	installModules(imports, module, options.modules ?? {});
	if (options.imports) Object.assign(imports, options.imports);

	const missing = missingModules(imports, module);
	if (missing.length > 0) throw new ImportError(missing);

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

	return {
		instance,
		module,
		exports: userExports,
		scheduler,
		call(name, ...args) {
			const fn = userExports[name];
			if (typeof fn !== 'function') throw new EntryPointError(name);
			try {
				return (fn as (...rest: unknown[]) => unknown)(...args);
			} catch (error) {
				throw asJavaError(error);
			}
		},
		drain: (drainOptions) => scheduler.drain(drainOptions),
		drainAsync: (drainOptions) => scheduler.drainAsync(drainOptions)
	};
}

/**
 * Sorts a failed compile into a request-time refusal or anything else.
 *
 * @internal exported so the classification is testable without a workerd request, since the two
 * outcomes are told apart by the engine's message and only one of them is reachable off-platform.
 */
export function compileError(cause: unknown): LoadError {
	const message = String(cause);
	if (message.includes('code generation disallowed')) {
		return new LoadError(
			'load() ran inside a request. workerd allows wasm compilation only during module evaluation, so call load() at module scope.',
			'bytebox.request_time_codegen',
			{ cause }
		);
	}
	return new LoadError(`could not compile the module: ${message}`, 'bytebox.compile', { cause });
}

/**
 * Replaces the interop's global lookup with one that needs no code generation.
 *
 * TeaVM resolves a JavaScript global by name with `new Function("return Promise;")`, which workerd
 * permits during module evaluation and refuses inside a request. Which resolution happens first
 * depends on the route a request takes, so the failure is a latent property of a program rather than
 * something its source shows. Worse, one of the resolutions happens inside a promise rejection
 * handler: throwing there means the handler never resumes the fiber that was waiting, and a failed
 * operation hangs instead of reporting anything.
 *
 * A property walk from `globalThis` answers the same question. It handles the dotted names too,
 * which is the only reason the string form was reached for.
 *
 * @internal exported so the replacements are testable off-platform, where the code generation this
 * avoids is permitted and the failure it prevents cannot be reproduced.
 */
export function withoutCodegen(imports: Record<string, unknown>): void {
	const jso = imports.teavmJso as Record<string, unknown> | undefined;
	if (!jso) return;

	const resolve = (name: string): unknown => {
		let value: unknown = globalThis;
		for (const part of name.split('.')) {
			if (value === null || value === undefined) break;
			value = (value as Record<string, unknown>)[part];
		}
		if (value === undefined) {
			throw new LoadError(
				`the compiled program reached for a JavaScript global named ${JSON.stringify(name)}, which this runtime does not have`,
				'bytebox.unknown_global'
			);
		}
		return value;
	};

	jso.global = resolve;
	jso.getProperty = (target: Record<string, unknown> | null, name: string) =>
		target === null ? resolve(name) : target[name];
	jso.apply = (target: Record<string, unknown> | null, method: string, args: unknown[]) => {
		if (target === null) return (resolve(method) as (...rest: unknown[]) => unknown)(...args);
		return (target[method] as (...rest: unknown[]) => unknown)(...args);
	};
}

/** Wraps each supplied namespace in the per-name globals TeaVM's import table expects. */
function installModules(
	imports: Record<string, unknown>,
	module: WebAssembly.Module,
	modules: Record<string, unknown>
): void {
	for (const required of requiredModules(module)) {
		if (required.module in imports) continue;
		const namespace = modules[required.module];
		if (namespace === undefined) continue;
		const target = (imports[required.module] ??= {}) as Record<string, unknown>;
		const value =
			required.name === WHOLE_MODULE
				? namespace
				: (namespace as Record<string, unknown>)[required.name];
		target[required.name] = new WebAssembly.Global(
			{ value: 'externref', mutable: false },
			value
		);
	}
}

/**
 * Specifiers the module imports that nothing installed.
 *
 * Instantiating without them fails with `Import #30 "some-module": module is not an object or
 * function`, which names an index rather than the specifier a caller has to go and import.
 */
function missingModules(imports: Record<string, unknown>, module: WebAssembly.Module): string[] {
	const missing = new Set<string>();
	for (const required of requiredModules(module)) {
		const supplied = imports[required.module] as Record<string, unknown> | undefined;
		if (supplied === undefined || !(required.name in supplied)) missing.add(required.module);
	}
	return [...missing];
}

/**
 * Rounds a data-segment size up to where the Java heap starts.
 *
 * 256 bytes, matching TeaVM's own loader. Its alignment helper takes a shift rather than a
 * multiple, so the `8` in the generated source means 2^8.
 *
 * @internal exported for the alignment test, since the value it produces is an import the
 * instance gives no way to read back.
 */
export function alignHeap(dataSize: number): number {
	return (((dataSize - 1) >> 8) + 1) << 8;
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
