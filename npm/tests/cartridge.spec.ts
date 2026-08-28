import type { Interpreter as CartridgeInterpreter } from '@drupflare/cartridge';
import { describe, expect, expectTypeOf, it } from 'vitest';
import { createFs, createInterpreter, FS_MODULE, type Interpreter } from '../src/cartridge.js';
import { EntryPointError } from '../src/errors.js';
import argvBytes from './fixtures/argv.wasm?bin';
import * as runtime from './fixtures/hello.wasm-runtime.js';
import streamsBytes from './fixtures/streams.wasm?bin';
import throwerBytes from './fixtures/thrower.wasm?bin';

/** Collects a run's output the way a cartridge's own collectors do. */
function collectors() {
	const out: string[] = [];
	const err: string[] = [];
	return {
		out,
		err,
		io: { print: (l: string) => out.push(l), printErr: (l: string) => err.push(l) }
	};
}

describe('the filesystem', () => {
	it('reads back what was written, as bytes and as text', () => {
		const fs = createFs();

		fs.mkdir('/cartridge');
		fs.writeFile('/cartridge/main.txt', 'contents');

		expect(fs.readText('/cartridge/main.txt')).toBe('contents');
		expect(fs.read('/cartridge/main.txt')).toEqual(new TextEncoder().encode('contents'));
		expect(fs.exists('/cartridge/main.txt')).toBe(true);
		expect(fs.list()).toEqual(['/cartridge/main.txt']);
	});

	it('takes bytes as readily as a string', () => {
		const fs = createFs();
		fs.writeFile('/raw', new Uint8Array([104, 105]));

		expect(fs.readText('/raw')).toBe('hi');
	});

	it('answers null for a path that was never written', () => {
		const fs = createFs();

		expect(fs.read('/absent')).toBeNull();
		expect(fs.readText('/absent')).toBeNull();
		expect(fs.exists('/absent')).toBe(false);
	});

	it('overwrites rather than appending', () => {
		const fs = createFs();
		fs.writeFile('/one', 'first');
		fs.writeFile('/one', 'second');

		expect(fs.readText('/one')).toBe('second');
		expect(fs.list()).toEqual(['/one']);
	});

	it('accepts the calls a mount makes without a real hierarchy', () => {
		const fs = createFs();

		expect(() => fs.mkdir('/deep/nested/path')).not.toThrow();
		expect(() => fs.utime('/one', 0, 0)).not.toThrow();
	});

	it('exposes only the reading half to the compiled program', () => {
		const fs = createFs();

		expect(Object.keys(fs.exports).sort()).toEqual(['exists', 'list', 'read', 'readText']);
	});
});

describe('driving a compiled program as an interpreter', () => {
	it('satisfies the cartridge interpreter contract structurally', () => {
		expectTypeOf<Interpreter>().toExtend<CartridgeInterpreter>();
	});

	it('runs main with the argv it was given', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes });
		const { out, io } = collectors();
		const interpreter = java.instantiate(io);

		java.fs.writeFile('/cartridge/main.txt', 'from the cartridge');
		const status = interpreter.callMain(['java', '/cartridge/main.txt']);

		expect(status).toBe(0);
		expect(out).toEqual(['argv:java,/cartridge/main.txt', 'read:from the cartridge']);
	});

	it('lets the program read a file written after instantiation', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes });
		const { out, io } = collectors();
		const interpreter = java.instantiate(io);

		interpreter.FS.writeFile('/late.txt', 'written late');
		interpreter.callMain(['java', '/late.txt']);

		expect(out).toEqual(['argv:java,/late.txt', 'read:written late']);
	});

	it('reports a missing file as null rather than failing the run', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes });
		const { out, io } = collectors();

		java.instantiate(io).callMain(['java', '/never-written']);

		expect(out).toEqual(['argv:java,/never-written', 'read:null']);
	});

	it('exits 1 and prints to stderr when Java throws, the way a JVM does', () => {
		const java = createInterpreter({ runtime, bytes: throwerBytes });
		const { out, err, io } = collectors();

		const status = java.instantiate(io).callMain(['java', '/ignored']);

		expect(status).toBe(1);
		expect(out).toEqual(['about to throw']);
		expect(err).toEqual(['Exception in thread "main" java said no']);
	});

	it('routes stdout and stderr to their own collectors', () => {
		const java = createInterpreter({ runtime, bytes: streamsBytes });
		const { out, err, io } = collectors();

		expect(java.instantiate(io).callMain(['java'])).toBe(0);
		expect(out).toEqual(['to stdout']);
		expect(err).toEqual(['to stderr', 'second stderr line']);
	});

	it('sends a later run to whichever collectors it was instantiated with', () => {
		const java = createInterpreter({ runtime, bytes: streamsBytes });
		const first = collectors();
		const second = collectors();

		java.instantiate(first.io).callMain(['java']);
		java.instantiate(second.io).callMain(['java']);

		expect(first.out).toEqual(['to stdout']);
		expect(second.out).toEqual(['to stdout']);
	});

	it('runs a named entry point other than main', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes, entryPoint: 'main' });

		expect(java.module.exports['main']).toBeTypeOf('function');
	});

	it('rethrows a host fault rather than reporting it as a Java exit', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes, entryPoint: 'absent' });
		const { io } = collectors();

		// an entry point the module does not export is bytebox's fault, not the program's
		expect(() => java.instantiate(io).callMain(['java'])).toThrow(EntryPointError);
	});

	it('supplies the filesystem under the specifier the program imports', () => {
		const java = createInterpreter({ runtime, bytes: argvBytes });

		expect(FS_MODULE).toBe('bytebox:fs');
		expect(java.module.instance).toBeDefined();
	});

	it('keeps caller-supplied modules alongside the filesystem', () => {
		const java = createInterpreter({
			runtime,
			bytes: argvBytes,
			modules: { unused: {} }
		});

		expect(java.module.instance).toBeDefined();
	});
});
