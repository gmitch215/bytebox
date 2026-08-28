import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import * as ts from 'typescript';

/**
 * Where a package's types came from.
 *
 * Resolution walks these in order and stops at the first that answers, so a package with no types of
 * its own still binds. Tier 7 is the floor and cannot fail: whatever the module turns out to be, it
 * binds as one `TSObject` and the caller drives it by name.
 *
 * | Tier | Source                                        | What you get               |
 * | ---- | --------------------------------------------- | -------------------------- |
 * | 1    | the package's own `.d.ts`                      | full types                 |
 * | 2    | `@types/<pkg>` from DefinitelyTyped            | full types                 |
 * | 3    | JSDoc-annotated `.js`, demoted on disagreement | most types                 |
 * | 4    | inference over plain `.js`                     | partial types              |
 * | 5    | the syntax tree alone                          | names and arity            |
 * | 6    | importing the module and reading it            | names and arity            |
 * | 7    | the module itself                              | one `TSObject`             |
 *
 * Tiers 1 through 4 share one code path and differ only in compiler flags. Tier 5 walks the syntax
 * tree for names the checker did not resolve, and tier 6 imports the module and reads its own
 * properties, which is the only thing that sees exports a package assembles at run time.
 *
 * @since 1.0.0
 */
export type Tier = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/**
 * What to generate.
 *
 * @since 1.0.0
 */
export interface BindgenOptions {
	/** Package specifiers, each optionally naming a subpath export: `nanoid`, `edgeport/smtp`. */
	packages: string[];
	/** The directory holding `node_modules`. */
	root: string;
	/** The Java package the generated classes live in. */
	javaPackage?: string;
	/**
	 * Whether to import a package whose exports no static analysis could see, and read them off the
	 * module object. Defaults to on.
	 *
	 * Installing a package already runs its own install scripts, so importing one is not a new trust
	 * boundary. It is still the only tier that executes third-party code, and turning this off is the
	 * way to say a build may not.
	 */
	introspect?: boolean;
}

/**
 * One package's generated binding.
 *
 * @since 1.0.0
 */
export interface PackageBindings {
	/** The specifier asked for. */
	specifier: string;
	/** The generated class's simple name. */
	className: string;
	/** The Java package it lives in. */
	javaPackage: string;
	/** Which tier answered. */
	tier: Tier;
	/** How many methods, properties and nested types were bound. */
	members: number;
	/** Anything a reader should know: a demoted type, a dropped rest parameter, a collision. */
	notes: string[];
	/** The generated Java source. */
	source: string;
}

/**
 * What was generated.
 *
 * @since 1.0.0
 */
export interface BindgenResult {
	/** One entry per requested package, in the order asked for. */
	packages: PackageBindings[];
}

const DEFAULT_JAVA_PACKAGE = 'dev.gmitch215.bytebox.npm';

const JAVA_RESERVED = new Set([
	'abstract',
	'assert',
	'boolean',
	'break',
	'byte',
	'case',
	'catch',
	'char',
	'class',
	'const',
	'continue',
	'default',
	'do',
	'double',
	'else',
	'enum',
	'extends',
	'final',
	'finally',
	'float',
	'for',
	'goto',
	'if',
	'implements',
	'import',
	'instanceof',
	'int',
	'interface',
	'long',
	'native',
	'new',
	'package',
	'private',
	'protected',
	'public',
	'return',
	'short',
	'static',
	'strictfp',
	'super',
	'switch',
	'synchronized',
	'this',
	'throw',
	'throws',
	'transient',
	'try',
	'void',
	'volatile',
	'while',
	'_',
	'true',
	'false',
	'null',
	'record',
	'sealed',
	'permits',
	'var',
	'yield'
]);

const JS_RESERVED = new Set([
	'await',
	'break',
	'case',
	'catch',
	'class',
	'const',
	'continue',
	'debugger',
	'default',
	'delete',
	'do',
	'else',
	'enum',
	'export',
	'extends',
	'false',
	'finally',
	'for',
	'function',
	'if',
	'import',
	'in',
	'instanceof',
	'new',
	'null',
	'return',
	'super',
	'switch',
	'this',
	'throw',
	'true',
	'try',
	'typeof',
	'var',
	'void',
	'while',
	'with',
	'yield',
	'let',
	'static',
	'implements',
	'interface',
	'package',
	'private',
	'protected',
	'public'
]);

interface Param {
	name: string;
	java: string;
	ts: string;
}

interface Returns {
	java: string;
	ts: string;
	promise: boolean;
}

interface Method {
	javaName: string;
	jsName: string;
	params: Param[];
	returns: Returns;
	construct: boolean;
}

interface Property {
	javaName: string;
	jsName: string;
	java: string;
	ts: string;
	readonly: boolean;
}

interface Nested {
	javaName: string;
	jsName: string;
	methods: Method[];
	properties: Property[];
	constructors: Method[];
}

interface Model {
	methods: Method[];
	properties: Property[];
	nested: Nested[];
	notes: string[];
}

interface Resolution {
	tier: Tier;
	/** What the checker reads: a declaration file, or the JavaScript itself. */
	entry: string | null;
	/** The JavaScript, which the lower tiers need even when the checker read declarations. */
	script: string | null;
	allowJs: boolean;
	checkJs: boolean;
}

/**
 * Reads each package's TypeScript types and returns the Java that binds them.
 *
 * Nothing is written; {@link writeBindings} does that. An npm package is JavaScript, so none of this
 * enters the WebAssembly: the generated methods are `@JSBody` scripts against a static import, which
 * is what makes the package resolvable to a bundler.
 *
 * @param options what to generate
 * @returns one entry per requested package
 * @since 1.0.0
 */
export async function generateBindings(options: BindgenOptions): Promise<BindgenResult> {
	const javaPackage = options.javaPackage ?? DEFAULT_JAVA_PACKAGE;
	const packages: PackageBindings[] = [];
	for (const specifier of options.packages) {
		packages.push(await bind(specifier, options, javaPackage));
	}
	return { packages };
}

