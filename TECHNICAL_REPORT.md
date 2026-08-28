# Technical Report

Java compiled to WebAssembly and executed inside a Cloudflare Worker. Every figure here was measured
on the runtime named beside it. Nothing is estimated.

## Summary

Java runs on Cloudflare Workers. A `System.out.println("hello world!")` compiled with TeaVM 0.15.0 to
the WebAssembly GC target instantiates and executes inside workerd, and the complete Worker measures
**11.82 KiB on wrangler's gzip meter** against a 3 MB free-plan ceiling.

Getting there requires a purpose-built loader. TeaVM's own loading path cannot run on Workers at all,
for reasons that are properties of the platform rather than defects in TeaVM.

| Result                                             | Value                                      |
| -------------------------------------------------- | ------------------------------------------ |
| Hello world, raw wasm                              | 16,539 bytes                               |
| Hello world, gzip -6                               | 6,996 bytes                                |
| Complete Worker, wrangler meter                    | **11.82 KiB** of a 3 MB ceiling            |
| Worker Startup Time, deployed                      | **8 ms** of a 1 second limit               |
| `cpuTime` per request, deployed                    | **0-1 ms** of a 10 ms free-plan limit, n=6 |
| Declared linear memory, default                    | 2,162,688 bytes                            |
| Declared linear memory, `minDirectBuffersSize = 0` | 65,536 bytes                               |

The deployed figures come from one Worker uploaded to an empty account, driven six times, and
deleted; the account was verified back to zero Workers afterwards. Startup time is Cloudflare's own
figure, reported on upload. `cpuTime` comes from `wrangler tail`, which emits one event per
invocation.

## Measurement Rules

- Bundle figures come from `wrangler deploy --dry-run`, which reports `Total Upload` and the gzip
  figure the ceiling is enforced against. A local `gzip -c | wc -c` is not that meter.
- Runtime behaviour comes from `wrangler dev --local`, which runs workerd. Node and bun disagree with
  it on every result in the Codegen section.
- Compression ratios are a function of input size. A ratio measured on one binary does not transfer to
  another, and one that did not transfer is corrected in Packaging below.

## Codegen

workerd permits WebAssembly compilation during module evaluation and forbids it inside a request. The
rule is finer than that summary, and the difference decides the design.

| Operation                                     | Module scope                                                        | Request                                                     |
| --------------------------------------------- | ------------------------------------------------------------------- | ----------------------------------------------------------- |
| `new WebAssembly.Module(bytes)`               | ok                                                                  | `CompileError: Wasm code generation disallowed by embedder` |
| `new WebAssembly.Module(bytes, { builtins })` | ok                                                                  | same                                                        |
| `WebAssembly.compile(bytes)`                  | **never settles**                                                   | `CompileError`                                              |
| `WebAssembly.compileStreaming(response)`      | `Disallowed operation called within global scope. Asynchronous I/O` | **not a function**                                          |
| `new Function('return 1')`                    | ok                                                                  | `EvalError: Code generation from strings disallowed`        |

Only synchronous compilation at module scope works. The asynchronous form never settles, because
resolving it needs I/O that Workers do not permit outside a request context, and a module whose
top-level `await` never settles fails to start with `Uncaught Error: Top-level await in module is
unsettled`.

`new Function` inverts the pattern: permitted at module scope, refused inside a request. Any wrapper
function built from a string must therefore be materialised during startup.

## JS String Builtins

TeaVM's WasmGC output imports seven functions from the `wasm:js-string` namespace. A hello world
imports all seven, including `fromCharCodeArray` and `intoCharCodeArray`, which take WebAssembly GC
arrays that JavaScript cannot address.

Compiling with `{ builtins: ['js-string'] }` reduces those imports to zero; compiling without leaves
all seven unsatisfied. A wrangler `CompiledWasm` module is compiled by the platform, which offers no
way to pass the option, so a Worker built that way fails at startup:

```txt
Uncaught TypeError: WebAssembly.Instance(): Import #4 "wasm:js-string":
module is not an object or function
```

The binary must therefore arrive as bytes and be compiled at module scope. `CompiledWasm` is
unavailable to this toolchain, and no compatibility flag changes that.

## Packaging

Three shapes, same hello world, `wrangler deploy --dry-run`:

