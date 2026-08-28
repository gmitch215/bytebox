/**
 * Base class for every error this package raises. Match on {@link ByteboxError.code}
 * rather than on the message text; codes are stable across versions.
 */
export class ByteboxError extends Error {
	readonly code: string;

	constructor(message: string, code = 'bytebox.error', options?: ErrorOptions) {
		super(message, options);
		this.name = new.target.name;
		this.code = code;
	}
}

/**
 * The module could not be compiled or instantiated.
 *
 * `bytebox.request_time_codegen` is raised when a load is attempted from inside a request.
 * workerd permits WebAssembly compilation only during module evaluation, so a bytebox module
 * has to be loaded at module scope.
 */
export class LoadError extends ByteboxError {
	constructor(message: string, code = 'bytebox.load', options?: ErrorOptions) {
		super(message, code, options);
	}
}

/**
 * The compiled program imports a JavaScript module that {@link load} was not given.
 *
 * Import each named module statically and pass it through `modules`. A static import is what
 * makes the bundle resolvable: the specifier lives in a wasm custom section, which no bundler
 * can follow.
 */
export class ImportError extends ByteboxError {
	/** The specifiers the module asked for and did not get. */
	readonly modules: readonly string[];

	constructor(modules: readonly string[]) {
		const list = modules.map((name) => JSON.stringify(name)).join(', ');
		super(
			`the compiled module imports ${list}, which load() was not given; import each one statically and pass it in \`modules\``,
			'bytebox.missing_import'
		);
		this.modules = [...modules];
	}
}

/**
 * A Java exception crossed back into JavaScript.
 *
 * {@link ByteboxError.cause} carries the wrapper TeaVM's runtime raised, whose `message` reads
 * the Java throwable's own message out of the instance.
 */
export class JavaError extends ByteboxError {
	constructor(message: string, options?: ErrorOptions) {
		super(message, 'bytebox.java', options);
	}
}

/** An entry point named in configuration is absent from the compiled module. */
export class EntryPointError extends ByteboxError {
	constructor(name: string) {
		super(
			`the module exports no entry point named ${JSON.stringify(name)}`,
			'bytebox.no_entry_point'
		);
	}
}

/**
 * A second entry into the module was attempted while the first had not finished.
 *
 * Java code on this runtime runs as fibers over the host timer queue, so a handler that suspends
 * yields to the event loop and the runtime delivers the next request. Guard the module with a
 * {@link Gate} rather than letting two requests share one heap.
 */
export class ReentrancyError extends ByteboxError {
	constructor(label: string) {
		super(
			`${label} was entered while another call was still running; serialise entry through a gate`,
			'bytebox.reentrancy'
		);
	}
}

/**
 * A drain stopped with fibers still queued.
 *
 * The budget counts fibers rather than milliseconds because this runtime exposes no readable CPU
 * meter: `Date.now()` and `performance.now()` are both pinned between I/O, so neither can measure
 * elapsed time inside a request.
 */
export class BudgetError extends ByteboxError {
	/** How many fibers ran before the budget ran out. */
	readonly ran: number;
	/** How many were still queued. */
	readonly pending: number;

	constructor(ran: number, pending: number) {
		super(
			`the fiber budget of ${ran} ran out with ${pending} still queued`,
			'bytebox.budget_exhausted'
		);
		this.ran = ran;
		this.pending = pending;
	}
}

export function isByteboxError(error: unknown): error is ByteboxError {
	return error instanceof ByteboxError;
}

/**
 * The description of the symbol TeaVM's runtime brands its Java exception wrapper with.
 *
 * The symbol is unregistered, so it cannot be recovered with `Symbol.for`; matching on the
 * description is what is left. The wrapper's own class name is minified away and its `name` is
 * plain `Error`, so neither is usable.
 */
const JAVA_EXCEPTION = 'javaException';

/** True when `error` is TeaVM's wrapper around a Java throwable. */
export function isJavaException(error: unknown): boolean {
	if (typeof error !== 'object' || error === null) return false;
	return Object.getOwnPropertySymbols(error).some(
		(symbol) => symbol.description === JAVA_EXCEPTION
	);
}

/**
 * Rewraps a Java throwable as a {@link JavaError}, and returns anything else unchanged.
 *
 * Reading `message` on the wrapper calls back into the instance, so this has to run while the
 * instance is still alive.
 */
export function asJavaError(error: unknown): unknown {
	if (!isJavaException(error)) return error;
	const message = (error as Error).message;
	return new JavaError(message, { cause: error });
}