/**
 * Generates the bindings and writes them under a directory, one file per package.
 *
 * This is what the `bytebox-bindgen` command runs. That command, in `src/bin/bindgen.ts`, is a
 * wrapper: it parses arguments, calls this, and prints the result. Import this instead when the
 * caller is TypeScript rather than a shell.
 *
 * @param options what to generate, plus where to put it
 * @returns one entry per requested package
 * @since 1.0.0
 */
export async function writeBindings(
	options: BindgenOptions & { out: string }
): Promise<BindgenResult> {
	const result = await generateBindings(options);
	for (const bindings of result.packages) {
		const directory = join(options.out, bindings.javaPackage.replace(/\./g, '/'));
		mkdirSync(directory, { recursive: true });
		writeFileSync(join(directory, `${bindings.className}.java`), bindings.source);
	}
	return result;
}

/**
 * Walks the ladder, stopping at the first tier that produces a member.
 *
 * Each rung falls through on an empty result rather than on an error, because a package whose
 * exports static analysis cannot see does not fail, it resolves to nothing. Tier 7 is the floor.
 */
async function bind(
	specifier: string,
	options: BindgenOptions,
	javaPackage: string
): Promise<PackageBindings> {
	const className = classNameOf(specifier);
	const alias = aliasOf(specifier);
	const resolution = resolve(options.root, specifier);
	let tier = resolution.tier;
	let model: Model = empty();

	if (resolution.entry !== null) {
		model = read(resolution);
		if (total(model) === 0) {
			const script = resolution.script;
			if (script !== null) {
				const walked = syntax(script);
				if (total(walked) > 0) {
					tier = 5;
					model = walked;
					model.notes.push(
						'the type checker resolved no exports, so names and arity come from the' +
							' syntax tree and every type is a TSObject'
					);
				}
			}
		}
	}

	if (total(model) === 0 && resolution.script !== null && options.introspect !== false) {
		const read_ = await introspected(resolution.script);
		if (read_ !== null && total(read_) > 0) {
			tier = 6;
			model = read_;
			model.notes.push(
				"nothing static could see this module's exports, so they were read off the module" +
					' after importing it, and every type is a TSObject'
			);
		}
	}

	if (total(model) === 0) {
		tier = 7;
		model.notes.push('nothing resolved any exports, so only the module handle is bound');
	}

	return {
		specifier,
		className,
		javaPackage,
		tier,
		members: total(model),
		notes: model.notes,
		source: render(specifier, className, alias, javaPackage, tier, model)
	};
}

function empty(): Model {
	return { methods: [], properties: [], nested: [], notes: [] };
}

function total(model: Model): number {
	let count = model.methods.length + model.properties.length;
	for (const nested of model.nested) {
		count += 1 + nested.methods.length + nested.properties.length + nested.constructors.length;
	}
	return count;
}

// #region resolution

