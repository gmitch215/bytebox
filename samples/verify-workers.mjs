import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// buildWorker only compiles and packs; nothing in the build instantiates what it wrote, so a module
// the engine refuses ships green. This loads each one the way its generated Worker does.
// usage: node verify-workers.mjs [root]; needs `bun run --cwd npm build` first

const major = Number(process.versions.node.split('.')[0]);
if (major < 24) {
	console.error(`verify-workers.mjs needs Node 24 or later; this is ${process.versions.node}`);
	process.exit(1);
}

const here = dirname(fileURLToPath(import.meta.url));
const root = process.argv[2] ? resolve(process.argv[2]) : here;
const { load } = await import(pathToFileURL(join(here, '..', 'npm', 'dist', 'loader.js')).href);

const workers = readdirSync(root, { withFileTypes: true })
	.filter((entry) => entry.isDirectory())
	.map((entry) => {
		const src = join(root, entry.name, 'build', 'bytebox', 'worker', 'src');
		return { name: entry.name, src, wasm: join(src, 'app.wasmbin') };
	})
	.filter((worker) => {
		try {
			return statSync(worker.wasm).isFile();
		} catch {
			return false;
		}
	});

if (workers.length === 0) {
	console.error('no built Workers found; run `../gradlew buildWorkers` first');
	process.exit(1);
}

// the keys of the `modules: { ... }` map index.ts hands to load(); nothing else is supplied
function suppliedModules(source) {
	const map = /modules:\s*\{([^}]*)\}/.exec(source);
	if (!map) return [];
	return [...map[1].matchAll(/"([^"]+)"\s*:/g)].map((entry) => entry[1]);
}

// answers every member the loader or the runtime probes; instantiation never calls one
function stub() {
	const noop = () => {};
	return new Proxy(noop, {
		get: (_, key) => (typeof key === 'symbol' ? undefined : noop),
		has: () => true
	});
}

const failures = [];

for (const worker of workers) {
	const bytes = readFileSync(worker.wasm);
	let stubbed = [];
	try {
		stubbed = suppliedModules(readFileSync(join(worker.src, 'index.ts'), 'utf8'));
		const runtime = await import(pathToFileURL(join(worker.src, 'app.wasm-runtime.js')).href);
		const modules = Object.fromEntries(stubbed.map((name) => [name, stub()]));
		load({ runtime, bytes, modules });
		console.log(
			`ok    ${worker.name.padEnd(18)} ${bytes.length}${stubbed.length ? `  stubbed: ${stubbed.join(', ')}` : ''}`
		);
	} catch (refused) {
		console.log(`FAIL  ${worker.name.padEnd(18)} ${bytes.length}  ${refused.message}`);
		failures.push(worker.name);
	}
}

console.log(`\n${workers.length} Workers, ${failures.length} refused`);
if (failures.length > 0) process.exit(1);
