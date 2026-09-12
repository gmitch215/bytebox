# Technical Report

Java compiled to WebAssembly and executed inside a Cloudflare Worker. Every figure here was measured
on the runtime named beside it. Nothing is estimated.

## Summary

Java runs on Cloudflare Workers. A `System.out.println("hello world!")` compiled with TeaVM 0.15.0 to
the WebAssembly GC target instantiates and executes inside workerd, and the complete Worker measures
**33.27 KiB uncompressed**, which is the figure Cloudflare meters, against 64 MiB on either plan.

Getting there requires a purpose-built loader. TeaVM's own loading path cannot run on Workers at all,
for reasons that are properties of the platform rather than defects in TeaVM.

| Result                                             | Value                                      |
| -------------------------------------------------- | ------------------------------------------ |
| Hello world, raw wasm                              | 16,539 bytes                               |
| Hello world, gzip -6                               | 6,996 bytes                                |
| Complete Worker, wrangler meter                    | **33.27 KiB** of a 64 MiB ceiling          |
| Worker Startup Time, deployed                      | **10 ms** median, 8-11, n=9                |
| `cpuTime` per request, deployed                    | **0-1 ms** of a 10 ms free-plan limit, n=6 |
| Declared linear memory, default                    | 2,162,688 bytes                            |
| Declared linear memory, `minDirectBuffersSize = 1` | 1,114,112 bytes                            |

The deployed figures come from one Worker uploaded to an empty account, driven six times, and
deleted; the account was verified back to zero Workers afterwards. Startup time is Cloudflare's own
figure, reported on upload. `cpuTime` comes from `wrangler tail`, which emits one event per
invocation.

## Measurement Rules

- Bundle figures come from `wrangler deploy --dry-run`, which reports `Total Upload` and a gzip
  figure. `Total Upload` is the uncompressed size and is the only one metered.
- **The meter changed on 2026-09-04.** Cloudflare removed the compressed limits of 3 MB free and
  10 MB paid, and now checks only the uncompressed bundle, at 64 MiB on both plans. Every gzip figure
  below was measured against the old ceiling and is kept as a comparison, not as a limit. The
  constraints that did not change are the 1 second startup budget and the 128 MB isolate.
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

Raw bytes in a `Data` module beat the compressed frame on both columns, land within 0.11 KiB of the
shape that does not run, and need no decompressor.

Under the old compressed ceiling that verdict was marginal: zstd -22 beats gzip -6 by 6.3% on a
16 KB binary, while a synchronous zstd decoder costs about 5.6 KB flat, so a frame only paid above a
crossover. The uncompressed meter removes that arithmetic. A frame is now the smaller of the two on
the figure Cloudflare checks and it loses anyway, because inflating one runs at module scope, inside
the 1 second a Worker has to start, and raw bytes have nothing to inflate. The ratios below are
unchanged and kept for comparison:

| Binary        | raw     | gzip -6 | zstd -22 | zstd advantage |
| ------------- | ------- | ------- | -------- | -------------- |
| hello world   | 16,539  | 6,996   | 6,556    | 440 (6.3%)     |
| threads       | 33,540  | 13,820  | 12,685   | 1,135 (8.2%)   |
| charsets      | 44,212  | 16,685  | 15,265   | 1,420 (8.5%)   |
| BigDecimal    | 56,398  | 19,696  | 17,670   | 2,026 (10.3%)  |
| locale        | 216,127 | 82,657  | 67,894   | 14,763 (17.9%) |
| String.format | 249,270 | 93,028  | 77,483   | 15,545 (16.7%) |

Against the old compressed ceiling the advantage passed the decoder's fixed cost between 56 KB and
216 KB of raw wasm, putting the crossover near **120-140 KB raw**. That crossover no longer decides
anything: the plugin ships raw bytes at every size a Java Worker reaches, and reaches for a frame only
past 64 MiB, where raw bytes are refused outright.

## Startup Scaling

