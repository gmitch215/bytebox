# Experiments

Measurement projects behind [TECHNICAL_REPORT.md](../TECHNICAL_REPORT.md). Each one answers a single
question about the platform, the compiler or a library, and most of them are a Worker of the same
shape as [samples/hello-world](../samples/hello-world) with one thing changed. They are a separate
composite build and are not part of CI, because several of them are expected to fail: a refused build
is the result they exist to record.

The [samples](../samples) show how to use bytebox. These show where its edges are.

| Experiment                             | Question                                                                        |
| -------------------------------------- | ------------------------------------------------------------------------------- |
| [dep-none](dep-none)                   | The baseline every library build is compared against                            |
| [dep-guava](dep-guava)                 | Guava 33.4.0                                                                    |
| [dep-commons](dep-commons)             | Commons Lang 3.17.0                                                             |
| [dep-jackson-core](dep-jackson-core)   | Jackson Core 2.18.2, the streaming layer on its own                             |
| [dep-jackson](dep-jackson)             | Jackson Databind 2.18.2                                                         |
| [dep-log4j-api](dep-log4j-api)         | The Log4j 2.24.3 API                                                            |
| [dep-log4j](dep-log4j)                 | Log4j 2.24.3 Core                                                               |
| [lib-codec](lib-codec)                 | Commons Codec 1.17.1                                                            |
| [lib-collections4](lib-collections4)   | Commons Collections 4.4                                                         |
| [lib-gson](lib-gson)                   | Gson 2.11.0                                                                     |
| [lib-joda](lib-joda)                   | Joda-Time 2.13.0                                                                |
| [lib-json](lib-json)                   | org.json 20250107                                                               |
| [lib-okhttp](lib-okhttp)               | OkHttp 4.12.0                                                                   |
| [lib-slf4j](lib-slf4j)                 | SLF4J 2.0.16 with no provider                                                   |
| [lib-snakeyaml](lib-snakeyaml)         | SnakeYAML 2.3                                                                   |
| [conformance](conformance)             | Whether retargeted classes print what a JVM prints, and regex backtracking cost |
| [isolate](isolate)                     | How much Java heap one request can allocate before the isolate refuses          |
| [memory](memory)                       | What `minDirectBuffersSize` sizes, staged through a `byte[]`                    |
| [monitors](monitors)                   | Whether `synchronized` compiles to real monitors                                |
| [wrappers](wrappers)                   | Which JavaScript values make the runtime cache a Java wrapper                   |
| [jsbody](jsbody)                       | Which `@JSBody` constructs the compiler accepts                                 |
| [probe](probe)                         | A scratch Worker for checking one class-library behaviour at startup            |
| [lock-delegation](lock-delegation)     | Where a blocking lock can suspend, one build per cell                           |
| [socket-write](socket-write)           | What a `Socket` write costs per call                                            |
| [kotlin-jvm](kotlin-jvm)               | Kotlin/JVM through the bytebox plugin                                           |
| [kotlin-coroutines](kotlin-coroutines) | kotlinx-coroutines 1.11.0 through the bytebox plugin                            |
| [kotlin-wasmjs](kotlin-wasmjs)         | Kotlin/Wasm's `wasmJs` target on workerd, without bytebox                       |
| [kotlin-wasmwasi](kotlin-wasmwasi)     | Kotlin/Wasm's `wasmWasi` target on workerd, without bytebox                     |
| [kotlin-js](kotlin-js)                 | Kotlin/JS with coroutines on workerd, without bytebox                           |

## Building

```sh
cd experiments
../gradlew :dep-guava:buildWorker
```

The Java experiments use the plugin in this tree, the same way the samples do, and write a Worker into
`build/bytebox/worker`. A library experiment that fails to build has done its job: the first lines of
the TeaVM error are the measurement.

The three Kotlin targets that bypass bytebox build with Kotlin's own tasks, then copy the output next
to a hand-written entry point:

```sh
../gradlew :kotlin-wasmjs:wasmJsProductionExecutableCompileSync
sh kotlin-wasmjs/worker/copy.sh

../gradlew :kotlin-wasmwasi:compileProductionExecutableKotlinWasmWasiOptimize
sh kotlin-wasmwasi/worker/copy.sh

../gradlew :kotlin-js:compileProductionExecutableKotlinJs
sh kotlin-js/worker/copy.sh
```

Each `worker` directory then runs with `bunx wrangler dev`.

## Kotlin

Kotlin reaches a Worker by four routes. Kotlin/JVM is the one bytebox ships, as
[samples/kotlin](../samples/kotlin). The other three are separate Kotlin compilers that never pass
through TeaVM, and they stay experiments.

All four were measured on 2026-10-08 with Kotlin 2.4.20, deployed to a free-plan account, four
uploads per arm. The control is `samples/hello-world`, measured in two sessions. Startup is the `Worker Startup Time` that
`wrangler deploy` prints, in whole milliseconds. Each Kotlin program is a hello handler, so these
figures describe the floor of each route and not a real program, since startup grows with the amount
of compiled code.