function resolve(root: string, specifier: string): Resolution {
	const { name, subpath } = split(specifier);
	const directory = join(root, 'node_modules', name);
	const manifest = manifestOf(directory);
	const missing: Resolution = {
		tier: 7,
		entry: null,
		script: null,
		allowJs: false,
		checkJs: false
	};
	if (manifest === null) return missing;

	const script = javascript(directory, manifest, subpath);

	const own = declarations(directory, manifest, subpath);
	if (own !== null) {
		return { tier: 1, entry: own, script, allowJs: false, checkJs: false };
	}

	const typesDirectory = join(
		root,
		'node_modules',
		'@types',
		name.replace('@', '').replace('/', '__')
	);
	const types = manifestOf(typesDirectory);
	if (types !== null) {
		const entry = declarations(typesDirectory, types, subpath);
		if (entry !== null) return { tier: 2, entry, script, allowJs: false, checkJs: false };
	}

	if (script !== null) {
		const documented = /@(param|returns?|type|typedef|callback)\s*[{[]/.test(
			readFileSync(script, 'utf8')
		);
		return {
			tier: documented ? 3 : 4,
			entry: script,
			script,
			allowJs: true,
			checkJs: documented
		};
	}

	return missing;
}

function split(specifier: string): { name: string; subpath: string | null } {
	const parts = specifier.split('/');
	const scoped = specifier.startsWith('@');
	const name = scoped ? parts.slice(0, 2).join('/') : (parts[0] as string);
	const rest = specifier.slice(name.length + 1);
	return { name, subpath: rest.length === 0 ? null : rest };
}

function manifestOf(directory: string): Record<string, unknown> | null {
	const file = join(directory, 'package.json');
	if (!existsSync(file)) return null;
	try {
		return JSON.parse(readFileSync(file, 'utf8')) as Record<string, unknown>;
	} catch {
		return null;
	}
}

/** The `.d.ts` a package points at, whether through `types`, an export map, or convention. */
function declarations(
	directory: string,
	manifest: Record<string, unknown>,
	subpath: string | null
): string | null {
	const candidates: string[] = [];
	const exported = exportEntry(manifest, subpath);

	if (subpath === null) {
		for (const key of ['types', 'typings']) {
			const declared = manifest[key];
			if (typeof declared === 'string') candidates.push(declared);
		}
		if (exported !== null) candidates.push(exported);
		const main = manifest['main'];
		if (typeof main === 'string') candidates.push(main);
		candidates.push('index.d.ts');
	} else {
		if (exported !== null) candidates.push(exported);
		candidates.push(`${subpath}.d.ts`, join(subpath, 'index.d.ts'), subpath);
	}

	for (const candidate of candidates) {
		const found = asDeclaration(join(directory, candidate));
		if (found !== null) return found;
	}
	return null;
}

/** A path that is, or sits beside, a declaration file. */
function asDeclaration(path: string): string | null {
	if (path.endsWith('.d.ts') && existsSync(path)) return path;
	const sibling = path.replace(/\.[cm]?js$/, '.d.ts');
	if (sibling !== path && existsSync(sibling)) return sibling;
	for (const suffix of ['.d.ts', '/index.d.ts']) {
		if (existsSync(path + suffix)) return path + suffix;
	}
	return null;
}

function javascript(
	directory: string,
	manifest: Record<string, unknown>,
	subpath: string | null
): string | null {
	const candidates: string[] = [];
	const exported = exportEntry(manifest, subpath);
	if (exported !== null) candidates.push(exported);
	if (subpath === null) {
		for (const key of ['module', 'main']) {
			const declared = manifest[key];
			if (typeof declared === 'string') candidates.push(declared);
		}
		candidates.push('index.js');
	} else {
		candidates.push(`${subpath}.js`, join(subpath, 'index.js'));
	}
	for (const candidate of candidates) {
		const path = join(directory, candidate);
		if (existsSync(path) && /\.[cm]?js$/.test(path)) return path;
		for (const suffix of ['.js', '/index.js']) {
			if (existsSync(path + suffix)) return path + suffix;
		}
	}
	return null;
}

/**
 * One entry out of an export map.
 *
 * Only the shapes a real package uses are followed: a plain string, or an object keyed by condition.
 * A conditional whose value is itself conditional resolves recursively.
 */
function exportEntry(manifest: Record<string, unknown>, subpath: string | null): string | null {
	const exports = manifest['exports'];
	if (exports === undefined || exports === null) return null;
	const key = subpath === null ? '.' : `./${subpath}`;
	if (typeof exports === 'string') return subpath === null ? exports : null;
	const map = exports as Record<string, unknown>;
	const entry = map[key] ?? (subpath === null ? undefined : wildcard(map, key));
	return condition(entry ?? (isConditional(map) && subpath === null ? map : undefined));
}

function isConditional(map: Record<string, unknown>): boolean {
	return Object.keys(map).every((key) => !key.startsWith('.'));
}

/** A `./*` pattern, substituted rather than matched loosely. */
function wildcard(map: Record<string, unknown>, key: string): unknown {
	for (const [pattern, value] of Object.entries(map)) {
		if (!pattern.includes('*')) continue;
		const [before, after = ''] = pattern.split('*');
		if (!key.startsWith(before as string) || !key.endsWith(after)) continue;
		const middle = key.slice((before as string).length, key.length - after.length);
		const resolved = condition(value);
		return resolved === null ? undefined : resolved.replace('*', middle);
	}
	return undefined;
}

function condition(entry: unknown): string | null {
	if (typeof entry === 'string') return entry;
	if (entry === null || typeof entry !== 'object') return null;
	const map = entry as Record<string, unknown>;
	for (const key of ['types', 'import', 'module', 'require', 'default']) {
		if (key in map) {
			const resolved = condition(map[key]);
			if (resolved !== null) return resolved;
		}
	}
	return null;
}

// #endregion

// #region reading

function read(resolution: Resolution): Model {
	const entry = resolution.entry as string;
	const model = model_(entry, resolution.allowJs, resolution.checkJs, false);
	if (!resolution.checkJs) return model;

	// the JSDoc tier's second opinion. `checkJs: false` is not enough to get one: it turns off error
	// reporting and TypeScript still reads the annotations, so both programs would agree by
	// construction. The comments have to be gone from the text the second program sees.
	const inferred = model_(entry, true, false, true);
	demote(model, inferred);
	return model;
}

function model_(entry: string, allowJs: boolean, checkJs: boolean, stripDoc: boolean): Model {
	// NodeNext for JavaScript, so a `.js` file is read as CommonJS or ESM the way the runtime reads
	// it. Under plain ESNext a `module.exports =` is an assignment to an undeclared variable rather
	// than an export, which resolves every CommonJS package to nothing.
	const commonjs = allowJs;
	const program = programOf(entry, stripDoc, {
		target: ts.ScriptTarget.ESNext,
		module: commonjs ? ts.ModuleKind.NodeNext : ts.ModuleKind.ESNext,
		moduleResolution: commonjs
			? ts.ModuleResolutionKind.NodeNext
			: ts.ModuleResolutionKind.Bundler,
		allowJs,
		checkJs,
		noEmit: true,
		skipLibCheck: true,
		strict: false,
		// without this a `number | undefined` collapses to `number`, and the binding claims a
		// primitive for a function that can answer with nothing
		strictNullChecks: true,
		types: []
	});
	const checker = program.getTypeChecker();
	const source = program.getSourceFile(entry);
	const model: Model = { methods: [], properties: [], nested: [], notes: [] };
	if (source === undefined) return model;

	const moduleSymbol = checker.getSymbolAtLocation(source);
	if (moduleSymbol === undefined) return model;

	const taken = new Set<string>();
	for (const symbol of exportsOf(checker, moduleSymbol, source)) {
		member(checker, symbol, model, taken);
	}
	return model;
}

/**
 * A program over the entry, optionally with its documentation comments blanked out.
 *
 * Blanking keeps every offset where it was, so a stripped file still parses and every position in
 * the tree still lines up. A comment that gets mangled produces a program that resolves nothing,
 * which skips the comparison rather than corrupting it.
 */
function programOf(entry: string, stripDoc: boolean, options: ts.CompilerOptions): ts.Program {
	if (!stripDoc) return ts.createProgram([entry], options);

	const host = ts.createCompilerHost(options, true);
	const original = host.getSourceFile.bind(host);
	host.getSourceFile = (fileName, languageVersion, onError, shouldCreate) => {
		if (fileName !== entry) return original(fileName, languageVersion, onError, shouldCreate);
		const text = readFileSync(fileName, 'utf8').replace(/\/\*\*[\s\S]*?\*\//g, (comment) =>
			' '.repeat(comment.length)
		);
		return ts.createSourceFile(fileName, text, languageVersion, true);
	};
	return ts.createProgram([entry], options, host);
}

/**
 * What a module exports, whether it says so with `export` or with `module.exports =`.
 *
 * A CommonJS module's value is one `export=` symbol, and `getExportsOfModule` reports nothing for
 * it. Its members are the properties of the module symbol's own type, which is where
 * `module.exports = { ... }` and `exports.name = ...` both end up.
 */
function exportsOf(
	checker: ts.TypeChecker,
	moduleSymbol: ts.Symbol,
	source: ts.SourceFile
): ts.Symbol[] {
	const exported = checker.getExportsOfModule(moduleSymbol);
	const assignment = exported.find((symbol) => symbol.getName() === 'export=');
	if (assignment !== undefined) {
		const type = typeOf(checker, assignment);
		const properties = type === null ? [] : type.getProperties();
		if (properties.length > 0) return properties;
	}
	if (exported.length > 0) return exported;
	return checker.getTypeOfSymbolAtLocation(moduleSymbol, source).getProperties();
}

function member(
	checker: ts.TypeChecker,
	symbol: ts.Symbol,
	model: Model,
	taken: Set<string>
): void {
	const resolved = alias(checker, symbol);
	const jsName = symbol.getName();
	if (jsName === 'export=' || jsName.startsWith('__')) return;

	if (resolved.flags & (ts.SymbolFlags.Class | ts.SymbolFlags.Interface)) {
		const nested = interfaceOf(checker, resolved, jsName, model);
		if (nested !== null) model.nested.push(nested);
		return;
	}

	const type = typeOf(checker, resolved);
	if (type === null) return;

	const signatures = type.getCallSignatures();
	if (signatures.length > 0) {
		for (const method of methods(checker, signatures, jsName, false, model)) {
			if (!taken.has(signature(method))) {
				taken.add(signature(method));
				model.methods.push(method);
			}
		}
		return;
	}

	const mapped = map(checker, type);
	model.properties.push({
		javaName: javaName(jsName),
		jsName,
		java: mapped.promise ? 'TSObject' : mapped.java,
		ts: mapped.ts,
		// a module's own exports are reached through a namespace import, and assigning to one of
		// those throws whatever the declaration says, so a top-level export never gets a setter
		readonly: true
	});
}

function isReadonly(symbol: ts.Symbol): boolean {
	const declaration = symbol.declarations?.[0];
	if (declaration === undefined) return false;
	const modifiers = ts.canHaveModifiers(declaration) ? ts.getModifiers(declaration) : undefined;
	return modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ReadonlyKeyword) ?? false;
}

function interfaceOf(
	checker: ts.TypeChecker,
	symbol: ts.Symbol,
	jsName: string,
	model: Model
): Nested | null {
	const instance = checker.getDeclaredTypeOfSymbol(symbol);
	const methodList: Method[] = [];
	const properties: Property[] = [];
	const taken = new Set<string>();

	for (const property of instance.getProperties()) {
		const type = typeOf(checker, property);
		if (type === null) continue;
		const name = property.getName();
		if (name.startsWith('__') || !/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name)) continue;
		const signatures = type.getCallSignatures();
		if (signatures.length > 0) {
			for (const method of methods(checker, signatures, name, false, model)) {
				if (taken.has(signature(method))) continue;
				taken.add(signature(method));
				methodList.push(method);
			}
			continue;
		}
		const mapped = map(checker, type);
		properties.push({
			javaName: javaName(name),
			jsName: name,
			java: mapped.promise ? 'TSObject' : mapped.java,
			ts: mapped.ts,
			readonly: isReadonly(property)
		});
	}

	const constructors: Method[] = [];
	if (symbol.flags & ts.SymbolFlags.Class) {
		const statics = typeOf(checker, symbol);
		const construct = statics === null ? [] : statics.getConstructSignatures();
		const built = methods(checker, construct, jsName, true, model);
		const seen = new Set<string>();
		for (const method of built) {
			if (seen.has(signature(method))) continue;
			seen.add(signature(method));
			constructors.push(method);
		}
	}

	if (methodList.length === 0 && properties.length === 0 && constructors.length === 0)
		return null;
	return { javaName: typeName(jsName), jsName, methods: methodList, properties, constructors };
}