| Shape                                  | Total Upload | wrangler gzip | Runs                                         |
| -------------------------------------- | ------------ | ------------- | -------------------------------------------- |
| `CompiledWasm`                         | 33.16 KiB    | 11.77 KiB     | **no**, unsatisfied `wasm:js-string` imports |
| `Data` + zstd -22, inflated by `fzstd` | 38.85 KiB    | 16.57 KiB     | yes                                          |
| `Data` + raw bytes, no decompressor    | 33.27 KiB    | **11.82 KiB** | yes                                          |

Raw bytes in a `Data` module win by 4,864 bytes, land within 51 bytes of the shape that does not run,
and need no decompressor.

**A pre-compressed frame costs more than it saves at this size.** zstd -22 beats gzip -6 by 6.3% on a
16 KB binary, while a synchronous zstd decoder costs about 5.6 KB flat. The advantage grows with
input:

| Binary        | raw     | gzip -6 | zstd -22 | zstd advantage |
| ------------- | ------- | ------- | -------- | -------------- |
| hello world   | 16,539  | 6,996   | 6,556    | 440 (6.3%)     |
| threads       | 33,540  | 13,820  | 12,685   | 1,135 (8.2%)   |
| charsets      | 44,212  | 16,685  | 15,265   | 1,420 (8.5%)   |
| BigDecimal    | 56,398  | 19,696  | 17,670   | 2,026 (10.3%)  |
| locale        | 216,127 | 82,657  | 67,894   | 14,763 (17.9%) |
| String.format | 249,270 | 93,028  | 77,483   | 15,545 (16.7%) |

The advantage passes the decoder's fixed cost between 56 KB and 216 KB of raw wasm, so the crossover
sits near **120-140 KB raw**. Below it, ship raw bytes; above it, ship a zstd frame.

## Size by Feature

Each row is one program compiled on its own, TeaVM at `AGGRESSIVE` optimisation, obfuscated, no debug
information or source maps. The delta is against the hello world in the same configuration.

| Program                     | raw     | delta raw | gzip -6 | delta gzip  |
| --------------------------- | ------- | --------- | ------- | ----------- |
| hello world                 | 16,539  | —         | 6,996   | —           |
| `java.util.Date`            | 16,656  | +117      | 7,068   | +72         |
| `StringBuilder`             | 17,449  | +910      | 7,338   | +342        |
| exceptions                  | 17,668  | +1,129    | 7,536   | +540        |
| streams and collectors      | 23,372  | +6,833    | 10,016  | +3,020      |
| reflection                  | 25,334  | +8,795    | 10,838  | +3,842      |
| collections                 | 25,513  | +8,974    | 10,569  | +3,573      |
| threads                     | 33,540  | +17,001   | 13,820  | +6,824      |
| charset round trip          | 44,212  | +27,673   | 16,685  | +9,689      |
| `BigDecimal`                | 56,398  | +39,859   | 19,696  | +12,700     |
| `java.util.regex`           | 237,389 | +220,850  | 72,343  | **+65,347** |
| `Locale` and `NumberFormat` | 216,127 | +199,588  | 82,657  | **+75,661** |
| `String.format`             | 249,270 | +232,731  | 93,028  | **+86,032** |

Three APIs account for almost all of the weight, and each costs an order of magnitude more than the
whole hello world. Reflection, collections and streams are inexpensive.

`UUID.nameUUIDFromBytes` does not compile. TeaVM refuses with the exact member:

```txt
Method java.util.UUID.nameUUIDFromBytes([B)Ljava/util/UUID; was not found
```

A class library gap is a build failure naming the method, not a runtime surprise.

## Where the Bytes Are

The code section attributed by function name, on modules built with obfuscation off so the name
section survives. The compiler's runtime is close to a fixed cost; the class library is what grows.

| Program                 | code section | compiler runtime | class library  | own code      |
| ----------------------- | ------------ | ---------------- | -------------- | ------------- |
| hello world             | 12,693       | 10,049 (79.2%)   | 2,140 (16.9%)  | 504 (4.0%)    |
| collections and streams | 16,674       | 10,480 (62.9%)   | 4,050 (24.3%)  | 2,144 (12.9%) |
| `String.format`         | 125,534      | 25,807 (20.6%)   | 98,718 (78.6%) | 1,009 (0.8%)  |

On a program that does real work the class library is four fifths of the binary and the runtime is a
rounding error, so size work belongs in the class library rather than in the compiler.

