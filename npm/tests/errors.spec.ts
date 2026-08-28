import { describe, expect, it } from 'vitest';
import {
	BudgetError,
	ByteboxError,
	EntryPointError,
	ImportError,
	JavaError,
	LoadError,
	ReentrancyError,
	asJavaError,
	isByteboxError,
	isJavaException
} from '../src/errors.js';

/** The brand TeaVM's runtime puts on its Java exception wrapper. */
function branded(message: string): Error {
	const error = new Error(message);
	Object.defineProperty(error, Symbol('javaException'), { value: 1 });
	return error;
}

describe('the error vocabulary', () => {
	it('carries a stable code on the base class', () => {
		const error = new ByteboxError('something went wrong');

		expect(error.code).toBe('bytebox.error');
		expect(error.name).toBe('ByteboxError');
		expect(error.message).toBe('something went wrong');
	});

	it('takes a code that callers can match on', () => {
		expect(new ByteboxError('x', 'bytebox.custom').code).toBe('bytebox.custom');
	});

	it('names each subclass after itself', () => {
		expect(new LoadError('x').name).toBe('LoadError');
		expect(new JavaError('x').name).toBe('JavaError');
		expect(new EntryPointError('main').name).toBe('EntryPointError');
		expect(new ImportError(['pkg']).name).toBe('ImportError');
		expect(new ReentrancyError('the module').name).toBe('ReentrancyError');
		expect(new BudgetError(1, 2).name).toBe('BudgetError');
	});

	it('defaults a load failure to the load code', () => {
		expect(new LoadError('x').code).toBe('bytebox.load');
	});

	it('distinguishes a request-time compile from any other load failure', () => {
		const error = new LoadError('x', 'bytebox.request_time_codegen');

		expect(error.code).toBe('bytebox.request_time_codegen');
		expect(error).toBeInstanceOf(LoadError);
	});

	it('names the missing entry point in the message', () => {
		const error = new EntryPointError('main');

		expect(error.code).toBe('bytebox.no_entry_point');
		expect(error.message).toContain('"main"');
	});

	it('keeps the cause when one is given', () => {
		const cause = new Error('underlying');

		expect(new LoadError('x', 'bytebox.load', { cause }).cause).toBe(cause);
	});

	it('recognises its own errors and nothing else', () => {
		expect(isByteboxError(new JavaError('x'))).toBe(true);
		expect(isByteboxError(new EntryPointError('main'))).toBe(true);
		expect(isByteboxError(new Error('x'))).toBe(false);
		expect(isByteboxError(null)).toBe(false);
		expect(isByteboxError('bytebox.error')).toBe(false);
	});

	it('is catchable as an Error', () => {
		expect(() => {
			throw new LoadError('x');
		}).toThrow(Error);
	});
});

describe('a missing module import', () => {
	it('lists every specifier in the message and on the error', () => {
		const error = new ImportError(['nanoid', 'pako']);

		expect(error.code).toBe('bytebox.missing_import');
		expect(error.modules).toEqual(['nanoid', 'pako']);
		expect(error.message).toContain('"nanoid"');
		expect(error.message).toContain('"pako"');
	});

	it('copies the list rather than holding the array it was given', () => {
		const modules = ['nanoid'];
		const error = new ImportError(modules);
		modules.push('pako');

		expect(error.modules).toEqual(['nanoid']);
	});
});

describe('a reentrant entry', () => {
	it('names what was entered twice', () => {
		const error = new ReentrancyError('the module');

		expect(error.code).toBe('bytebox.reentrancy');
		expect(error.message).toContain('the module');
	});
});

describe('an exhausted budget', () => {
	it('carries both counts', () => {
		const error = new BudgetError(100, 7);

		expect(error.code).toBe('bytebox.budget_exhausted');
		expect(error.ran).toBe(100);
		expect(error.pending).toBe(7);
		expect(error.message).toContain('100');
		expect(error.message).toContain('7');
	});
});

describe('recognising a Java throwable', () => {
	it('matches the symbol TeaVM brands its wrapper with', () => {
		expect(isJavaException(branded('boom'))).toBe(true);
	});

	it('does not match an ordinary error', () => {
		expect(isJavaException(new Error('boom'))).toBe(false);
		expect(isJavaException(new JavaError('boom'))).toBe(false);
	});

	it('does not match a value that is not an object', () => {
		expect(isJavaException(null)).toBe(false);
		expect(isJavaException(undefined)).toBe(false);
		expect(isJavaException('boom')).toBe(false);
		expect(isJavaException(7)).toBe(false);
	});

	it('does not match an object carrying some other symbol', () => {
		const error = new Error('boom');
		Object.defineProperty(error, Symbol('somethingElse'), { value: 1 });

		expect(isJavaException(error)).toBe(false);
	});

	it('rewraps a Java throwable, keeping the message and the original', () => {
		const original = branded('java said no');
		const wrapped = asJavaError(original);

		expect(wrapped).toBeInstanceOf(JavaError);
		expect((wrapped as JavaError).message).toBe('java said no');
		expect((wrapped as JavaError).code).toBe('bytebox.java');
		expect((wrapped as JavaError).cause).toBe(original);
	});

	it('passes anything else through untouched', () => {
		const plain = new Error('host fault');

		expect(asJavaError(plain)).toBe(plain);
		expect(asJavaError('a string')).toBe('a string');
	});
});