/**
 * One Java method per call signature, plus one per trailing optional parameter dropped.
 *
 * A rest parameter ends the list rather than becoming varargs: the compiler's `@JSBody` parser
 * refuses argument spread, so a generated script cannot forward one.
 */
function methods(
	checker: ts.TypeChecker,
	signatures: readonly ts.Signature[],
	jsName: string,
	construct: boolean,
	model: Model
): Method[] {
	const built: Method[] = [];
	for (const sig of signatures) {
		const params: Param[] = [];
		let required = 0;
		let rest = false;
		for (const parameter of sig.getParameters()) {
			const declaration = parameter.valueDeclaration;
			if (
				declaration !== undefined &&
				ts.isParameter(declaration) &&
				declaration.dotDotDotToken
			) {
				rest = true;
				break;
			}
			const type = typeOf(checker, parameter);
			const skippable = optional(parameter);
			// an optional parameter's type carries `undefined`, and mapping that would widen every
			// one of them to TSObject. The overload that omits it is what says it can be omitted.
			const effective = type !== null && skippable ? checker.getNonNullableType(type) : type;
			const mapped =
				effective === null
					? { java: 'TSObject', ts: 'unknown', promise: false }
					: { ...map(checker, effective), ts: checker.typeToString(type as ts.Type) };
			params.push({
				name: paramName(parameter.getName(), params.length),
				java: mapped.promise ? 'TSObject' : mapped.java,
				ts: mapped.ts
			});
			if (!skippable) required = params.length;
		}
		if (rest) {
			model.notes.push(
				`${jsName} takes a rest parameter, which is bound up to the parameters before it`
			);
		}

		const returned = construct
			? { java: typeName(jsName), ts: jsName, promise: false }
			: map(checker, sig.getReturnType());

		for (let arity = params.length; arity >= required; arity--) {
			built.push({
				javaName: construct ? `new${typeName(jsName)}` : javaName(jsName),
				jsName,
				params: params.slice(0, arity),
				returns: returned,
				construct
			});
		}
	}
	return built;
}

