import { asJavaError, isByteboxError } from './errors.js';
import { load, type ByteboxModule, type LoadOptions } from './loader.js';
import { FIBER_BUDGET, type DrainOptions } from './scheduler.js';

/**
 * The specifier a compiled program imports to reach the cartridge filesystem.
 *
 * TeaVM has no filesystem of its own, so the Java side reaches this one through a module import.
 * Nothing resolves the specifier on disk; {@link createInterpreter} supplies the namespace
 * directly, and the name only ever appears in the binary's import table.
 *
 * @example
 * ```java
 * @JSBody(
 * 	params = "path",
 * 	imports = @JSBodyImport(alias = "fs", fromModule = "bytebox:fs"),
 * 	script = "return fs.readText(path);"
 * )
 * private static native String readText(String path);
 * ```
 */
export const FS_MODULE = 'bytebox:fs';

/** The filesystem, from the Java side. */
export interface ByteboxFs {
	read(path: string): Uint8Array | null;
	readText(path: string): string | null;
	exists(path: string): boolean;
	list(): string[];
}

/**
 * The filesystem, from the host side.
 *
 * The three writing members are exactly what a cartridge mount calls, so this satisfies its
 * filesystem contract structurally without either package importing the other.
 */
export interface HostFs extends ByteboxFs {
	mkdir(path: string): void;
	writeFile(path: string, data: Uint8Array | string): void;
	utime(path: string, atime: number, mtime: number): void;
	/** What the Java side imports. */
	readonly exports: ByteboxFs;
}

/** Where a run's output goes, one line at a time with the newline stripped. */
export interface InterpreterIo {
	print(line: string): void;
	printErr(line: string): void;
}

/** The two members a cartridge drives. */
export interface Interpreter {
	FS: {
		mkdir(path: string): void;
		writeFile(path: string, data: Uint8Array | string): void;
		utime?(path: string, atime: number, mtime: number): void;
	};
	callMain(argv: string[]): number | void;
}

export interface InterpreterOptions extends Omit<LoadOptions, 'print' | 'printErr'> {
	/** Entry point to call. Defaults to `main`. */
	entryPoint?: string;
	/** Fiber budget for one run. Defaults to the `scheduled` budget. */
	drain?: DrainOptions;
}

export interface ByteboxInterpreter {
	/** The loaded module, for anything this surface does not cover. */
	readonly module: ByteboxModule;
	readonly fs: HostFs;
	/** Pass as a cartridge's `instantiate`. */
	instantiate(io: InterpreterIo): Interpreter;
}

/**
 * Makes a compiled Java program drivable as a cartridge interpreter.
 *
 * Call at module scope: this loads the module, and workerd only allows wasm compilation during
 * module evaluation.
 *
 * A cartridge writes a script and runs it, which for an ahead-of-time compiled program means the
 * script is data rather than code. The program is whatever was compiled; the path a cartridge
 * writes to arrives as the last element of `argv`, and the program reads it back through
 * {@link FS_MODULE}.
 *
 * @example
 * ```ts
 * import { createCartridge } from '@drupflare/cartridge';
 * import { createInterpreter } from 'bytebox/cartridge';
 * import * as runtime from './app.wasm-runtime.js';
 * import bytes from './app.wasmbin';
 *
 * const java = createInterpreter({ runtime, bytes });
 *
 * export const cartridge = createCartridge({
 * 	instantiate: java.instantiate,
 * 	argv: (path) => ['java', path]
 * });
 * ```
 */
export function createInterpreter(options: InterpreterOptions): ByteboxInterpreter {
	const fs = createFs();
	const entryPoint = options.entryPoint ?? 'main';
	const drain = options.drain ?? { budget: FIBER_BUDGET.scheduled };

	// set per run, because a cartridge hands its collectors to `instantiate` and the module has to
	// be loaded before that
	let io: InterpreterIo | undefined;

	const module = load({
		...options,
		modules: { ...options.modules, [FS_MODULE]: fs.exports },
		print: (line) => io?.print(line),
		printErr: (line) => io?.printErr(line)
	});

	return {
		module,
		fs,
		instantiate(collectors) {
			io = collectors;
			return {
				FS: fs,
				callMain(argv) {
					try {
						module.call(entryPoint, argv);
						module.drain(drain);
						return 0;
					} catch (error) {
						const wrapped = asJavaError(error);
						// a JVM prints an uncaught throwable to stderr and exits 1; anything from
						// this package is a host fault and belongs to the caller
						if (!isByteboxError(wrapped) || wrapped.code !== 'bytebox.java')
							throw wrapped;
						collectors.printErr(`Exception in thread "main" ${wrapped.message}`);
						return 1;
					}
				}
			};
		}
	};
}

/** A filesystem in a `Map`, satisfying both sides. */
export function createFs(): HostFs {
	const files = new Map<string, Uint8Array>();
	const encoder = new TextEncoder();
	const decoder = new TextDecoder();

	const read = (path: string) => files.get(path) ?? null;
	const exports: ByteboxFs = {
		read,
		readText(path) {
			const bytes = files.get(path);
			return bytes === undefined ? null : decoder.decode(bytes);
		},
		exists: (path) => files.has(path),
		list: () => [...files.keys()]
	};

	return {
		...exports,
		// a Map has no directories, and pretending otherwise would invite assertions on a
		// hierarchy this does not have
		mkdir() {},
		writeFile(path, data) {
			files.set(path, typeof data === 'string' ? encoder.encode(data) : data);
		},
		utime() {},
		exports
	};
}
