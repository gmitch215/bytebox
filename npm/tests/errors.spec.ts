import { describe, expect, it } from 'vitest';
import {
	ByteboxError,
	EntryPointError,
	JavaError,
	LoadError,
	isByteboxError
} from '../src/errors.js';

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