function optional(parameter: ts.Symbol): boolean {
	if ((parameter.flags & ts.SymbolFlags.Optional) !== 0) return true;
	const declaration = parameter.valueDeclaration;
	if (declaration === undefined || !ts.isParameter(declaration)) return false;
	return declaration.questionToken !== undefined || declaration.initializer !== undefined;
}

function signature(method: Method): string {
	return `${method.javaName}(${method.params.map((param) => param.java).join(',')})`;
}

function typeOf(checker: ts.TypeChecker, symbol: ts.Symbol): ts.Type | null {
	const declaration = symbol.valueDeclaration ?? symbol.declarations?.[0];
	if (declaration === undefined) return null;
	return checker.getTypeOfSymbolAtLocation(symbol, declaration);
}

function alias(checker: ts.TypeChecker, symbol: ts.Symbol): ts.Symbol {
	return symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol;
}

/** Replaces every type the two programs disagree about, which is the JSDoc tier's trust level. */
function demote(model: Model, inferred: Model): void {
	// an uninformative second opinion is not a disagreement: inference reaches `any` for most of
	// what JSDoc annotates, and treating that as a conflict would demote everything
	const disagrees = (mine: string, theirs: string): boolean =>
		theirs !== 'TSObject' && mine !== 'TSObject' && theirs !== mine;

	for (const method of model.methods) {
		const other = inferred.methods.find(
			(candidate) =>
				candidate.javaName === method.javaName &&
				candidate.params.length === method.params.length
		);
		if (other === undefined) continue;

		if (disagrees(method.returns.java, other.returns.java)) {
			model.notes.push(
				`${method.jsName} returns ${method.returns.java} by its JSDoc and ` +
					`${other.returns.java} by inference, so it is bound as TSObject`
			);
			method.returns = { java: 'TSObject', ts: method.returns.ts, promise: false };
		}
		for (let i = 0; i < method.params.length; i++) {
			const mine = method.params[i] as Param;
			const theirs = other.params[i];
			if (theirs === undefined || !disagrees(mine.java, theirs.java)) continue;
			model.notes.push(
				`${method.jsName}'s ${mine.name} is ${mine.java} by its JSDoc and ` +
					`${theirs.java} by inference, so it is bound as TSObject`
			);
			mine.java = 'TSObject';
		}
	}
}

/**
 * Names and arity out of the syntax tree, for exports the checker did not resolve.
 *
 * Worth more than it looks: correct names and arity is what drives editor completion, and
 * `TSObject` carries every type. Four shapes are recognised, which is what real packages write.
 */
function syntax(script: string): Model {
	const model = empty();
	const source = ts.createSourceFile(
		script,
		readFileSync(script, 'utf8'),
		ts.ScriptTarget.ESNext,
		true,
		/\.[cm]?ts$/.test(script) ? ts.ScriptKind.TS : ts.ScriptKind.JS
	);
	const taken = new Set<string>();

	const add = (name: string, node: ts.Node | undefined): void => {
		if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name) || taken.has(name)) return;
		taken.add(name);
		const parameters = arity(node);
		if (parameters === null) {
			model.properties.push({
				javaName: javaName(name),
				jsName: name,
				java: 'TSObject',
				ts: 'unknown',
				readonly: true
			});
			return;
		}
		const params: Param[] = [];
		for (let i = 0; i < parameters; i++) {
			params.push({ name: `arg${i}`, java: 'TSObject', ts: 'unknown' });
		}
		model.methods.push({
			javaName: javaName(name),
			jsName: name,
			params,
			returns: { java: 'TSObject', ts: 'unknown', promise: false },
			construct: false
		});
	};

	const visit = (node: ts.Node): void => {
		if (ts.isFunctionDeclaration(node) && exported(node) && node.name !== undefined) {
			add(node.name.text, node);
		} else if (ts.isClassDeclaration(node) && exported(node) && node.name !== undefined) {
			add(node.name.text, undefined);
		} else if (ts.isVariableStatement(node) && exported(node)) {
			for (const declaration of node.declarationList.declarations) {
				if (ts.isIdentifier(declaration.name))
					add(declaration.name.text, declaration.initializer);
			}
		} else if (ts.isExpressionStatement(node) && ts.isBinaryExpression(node.expression)) {
			assignment(node.expression, add);
		} else if (ts.isCallExpression(node)) {
			call(node, add);
		}
		ts.forEachChild(node, visit);
	};
	ts.forEachChild(source, visit);
	return model;
}

/** `module.exports = {...}`, `module.exports = fn` and `exports.name = ...`. */
function assignment(
	expression: ts.BinaryExpression,
	add: (name: string, node: ts.Node | undefined) => void
): void {
	if (expression.operatorToken.kind !== ts.SyntaxKind.EqualsToken) return;
	const target = expression.left;
	if (!ts.isPropertyAccessExpression(target)) return;
	const owner = target.expression;

	if (ts.isIdentifier(owner) && owner.text === 'exports') {
		add(target.name.text, expression.right);
		return;
	}
	const isModuleExports =
		ts.isIdentifier(owner) && owner.text === 'module' && target.name.text === 'exports';
	if (!isModuleExports) return;

	const value = expression.right;
	if (ts.isObjectLiteralExpression(value)) literal(value, add);
}

/**
 * `Object.assign(module.exports, {...})` and `Object.defineProperty(exports, 'name', ...)`.
 *
 * Both are invisible to the checker, and catching them here is what keeps a package that writes
 * them from needing tier 6, which would have to run it.
 */
