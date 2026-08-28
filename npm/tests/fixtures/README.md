# Test Fixtures

Compiled Java, used as real input by both test lanes. Each `.wasm` sits beside the `.java` it was
compiled from.

| Fixture         | Program          | What it exercises                                                     |
| --------------- | ---------------- | --------------------------------------------------------------------- |
| `hello.wasm`    | `Hello.java`     | entry point, stdout, the baseline size figure                         |
| `streams.wasm`  | `Streams.java`   | stdout and stderr separately, multi-line buffering                    |
| `thrower.wasm`  | `Thrower.java`   | an uncaught Java exception crossing back into JavaScript              |
| `queued.wasm`   | `Queued.java`    | `Thread.start`, which queues a fiber instead of running it            |
| `sleeper.wasm`  | `Sleeper.java`   | `Thread.sleep`, a chained fiber due in the future                     |
| `imported.wasm` | `Imported.java`  | `@JSBodyImport`, resolved from a statically supplied module namespace |
| `argv.wasm`     | `Argv.java`      | argv passthrough and a file read over the `bytebox:fs` import         |
| `direct.wasm`   | `Direct.java`    | `ByteBuffer.allocateDirect`, which TeaVM 0.15.0 cannot run            |
| `core.wasm`     | `CoreWorker.java` | the whole of `bytebox-core` against real bindings                    |

`hello.wasm-runtime.js` is TeaVM's generated runtime. It carries no per-module content, so one copy
serves every fixture. `hello.wasm-runtime.d.ts` declares the one member bytebox drives.

`core.wasm` is the one that matters most: a handler implementing `Worker`, resolving KV, R2 and D1
from `Env`, suspending on promises, and reaching the platform builtins. The workers lane drives it
against miniflare's own KV, R2 and D1.

## Rebuilding

Compiled with TeaVM 0.15.0 against a JDK 21 toolchain:

```kotlin
teavm {
	all { mainClass = "fixture.Hello" }
	wasmGC {
		modularRuntime = true
		minDirectBuffersSize = 0
		obfuscated = true
		debugInformation = false
		sourceMap = false
		optimization = OptimizationLevel.AGGRESSIVE
	}
}
```

`Imported.java` and `Argv.java` also need `org.teavm:teavm-jso:0.15.0`; `CoreWorker.java` needs
`bytebox-core` on the compile classpath.

The build is reproducible: the same source and settings emit a byte-identical `hello.wasm` and an
identical runtime. The Gradle plugin generates these fixtures in a later phase; until then they are
committed as build output.
