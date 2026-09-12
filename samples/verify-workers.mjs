import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// buildWorker only compiles and packs; nothing in the build instantiates what it wrote, so a module
// the engine refuses ships green. This is the step that refuses it.

const root = dirname(fileURLToPath(import.meta.url));

const workers = readdirSync(root, { withFileTypes: true })
	.filter((entry) => entry.isDirectory())
	.map((entry) => ({
		name: entry.name,
		wasm: join(root, entry.name, 'build', 'bytebox', 'worker', 'src', 'app.wasmbin')
	}))
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

const failures = [];

for (const worker of workers) {
	const bytes = readFileSync(worker.wasm);
	try {
		// the same options the loader compiles with; without the builtins every module fails here
		new WebAssembly.Module(bytes, { builtins: ['js-string'] });
		console.log(`ok    ${worker.name.padEnd(18)} ${bytes.length}`);
	} catch (refused) {
		console.log(`FAIL  ${worker.name.padEnd(18)} ${bytes.length}  ${refused.message}`);
		failures.push(worker.name);
	}
}

console.log(`\n${workers.length} Workers, ${failures.length} refused`);
if (failures.length > 0) process.exit(1);