function call(
	expression: ts.CallExpression,
	add: (name: string, node: ts.Node | undefined) => void
): void {
	const callee = expression.expression;
	if (!ts.isPropertyAccessExpression(callee)) return;
	if (!ts.isIdentifier(callee.expression) || callee.expression.text !== 'Object') return;

	const [target, ...rest] = expression.arguments;
	if (target === undefined || !isExports(target)) return;

	if (callee.name.text === 'assign') {
		for (const argument of rest) {
			if (!ts.isObjectLiteralExpression(argument)) continue;
			literal(argument, add);
		}
		return;
	}
	if (callee.name.text !== 'defineProperty') return;
	const named = rest[0];
	if (named !== undefined && ts.isStringLiteralLike(named)) add(named.text, undefined);
}

function isExports(node: ts.Expression): boolean {
	if (ts.isIdentifier(node)) return node.text === 'exports';
	return (
		ts.isPropertyAccessExpression(node) &&
		ts.isIdentifier(node.expression) &&
		node.expression.text === 'module' &&
		node.name.text === 'exports'
	);
}

function literal(
	value: ts.ObjectLiteralExpression,
	add: (name: string, node: ts.Node | undefined) => void
): void {
	for (const property of value.properties) {
		const name = property.name;
		if (name === undefined || !ts.isIdentifier(name)) continue;
		if (ts.isPropertyAssignment(property)) add(name.text, property.initializer);
		else if (ts.isMethodDeclaration(property)) add(name.text, property);
		else add(name.text, undefined);
	}
}

function exported(node: ts.Node): boolean {
	const modifiers = ts.canHaveModifiers(node) ? ts.getModifiers(node) : undefined;
	return modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword) ?? false;
}

/** How many parameters a node declares, or `null` when it is not callable. */
function arity(node: ts.Node | undefined): number | null {
	if (node === undefined) return null;
	if (
		ts.isFunctionDeclaration(node) ||
		ts.isFunctionExpression(node) ||
		ts.isArrowFunction(node) ||
		ts.isMethodDeclaration(node)
	) {
		let count = 0;
		for (const parameter of node.parameters) {
			if (parameter.dotDotDotToken) break;
			count++;
		}
		return count;
	}
	return null;
}

/**
 * Names and arity read off the module after importing it.
 *
 * The only tier that sees exports a package assembles at run time, and the only one that executes
 * third-party code. A module that throws on import falls through rather than failing the build.
 */
async function introspected(script: string): Promise<Model | null> {
	const loaded = await imported(script);
	if (loaded === null) return null;

	const model = empty();
	const seen = new Set<string>();
	const targets: Record<string, unknown>[] = [loaded];
	// a CommonJS module reached through the ESM bridge puts its own exports on `default`
	const fallback = loaded['default'];
	if (fallback !== null && typeof fallback === 'object') {
		targets.push(fallback as Record<string, unknown>);
	}
	if (typeof fallback === 'function') {
		record(model, seen, 'default', fallback);
	}

	for (const target of targets) {
		for (const name of Object.getOwnPropertyNames(target)) {
			if (name === 'default' || name === '__esModule') continue;
			let value: unknown;
			try {
				value = target[name];
			} catch {
				// a getter that throws is still a name worth binding
				value = undefined;
			}
			record(model, seen, name, value);
		}
	}
	return model;
}

/**
 * The module, however it loads.
 *
 * `import()` is the general answer and `require` is the reliable one for CommonJS, which a bundler
 * between here and the file may otherwise transform. Both failing means the package cannot be read
 * this way, which is a fall-through rather than an error.
 */
async function imported(script: string): Promise<Record<string, unknown> | null> {
	try {
		return (await import(pathToFileURL(script).href)) as Record<string, unknown>;
	} catch {
		// fall through to require
	}
	try {
		return createRequire(pathToFileURL(script).href)(script) as Record<string, unknown>;
	} catch {
		return null;
	}
}

function record(model: Model, seen: Set<string>, name: string, value: unknown): void {
	if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name) || seen.has(name)) return;
	seen.add(name);
	if (typeof value !== 'function') {
		model.properties.push({
			javaName: javaName(name),
			jsName: name,
			java: 'TSObject',
			ts: typeof value,
			readonly: true
		});
		return;
	}
	const params: Param[] = [];
	for (let i = 0; i < value.length; i++) {
		params.push({ name: `arg${i}`, java: 'TSObject', ts: 'unknown' });
	}
	model.methods.push({
		javaName: javaName(name),
		jsName: name,
		params,
		returns: { java: 'TSObject', ts: 'unknown', promise: false },
		construct: false
	});
}

// #endregion

// #region types

/** A TypeScript type as the nearest Java one, walking the type rather than its text. */
function map(checker: ts.TypeChecker, type: ts.Type): Returns {
	const ts_ = checker.typeToString(type);
	return { ...java(checker, type), ts: ts_ };
}

function java(checker: ts.TypeChecker, type: ts.Type): { java: string; promise: boolean } {
	const flags = type.flags;
	if (flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown))
		return { java: 'TSObject', promise: false };
	if (flags & (ts.TypeFlags.Void | ts.TypeFlags.Never)) return { java: 'void', promise: false };
	if (flags & ts.TypeFlags.StringLike) return { java: 'String', promise: false };
	if (flags & ts.TypeFlags.NumberLike) return { java: 'double', promise: false };
	if (flags & ts.TypeFlags.BooleanLike) return { java: 'boolean', promise: false };
	if (flags & ts.TypeFlags.BigIntLike) return { java: 'long', promise: false };
	if (flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null)) {
		return { java: 'TSObject', promise: false };
	}

	if (type.isUnion()) return union(checker, type);
	if (isPromise(type)) return { java: 'TSObject', promise: true };
	return { java: 'TSObject', promise: false };
}

/**
 * A union of one type plus `null` or `undefined` keeps that type when a Java reference can be null,
 * and widens to `TSObject` when it cannot. Anything else is a `TSObject`.
 */