| Route                   | Output                                      | Upload     | Startup median | Startup range |
| ----------------------- | ------------------------------------------- | ---------- | -------------- | ------------- |
| Java, bytebox (control) | 25,334 B wasm + 16,425 B runtime JavaScript | 65.97 KiB  | 11 and 13.5 ms | 9-18 ms       |
| Kotlin/Wasm, `wasmJs`   | 3,563 B wasm                                | 9.52 KiB   | 1 ms           | 1-2 ms        |
| Kotlin/Wasm, `wasmWasi` | 9,731 B wasm                                | 10.87 KiB  | 1 ms           | 1 ms          |
| Kotlin/JS               | 321,035 B of ES modules, 316,638 B bundled  | 309.22 KiB | 2 ms           | 1-3 ms        |

### kotlin-jvm and kotlin-coroutines

Kotlin/JVM bytecode goes through the same TeaVM WasmGC pipeline as Java, so everything in `core` is
available to it and a Kotlin `object` can be the handler. Data classes, collection functions and
string templates compile with `kotlin-stdlib` and print what the JVM prints. Bringing in
`kotlin-stdlib` makes the module larger than a Java one with the same behaviour. The kotlin-jvm handler
built to 221,364 bytes against the 26,193 of the Java baseline, `dep-none`.

kotlinx-coroutines 1.11.0 does not build. All six coroutine programs in `kotlin-coroutines` are
refused, the first refusal coming from the library's own setup code, which needs
`ReentrantReadWriteLock`, `LockSupport` and a thread's context class loader. A coroutine written
against the standard library alone, with `startCoroutine`, compiles, but V8 rejects the module: the
type TeaVM emits for `ContinuationImpl`'s virtual table is not a subtype of `Object`'s, so the
generated code fails validation. The fault is in the module TeaVM emits; its cause inside TeaVM is not
established. Kotlin/JVM ships without coroutines.

### kotlin-wasmjs

The `wasmJs` target produces a WasmGC module and a JavaScript loader. The module runs on workerd and
on the deployed platform; the loader does not. It locates the `.wasm` file by URL relative to
`import.meta.url` and fetches it, which fails on workerd with `Invalid URL string`. With
`nodejs_compat` on, it takes its Node branch instead and fails on `import.meta.resolve`, which workerd
does not provide.

`worker/src/index.mjs` replaces the loader in 14 lines. It imports the `.wasm` as a bundled module and
compiles it with `new WebAssembly.Module` while the Worker loads, which is the same pattern bytebox
uses for its own output. Kotlin's `js-builtins.mjs` polyfill supplies the `wasm:js-string` imports, so
no engine flag is needed, and the deployed V8 accepted both the legacy and the new exception-handling
proposals.

It is not a supported target for three reasons:

- **A second binding layer:** every binding in `core` is a TeaVM `@JSBody` declaration, which the
  Kotlin/Wasm compiler cannot read. Supporting `wasmJs` means writing and maintaining Kotlin
  `external` declarations for `Request`, `Response`, `Env` and every binding, parallel to the Java
  ones.
- **A hand-written loader:** the 14-line entry point stands in for Kotlin's generated loader. The
  plugin would have to generate it and follow any change in the imports the module expects.
- **Scope:** the experiment passes a string in and a string out. Passing a `Request`, returning a
  `Promise`, and coroutines on `wasmJs` were not tried, and Kotlin/Wasm is still Beta.

### kotlin-wasmwasi

The `wasmWasi` target produces a module that imports WASI preview 1. Kotlin's loader builds that
environment from `node:wasi`, and workerd's `node:wasi` is a stub whose constructor throws `The WASI
method is not implemented`. `worker/src/index.mjs` supplies the imports itself in 38 lines of
JavaScript: enough of WASI preview 1 for the program to print through `fd_write`.

It is not a supported target because a Worker would run on that hand-written WASI layer. Every system
call a program reaches has to be written and kept correct in JavaScript, and only `fd_write` is
written here, without `clock_time_get`, `random_get` or `proc_exit`. `@WasmExport` also accepts only
numbers and booleans, so the handler is `handle(n: Int): Int` and text leaves the module through
standard output. A request handler would need a string encoding over linear memory on top.

### kotlin-js

Kotlin/JS compiles to ES modules, which load on workerd unchanged behind a five-line entry point. It
is the only one of the three that runs kotlinx-coroutines: the handler awaits a coroutine and returns
its value, locally and deployed.

The cost is size. The hello handler uploads 309.22 KiB, almost five times the Java hello world,
because it carries the Kotlin standard library and coroutines as JavaScript (173,997 and 136,727
bytes of the 321,035). Startup is lower than the Java control's, because a JavaScript module is
parsed rather than compiled in full, so the two figures describe different work. Per-request CPU
time could not be told apart. The Kotlin/JS arm had occasional 6-15 ms requests that the control did
not, and their cause was not isolated.

It is not a supported target for the same reason as `wasmJs`: it never passes through TeaVM, so
nothing in `core` reaches it, and supporting it means a third set of binding declarations. Cloudflare's
own [kotlin-worker-hello-world](https://github.com/cloudflare/kotlin-worker-hello-world) template
takes this route.
