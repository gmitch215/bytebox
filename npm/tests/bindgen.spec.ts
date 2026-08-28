import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, describe, expect, it } from 'vitest';
import { parse } from '../src/bin/bindgen.js';
import { aliasOf, classNameOf, generateBindings, writeBindings } from '../src/bindgen.js';

const roots: string[] = [];

function root(): string {
	const created = mkdtempSync(join(tmpdir(), 'bytebox-bindgen-'));
	roots.push(created);
	return created;
}

/** Writes a package into a throwaway `node_modules`, so every tier is reached deterministically. */
function pkg(
	where: string,
	name: string,
	manifest: Record<string, unknown>,
	files: Record<string, string>
): void {
	const directory = join(where, 'node_modules', name);
	mkdirSync(directory, { recursive: true });
	writeFileSync(
		join(directory, 'package.json'),
		JSON.stringify({ name, version: '1.0.0', ...manifest })
	);
	for (const [path, content] of Object.entries(files)) {
		const file = join(directory, path);
		mkdirSync(join(file, '..'), { recursive: true });
		writeFileSync(file, content);
	}
}

async function bind(where: string, ...packages: string[]) {
	const result = await generateBindings({ root: where, packages });
	return result.packages;
}

afterAll(() => {
	// the temp trees are small and the OS reclaims them; nothing here writes outside one
	roots.length = 0;
});

describe('the resolution ladder', () => {
	it("reads the package's own declarations at tier 1", async () => {
		const where = root();
		pkg(
			where,
			'typed',
			{ types: 'index.d.ts' },
			{
				'index.d.ts': 'export declare function greet(name: string): string;\n'
			}
		);

		const [bound] = await bind(where, 'typed');
		expect(bound?.tier).toBe(1);
		expect(bound?.source).toContain('public static native String greet(String name);');
	});

	it('falls to DefinitelyTyped at tier 2', async () => {
		const where = root();
		pkg(
			where,
			'untyped',
			{ main: 'index.js' },
			{ 'index.js': 'exports.go = function () {};\n' }
		);
		pkg(
			where,
			'@types/untyped',
			{ types: 'index.d.ts' },
			{
				'index.d.ts': 'export declare function go(count: number): void;\n'
			}
		);

		const [bound] = await bind(where, 'untyped');
		expect(bound?.tier).toBe(2);
		expect(bound?.source).toContain('public static native void go(double count);');
	});

	it('reads JSDoc at tier 3', async () => {
		const where = root();
		pkg(
			where,
			'documented',
			{ main: 'index.js' },
			{
				'index.js':
					'/**\n * @param {string} name\n * @returns {string}\n */\n' +
					"export function hello(name) {\n\treturn 'hi ' + name;\n}\n"
			}
		);

		const [bound] = await bind(where, 'documented');
		expect(bound?.tier).toBe(3);
		expect(bound?.source).toContain('public static native String hello(String name);');
	});

	it('infers from plain JavaScript at tier 4', async () => {
		const where = root();
		pkg(
			where,
			'plain',
			{ main: 'index.js' },
			{
				'index.js': 'module.exports = {\n\tadd(a, b) {\n\t\treturn a + b;\n\t}\n};\n'
			}
		);

		const [bound] = await bind(where, 'plain');
		expect(bound?.tier).toBe(4);
		expect(bound?.source).toContain('add(TSObject a, TSObject b)');
	});

	it('walks the syntax tree at tier 5 when the checker resolves nothing', async () => {
		const where = root();
		pkg(
			where,
			'assigned',
			{ main: 'index.js' },
			{
				'index.js': 'Object.assign(module.exports, {\n\tfoo(a) {\n\t\treturn a;\n\t}\n});\n'
			}
		);

		const [bound] = await bind(where, 'assigned');
		expect(bound?.tier).toBe(5);
		expect(bound?.source).toContain('public static native TSObject foo(TSObject arg0);');
		expect(bound?.notes.join(' ')).toContain('syntax tree');
	});

	it('imports the module at tier 6 when nothing static can see its exports', async () => {
		const where = root();
		pkg(
			where,
			'dynamic',
			{ main: 'index.js' },
			{
				'index.js':
					"for (const name of ['alpha', 'beta']) {\n" +
					'\texports[name] = function (input) {\n\t\treturn name + input;\n\t};\n}\n'
			}
		);

		const [bound] = await bind(where, 'dynamic');
		expect(bound?.tier).toBe(6);
		expect(bound?.source).toContain('alpha(TSObject arg0)');
		expect(bound?.source).toContain('beta(TSObject arg0)');
	});

	it('stops at tier 7 when introspection is refused', async () => {
		const where = root();
		pkg(
			where,
			'dynamic',
			{ main: 'index.js' },
			{
				'index.js':
					"for (const name of ['alpha']) {\n\texports[name] = function () {};\n}\n"
			}
		);

		const result = await generateBindings({
			root: where,
			packages: ['dynamic'],
			introspect: false
		});
		expect(result.packages[0]?.tier).toBe(7);
	});

	it('binds a package that is not installed as the module handle alone', async () => {
		const [bound] = await bind(root(), 'absent');
		expect(bound?.tier).toBe(7);
		expect(bound?.members).toBe(0);
		expect(bound?.source).toContain('public static native TSObject module();');
	});

	it('does not fail on a module that throws while loading', async () => {
		const where = root();
		pkg(
			where,
			'explodes',
			{ main: 'index.js' },
			{
				'index.js': "throw new Error('no');\n"
			}
		);

		const [bound] = await bind(where, 'explodes');
		expect(bound?.tier).toBe(7);
		expect(bound?.source).toContain('public static native TSObject module();');
	});

	it('resolves a subpath export', async () => {
		const where = root();
		pkg(
			where,
			'multi',
			{ exports: { './smtp': { types: './smtp/index.d.ts', import: './smtp/index.js' } } },
			{ 'smtp/index.d.ts': 'export declare function send(to: string): void;\n' }
		);

		const [bound] = await bind(where, 'multi/smtp');
		expect(bound?.tier).toBe(1);
		expect(bound?.className).toBe('MultiSmtp');
		expect(bound?.source).toContain('fromModule = "multi/smtp"');
	});
});