function union(checker: ts.TypeChecker, type: ts.UnionType): { java: string; promise: boolean } {
	const members: { java: string; promise: boolean }[] = [];
	let nullable = false;
	for (const part of type.types) {
		if (part.flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null)) {
			nullable = true;
			continue;
		}
		members.push(java(checker, part));
	}
	if (members.length === 0) return { java: 'TSObject', promise: false };
	const first = members[0] as { java: string; promise: boolean };
	for (const other of members) {
		if (other.java !== first.java || other.promise !== first.promise) {
			return { java: 'TSObject', promise: false };
		}
	}
	if (!nullable || first.promise) return first;
	const primitive = ['double', 'boolean', 'long', 'void'].includes(first.java);
	return primitive ? { java: 'TSObject', promise: false } : first;
}

function isPromise(type: ts.Type): boolean {
	const name = type.getSymbol()?.getName();
	return name === 'Promise' || name === 'PromiseLike';
}

// #endregion

// #region emission

function render(
	specifier: string,
	className: string,
	alias_: string,
	javaPackage: string,
	tier: Tier,
	model: Model
): string {
	const imports = new Set<string>([
		'dev.gmitch215.bytebox.js.TSObject',
		'org.teavm.jso.JSBody',
		'org.teavm.jso.JSBodyImport'
	]);
	const body: string[] = [];

	for (const method of model.methods) {
		body.push(
			methodSource(specifier, alias_, method, `${alias_}.${method.jsName}`, imports, '\t')
		);
	}
	for (const property of model.properties) {
		body.push(
			propertySource(specifier, alias_, property, `${alias_}.${property.jsName}`, imports)
		);
	}
	for (const nested of model.nested) {
		body.push(nestedSource(specifier, alias_, nested, imports));
	}

	body.push(
		[
			'\t/** {@return the module itself, for anything these bindings do not cover} */',
			`\t@JSBody(${importClause(specifier, alias_)}script = "return ${alias_};")`,
			'\tpublic static native TSObject module();'
		].join('\n')
	);

	const out: string[] = [];
	out.push(`package ${javaPackage};`, '');
	for (const imported of [...imports].sort()) out.push(`import ${imported};`);
	out.push('');
	out.push('/**');
	out.push(` * Bindings for the npm package {@code ${specifier}}.`);
	out.push(' *');
	out.push(` * <p>Generated from ${tierDescription(tier)}.`);
	out.push(' * Written by the bytebox Gradle plugin, so edits here are overwritten.');
	if (model.notes.length > 0) {
		out.push(' *');
		out.push(' * <ul>');
		for (const note of model.notes) out.push(` *   <li>${escapeDoc(note)}`);
		out.push(' * </ul>');
	}
	out.push(' */');
	out.push(`public final class ${className} {`, '');
	out.push(`\tprivate ${className}() {}`, '');
	out.push(body.join('\n\n'));
	out.push('}');
	return out.join('\n') + '\n';
}

function tierDescription(tier: Tier): string {
	switch (tier) {
		case 1:
			return "the package's own TypeScript declarations";
		case 2:
			return 'its DefinitelyTyped declarations';
		case 3:
			return 'the JSDoc annotations in its JavaScript';
		case 4:
			return 'what TypeScript infers from its JavaScript';
		case 5:
			return "its JavaScript's syntax tree, which carries names but no types";
		case 6:
			return 'the module object, read after importing it';
		default:
			return 'the module itself, which carries no types';
	}
}

function methodSource(
	specifier: string,
	alias_: string,
	method: Method,
	call: string,
	imports: Set<string>,
	indent: string
): string {
	const args = method.params.map((param) => param.name);
	const invocation = method.construct
		? `new ${call}(${args.join(', ')})`
		: `${call}(${args.join(', ')})`;
	const script = method.returns.java === 'void' ? `${invocation};` : `return ${invocation};`;

	const doc = docBlock(
		`{@code ${method.jsName}(${method.params.map((param) => `${param.name}: ${param.ts}`).join(', ')}): ${method.returns.ts}}`,
		method.params,
		method.returns,
		indent
	);

	if (!method.returns.promise) {
		return [
			doc,
			annotation(specifier, alias_, args, script, indent),
			`${indent}public static native ${method.returns.java} ${method.javaName}(${signatureText(method.params)});`
		].join('\n');
	}

	imports.add('dev.gmitch215.bytebox.concurrent.Future');
	imports.add('org.teavm.jso.core.JSPromise');
	const hidden = `$${method.javaName}`;
	return [
		annotation(specifier, alias_, args, script, indent),
		`${indent}private static native JSPromise<TSObject> ${hidden}(${signatureText(method.params)});`,
		'',
		doc,
		`${indent}public static Future<TSObject> ${method.javaName}(${signatureText(method.params)}) {`,
		`${indent}\treturn Future.of(${hidden}(${args.join(', ')}));`,
		`${indent}}`
	].join('\n');
}

function propertySource(
	specifier: string,
	alias_: string,
	property: Property,
	access: string,
	imports: Set<string>
): string {
	const parts: string[] = [];
	parts.push(
		[
			`\t/** {@return {@code ${property.jsName}}, declared {@code ${property.ts}}} */`,
			annotation(specifier, alias_, [], `return ${access};`, '\t'),
			`\tpublic static native ${property.java} ${getterName(property)}();`
		].join('\n')
	);
	if (!property.readonly) {
		parts.push(
			[
				`\t/**`,
				`\t * Sets {@code ${property.jsName}}, declared {@code ${property.ts}}.`,
				`\t *`,
				`\t * @param value the value`,
				`\t */`,
				annotation(specifier, alias_, ['value'], `${access} = value;`, '\t'),
				`\tpublic static native void set${capitalise(property.javaName)}(${property.java} value);`
			].join('\n')
		);
	}
	if (property.java === 'TSObject') imports.add('dev.gmitch215.bytebox.js.TSObject');
	return parts.join('\n\n');
}

