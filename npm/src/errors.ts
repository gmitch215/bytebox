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

/** A Java exception crossed back into JavaScript. */
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

export function isByteboxError(error: unknown): error is ByteboxError {
	return error instanceof ByteboxError;
}
