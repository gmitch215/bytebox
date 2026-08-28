#!/usr/bin/env node
import { pathToFileURL } from 'node:url';
import { writeBindings, type BindgenOptions } from '../bindgen.js';

const USAGE = `usage: bytebox-bindgen --out <dir> [options] <package>...

Reads each package's TypeScript types out of node_modules and writes the Java that binds them.

	--out             where the generated .java files go
	--root            the directory holding node_modules (default: the working directory)
	--java-package    the Java package to generate into (default: dev.gmitch215.bytebox.npm)
	--no-introspect   never import a package to read its exports
`;

interface Parsed extends BindgenOptions {
	out: string;
}

/**
 * Parses the command line.
 *
 * This module is the `bytebox-bindgen` executable and nothing else: it turns arguments into a
 * {@link BindgenOptions}, calls {@link writeBindings}, and prints what came back. Every decision
 * about resolution, type mapping and Java emission lives in `bindgen.ts`, which is what
 * `bytebox/bindgen` exports and what the tests drive. Two files share the name because they are two
 * halves of one tool; the split is what lets the Gradle plugin shell out to a command while a
 * TypeScript caller imports a function.
 *
 * Nothing here is exported to package consumers. It is exported at all so the parser can be tested
 * without running the process.
 *
 * @param argv the arguments, without the runtime or the script
 * @returns what to generate
 * @internal
 */
export function parse(argv: string[]): Parsed {
	const packages: string[] = [];
	let out: string | null = null;
	let root = process.cwd();
	let javaPackage: string | undefined;
	let introspect = true;

	for (let i = 0; i < argv.length; i++) {
		const argument = argv[i] as string;
		const value = argv[i + 1];
		switch (argument) {
			case '--out':
				if (value === undefined) throw new Error('--out needs a directory');
				out = value;
				i++;
				break;
			case '--root':
				if (value === undefined) throw new Error('--root needs a directory');
				root = value;
				i++;
				break;
			case '--java-package':
				if (value === undefined) throw new Error('--java-package needs a name');
				javaPackage = value;
				i++;
				break;
			case '--no-introspect':
				introspect = false;
				break;
			case '--help':
			case '-h':
				throw new Error(USAGE);
			default:
				if (argument.startsWith('-')) throw new Error(`unknown option ${argument}`);
				packages.push(argument);
		}
	}

	if (out === null) throw new Error('--out is required');
	if (packages.length === 0) throw new Error('name at least one package');
	return { out, root, packages, javaPackage, introspect };
}

async function main(): Promise<void> {
	let parsed: Parsed;
	try {
		parsed = parse(process.argv.slice(2));
	} catch (cause) {
		process.stderr.write(`${(cause as Error).message}\n`);
		process.exitCode = 2;
		return;
	}

	const result = await writeBindings(parsed);
	for (const bindings of result.packages) {
		const plural = bindings.members === 1 ? '' : 's';
		process.stdout.write(
			`bytebox: ${bindings.specifier} -> ${bindings.className} ` +
				`(tier ${bindings.tier}, ${bindings.members} member${plural})\n`
		);
		for (const note of bindings.notes) process.stdout.write(`bytebox:   ${note}\n`);
	}
}

// importing this module for its parser must not run it
if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) await main();