describe("the JSDoc tier's second opinion", () => {
	it('demotes a type its own JavaScript contradicts', async () => {
		const where = root();
		pkg(
			where,
			'stale',
			{ main: 'index.js' },
			{
				'index.js':
					'/**\n * @param {string} count\n * @returns {string}\n */\n' +
					'export function lying(count) {\n\treturn 42;\n}\n'
			}
		);

		const [bound] = await bind(where, 'stale');
		expect(bound?.tier).toBe(3);
		expect(bound?.source).toContain('public static native TSObject lying(String count);');
		expect(bound?.notes.join(' ')).toContain('bound as TSObject');
	});

	it('keeps a type inference agrees with', async () => {
		const where = root();
		pkg(
			where,
			'honest',
			{ main: 'index.js' },
			{
				'index.js':
					'/**\n * @param {string} name\n * @returns {string}\n */\n' +
					"export function hello(name) {\n\treturn 'hi ' + name;\n}\n"
			}
		);

		const [bound] = await bind(where, 'honest');
		expect(bound?.source).toContain('public static native String hello(String name);');
		expect(bound?.notes).toHaveLength(0);
	});

	it('keeps a JSDoc type inference has no opinion about', async () => {
		const where = root();
		pkg(
			where,
			'opaque',
			{ main: 'index.js' },
			{
				'index.js':
					'/**\n * @param {string} name\n */\n' +
					'export function take(name) {\n\tglobalThis.sink = name;\n}\n'
			}
		);

		const [bound] = await bind(where, 'opaque');
		expect(bound?.source).toContain('public static native void take(String name);');
	});
});

