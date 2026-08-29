# bytebox

> The Cloudflare Workers loader for Java compiled to WebAssembly

```sh
bun add @gmitch215/bytebox
```

This is the JavaScript half of [bytebox](https://github.com/gmitch215/bytebox). The Gradle plugin
generates a Worker that imports it, so a project built with `./gradlew buildWorker` needs nothing
from this page. Reach for it directly to assemble a Worker by hand.

## Why It Exists

TeaVM's own loader cannot run on Workers. It compiles a feature-detection module from bytes, which
the runtime refuses outside module evaluation; it takes the Node branch whenever `process` is
defined, which `nodejs_compat` makes true; and it resolves npm imports through a dynamic `import()`
of a string read from a wasm custom section, which no bundler can follow.

## Loading A Module

```ts
import { load } from '@gmitch215/bytebox';
import * as runtime from './app.wasm-runtime.js';
import bytes from './app.wasmbin';

const instance = await load({ runtime, bytes });
```

`load` must be called during module evaluation. Workers allows WebAssembly compilation at startup
and forbids it inside a request, so a module compiled lazily on the first request throws.

## Handling Requests

One instance serves every request in the isolate, and a Java fiber that suspends yields to the event
loop, so a second request can arrive while the first is parked. The gate serialises entry:

```ts
import { createGate, load } from '@gmitch215/bytebox';

const instance = await load({ runtime, bytes });
const gate = createGate(instance);

export default {
	async fetch(request, env, ctx) {
		return gate.fetch(request, env, ctx);
	}
};
```

## Timers

Compiled Java schedules its fibers on a queue the host owns. Workers freeze the clock between I/O,
so a `Thread.sleep` measured against `Date.now()` would never come due; the scheduler advances a
clock of its own to cover one and gives the compiled program the same clock to read.

Each trigger drains under a fiber budget rather than a wall-clock timeout, because no CPU meter is
readable from inside a request. `FIBER_BUDGET` carries the defaults.

## Exports

| Import                         | Provides                                                     |
| ------------------------------ | ------------------------------------------------------------ |
| `@gmitch215/bytebox`           | `load`, `createGate`, `createScheduler`, the error types     |
| `@gmitch215/bytebox/loader`    | `load`, `requiredModules`                                    |
| `@gmitch215/bytebox/gate`      | `createGate`                                                 |
| `@gmitch215/bytebox/scheduler` | `createScheduler`, `FIBER_BUDGET`                            |
| `@gmitch215/bytebox/errors`    | `LoadError`, `EntryPointError`, `ImportError`, `BudgetError` |
| `@gmitch215/bytebox/bundler`   | an esbuild plugin that stubs the `node:*` imports out        |
| `@gmitch215/bytebox/bindgen`   | generates Java bindings from a package's TypeScript types    |

`esbuild` and `typescript` are optional peers, needed only by `@gmitch215/bytebox/bundler` and
`@gmitch215/bytebox/bindgen`. A Worker at runtime needs neither.

`@gmitch215/bytebox/bindgen` also installs a command. The two are the same tool: `bytebox-bindgen`
parses arguments and calls `writeBindings`, which is the function `@gmitch215/bytebox/bindgen`
exports. The Gradle plugin runs the command; a TypeScript caller imports the function.

```sh
bunx bytebox-bindgen --out src/generated nanoid @noble/hashes
```

## License

[MIT](LICENSE)
