import { describe, expect, it } from 'vitest';
import type { RequiredImport } from '../../src/loader.js';
import { drive } from './drive.js';

describe('a compiled module on workerd', () => {
	it('runs its entry point and reports what it printed', async () => {
		expect(await drive('/hello')).toEqual({ out: ['hello world!'] });
	});

	it('refuses a load from inside a request, naming the reason', async () => {
		expect(await drive('/request-time-load')).toEqual({
			refused: true,
			code: 'bytebox.request_time_codegen'
		});
	});
});

describe('a Java exception crossing back out', () => {
	it('arrives as a JavaError carrying the Java message', async () => {
		const result = await drive<{
			threw: boolean;
			code: string;
			message: string;
			out: string[];
		}>('/thrower');

		expect(result.threw).toBe(true);
		expect(result.code).toBe('bytebox.java');
		expect(result.message).toBe('java said no');
		// everything printed before the throw still arrived
		expect(result.out).toEqual(['about to throw']);
	});
});

describe('JavaScript modules the program imports', () => {
	it('resolves them from the statically supplied namespaces', async () => {
		const result = await drive<{
			out: string[];
			required: RequiredImport[];
			missing: string[];
		}>('/imported');

		expect(result.out).toEqual(['twice(21) = 42']);
		expect(result.required).toEqual(
			expect.arrayContaining([{ module: 'bytebox-test-module', name: '__self__' }])
		);
	});

	it('names the specifier when one was not supplied', async () => {
		const result = await drive<{ missing: string[] }>('/imported');

		expect(result.missing).toEqual(['bytebox-test-module']);
	});
});