describe('the type map', () => {
	async function bound(declaration: string) {
		const where = root();
		pkg(where, 'shapes', { types: 'index.d.ts' }, { 'index.d.ts': declaration });
		const [only] = await bind(where, 'shapes');
		return only?.source ?? '';
	}

	it('maps every scalar TypeScript names', async () => {
		const source = await bound(
			'export declare function f(a: string, b: number, c: boolean, d: bigint): void;\n'
		);
		expect(source).toContain('void f(String a, double b, boolean c, long d)');
	});

	it('maps a nullable reference to the reference and a nullable primitive to TSObject', async () => {
		const source = await bound(
			'export declare function text(value: string | null): string | null;\n' +
				'export declare function count(value: number | undefined): number | undefined;\n'
		);
		expect(source).toContain('native String text(String value)');
		expect(source).toContain('native TSObject count(TSObject value)');
	});

	it('widens a union of unlike types', async () => {
		const source = await bound('export declare function f(a: string | number): void;\n');
		expect(source).toContain('void f(TSObject a)');
	});

	it('keeps a union of one type', async () => {
		const source = await bound("export declare function f(a: 'x' | 'y'): void;\n");
		expect(source).toContain('void f(String a)');
	});

	it('binds a promise as a future over the resolved value', async () => {
		const source = await bound('export declare function load(): Promise<string>;\n');
		expect(source).toContain('private static native JSPromise<TSObject> $load();');
		expect(source).toContain('public static Future<TSObject> load() {');
		expect(source).toContain('return Future.of($load());');
	});

	it('binds an array as a TSObject and names the element type in the doc', async () => {
		const source = await bound('export declare function f(values: string[]): void;\n');
		expect(source).toContain('void f(TSObject values)');
		expect(source).toContain('declared {@code string[]}');
	});

	it('escapes a generic type in the documentation', async () => {
		const source = await bound('export declare function f(): Map<string, number>;\n');
		expect(source).toContain('&lt;string, number&gt;');
		expect(source).not.toMatch(/@return \{@code Map</);
	});
});

describe('signatures', () => {
	async function bound(declaration: string) {
		const where = root();
		pkg(where, 'sigs', { types: 'index.d.ts' }, { 'index.d.ts': declaration });
		const [only] = await bind(where, 'sigs');
		return only;
	}

	it('emits one overload per trailing optional parameter', async () => {
		const source = (await bound('export declare function f(a: string, b?: number): void;\n'))
			?.source;
		expect(source).toContain('void f(String a, double b)');
		expect(source).toContain('void f(String a)');
	});

	it('emits one method per declared overload', async () => {
		const source = (
			await bound(
				'export declare function f(a: string): void;\nexport declare function f(a: number): void;\n'
			)
		)?.source;
		expect(source).toContain('void f(String a)');
		expect(source).toContain('void f(double a)');
	});

	it('binds up to a rest parameter and says so', async () => {
		const only = await bound(
			'export declare function f(a: string, ...rest: number[]): void;\n'
		);
		expect(only?.source).toContain('void f(String a)');
		expect(only?.source).not.toContain('double rest');
		expect(only?.notes.join(' ')).toContain('rest parameter');
	});

	it('does not emit the same signature twice', async () => {
		const source =
			(
				await bound(
					'export declare function f(a: string): void;\n' +
						'export declare function f(a: string): void;\n'
				)
			)?.source ?? '';
		expect(source.split('void f(String a)')).toHaveLength(2);
	});
});

describe('classes and interfaces', () => {
	it('binds a class as a JSO interface plus a constructor', async () => {
		const where = root();
		pkg(
			where,
			'classy',
			{ types: 'index.d.ts' },
			{
				'index.d.ts':
					'export declare class Session {\n' +
					'\tconstructor(host: string);\n' +
					'\treadonly host: string;\n' +
					'\ttimeout: number;\n' +
					'\tsend(payload: string): boolean;\n' +
					'}\n'
			}
		);

		const [bound] = await bind(where, 'classy');
		expect(bound?.source).toContain('public interface Session extends JSObject {');
		expect(bound?.source).toContain('@JSMethod("send")');
		expect(bound?.source).toContain('boolean send(String payload);');
		expect(bound?.source).toContain('@JSProperty("host")');
		expect(bound?.source).toContain('String getHost();');
		expect(bound?.source).toContain('void setTimeout(double value);');
		expect(bound?.source).not.toContain('void setHost(');
		expect(bound?.source).toContain('native Session newSession(String host);');
	});

	it('gives a top-level export a getter and never a setter', async () => {
		const where = root();
		pkg(
			where,
			'vars',
			{ types: 'index.d.ts' },
			{
				'index.d.ts':
					'export declare let counter: number;\nexport declare const name: string;\n'
			}
		);

		const [bound] = await bind(where, 'vars');
		expect(bound?.source).toContain('native double getCounter();');
		expect(bound?.source).toContain('native String getName();');
		expect(bound?.source).not.toContain('setCounter');
	});

	it('names a boolean property accessor with is', async () => {
		const where = root();
		pkg(
			where,
			'flags',
			{ types: 'index.d.ts' },
			{
				'index.d.ts': 'export declare const ready: boolean;\n'
			}
		);

		const [bound] = await bind(where, 'flags');
		expect(bound?.source).toContain('native boolean isReady();');
	});
});

describe('names', () => {
	it('derives a class name from the specifier', () => {
		expect(classNameOf('nanoid')).toBe('Nanoid');
		expect(classNameOf('crypto-js')).toBe('CryptoJs');
		expect(classNameOf('@noble/hashes')).toBe('NobleHashes');
		expect(classNameOf('edgeport/smtp')).toBe('EdgeportSmtp');
		expect(classNameOf('2fa')).toBe('N2fa');
	});

	it('derives a JavaScript identifier for the import alias', () => {
		expect(aliasOf('nanoid')).toBe('nanoid');
		expect(aliasOf('crypto-js')).toBe('crypto_js');
		expect(aliasOf('@noble/hashes')).toBe('noble_hashes');
		expect(aliasOf('2fa')).toBe('_2fa');
	});

	it('does not let a package name become a JavaScript keyword', () => {
		expect(aliasOf('class')).toBe('class_');
		expect(aliasOf('new')).toBe('new_');
	});

	it('renames an export that collides with a Java keyword', async () => {
		const where = root();
		pkg(
			where,
			'keywords',
			{ types: 'index.d.ts' },
			{
				'index.d.ts':
					'export declare function _new(): void;\nexport declare function assert(): void;\n'
			}
		);

		const [bound] = await bind(where, 'keywords');
		expect(bound?.source).toContain('void assert_();');
		expect(bound?.source).toContain('script = "keywords.assert();"');
	});

	it('renames a default export', async () => {
		const where = root();
		pkg(
			where,
			'defaulted',
			{ types: 'index.d.ts' },
			{
				'index.d.ts': 'declare function run(a: string): void;\nexport default run;\n'
			}
		);

		const [bound] = await bind(where, 'defaulted');
		expect(bound?.source).toContain('defaultExport(String a)');
		expect(bound?.source).toContain('script = "defaulted.default(a);"');
	});
});

describe('writing', () => {
	it('puts each class in its Java package', async () => {
		const where = root();
		pkg(
			where,
			'typed',
			{ types: 'index.d.ts' },
			{
				'index.d.ts': 'export declare function greet(): string;\n'
			}
		);
		const out = join(where, 'generated');

		const result = await writeBindings({
			root: where,
			out,
			packages: ['typed'],
			javaPackage: 'com.example.npm'
		});

		const file = join(out, 'com/example/npm/Typed.java');
		expect(readFileSync(file, 'utf8')).toContain('package com.example.npm;');
		expect(result.packages[0]?.javaPackage).toBe('com.example.npm');
	});
});

describe('the command line', () => {
	it('needs an output directory and a package', () => {
		expect(() => parse(['nanoid'])).toThrow('--out is required');
		expect(() => parse(['--out', 'x'])).toThrow('name at least one package');
	});

	it('refuses an option it does not know', () => {
		expect(() => parse(['--out', 'x', '--wat', 'nanoid'])).toThrow('unknown option --wat');
	});

	it('refuses an option with no value', () => {
		expect(() => parse(['--out'])).toThrow('--out needs a directory');
		expect(() => parse(['--out', 'x', 'p', '--root'])).toThrow('--root needs a directory');
		expect(() => parse(['--out', 'x', 'p', '--java-package'])).toThrow(
			'--java-package needs a name'
		);
	});

	it('reads every option', () => {
		const parsed = parse([
			'--out',
			'build/generated',
			'--root',
			'.',
			'--java-package',
			'com.example',
			'--no-introspect',
			'nanoid',
			'@noble/hashes'
		]);
		expect(parsed.out).toBe('build/generated');
		expect(parsed.javaPackage).toBe('com.example');
		expect(parsed.introspect).toBe(false);
		expect(parsed.packages).toEqual(['nanoid', '@noble/hashes']);
	});

	it('prints its usage on request', () => {
		expect(() => parse(['--help'])).toThrow('usage: bytebox-bindgen');
	});
});

describe('a real package', () => {
	it('binds typescript itself from its own declarations', async () => {
		const [bound] = await bind(join(import.meta.dirname, '..'), 'typescript');
		expect(bound?.tier).toBe(1);
		expect(bound?.members).toBeGreaterThan(50);
		expect(bound?.source).toContain('public final class Typescript {');
		expect(bound?.source).toContain('fromModule = "typescript"');
	});
});