The largest single group inside the fixed runtime is `org.teavm.runtime.heap`, at roughly 3,500 bytes
across five functions. It is the linear-memory allocator, which a program that never touches direct
buffers has no use for.

## Kotlin

Kotlin compiles through the same toolchain. Measured on the same axes as the Java fixtures:

| Program                                 | code section | Kotlin standard library |
| --------------------------------------- | ------------ | ----------------------- |
| hello world                             | 12,697       | none attributed         |
| data classes, collections, lambdas      | 40,326       | 7,438 (18.4%)           |
| `KClass.simpleName` and `qualifiedName` | 26,448       | 5,703 (21.6%)           |

A Kotlin hello world is four bytes larger than the Java one, and dead-code elimination removes the
standard library entirely when nothing reaches it.

**Coroutines do not compile.** `kotlinx-coroutines-core` needs classes the class library does not
have, and the failure is a build error with the call chain attached:

```txt
Class java.util.concurrent.locks.LockSupport was not found
    at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:97)
    at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:70)
```

Choosing `Dispatchers.Unconfined` does not avoid it. Referencing `Dispatchers` at all runs its static
initializer, which constructs the default thread-pool scheduler and reaches
`java.util.concurrent.atomic.AtomicReferenceArray`, also absent. A dispatcher built on the fiber
queue would compile; the shipped ones do not.

## Available Platform APIs

Measured inside workerd at compatibility date 2026-08-01.

| API                                   | Available | Result                                                   |
| ------------------------------------- | --------- | -------------------------------------------------------- |
| `Intl.NumberFormat`                   | yes       | `de-DE` formats 1234.5 as `1.234,5`                      |
| `Intl.DateTimeFormat` with `timeZone` | yes       | `ja-JP` in `Asia/Tokyo` renders correctly                |
| `Intl` locale coverage                | yes       | all four of `de-DE`, `ja-JP`, `ar-EG`, `hi-IN` supported |
| `FinalizationRegistry`, `WeakRef`     | yes       | callback timing is not guaranteed                        |
| `setTimeout`                          | yes       |                                                          |
| `SharedArrayBuffer`                   | present   | no worker threads exist to synchronise with              |
| `self.location`                       | undefined |                                                          |

Full ICU data is present in the isolate, so locale and time zone tables do not need to be carried
inside the binary.

## Node Compatibility

`process` is a global whenever Node compatibility is active, and TeaVM's loader treats its presence as
proof it is running under Node.

| Compatibility date | Flags                                     | `typeof process` |
| ------------------ | ----------------------------------------- | ---------------- |
| 2026-08-01         | none                                      | `undefined`      |
| 2026-08-26         | none                                      | `object`         |
| 2026-08-26         | `no_nodejs_compat`, `no_nodejs_compat_v2` | `undefined`      |

Compatibility dates from 2026-08-04 enable Node compatibility by default, so a current date makes
`process` appear and sends TeaVM's loader down a path that reaches `node:fs/promises`. esbuild reports
the same import as a bundling warning.

Bundle size is **33.27 KiB / 11.82 KiB in all three configurations**, so disabling Node compatibility
is a correctness measure and not a size measure.

## Entry Points and the Event Queue

A compiled `main` is exported as a WebAssembly `Global` holding the callable, not as a function
export. Reading `instance.exports.main` yields a `Global`; its `.value` is the function.

The module also exports `teavm_processQueue` and `teavm_stopped`, which the host calls to drive
TeaVM's fibers. `teavm_stopped` did not return true after `main` returned, so a host that pumps until
it does will not terminate. Quiescence needs a different signal.

## Configuration Effects

| Setting                    | Effect                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `minDirectBuffersSize = 0` | declared linear memory 2,162,688 to 65,536 bytes; wasm size unchanged                       |
| `modularRuntime = true`    | runtime JavaScript 17,268 to 16,425 bytes, and an ES module rather than a global assignment |

## Not Yet Measured

- **Wrapper-map growth.** The compiler's runtime bridges Java and JavaScript object identity through
  three `WeakMap`s of `WeakRef`s and five `FinalizationRegistry` instances. Cloudflare documents
  finalizer callbacks as running in quiet slots between I/O phases, with non-deterministic timing and
  no guarantee they run at all. Whether the maps grow without bound needs a long-lived isolate under
  sustained interop, which a hello world does not produce.
- **The crossover between raw and compressed packaging**, which the Packaging table brackets between
  56 KB and 216 KB of raw wasm but does not pin.