function nestedSource(
	specifier: string,
	alias_: string,
	nested: Nested,
	imports: Set<string>
): string {
	imports.add('org.teavm.jso.JSObject');
	const parts: string[] = [];
	parts.push(`\t/** The {@code ${nested.jsName}} type. */`);
	parts.push(`\tpublic interface ${nested.javaName} extends JSObject {`);

	const members: string[] = [];
	for (const method of nested.methods) {
		imports.add('org.teavm.jso.JSMethod');
		const doc = docBlock(
			`{@code ${method.jsName}(${method.params.map((param) => `${param.name}: ${param.ts}`).join(', ')}): ${method.returns.ts}}`,
			method.params,
			method.returns,
			'\t\t'
		);
		if (method.returns.promise) {
			imports.add('org.teavm.jso.core.JSPromise');
			members.push(
				[
					doc,
					`\t\t@JSMethod("${method.jsName}")`,
					`\t\tJSPromise<TSObject> ${method.javaName}(${signatureText(method.params)});`
				].join('\n')
			);
			continue;
		}
		members.push(
			[
				doc,
				`\t\t@JSMethod("${method.jsName}")`,
				`\t\t${method.returns.java} ${method.javaName}(${signatureText(method.params)});`
			].join('\n')
		);
	}
	for (const property of nested.properties) {
		imports.add('org.teavm.jso.JSProperty');
		members.push(
			[
				`\t\t/** {@return {@code ${property.jsName}}, declared {@code ${property.ts}}} */`,
				`\t\t@JSProperty("${property.jsName}")`,
				`\t\t${property.java} ${getterName(property)}();`
			].join('\n')
		);
		if (property.readonly) continue;
		members.push(
			[
				'\t\t/**',
				`\t\t * Sets {@code ${property.jsName}}.`,
				'\t\t *',
				'\t\t * @param value the value',
				'\t\t */',
				`\t\t@JSProperty("${property.jsName}")`,
				`\t\tvoid set${capitalise(property.javaName)}(${property.java} value);`
			].join('\n')
		);
	}

	parts.push(members.join('\n\n'));
	parts.push('\t}');

	const constructors = nested.constructors.map((constructor) =>
		methodSource(specifier, alias_, constructor, `${alias_}.${nested.jsName}`, imports, '\t')
	);
	return [parts.join('\n'), ...constructors].join('\n\n');
}

function annotation(
	specifier: string,
	alias_: string,
	params: string[],
	script: string,
	indent: string
): string {
	const lines: string[] = [`${indent}@JSBody(`];
	if (params.length > 0) {
		const quoted = params.map((param) => `"${param}"`).join(', ');
		lines.push(`${indent}\tparams = { ${quoted} },`);
	}
	lines.push(
		`${indent}\timports = @JSBodyImport(alias = "${alias_}", fromModule = "${specifier}"),`
	);
	lines.push(`${indent}\tscript = ${javaString(script)}`);
	lines.push(`${indent})`);
	return lines.join('\n');
}

function importClause(specifier: string, alias_: string): string {
	return `imports = @JSBodyImport(alias = "${alias_}", fromModule = "${specifier}"), `;
}

function docBlock(summary: string, params: Param[], returns: Returns, indent: string): string {
	const lines: string[] = [`${indent}/**`, `${indent} * ${escapeDoc(summary)}`];
	if (params.length > 0 || returns.java !== 'void') lines.push(`${indent} *`);
	for (const param of params) {
		lines.push(`${indent} * @param ${param.name} declared {@code ${escapeDoc(param.ts)}}`);
	}
	if (returns.java !== 'void') {
		const described = returns.promise
			? `a future over {@code ${escapeDoc(returns.ts)}}`
			: `{@code ${escapeDoc(returns.ts)}}`;
		lines.push(`${indent} * @return ${described}`);
	}
	lines.push(`${indent} */`);
	return lines.join('\n');
}

function signatureText(params: Param[]): string {
	return params.map((param) => `${param.java} ${param.name}`).join(', ');
}

function getterName(property: Property): string {
	const capitalised = capitalise(property.javaName);
	return property.java === 'boolean' ? `is${capitalised}` : `get${capitalised}`;
}

function capitalise(name: string): string {
	return name.charAt(0).toUpperCase() + name.slice(1);
}

/** A javadoc body cannot carry a raw `<`, `>`, `&` or a comment terminator. */
function escapeDoc(text: string): string {
	return text
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/\*\//g, '*&#47;');
}

function javaString(text: string): string {
	return `"${text.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

// #endregion

// #region names

/** `nanoid` becomes `Nanoid`, `@noble/hashes` becomes `NobleHashes`, `edgeport/smtp` `EdgeportSmtp`. */
export function classNameOf(specifier: string): string {
	const parts = specifier
		.replace(/^@/, '')
		.split(/[^A-Za-z0-9]+/)
		.filter(Boolean);
	const name = parts.map((part) => capitalise(part)).join('');
	return /^[0-9]/.test(name) ? `N${name}` : name;
}

/** The JavaScript identifier the static import binds the module to. */
export function aliasOf(specifier: string): string {
	const name = specifier.replace(/^@/, '').replace(/[^A-Za-z0-9_$]+/g, '_');
	const prefixed = /^[0-9]/.test(name) ? `_${name}` : name;
	return JS_RESERVED.has(prefixed) ? `${prefixed}_` : prefixed;
}

function javaName(name: string): string {
	if (name === 'default') return 'defaultExport';
	const cleaned = name.replace(/[^A-Za-z0-9_$]/g, '_');
	const legal = /^[0-9]/.test(cleaned) ? `_${cleaned}` : cleaned;
	return JAVA_RESERVED.has(legal) ? `${legal}_` : legal;
}

function typeName(name: string): string {
	const cleaned = javaName(name);
	return capitalise(cleaned);
}

function paramName(name: string, index: number): string {
	const cleaned = javaName(name);
	return cleaned.length === 0 || cleaned.startsWith('_') ? `arg${index}` : cleaned;
}

// #endregion