Measured 2026-09-12 on deployed Workers. Cloudflare reports startup on upload and there is no way to
read it locally, so every reading here is one `wrangler deploy` against a live account. Three arms,
n=4, interleaved round by round; the account was verified back to its starting Worker count of 9
afterwards.

Every upload carried a unique WebAssembly custom section, so no two uploads shared a content hash and
each compile was cold by construction. The entry point and the runtime JavaScript are byte-identical
across the arms, so the compiled module is the only thing that differs.

| Arm | Artifact                                 | Wasm bytes | Upload     | Readings       | Median | Range |
| --- | ---------------------------------------- | ---------- | ---------- | -------------- | ------ | ----- |
| A   | `samples/hello-world`                    | 25,343     | 65.73 KiB  | 11, 10, 8, 9   | 9.5    | 8-11  |
| B   | `samples/standard-library`               | 515,208    | 544.12 KiB | 14, 20, 17, 16 | 16.5   | 14-20 |
| C   | arm A padded to arm B's exact byte count | 515,208    | 544.12 KiB | 10, 10, 7, 8   | 9.0    | 7-10  |

**Arm C is the result.** It uploads the same 544.12 KiB as arm B and contains only hello world's code;
the other 490 KB is an inert custom section. It measures at arm A, half a millisecond below it. **The
7 ms that B costs over A is bought by compiled code, not by bundle bytes.**

The arms separate completely: the lowest B reading, 14, is above the highest A reading, 11, and the
highest C reading, 10. Nothing overlaps across four interleaved rounds.

The gzip column strengthens this rather than weakening it. C's padding is incompressible noise, so it
went over the wire at 502 KiB against B's 174 KiB, roughly 2.9x more transferred bytes, and still
started about 7 ms faster. Neither the metered uncompressed figure nor the transferred compressed
figure predicts startup here. Only the quantity of real wasm code does.

Two points at 25 KB and 515 KB say nothing about the shape of the curve between or beyond them, and
this design does not separate the features inside arm B, so no per-feature cost and no per-kilobyte
rate is given. One earlier reading of an instrumented 1.25 MB npm test fixture came in at a 21.5 ms
median, which is consistent with sub-linear growth and is not a shipped artifact.

Arm B's 20 ms is the one questionable reading, 3 ms above its next highest. The uploads either side
of it were 10 ms each, so there was no slow window on the platform; it reads as variance inside that
arm's own compile. Dropping it moves B's median to 16.0 and changes nothing.

Pooling every hello world reading taken this way, n=13, the median is 10 and the range is 8 to 11.
**The 8 ms this report carried before was the minimum of that distribution rather than its centre.**

## Defects

Two samples built and could not run. Both were found by deploying during the startup measurement, and
neither was visible to any lane: `e2e.yml` ran `buildWorkers`, which compiles and packs but never
instantiates, so a sample could be broken indefinitely while CI stayed green. `samples/verify-workers.mjs`
now closes that hole by compiling every built module with the loader's own options, and runs in e2e
between the build and the size report. It refuses the first defect below and would have refused it
from the day it appeared. It does not catch the second, which failed at load rather than at compile,
and that one is now pinned by three generator tests instead.

**`samples/standard-library` does not compile.** Loading its module raises:

```txt
CompileError: WebAssembly.Module(): Compiling function #134 failed:
type error in fallthru[0] (expected (ref null 30), got (ref null 500)) @+59609
```

Reproduced three ways on the pristine artifact straight out of Gradle, with no packing step involved:
node's own V8 with `{ builtins: ['js-string'] }`, local workerd under `wrangler dev`, and the edge on
upload. Same offset every time. It is a TeaVM WasmGC codegen defect, it is specific to whatever this
sample links that the other eight do not, and this sample has never run. It is the largest sample and
therefore the one the Startup Scaling arms wanted, which is why those arms use npm test fixtures
instead.

**`samples/tcp-client` could not be deployed. Fixed 2026-09-12.** The loader refused it:

```txt
ImportError: the compiled module imports "cloudflare:sockets", which load() was not given
```

`GenerateScaffoldTask` emitted `load({ runtime, bytes, modules: { ... } })` only for declared npm
packages. A platform module the compiled program imports, which is what `java.net.Socket` retargets
to, reached `installModules()` as a required import with no namespace supplied, and was then reported
as missing. Any project using `java.net.Socket` generated a Worker that refused at startup.

The fix is in the generator, not the loader. The loader's contract is that a caller supplies what the
program imports, and the generator is the caller it writes. It now reads the module's own
`teavm.imports` custom section, which is where the compiler records the table, and emits a static
import and a `modules` entry for every `cloudflare:` specifier it finds. Only those are derived: an
undeclared npm package stays the project's own error, because declaring one also writes it into the
manifest, while a `cloudflare:` specifier has nowhere to be declared.

Verified by running the sample: under `wrangler dev` it loads, opens a TCP connection through
`cloudflare:sockets` and answers `example.com answered HTTP/1.1 200 OK`.

## Reproducibility

The same source and the same compiler settings emit a byte-identical binary and an identical runtime
across runs. That makes a committed binary usable as a test input with no drift, and makes a size
figure attributable to a change rather than to the build.

## Configuration Effects

| Setting                    | Effect                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `minDirectBuffersSize = 1` | declared linear memory 2,162,688 to 1,114,112 bytes; wasm size unchanged                    |
| `modularRuntime = true`    | runtime JavaScript 17,268 to 16,425 bytes, and an ES module rather than a global assignment |

## Not Yet Measured

- **Wrapper-map growth.** The compiler's runtime bridges Java and JavaScript object identity through
  three `WeakMap`s of `WeakRef`s and five `FinalizationRegistry` instances. Cloudflare documents
  finalizer callbacks as running in quiet slots between I/O phases, with non-deterministic timing and
  no guarantee they run at all. Whether the maps grow without bound needs a long-lived isolate under
  sustained interop, which a hello world does not produce.
- ~~**The crossover between raw and compressed packaging**, which the Packaging table brackets between
  56 KB and 216 KB of raw wasm but does not pin.~~ Closed 2026-09-11 without being measured: the
  compressed ceiling it would have informed was removed on 2026-09-04, and under the uncompressed one
  the packaging decision turns on startup rather than on where the two curves cross.
- **`ByteBuffer.allocateDirect` on a later TeaVM.** The failure is reproducible under TeaVM's own
  loader on 0.15.0, which places it upstream, but no version after that has been tried.
- **Whether the three refused `@JSBody` constructs share one cause.** Each was found by building a
  real Worker and each is fixed by the rewrite named beside it, but an isolated program containing
  the same three constructs compiled without complaint. So the rule above is a reliable practice
  rather than a pinned parser boundary, and what makes the difference between the two cases is not
  yet known.
- **Whether the two regular expression engines agree about the syntax they share.** The translator's
  rewrites are checked by running the translated source through a JVM's engine and comparing it with
  the original pattern, which is what pins the constructs that differ. Whether V8 and a JVM then agree
  about the constructs neither rewrites needs a corpus run inside workerd.
- **Whether `Double.toString` produces the same shortest decimal on both.** `String.format`'s digits
  are computed from it, and a JVM and TeaVM producing different shortest decimals for the same double
  would change the last digit of a formatted number. Nothing observed suggests they differ, and
  nothing has measured it.
- **How much a catastrophic backtracking pattern costs on the platform's engine.** The two engines
  backtrack differently, so a pattern that is slow on one is not necessarily slow on the other, and a
  free-plan request has 10 ms of CPU.
- **`cpuTime` for a Worker that uses the retargeted class library.** The `cpuTime` figure in the
  Summary comes from a hello world. Startup for a larger binary is measured in Startup Scaling above;
  `cpuTime` for one is not.
