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
| `cpuTime`, retargeted class library, deployed      | **43 ms** median, 2-79, n=8                |
| Heap one request can allocate, deployed            | **254 MiB** ok, 256 MiB refused            |
| Declared linear memory, default                    | 2,162,688 bytes                            |
| Declared linear memory, `minDirectBuffersSize = 1` | 1,114,112 bytes                            |

Startup time is Cloudflare's own figure, reported on upload. `cpuTime` comes from `wrangler tail`,
which emits one event per invocation.

The hello world figures come from one Worker uploaded to an empty account, driven six times, and
deleted, with the account verified back to zero afterwards. The 2026-09-17 figures come from a free
account already holding ten Workers: the count was recorded before anything was deployed, three
Workers were created, and the count was confirmed back at ten with none of the three remaining. Every
deployed measurement in this report was taken on the free plan.

## Measurement Rules

- Bundle figures come from `wrangler deploy --dry-run`, which reports `Total Upload` and a gzip
  figure. `Total Upload` is the uncompressed size and is the only one metered.
- **The meter changed on 2026-09-04.** Cloudflare removed the compressed limits of 3 MB free and
  10 MB paid, and now checks only the uncompressed bundle, at 64 MiB on both plans. Every gzip figure
  below was measured against the old ceiling and is kept as a comparison, not as a limit. The
  constraint that did not change is the 1 second startup budget. The isolate limit also did not
  change, but it is documented as 128 MB and measures at 254 MiB here; see The Isolate Ceiling.
- Runtime behaviour comes from `wrangler dev --local`, which runs workerd. Node and bun disagree with
  it on every result in the Codegen section.
- Compression ratios are a function of input size. A ratio measured on one binary does not transfer to
  another, and one that did not transfer is corrected in Packaging below.

## Environment

Every figure in this report was produced on this configuration unless a section names another.

| Component                   | Version                                     |
| --------------------------- | ------------------------------------------- |
| Compiler                    | TeaVM 0.15.0, WasmGC target                 |
| Compiled target             | Java 21 toolchain                           |
| Reference JVM               | OpenJDK 26.0.2.1, Homebrew build 2026-08-18 |
| Host runtime for local runs | node v26.8.2                                |
| Deploy tool                 | wrangler 4.x                                |
| Build                       | Gradle 9.7.1                                |
| Machine                     | Darwin 25.5.0, arm64                        |

TeaVM 0.15.0 is the newest release there is. Nothing later is published on Maven Central and no
snapshot is available, so anything recorded here as failing under the compiler cannot be retried
against a newer one, including `ByteBuffer.allocateDirect`, whose failure is reproducible under
TeaVM's own loader and therefore sits upstream.

The reference JVM and the compiled target are different versions on purpose: the target is what the
samples compile against, and the reference is what a differential transcript is compared with. The
one place the difference could matter is `Double.toString`, whose shortest-decimal behaviour changed
in JDK 19; both versions here are past that change, so they agree with each other and the comparison
is against post-19 semantics.

Local runs go through node rather than workerd wherever the result does not depend on the embedder,
which is every differential result below. Startup and `cpuTime` are the exceptions, because
Cloudflare reports them and nothing else can.

The sections that came with 1.0.3 name the reference JVM they used where it differs from the table:
OpenJDK 21.0.8 (Temurin, the toolchain's major) for the regex, formatter and zone transcripts, with
25.0.2 and 27 as cross-checks where a section says so, and OpenJDK 27 for the Kotlin transcripts and
the `CompletableFuture` scenarios.

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

Arm A's module is 25,343 bytes as built on 2026-09-12. The same sample builds to 25,334 bytes on the
1.0.3 tree.

Arm C uploads the same 544.12 KiB as arm B and contains only hello world's code; the other 490 KB is
an inert custom section. It measures at arm A, half a millisecond below it. **The 7 ms that B costs
over A is bought by compiled code, not by bundle bytes.**

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
from the day it appeared. When this section was written it did not catch the second, which failed at
load rather than at compile, and that one was pinned by three generator tests instead. Since 1.0.3
the script catches both; see The Sample Check Loads Every Module.

**`samples/standard-library` did not compile. Fixed 2026-09-12.** Loading its module raised:

```txt
CompileError: WebAssembly.Module(): Compiling function #134:"java.time.ZoneOffset::getRules"
failed: type error in fallthru[0] (expected (ref null 30), got (ref null 500)) @+59609
```

Not a TeaVM codegen defect. The module carried two unrelated `ZoneRules` types, which the name
section names: type 30 `java.time.zone.ZoneRules` and type 76
`dev.gmitch215.bytebox.time.ZoneRules`. `ZoneOffset.getRules()` declared the first and returned
`ZoneRules$Fixed`, whose supertype is the second.

The substitution translates `java.time.zone.ZoneRules` into a copy of bytebox's class under the
original name, while `IntlZoneRules` and the nested `Fixed` went on extending the bytebox name, which
kept the original linked as a second root type. The fix is composition: `ZoneRules` is a final
concrete class holding either a bare offset or an `IntlZoneRules`, and `IntlZoneRules` extends
nothing. `final` is what stops it recurring, because javac now refuses the subclass that caused it.

A cheaper fix was measured and rejected. Deleting the `java.time.zone.ZoneRules` rule also compiles,
by letting ThreeTen-Backport's own rules through, which restores the 108 KB timezone database and
takes the unobfuscated sample from 689,162 to 1,112,576 bytes.

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

## The Sample Check Loads Every Module

`samples/verify-workers.mjs` used to compile each built module and stop. It now imports
`npm/dist/loader.js`, reads the keys of the `modules: { ... }` map in each sample's `src/index.ts`,
supplies a stub for each, and calls `load` the way the generated entry point does. The stubs answer
every member with a no-op, so a pass shows that the module instantiates and that every platform
import is supplied. It does not run `main`, because a static initialiser may call a stub and a stub's
return value would make a pass or a fail meaningless.

One variable was changed to test it: a copy of `samples/tcp-client` with the `cloudflare:sockets`
import and its `modules` entry removed, which reproduces the second defect above.

| Arm                           | Old script                       | New script                                                                                             |
| ----------------------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `tcp-client` as built         | `ok`                             | `ok`, `stubbed: cloudflare:sockets`                                                                    |
| `tcp-client`, import removed  | `ok    tcp-client         41262` | `FAIL`, `the compiled module imports "cloudflare:sockets", which load() was not given`                 |
| `app.wasmbin` cut by one byte | not run                          | `FAIL`, `section (code 0, "Unknown") extends past end of the module (length 152, remaining bytes 151)` |

All twelve samples built that day load, on Node 24.20.0 and on Node 26.8.2, with no
`--experimental-wasm-exnref` flag. Node 22.23.3 printed nothing and was killed after 120 seconds; the
cause was not diagnosed, and the reading that Node 22's V8 predates the `js-string` builtins is an
inference that was not tested. The e2e job therefore pins Node 24. The script is deterministic, so no
spread applies. Source: `build/batch/v103/results/B6.md`.

## Java Libraries

Fourteen library builds, each in the same Worker shape, against a baseline of the same shape with no
library. Measured 2026-09-17. Projects are under `experiments/`, which is its own composite build and
is not wired into CI, because nine of the fourteen are expected to fail.

Five compile and run:

| Library                 | Raw wasm | gzip -6 | Over baseline | What the program calls                 |
| ----------------------- | -------- | ------- | ------------- | -------------------------------------- |
| none, baseline          | 26,193   | 11,087  | 0             | one string constant                    |
| Commons Collections 4.4 | 30,523   | 12,430  | 4,330         | `DualHashBidiMap`, `getKey`            |
| Guava 33.4.0-jre        | 51,862   | 19,693  | 25,669        | `ImmutableList.of`, `Joiner.on().join` |
| Commons Lang 3.17.0     | 89,535   | 30,734  | 63,342        | seven `StringUtils` methods            |
| org.json 20250107       | 125,722  | 43,434  | 99,529        | `JSONObject.put`, `toString`           |
| Jackson Core 2.18.2     | 330,555  | 115,455 | 304,362       | `JsonFactory`, generator and parser    |

Nine were refused while compiling when these were measured. Missing is the count of distinct classes
and methods the build named, and the wall is which of the three it stopped at. The two rows that no
longer stop a build are listed after the table:

| Library                 | Wall  | Missing | First blocker reported                        |
| ----------------------- | ----- | ------- | --------------------------------------------- |
| Joda-Time 2.13.0        | one   | 1       | `Collections.unmodifiableSortedSet`           |
| Gson 2.11.0             | three | 5       | `Class.getGenericSuperclass`                  |
| Log4j API 2.24.3        | three | 8       | `ClassLoader.getResources`                    |
| Log4j Core 2.24.3       | three | 8       | `ClassLoader.getResources`                    |
| Jackson Databind 2.18.2 | three | 9       | `Class.getGenericSuperclass`                  |
| SnakeYAML 2.3           | three | 10      | `java.beans.Introspector`                     |
| OkHttp 4.12.0           | two   | 20      | `android.net.http.X509TrustManagerExtensions` |

Every size prices the reached subset after dead-code elimination, not the library, which is why the
last column is there. Commons Lang is the clearest case: the same dependency measured 64,656 bytes
when the program called one method and 89,535 when it called seven. Quote these as the cost of a
usage, never as the cost of a library.

The gzip column is kept for comparison with the rest of this report and is not metered by anything.
What a larger module costs is startup, which Startup Scaling prices at roughly 7 ms per half megabyte
of compiled code.

Two rows left the refusal table in 1.0.3. Commons Codec 1.17.1 compiles and runs once
`java.security.MessageDigest` is supplied (MessageDigest and the Next Wall). SLF4J API 2.0.16
compiles at 179,191 bytes, 152,998 over the baseline, once `ClassLoader` answers with an empty
classpath and `LinkedBlockingQueue` exists (Logging Facades). The sizes above and the counts in the
table are the 2026-09-17 builds and were not repeated.

The refusals arrive in three layers, and a build hits them in order. First, classes the class library
simply lacks: all of `java.util.concurrent.locks`, the array atomics, and `java.util.StringJoiner`.
Neither refused library uses a lock for parallelism. Second, references into optional integrations
that whole-program compilation cannot know are optional, which is where Log4j stops:
`org.osgi.framework.*` and `org.fusesource.jansi.*` are not on the classpath and are still required.
Third, generic reflection and dynamic loading, which is where Jackson stops: TeaVM's `TClass` has no
`getEnclosingMethod` and no `getGenericSuperclass`, and its `TClassLoader` has no `loadClass`, all
confirmed by reading the classlib jar.

Supplying the first layer was tried and is reported under A Limit of the Substitution SPI and Locks
That Block. Three pieces of it shipped: `java.util.StringJoiner`, which unblocked Commons Lang's join APIs, and
`java.util.concurrent.locks.ReentrantLock` with `java.util.concurrent.atomic.AtomicReferenceArray`,
which are the whole of what `jackson-core` was missing.

Two of the refusals have a workaround and one does not. `jackson-core` is the streaming half of
Jackson and stops at the first wall only, so supplying those two classes is enough: it writes and
parses JSON on the runtime. `jackson-databind` is the reflective half and stops at the third wall,
where supplying classes cannot help. Log4j stops in the same place whether the dependency is
`log4j-core` or `log4j-api`, because `LoaderUtil.findUrlResources` calls `ClassLoader.getResources`
to scan the classpath for `log4j2.component.properties` while the property system initialises, which
happens before any logger is requested. There is no classpath at run time to enumerate. That
explanation was right for SLF4J and incomplete for Log4j; see Logging Facades.

The lock shipped with the first measurement never waited, and threw on contention. Since 1.0.3 it
blocks; see Locks That Block.

Fourteen builds were put through this, and the three walls held across all of them. The two that
stopped at wall one stopped one class short: Gson needs the two array atomics, which now ship, and
then hits generic reflection exactly where Jackson Databind does. Joda-Time is the narrowest refusal
measured, blocked by a single missing method, `Collections.unmodifiableSortedSet`; supplying that
method alone is not possible, see Collections. Commons Codec was blocked by
`java.security.MessageDigest`, which the platform has no class for, and compiles since 1.0.3.

Wall two is clearest in OkHttp, which needs `android.net.http.X509TrustManagerExtensions` and
nineteen other classes that are not on the classpath and never would be, because they are its Android
integration. Wall three had four members when this was written: Jackson Databind, Gson and SnakeYAML
by reflection, and every logging facade tried, both Log4j halves and SLF4J, by classpath
enumeration. SLF4J has since left it, so **a logging facade has now compiled**, and Log4j stays out
for reasons beyond enumeration.

Commons Lang then shows the fourth thing that can happen, which is a library compiling and meeting a
retargeted API at run time. `StringUtils` compiles `\p{InCombiningDiacriticalMarks}+` in its class
initialiser and the regex translator refused it by name when this was measured; the translator reads
that pattern since 1.0.3 (Case Folding) and the run was not repeated, so the next wall is unknown.
Six of seven measured calls worked. Two details came out of reordering them: the refusal landed on
whichever `StringUtils` call ran first, and later calls succeeded, where a JVM marks a class whose
initialiser threw as erroneous for the rest of the program. The second is a deviation from the JVM
and belongs upstream.

## Kotlin on the JVM Target

Measured 2026-10-08 with Kotlin 2.4.20, TeaVM 0.15.0 and node v26.8.2, against a JVM on JDK 27. The
question was whether a Kotlin project builds through the plugin and behaves as it does on a JVM.
Projects are `experiments/kotlin-jvm` and `experiments/kotlin-coroutines`.

**Three defects stood between a Kotlin project and a Worker, and the plugin now handles all three.**
A build hit them in this order:

| #   | First error                                                                                                                                          | Layer                        | Cause and fix                                                                                                                                                                                     |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `Task ':kotlin-jvm:compileKotlin' uses this output of task ':kotlin-jvm:generateNpmBindings' without declaring an explicit or implicit dependency`   | Gradle wiring                | the plugin adds the generated bindings directory to the Java sources, and the Kotlin plugin puts every Java source directory on `compileKotlin` too; `compileKotlin` now runs after the generator |
| 2   | `Task ':kotlin-jvm:compileByteboxKotlin' uses this output of task ':kotlin-jvm:generateCodecs' without declaring an explicit or implicit dependency` | Gradle wiring                | the `bytebox` source set gets a `compileByteboxKotlin` with no ordering after the generators; it now runs after the entry point and both codec generators                                         |
| 3   | `ByteboxEntry.java:15: error: ObjectWorker() has private access in ObjectWorker`                                                                     | javac on the generated entry | a Kotlin `object` has a private constructor; the entry reads a public static `INSTANCE` of the handler's own type when there is one                                                               |

A handler written as a class with a public no-argument constructor needed none of them. A Kotlin
`object` that is a Durable Object is refused at generation with a message naming the cause, because
a Durable Object is one instance per id. `kotlin-stdlib` reaches TeaVM with nothing added, and
`generateCodecs` reads a `@JSONType` Kotlin class from the Kotlin output directory.

Behaviour was compared by running the compiled module under node and the same source on a JVM, and
diffing the printed lines.

| Handler                     | Constructs | Lines | Difference from the JVM |
| --------------------------- | ---------- | ----- | ----------------------- |
| `ClassWorker`               | 13         | 29    | none                    |
| `ObjectWorker`, same source | 13         | 29    | none                    |
| `ProbeWorker`               | 21 more    | 21    | none                    |

The constructs include collections, `Sequence`, string templates, data classes, lambdas, `inline`,
`when`, `?.` and `?:`, `lateinit`, companion objects, `enum class`, `String.format`, `Regex`,
extension functions, `lazy`, `sequence { yield }`, `by` delegation, sealed classes with an exhaustive
`when`, `kotlin.random.Random(42).nextInt(1000)` (333 on both sides), `runCatching` and `use`. The
diff is over printed lines only, so a difference in a value nobody printed is invisible.

**kotlinx-coroutines 1.11.0 is refused by TeaVM in all six programs measured.** Each stops in
`generateWasmGC` with "Errors occurred during TeaVM build". Every program sits inside `runBlocking`,
so the first refused class is the same:

| Program                              | First refused class, and where it is reached from                                             | Refusals, unique / total |
| ------------------------------------ | --------------------------------------------------------------------------------------------- | ------------------------ |
| `runBlocking { }`                    | `java.util.concurrent.locks.LockSupport`, at `BlockingCoroutine.joinBlocking(Builders.kt:57)` | 8 / 27                   |
| `runBlocking { launch { println } }` | same                                                                                          | 8 / 27                   |
| `launch` and `join`                  | same                                                                                          | 8 / 27                   |
| `delay(10)`                          | same                                                                                          | 9 / 28                   |
| `withContext(Dispatchers.Default)`   | same                                                                                          | 8 / 27                   |
| `launch(Dispatchers.Unconfined)`     | same                                                                                          | 8 / 27                   |

Without `runBlocking`, `CoroutineScope(Dispatchers.Unconfined).launch { println }` is refused at
`java.util.concurrent.locks.ReentrantReadWriteLock`, reached from `WeakMapCtorCache.<clinit>`
(`ExceptionsConstructor.kt:111`), with 8 unique refusals that still include `LockSupport`. The
distinct refusals, read from the `delay` and `scope-unconfined` logs, are `LockSupport`,
`ReentrantReadWriteLock` with its two nested lock classes, `RejectedExecutionException`,
`Thread.setContextClassLoader(ClassLoader)`, `ClassLoader.loadClass(String)` (from the Kotlin
standard library, not kotlinx) and, for `delay` only, `ClassLoader.getResources(String)`.

**A suspend lambda started with the standard library's `startCoroutine` builds and then fails V8
validation.** No kotlinx class is referenced. The same message appeared in four variants (an inline
`Continuation`, an explicit `object : Continuation<Unit>`, `createCoroutine(done).resume(Unit)`, and
a hand-written `CoroutineContext`), at functions 134, 126, 141 and 126:

```txt
LoadError: could not compile the module: CompileError: WebAssembly.Module(): Compiling function #134
failed: struct.set[1] expected type (ref null 12), found global.get of type (ref 126) @+23024
```

An unobfuscated diagnostic build puts it in `com.example.CoWorker::<clinit>`. The global is typed
`(ref $VT<kotlin.coroutines.jvm.internal.ContinuationImpl>)`, and that type (type 126) is declared as
a root with one field and no supertype, where its sibling `$VT<...BaseContinuationImpl>` extends
`$VT<kotlin.coroutines.Continuation>`. So the vtable type of `ContinuationImpl` is not a subtype of
`$VT<java.lang.Object>`. That is the observed declaration; why TeaVM 0.15.0 emits it was not
established. `sequence { yield(...) }` builds on `RestrictedSuspendLambda` and runs, so the defect is
specific to a plain `SuspendLambda` started through `startCoroutine` or `createCoroutine`.

| Arm                                 | Raw wasm bytes         | Against `dep-none`  | Outcome               |
| ----------------------------------- | ---------------------- | ------------------- | --------------------- |
| `dep-none`, Java control            | 26,193                 | 1.00x               | runs                  |
| `ClassWorker`, 13 constructs        | 221,364                | 8.45x               | runs, matches the JVM |
| `ObjectWorker`, same constructs     | 222,879                | 8.51x               | runs, matches the JVM |
| `ProbeWorker`, 21 more constructs   | 275,285                | 10.51x              | runs, matches the JVM |
| `JsonWorker`, one `@JSONType` class | 40,479                 | 1.55x               | builds, see below     |
| `stdlib-suspend`                    | 39,827                 | 1.52x               | invalid module        |
| `stdlib-suspend-b`, `-c`, `-d`      | 38,153, 39,388, 38,473 | 1.46x, 1.50x, 1.47x | invalid module        |

The control gave the identical md5 (`c9de89b007306e27a904abc3b63544ad`) in two builds with a plugin
change between them. The Kotlin arms are one build each. Size is a function of the constructs a
program reaches, not a fixed cost for Kotlin: the 221 KB is mostly the reached part of
`kotlin-stdlib`. A sample with a data class, `filter`, `associate`, `joinToString` and string
templates, `samples/kotlin`, is 43,486 bytes and was built twice with the same result.

**Rebuilt on 2026-10-09, after the regular expression, formatter, lock and module-start work.** The
same experiment and sample, one build each from one tree, bytes counted with `wc -c` on
`app.wasmbin`:

| Arm                             | 2026-10-08 | 2026-10-09 | Against `dep-none` now |
| ------------------------------- | ---------- | ---------- | ---------------------- |
| `dep-none`, Java control        | 26,193     | 27,417     | 1.00x                  |
| `ClassWorker`, 13 constructs    | 221,364    | 304,569    | 11.11x                 |
| `ObjectWorker`, same constructs | 222,879    | 304,498    | 11.11x                 |
| `ProbeWorker`, 21 more          | 275,285    | 359,988    | 13.13x                 |
| `samples/kotlin`                | 43,486     | 46,761     | 1.71x                  |

The control grew 1,224 bytes, and the three Kotlin transcripts grew 83,205, 81,619 and 84,703. The
growth is not diagnosed. The regular expression and formatter code (B2 to B4) and the module-start
fiber are the candidates, and a build between the two dates found 293,019 for `ClassWorker`. The
transcript output was not rerun against a JVM on the new builds, so the "matches the JVM" column
above is the 2026-10-08 result. The 2026-10-08 figures are kept so the two dates stay comparable.

**Coroutines after `ClassLoader` and the blocking lock.** `experiments/kotlin-coroutines`, five
programs (`empty`, `launch`, `delay`, `withcontext`, `unconfined`), all still `BUILD FAILED` at
`generateWasmGC` with `java.util.concurrent.locks.LockSupport` first and 26 `was not found` lines
each. The distinct refusals fell from 8 to 7 for `empty` and from 9 to 7 for `delay`: `LockSupport`,
`ReentrantReadWriteLock` with its `ReadLock` and `WriteLock`, `RejectedExecutionException`,
`Thread.setContextClassLoader` and `CoroutineScheduler$Worker.setContextClassLoader`.
`ClassLoader.loadClass` and `ClassLoader.getResources` are answered. Source: `results/V3.md`.

**Not measured:** run time on workerd (cpuTime, startup); the `@JSONType` codec round trip at run
time (the handler's static initialiser called `JSON.stringify` before `ByteboxCodecs.register()` ran
and printed `no JSON codec is registered for com.example.Cfg`, which a Java handler does too);
Durable Object, `Scheduled`, `Mail` and `Consumer` handlers in Kotlin; and a Kotlin `suspend fun
fetch`. The mechanism closed is kotlinx-coroutines 1.11.0 as it stood against the 1.0.2 class
library, and a plain `SuspendLambda` through TeaVM 0.15.0. Kotlin coroutines on bytebox stays open.
Sources: `build/batch/v103/results/A1.md` and `B1.md`.

## Kotlin/Wasm on Workers

Measured 2026-10-08 with Kotlin 2.4.20 (`kotlin("multiplatform")`), node v26.8.2, wrangler 4.127.0
(miniflare 5.20260815.0-alpha, which declared workerd 1.20260815.1 and was the lane's version at
the time; the installed binary's own version was not read), `compatibility_date` 2026-08-22 and `no_nodejs_compat` with
`no_nodejs_compat_v2`. **The scope is a hello `fetch` handler per target, so nothing here says how a
real Kotlin/Wasm program behaves.**

| Check                    | `wasmJs`                                                                            | `wasmWasi`                                             |
| ------------------------ | ----------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Optimized wasm           | 3,563 bytes (28,020 before binaryen)                                                | 9,731 bytes (88,727 before binaryen)                   |
| Rebuild md5              | `de0c74972b86fb2ee16d80224f066b56`, same                                            | `1d90d2815b25807ef3a0ba070f8f3853`, same               |
| Imports                  | 12 `js_code` functions, 3 `wasm:js-string`, 27 string constants, 1 `intrinsics.tag` | 2 `wasi_snapshot_preview1` (`fd_write`, `poll_oneoff`) |
| Default exceptions       | legacy `try`/`catch`                                                                | `try_table` and `throw_ref`                            |
| Stock loader on workerd  | fails                                                                               | fails                                                  |
| Hand-written `index.mjs` | 14 lines                                                                            | 38 lines                                               |
| Local workerd            | loads, `/boom` answers `caught boom`                                                | loads, `handle(4) = 9`, `handle(-1) = -1`              |
| Other exception proposal | loads (`-PnewEh`, 3,574 bytes)                                                      | loads (`-PlegacyEh`, 9,706 bytes)                      |

Neither generated loader runs on workerd:

| Variant                                   | First error                                                          |
| ----------------------------------------- | -------------------------------------------------------------------- |
| `wasmJs` stock `.mjs`, no `nodejs_compat` | `Uncaught TypeError: Invalid URL string.`                            |
| `wasmJs` stock `.mjs`, `nodejs_compat`    | `Uncaught TypeError: (intermediate value).resolve is not a function` |
| `wasmWasi` stock `.mjs`, `nodejs_compat`  | `Uncaught Error: The WASI method is not implemented`                 |

The first takes the `fetch` branch, which builds a URL from `import.meta.url`; the second takes the
Node branch, which calls `import.meta.resolve`; the third reaches workerd's `node:wasi`, a stub
whose constructor throws. The replacement is the `.wasm` as a Data module and a synchronous
`new WebAssembly.Module` and `new WebAssembly.Instance` at module scope, the same rule as Codegen.
`wasmJs` needs no `CompiledWasm`: Kotlin ships `js-builtins.mjs`, which satisfies `wasm:js-string`
with plain JavaScript functions, and a variant passing no options loads and answers the same.
`wasmWasi` needs an own `wasi_snapshot_preview1` object, where `fd_write` copies stdout out of the
exported memory and `poll_oneoff` returns 52. A `@WasmExport` accepts only numeric and `Boolean`
types, so the handler is `handle(n: Int): Int` and the text reaches the Worker through stdout.

Deployed to the free account, startup being the `Worker Startup Time` line `wrangler deploy`
prints, in whole milliseconds. Four rounds, each round control, `wasmJs`, `wasmWasi`; every upload
carried a fresh 16-byte random custom section so no two shared a content hash.

| Arm                            | Wasm bytes | Total Upload (gzip)  | Startup per round (ms) | Median | Range |
| ------------------------------ | ---------- | -------------------- | ---------------------- | ------ | ----- |
| control, `samples/hello-world` | 25,334     | 65.97 KiB (21.79)    | 11, 9, 16, 18          | 13.5   | 9-18  |
| `wasmJs`                       | 3,563      | 9.52 KiB (3.75-3.76) | 1, 1, 2, 1             | 1      | 1-2   |
| `wasmWasi`                     | 9,731      | 10.87 KiB (5.40)     | 1, 1, 1, 1             | 1      | 1-1   |

`cpuTime` from `wrangler tail`, seven requests each, in ms: control 3, 1, 0, 0, 4, 2, 4; `wasmJs` 1,
1, 0, 0, 0, 0, 0; `wasmWasi` 0 on all seven. The control's startup range is wider than the 8-11 of
the n=13 pool in Startup Scaling, and the arms still separate completely: the highest Kotlin reading,
2, is below the lowest control reading, 9. **The arms are not like for like.** The control carries the
Java runtime and a handler that crosses the TeaVM bridge, and the Kotlin handlers are one string
concatenation and a `try`. A 3.5 KB and a 9.7 KB module have almost nothing to compile, and Startup
Scaling found startup scaling with compiled code, so these 1 ms readings say nothing about a Kotlin
program with collections or serialization. Both exception proposals loaded in production for both
targets; each target's default proposal has the four-upload series and the other proposal rests on
one deployed upload (the `wasmJs` upload printed no startup line, so it is not measured). The
account held no Worker before or after. Not measured: any larger program, kotlinx-coroutines on
`wasmJs`, a `Request` or a `Promise` across the boundary, WASI calls beyond `fd_write`. Source:
`build/batch/v103/results/A2.md`.

## Kotlin/JS on Workers

Measured 2026-10-08 with Kotlin 2.4.20, kotlinx-coroutines-core 1.11.0 and the same wrangler,
compatibility date and flags as the Kotlin/Wasm section. The scope is again one hello handler.

**Kotlin/JS (IR) with `useEsModules()` loads on workerd with a 5-line `index.mjs` and no runtime
shim.** `main` re-exports from the build output and wrangler bundles the rest. `@JsExport fun
handle(path: String): Promise<JsAny?>` is a named export. The handler builds its body after
`delay(1)` and `Promise.resolve(42.toJsNumber()).await()` inside `GlobalScope.promise { }`, and
answers `hello from kotlin js /x/y 42` locally and `hello from kotlin js /deployed 42` deployed. The
coroutine signatures were read in full from the 1.11.0 web target's API pages: `suspend fun <T :
JsAny?> Promise<T>.await(): T` and `fun <T> CoroutineScope.promise(context, start, block: suspend
CoroutineScope.() -> T): Promise<T>`; `GlobalScope.promise` needs `@DelicateCoroutinesApi`.

The emitted `.mjs` files are 321,035 bytes raw: the main module 4,629, the standard library 173,997,
coroutines 136,727, atomicfu 5,558 and an empty `dom-api-compat` 124. wrangler tree-shook about 4 KB.
Four rounds, each control then Kotlin, on the free account:

| Arm                            | Total Upload (gzip) | Startup per round (ms) | Median | Range |
| ------------------------------ | ------------------- | ---------------------- | ------ | ----- |
| control, `samples/hello-world` | 65.97 KiB (21.79)   | 10, 10, 12, 15         | 11     | 10-15 |
| Kotlin/JS                      | 309.22 KiB (54.75)  | 1, 3, 2, 2             | 2      | 1-3   |

The ranges do not overlap. The two arms are different kinds of startup work: a JavaScript source is
parsed, not compiled whole, so cold compile means something else for JS than for wasm.

| Arm       | Series | `cpuTime` (ms)                                               | Median | Range | n   |
| --------- | ------ | ------------------------------------------------------------ | ------ | ----- | --- |
| control   | 1      | 2, 2, 2, 0, 4, 0, 2                                          | 2      | 0-4   | 7   |
| Kotlin/JS | 1      | 5, 1, 7, 9, 0, 7, 12                                         | 7      | 0-12  | 7   |
| control   | 2      | 2, 0, 0, 0, 0, 2, 2, 0, 2, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0   | 0      | 0-2   | 20  |
| Kotlin/JS | 2      | 1, 8, 12, 15, 1, 6, 1, 1, 1, 1, 1, 1, 0, 8, 1, 1, 1, 6, 1, 0 | 1      | 0-15  | 20  |

The ranges overlap in both series, so **no difference is claimed**. What the table does show is
individual Kotlin/JS requests of 6 to 15 ms that the control never had in 27 requests, clustered near
the start of each series. The cause (JIT warm-up, collection of the 300 KB module's heap, or the
tail's sampling) was not isolated; a run with more requests and a warm-up discard would settle it.
The build printed four warnings: `kotlin.js.JsAny` and `toJsNumber` need the opt-in
`ExperimentalWasmJsInterop` on the `js` target in 2.4.20, and compile regardless. Source:
`build/batch/v103/results/A3.md`.

## MessageDigest and the Next Wall

The class library has no `java.security.MessageDigest` and no `DigestException`; it has
`NoSuchAlgorithmException`. Version 1.0.3 supplies both in pure Java: MD5, SHA-1, SHA-256, SHA-384
and SHA-512, with SHA-384 as `Sha512(true)`. The platform hashes through its own crypto, and nothing
retargets a `MessageDigest` onto it.

| Arm                                         | Result                                                                                                         |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `lib-codec`, substitute removed (control)   | refused, `Class java.security.MessageDigest was not found`                                                     |
| `lib-codec`, substitute present             | builds at 66,516 bytes, runs, prints `answer=c6cd749b880d987807bcec591c25e422685279d8f48e330d069c57e21a21426c` |
| `printf bytebox \| openssl dgst -sha256 -r` | `c6cd749b880d987807bcec591c25e422685279d8f48e330d069c57e21a21426c`                                             |
| `samples/hello-world`                       | 25,334 bytes, md5 `5ac53895a16e434e8537bcfb2700fa27` before and after                                          |

The JVM-lane tests compare against the JDK at lengths 0, 1, 55, 56, 63, 64, 65, 111, 112, 113, 127,
128, 129, 1000 and 4096 for all five algorithms, whole, split and byte by byte, and the workerd
route passes its three tests. The longer vectors were computed with `openssl`, not the JDK.
**Commons Codec 1.17.1's `DigestUtils.sha256Hex` runs and no further wall appeared, but `lib-codec`
calls only that method.** Whether `Base64`, `Hex` or `md5Hex` run was not tested, so the next
Commons Codec wall, if there is one, is not measured. SHA-224, the SHA-512 truncations, SHA-3 and MD2
throw `NoSuchAlgorithmException`. Differences from the JDK that are known: `toString` reads
`... from bytebox`, and a negative-offset `digest(buf, offset, len)` throws `IllegalArgumentException`
where the JDK throws `DigestException`. The hello-world arm shows unchanged output, not that the
substitute is unreachable from it. Source: `build/batch/v103/results/B8.md`.

## Logging Facades

An empty-classpath `java.lang.ClassLoader` and a non-blocking `LinkedBlockingQueue` let SLF4J 2.0.16
compile and run. The ship criterion was fixed before measuring: compiles, prints `answer=logged`, and
shows the no-provider message. All three held. Each row is one rebuild of `experiments/lib-slf4j`:

| Build   | What changed                                                                               | Distinct refusals                                                                       |
| ------- | ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| control | nothing                                                                                    | 4: `LinkedBlockingQueue`, `ClassLoader.getResources`, `getSystemResources`, `loadClass` |
| s1      | `ClassLoader` substitute with the resource methods, `getParent` and `getSystemClassLoader` | 2: `LinkedBlockingQueue`, `ClassLoader.loadClass`                                       |
| s2      | `loadClass` added, throws `ClassNotFoundException`                                         | 1: `LinkedBlockingQueue`                                                                |
| s3      | `LinkedBlockingQueue` added                                                                | 0, compiles                                                                             |

The result is 179,191 bytes against `dep-none`'s 26,193, which is 152,998 over, and the run prints
SLF4J's no-provider warning and `answer=logged`. With no provider SLF4J falls back to a no-op
logger, so "logs" means the facade runs and nothing is printed; a `slf4j-simple`-style binding was
not tried. The `ClassLoader` is closed-world: every lookup returns empty and `loadClass` throws
`ClassNotFoundException`. `Class.getResourceAsStream` returned null with and without the policy, so
the substitute made resource lookup no worse. `LinkedBlockingQueue` is non-blocking: `take()` on an
empty queue throws `IllegalStateException`. `samples/hello-world` is 25,334 bytes, md5
`5ac53895a16e434e8537bcfb2700fa27`, before and after.

**Log4j still stops.** The hypothesis that `getResources` was the blocker is refuted: it was the first
thing the build named, not the only one.

| Build           | Distinct refusals before | Distinct refusals after | Gone                        | Newly named                 |
| --------------- | ------------------------ | ----------------------- | --------------------------- | --------------------------- |
| `dep-log4j-api` | 12                       | 10                      | `getResources`, `loadClass` | none                        |
| `dep-log4j`     | 27                       | 26                      | `getResources`, `loadClass` | `java.net.JarURLConnection` |

The ten remaining for `log4j-api` are `RuntimePermission`, `Spliterators$AbstractSpliterator`,
`ReentrantReadWriteLock` (twice), three `org.osgi.framework` classes, `Class.getResource(String)`,
`System.getenv()`, `Date.toInstant()` and `ConcurrentHashMap.newKeySet()`. `log4j-core` adds, among
others, `CodeSource`, `Scanner`, `ConcurrentLinkedDeque`, `Executors`, `org.fusesource.jansi.Ansi`,
`Class.getProtectionDomain()`, `SecurityManager.getClassContext()` and `Thread.setContextClassLoader`
at 12 sites. The objective, a logging facade that compiles, is met for SLF4J and open for Log4j. The
two arms are counts of one build each, not properties of the library. Source:
`build/batch/v103/results/B7.md`.

## Collections

Joda-Time 2.13.0 stops at one missing method, `Collections.unmodifiableSortedSet`. A comparison of
`javap` output for the JDK and for the class library's `TCollections` puts **21 of the JDK's 74
public static methods** missing:

| Group        | Missing                                                                                                                                   |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| unmodifiable | `unmodifiableSortedSet`, `unmodifiableNavigableSet`, `unmodifiableSortedMap`, `unmodifiableNavigableMap`, and the three `Sequenced` forms |
| synchronized | `synchronizedSortedSet`, `synchronizedNavigableSet`, `synchronizedSortedMap`, `synchronizedNavigableMap`                                  |
| checked      | `checkedQueue`, `checkedSortedSet`, `checkedNavigableSet`, `checkedSortedMap`, `checkedNavigableMap`                                      |
| empty        | `emptySortedSet`, `emptyNavigableSet`, `emptySortedMap`, `emptyNavigableMap`                                                              |
| other        | `asLifoQueue`                                                                                                                             |

The substitution SPI renames one whole class, so a substitute carries everything the class has and
whatever it lacks is "not found" for every program that calls it. A substitute that delegates to
`TCollections` resolves and runs: with five methods, Joda-Time's `new LocalDate(2026, 1, 1).plusDays(59)`
printed `answer=2026-03-01`. Carrying the whole class costs 53 delegating methods, one cast-laden
method each (the delegates take `TList`, `TMap` and `TSet`, so every one throws `ClassCastException`
on a plain JVM and the JVM lane cannot test them), plus 21 new methods with wrapper classes. Five
methods took 36 lines; 53 would take roughly 190, an extrapolation, and the 21 new ones are
estimated at several hundred lines, not measured. The bar was about 60 lines beyond the missing
methods, so **nothing shipped**, and the default `lib-joda` build still fails with the same line as
before.

A fourth trap came out of the first attempts: **a substitution of an existing class library class
wins only when its policy is read before the class library's.** The compiler classpath puts the
core jar at index 0, the class library at index 1 and the project's own output at index 23, and
`originalToSubstitute` takes the first rule whose target exists, so the class library's rule from
`java` to `org.teavm.classlib.T*` beats a policy shipped by a project. That is why `Formatter` and
`Pattern` can be replaced from core and `Collections` could not be replaced from a project. A policy
loaded and ran in the project arm (a forced throw surfaced as `POLICY LOADED`), and the substitution
had no effect. The mechanism closed is a small additive patch to `Collections` through the SPI. The
objective, Joda-Time on the runtime, stays open: patching `ZoneInfoProvider` alone was not tried and
Joda's zone data was not measured. Source: `build/batch/v103/results/B9.md`.

## Direct Buffer Heap

`minDirectBuffersSize` is measured in megabytes and sizes the linear-memory heap the interop stages
every `byte[]` through. The declared minimum is `16 * n + 1` pages, so the heap is n MiB plus one
page. Measured by building the same Worker at each value and reading the `teavm.memoryRequirements`
custom section, then driving a 4 KB `byte[]` across the boundary in node.

| Value | Wasm bytes | Minimum pages | Declared heap | Crossing                                 |
| ----- | ---------- | ------------- | ------------- | ---------------------------------------- |
| 0     | 36,763     | 1             | 65,536        | `JavaError: Could not initialize buffer` |
| 1     | 36,767     | 17            | 1,114,112     | ok                                       |
| 2     | 36,767     | 33            | 2,162,688     | ok                                       |
| 4     | 36,767     | 65            | 4,259,840     | ok                                       |
| 8     | 36,769     | 129           | 8,454,144     | ok                                       |
| 16    | 36,769     | 257           | 16,842,752    | ok                                       |
| 64    | 36,770     | 1025          | 67,174,400    | ok                                       |

The module itself moves 7 bytes across the whole range, which is the decimal value written into a
custom section rather than anything generated. So the setting costs memory and not size, and 1 is the
floor rather than a budget: at 0 the heap is initialised empty and the first conversion fails
deterministically. Earlier prose in `ByteboxPlugin` said it spins in the allocator forever. It does
not; it fails fast, and the message names the cause.

## Monitors

`synchronized` is real on this target rather than compiled away, which matters because it decides
what a lock can mean here. Measured by a probe holding a monitor across two fibers:

- `synchronized` is reentrant, and a nested block on a held monitor proceeds
- a second fiber blocks: `main got the monitor back` printed before `waiter has the monitor`
- `Object.wait(1000)` suspended the waiting fiber, and it resumed
- `Thread.currentThread().getName()` is `main`

TeaVM implements this with an async callback on the monitor queue, so a fiber that cannot enter
suspends rather than spinning. A no-op lock would therefore be wrong here: a fiber can suspend inside
a critical section and let another fiber in, and a lock that does nothing would admit it.

## A Limit of the Substitution SPI

**This section recorded that a substituted class cannot contain a suspending call. That conclusion was
wrong, and Locks That Block replaces it.** What was observed on 2026-09-17 was real: adding
`Object.wait()` and `notifyAll()` to a substituted `java.util.StringJoiner` made the module trap at
instantiation with `RuntimeError: dereferencing a null pointer`, and removing them restored it.
`synchronized` on its own was fine, and so was `Thread.currentThread()`. Four other explanations were
tested and refuted, each by its own build: the interface and implementation being substituted as a
pair; a helper class naming the substitute in its own descriptors; three source packages sharing one
target package; and helpers living inside the substitution target package.

The cause was not substitution. An unsubstituted class with the same `wait()` traps the same way when
the call runs while the module starts up. The build that trapped most likely called its joiner from a
static initialiser; that program is not preserved, so this is inferred from the signature and not
shown. It still belongs beside the already-recorded rule that a substituted class loses its
`@JSBody` methods, which is a compiler-level transformation that does not survive substitution.

## Locks That Block

Measured 2026-10-09 (`date -u`) under the build lock, with node 26.8.2 and the loader's default
`virtual` time policy, one build per cell. The cells ran a program on a fiber and compared the printed
order. `java.util.concurrent.Semaphore` stood in for a class that is absent from the class library,
like `ReentrantLock`, so the shipped lock stayed the control. Two fibers contend for one permit.

| Cell | What                                                        | Printed order                                                                                      | Verdict                         |
| ---- | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------- |
| A    | shipped `ReentrantLock`, contention, before 1.0.3           | `t2 threw java.lang.IllegalStateException: another fiber is inside this lock...`, then `main done` | throws                          |
| A2   | `wait()` and `notifyAll()` inside the substitute            | `main releasing`, `t2 acquired`, `t2 released`, `main done`                                        | blocks correctly (42,757 bytes) |
| B    | substitute calls a static helper `Waits.acquire(int[])`     | same as A2                                                                                         | blocks correctly (42,950)       |
| C    | substitute holds an instance helper `sync.Gate`             | same                                                                                               | blocks correctly (42,905)       |
| D    | helper polls `Thread.sleep(1)`                              | same                                                                                               | blocks correctly (40,912)       |
| R    | the new `ReentrantLock`: contention, condition, timed waits | `t1 releasing` before `t2 acquired`; `t3 timed 30ms: false`; `await 20ms: false`                   | correct (51,500)                |
| F    | 34 `CompletableFuture` scenarios                            | `diff` of the module's transcript against JDK 27's prints nothing                                  | identical                       |

**No helper is needed.** `wait()` and `notifyAll()` directly inside the substitute (A2) block and wake
correctly, and so do a static helper, an instance helper and a sleep poll. The trap is a property of
when the call runs:

| Cell                       | Program                                                                          | Result                                |
| -------------------------- | -------------------------------------------------------------------------------- | ------------------------------------- |
| P                          | unsubstituted `PlainGate` with `wait()`, uncontended acquire, static initialiser | traps, `dereferencing a null pointer` |
| A2 with `-Pprog=clinit`    | substituted `Semaphore`, uncontended acquire, static initialiser                 | traps                                 |
| B, D with `-Pprog=clinit`  | helper delegation, same program                                                  | trap                                  |
| A2 with `-Pprog=fetchonly` | same class reachable only from `fetch`, never run                                | loads and runs, prints `clinit ran`   |
| I                          | the new `ReentrantLock`, `lock()` and `unlock()` uncontended, static initialiser | traps                                 |
| J                          | `CompletableFuture.completedFuture(1).join()`, static initialiser                | traps                                 |

The name section places the trap in `org.teavm.runtime.Fiber::isResuming`, called from
`ByteboxEntry::main_$caller$exported$1`. **Reaching a method TeaVM compiled as async while the module
starts traps, whether or not the call would wait.** With the entry point as it stood when these
cells ran, an uncontended `lock()` in a static initialiser trapped where the old lock, which never
waited, had worked. Module Start on a Fiber removes the trap. `jackson-core`, the only measured
consumer, prints the same output before and after (408,261 and 408,430 bytes, md5
`44c2650080bd5d1fb491d274190dcd04` and `b482d1afd74ce4b74ec7c7ea995e16fd`).

A clock finding changed the design. After `Thread.sleep(20)` a program saw `nano 0 ms, millis 20`:
**`System.nanoTime()` does not move across a sleep under the virtual time policy, and
`System.currentTimeMillis()` does.** The first timed waits used `nanoTime`, and `tryLock(10ms)` on a
lock held for 80 ms returned `true` after 60 virtual milliseconds. They count on `currentTimeMillis`
now, and the cell printed `tryLock(10ms) while held for 80: false ... millis 10`. The limit is that
a wall-clock step moves a deadline, which a JVM's monotonic clock would not. Two more facts from the
class library jar: it has no `CompletableFuture`, `CompletionStage`, `Future`, `TimeoutException`,
`Semaphore`, `CountDownLatch`, `Executors` or `ForkJoinPool`, and it has `Executor`,
`ExecutionException`, `CancellationException`, `Callable`, `TimeUnit`, `ConcurrentHashMap`,
`ArrayBlockingQueue` and `LinkedBlockingDeque`. A nested class of a substitute must be named in its
policy: with only `named("...ReentrantLock")` the build failed with `Method
dev.gmitch215.bytebox.locks.ReentrantLock$Waiters.<init>(Ljava/util/concurrent/locks/ReentrantLock;)V
was not found`. And **`Throwable.addSuppressed` throws a `NullPointerException` on this runtime** in
two builds, which sits under try-with-resources whenever both the body and `close()` throw; that
defect is not fixed.

Shipped: a blocking `ReentrantLock` with `lockInterruptibly`, timed `tryLock` and `newCondition`;
`Lock` and `Condition`; and `CompletableFuture` with `Future`, `CompletionStage`,
`CompletionException` and `TimeoutException`. The JVM-lane tests: `ReentrantLockTest` 13 against 9
failing on the old lock, `CompletableFutureTest` 5, `DeadlineTest` 3, 39 passed and 0 failed on both
`test` and `testCrlf`, and 12 of 12 consecutive `cleanTest` runs green. The workers lane runs the
generated module on workerd: `locks.spec.ts` 3 passed, and 60 with `core.spec.ts`. A mutation check
confirmed the golden transcript: iterating dependents oldest first instead of newest first fails it.

Absent: `ReentrantReadWriteLock`, `StampedLock`, `Semaphore`, `CountDownLatch` and `Executors`;
fairness is accepted and ignored; the `*Async`, `*Either`, `*Both`, `orTimeout`, `completeAsync` and
`obtrude*` forms of `CompletableFuture` are absent and fail the build that links them. The
2026-09-17 conclusion closed the mechanism "a substitute cannot suspend", and the measurements above
refute it. The objective, supplying `java.util.concurrent.locks` by substitution, is met for
`ReentrantLock`. Source: `build/batch/v103/results/B10.md`.

## Module Start on a Fiber

Locks That Block left one trap: a lock, a join or a get reached while the module starts. The
generated entry point caused it. JavaScript called `ByteboxEntry.main` directly, and the entry's own
static initialiser built the handler, so the handler's constructor and every static initialiser it
triggered ran on a stack with no fiber under it. TeaVM compiles a method that can suspend with a
check of the current fiber, and with none it dereferences null.

The entry point now starts the work on a fiber. `main` is `new Thread(ByteboxEntry::start).start()`,
and `start` registers the zone history when asked for, builds the handler, then registers both codec
sets, in that order, inside a `try` that keeps a failure. The handler is a plain static field. A
`static final` field, or a holder class with a static initialiser, would make every export
asynchronous when the handler's initialiser can suspend, and JavaScript cannot call an asynchronous
export. Each export reads the handler through `handler()`, which throws an
`IllegalStateException` naming the failure when the field is still null.

The loader's scheduler drains a fiber it was offered with a microtask, so on workerd the start runs
as soon as module evaluation ends and before the first request. Measured with the programs from
Locks That Block and a new `startup` program, built against the entry point before and after:

| Program                                                                     | Before           | After                                                                                                |
| --------------------------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------------------------- |
| cell I, `lock()` and `unlock()` in a static initialiser                     | traps (40,422 B) | prints `locked in clinit`, `unlocked in clinit` (40,907 B)                                           |
| cell J, `completedFuture(1).join()` in a static initialiser                 | traps (41,648 B) | prints `joined 1` (42,113 B)                                                                         |
| `startup`: contended lock, `supplyAsync(...).join()`, join in a constructor | not built        | blocks, wakes, answers 200 `hello from a fiber 42` (50,990 B)                                        |
| `startup` under `wrangler dev` 4.127.0, two requests                        | not built        | 200 and 200; the start printed before `Ready on`                                                     |
| a constructor that throws                                                   | not built        | first request fails, `module start failed: java.lang.IllegalStateException: boom in the constructor` |
| hello-world                                                                 | 25,334 B         | 26,141 B                                                                                             |

The `startup` program holds a lock on another fiber for 20 ms while the static initialiser asks for
it, so the initialiser waits and is woken. The 807 bytes on hello-world are probably the
asynchronous copies of the handler's constructor and the codec registration; the split was not
measured. Under `wrangler dev` the start's output appeared before `Ready on`, and both requests
found the handler built.

Two behaviours changed with it. A handler that throws while it is being built used to throw out of
`main`, which the scaffold calls at module scope; that is read from the code and was not measured.
It now fails each request until the isolate is replaced, because the exception is raised on a fiber
after evaluation has ended. And a start that never finishes leaves the handler null: a constructor
joining a future nothing completes failed its first request with `module start has not finished`,
measured under node with the same runner as the rows above.

A Durable Object's and a Workflow's constructor run on a fiber as well: the generated exports pass
`X::new` to `DurableHandlers` and `WorkflowHandlers`, which call it inside the `Thread` they start.
Measured under node and on workerd with a constructor that takes a contended lock, sleeps and joins
(Durable Object), one that throws on its first attempt, and one that joins a future (Workflow): the
old entry point traps with `dereferencing a null pointer` in every case, the new one answers. A per-id
monitor keeps two calls for one id that arrive while the constructor is suspended to one instance.
The module grows 1,211 B (2.1%) for a program with two Durable Objects and one Workflow. Source:
`build/batch/v103/results/X3.md`.

## Double Formatting

`String.format` computes its digits from `Double.toString`, so the two implementations producing
different shortest decimals would change the last digit of a formatted number. Measured by running
one source file both ways: compiled to wasm and run in node, and run directly on the reference JVM.
546 lines, covering 31 hand-picked values including the subnormal boundary and both infinities, plus
512 values drawn from a fixed LCG so both sides see identical bits.

**544 of 546 agree exactly. Two differ, and both are `Double.MIN_VALUE`:**

| Side          | `Double.toString(4.9E-324)` |
| ------------- | --------------------------- |
| Reference JVM | `4.9E-324`                  |
| This platform | `4.940656458412465E-324`    |

`Double.toHexString` agrees on every value including this one, so the bits are the same and the
disagreement is only in which decimal is chosen. Both parse back to the same double. A JVM emits the
shortest decimal that round-trips; at the smallest subnormal this implementation does not.

The sample held one subnormal, so it understated the defect. **Every subnormal prints the long form.**
A later transcript of 314 rows (52 powers of two and their neighbours, both ends of the range and 200
drawn values) found 305 that differ from a JVM; the 9 that agree are the normal-range ends. Bits `2`
print `9.88131291682493E-324` where a JVM prints `9.9E-324`, and bits `3` print
`1.4821969375237396E-323` against `1.5E-323`. `Double.toHexString` agrees on all 314. The platform's
`Double.toString`, `String.valueOf(double)`, string concatenation and `%s` of a double therefore
printed a long decimal for every subnormal, and nothing in the formatter could change that, because
the class library supplies it. Replacing the analyzer closes it, as the end of this section records.

The formatter no longer inherits the defect. `%e`, `%f` and `%g` take their digits from a
shortest-digits generator for subnormals, written in Java with a 40-limb integer bignum and no
`java.math`, and `%a` and `%A` are supported with the JDK's rounding. Measured against the same
transcript on JDK 21.0.8 and 25.0.2 (the rows `D T S A X` are identical between them):

| Rows                                    | Baseline: differ from a JVM                | 1.0.3: differ from a JVM |
| --------------------------------------- | ------------------------------------------ | ------------------------ |
| `S`, 2,198 subnormal conversions        | 1,436                                      | 0                        |
| `A`, 156 `%a` rows                      | 156, all `UnsupportedOperationException`   | 0                        |
| `X`, 88 `BigInteger` `%x` and `%o` rows | 79, all `IllegalFormatConversionException` | 0                        |

A standalone sweep of the generator against `Double.toString` found 0 differences in 1,000,359
subnormals on JDK 21.0.8 and on JDK 25.0.2 (the first and last 300,000, the powers of two and
their neighbours, and 400,000 drawn), and 0 in 12,000,359 on JDK 21.0.8 (the first and last
2,000,000, the powers, and 8,000,000 drawn). The generator is exact integer arithmetic on the
rounding interval, so the sweep tests the tie and boundary rules; the 2^52 subnormals were not
enumerated. The JVM-lane tests are `FormatterTest`, 158 passed and 0 failed on `test` and on
`testCrlf` (red: 54 failed against a stub).

The cost is in the module of a program that formats. `samples/standard-library` grew from 557,341 to
564,739 bytes, +7,398 (+1.33%). A one-class Worker calling `String.format("%s %d %.2f %x", ...)` grew
from 106,241 to 114,026 bytes, +7,785, and `strings -a` finds no `java.math` code in either, so a
`BigInteger` conversion reads `toString()` and does not link the class. `samples/hello-world` is
25,334 bytes, md5 `5ac53895a16e434e8537bcfb2700fa27`, before and after. The bytes split as 1,371 for the
class-name check that recognises a `BigInteger`, about 3.6 KB for `%a`, and the rest for the
generator and the radix code; the `%a` case first used `Double.toHexString` at 6,232 bytes, and an own
12-line layout took 2,629 off the total. No startup cost was measured.

Found on the way, in the same file. `%#010x` and `%#010o` of an `int`, `long` or `byte` put the zeros
before the prefix (`0000000xff`, where a JVM writes `0x000000ff`); that is fixed and pinned.
Left open by that measurement: the `0` flag on an infinity padded with zeros (`%010f` of positive
infinity gave `00Infinity`, and a JVM pads with spaces); `String.format("%d", (Object) null)` threw
`IllegalFormatConversionException` where a JVM prints `null`; `%d` of a whole `BigDecimal` is
accepted where a JVM throws; and a `BigInteger` subclass is not recognised. The first two are fixed
(below); the last two are not. Source: `build/batch/v103/results/B4.md`.

**Closed afterwards: subnormals in `Double.toString`.** `Double.toString(d)` is
`new TStringBuilder().append(d).toString()`, and `String.valueOf(double)`, concatenation,
`StringBuilder.append(double)` and `%s` all end in `TAbstractStringBuilder.insert(int, double)`, which
calls `DoubleAnalyzer.analyze`. For a subnormal that method shifts the significand up to 53 bits,
which makes the rounding interval as narrow as a normal double's and prints the long form. The one
point every path shares is `analyze`, so `text/Substitutions` replaces
`org.teavm.classlib.impl.text.DoubleAnalyzer` by name with a copy that hands a subnormal to the
shortest-digits generator the formatter already had. It is a copy because a substitute cannot call
the class it replaces, and `DoubleSynthesizer` reads the analyzer's package-private tables.
Measured against JDK 27 (JDK 25 gives a byte-identical transcript), the same source compiled to a
Worker under node and run as `java Probe.java`, with the tree before the rule as the control arm:

| Rows                                                     | Count  | Differ before | Differ after |
| -------------------------------------------------------- | ------ | ------------- | ------------ |
| the 314 subnormals above                                 | 314    | 305           | 0            |
| random subnormals, 1 to 52 bits wide                     | 50,000 | 48,543        | 0            |
| random negative subnormals                               | 200    | 189           | 0            |
| the 314 through `String.valueOf`, `+`, builders and `%s` | 314    | all same      | 0            |

The generator itself was swept against `Double.toString` over 12,000,359 subnormals (the first and
last 2,000,000, every power of two with its neighbours, and 8,000,000 drawn) with 0 differences; the
2^52 subnormals were not enumerated. A subnormal conversion costs about 16.5 us after the change
against about 0.2 us before (the old answer was wrong), which is 600 of them inside the free plan's 10
ms of CPU; a normal double costs 224 ns in both arms (35 samples per arm, 219 to 256 ns control,
221 to 277 ns new). `hello-world` is byte-identical (md5 `3a4298b79e686348e1b283d4ff64087e`) and
`standard-library` grew 202 bytes.

**Closed afterwards: normal doubles.** The same transcript found that the class library's analyzer
printed a decimal that does not read back as the same double for 72 of about 60,000 random normals
(0.12%), for example bits `1dcb2e81a65dee3f`: `3.687639494787356E-165` here,
`3.6876394947873562E-165` on a JVM, and `1e23` printed `9.999999999999999E22` where a JVM prints
`1.0E23`. The analyzer multiplies by 64-bit table entries and drops low bits, so a candidate near the
edge of the interval goes the wrong way. The copy now trusts the table result only when it holds for
every exact figure within the table's known error (exact minus computed lies in [0, 3.13) units of
the 19th digit, measured over every exponent with a BigDecimal oracle), and otherwise `Exact` scales
the value and its two edges by a power of ten without error, in limb arrays with no `java.math`, and
chooses again. Subnormals take the same exact route, which deleted the separate subnormal routine.
Against JDK 27, 0 of 1,008,011 platform rows differ (2,679 differ in the control arm), and 0 differ in
5,012,838 and 3,013,818 draws on a JVM and in 11,846,376 subnormals. A normal `Double.toString` costs
231 ns against 224 (+3.1%, near the instrument's resolution, 35 samples per arm), and a subnormal 1.4
us against 16 us. `standard-library` grew 2,240 bytes. The copy is TeaVM's Apache-2.0
`DoubleAnalyzer`; `NOTICE` credits it. Source: `build/batch/v103/results/W4.md` and `X4.md`.

**Closed afterwards: `Float.toString`.** `TAbstractStringBuilder.insert(int, float)` calls
`FloatAnalyzer.analyze`, which `text/Substitutions` now replaces by name with a class that calls
`Exact.analyze(int)`; the exact route needed one new piece, a short division by 5^13 steps, because
a float has no table seeds. Before, 333 of 200,000 random floats did not read back, and a control
sweep of 999,051 rows found 1,350 round-trip failures and 151,539 rows that differ from JDK 27. After,
0 of 999,051 platform rows differ, and 0 of every positive finite float (2,139,095,039) checked on a
JVM. There is no fast path, so a normal call costs 327 ns where it took 125 (2.6 times), typical
values 238 ns where 88, and sums 227 where 75 (3.0 times); one machine, one node, 35 samples per
cell, and no real workload was timed. `standard-library` is 595,366 bytes against 597,347 with the
class library's float analyzer, and a program that prints only floats grows by about 0.7 to 2 KB.
Source: `build/batch/v103/results/Y4.md`.

**Closed afterwards: `parseDouble` and `parseFloat`.** The class library's lexer accepted strings a
JVM refuses (`1e5.5`, `1e1e1`, `1e1_0`, `0e0e0`), refused hexadecimal floats, gave no message, and
rounded past the 19th digit by the first 19. A TeaVM plugin (`text/ParsePlugin`, found through
`META-INF/services/org.teavm.vm.spi.TeaVMPlugin`) now gives `Double.parseDouble` and
`Float.parseFloat` a body that calls `text/Parse`, because a substitution renames a whole class and
`Double` and `Float` cannot be renamed without losing their compiler natives. `Parse` implements the
JVM's grammar (sign, `NaN`, `Infinity`, hexadecimal floats, the suffixes `f`, `F`, `d`, `D`, trimmed
whitespace), reads every digit, rounds a float once, and refuses with the JVM's message (`For input
string: "..."`, shortened from the middle past 1,000 characters, `empty String`, `multiple points`).
1,637 grammar rows generated by running each string on OpenJDK 27 agree on workerd for both methods,
values as bit patterns and messages as text. One probe app that calls both methods once each is
5,208 bytes (7.8%) smaller, 61,336 against 66,544, with a 0-byte spread over two builds; one method
alone and other programs were not measured. `hello-world` and
`standard-library` are byte-identical. Not measured: the cost of one parse on workerd. One difference
remains: TeaVM compiles negation of a runtime `0.0` to `+0.0`, where a JVM gives `-0.0`
(`1 / -x` is `Infinity` here); `Parse` builds a negative number by setting the sign bit and is not
affected. Source: `build/batch/v103/results/Y2.md`.

**Closed afterwards: null and infinity.** `String.format("%d", (Object) null)` now prints `null`
under `%d %x %o %f %e %g` as a JVM does, with width, the `-` flag, precision truncation (`%.2f` of
null is `nu`) and `%X` upper-casing; the test compares 14 conversions, 10 flag sets, 2 widths and 3
precisions with `String.format` (160 `FormatterTest` tests, 0 failed, on `test` and `testCrlf`). The `0`
flag pads an infinity with spaces. `%D`, `%O` and `%F` throw `UnknownFormatConversionException`, as
they do on a JVM, where they had been accepted. Source: `build/batch/v103/results/W3.md`.

## Regular Expression Agreement

The translator rewrites the constructs where Java and the platform's engine differ, and refuses the
ones with no exact equivalent. What was not established is whether the two engines agree about
everything in between. Measured with the same differential transcript: 20 patterns over 48
pattern and input pairs, comparing `find()`, the match boundaries, every capture group, named
groups, `Pattern.matches` and `split`.

**Every construct the translator accepted produced byte-identical output on both sides.** The only
differences were six lines covering two patterns, and in both the platform refused (the same corpus
gave five JVM lines when it was rerun for 1.0.3):

| Pattern            | Reference JVM | This platform            |
| ------------------ | ------------- | ------------------------ |
| `(?i)HeLLo`        | matches       | `PatternSyntaxException` |
| `[a-z&&[^aeiou]]+` | matches       | `PatternSyntaxException` |

Flags written inside a pattern and character-class intersection were both on the documented refusal
list, so that corpus found no silent divergence. Twenty patterns is a corpus and not a proof, and the
constructs already known to be rewritten are the ones most likely to hide one. The next two sections
widen it, and the second one found silent divergences that predate it. Since 1.0.3 neither pattern
above is refused: a flag group that opens a pattern is read, and intersection is translated.

## Case Folding

`CASE_INSENSITIVE` alone folds ASCII on a JVM, and the platform's `i` flag folds the whole of
Unicode. The translator now writes the ASCII fold itself and passes the platform's `i` only where a
JVM would fold Unicode too. Measured by a sweep: every BMP code unit with a case mapping, as a
`\uXXXX` literal, against each partner its own `Character.toLowerCase`, `toUpperCase` and
`toTitleCase` gives. The reference is OpenJDK 21.0.8 (the toolchain's major); OpenJDK 27, which is the
default `java` on the machine, was also run and differs from 21 only at U+00DF and U+1E9E under
`UNICODE_CASE` and at Unicode 16 and 17 additions. Each mode has 2,399 lines on the JVM and 2,506 on
the platform, and the 2,391 keys present on both sides are compared; the rest are partners that only
one side's `Character` tables produce (U+0378 pairs with U+0377 on the platform), which was not
investigated.

| Mode                               | HEAD, regex sources before the change | 1.0.3                                          |
| ---------------------------------- | ------------------------------------- | ---------------------------------------------- |
| `CASE_INSENSITIVE` alone           | 2,270 of 2,391 pairs differ           | 0 of 2,391                                     |
| `CASE_INSENSITIVE \| UNICODE_CASE` | 69 of 2,391                           | 69 of 2,391, 62 distinct characters, unchanged |

The control is the same sweep on the regex sources at HEAD, against the same JVM. The 69 under
`UNICODE_CASE` are where a JVM folds and V8's non-unicode `i` does not: U+017F with `s`, U+212A with
`k`, U+0130 and U+0131 with `i`, U+2126 with `ω`, U+212B with `å`, U+03F4 with `θ`, U+1E9E with
U+00DF on JDK 21, and the Greek letters with iota subscript and their titlecase forms (U+1F80 to
U+1FFC). That is documented in `Pattern`'s Javadoc and not fixed. **A backreference under
`CASE_INSENSITIVE` alone keeps the platform's folding**, because a captured group cannot be written
as a pair of cases: `(é)\1` matches `éÉ` here and not on a JVM. Octal escapes, `\k<name>`, `\cX`,
`\x{...}` and a few shapes inside a class do the same.

The same item added Unicode block properties from a generated table of 161 BMP blocks (9.4 KB, checked
against all 65,536 code units by `blockTableMatchesTheJdk`, and by a mutation that shortens the Greek
entry by one unit) and reads a flag group that opens the pattern as the flags it sets. That makes the
`stripAccents` pattern from Commons Lang compile.

| Check                                     | Result                                                                     |
| ----------------------------------------- | -------------------------------------------------------------------------- |
| `PatternTest` before, on HEAD sources     | 224 tests, 75 failed                                                       |
| `PatternTest` and `SubstitutionsTest`     | 224 and 6 passed, 0 failed, 0 skipped                                      |
| whole core module                         | 586 passed, 0 failed, 0 skipped, 29 classes                                |
| transcript, base rows, HEAD               | 5 JVM lines differ, 2 patterns: `(?i)HeLLo` (3) and `[a-z&&[^aeiou]]+` (2) |
| workerd `/regex` route                    | `leadingFlag` `true`, `block` `ecole`, `asciiFold` `false,false,true`      |
| `samples/standard-library`, `app.wasmbin` | 515,199 to 542,180 bytes, +26,981, +5.2%, one build each                   |

The size delta was not split into the table and the fold code. **Divergences found that predate the
change and are not fixed:** `.`, `[^x]` and every negated class read a character above U+FFFF as two
units, because a pattern without the `u` flag reads UTF-16 units, so `a.b` over `a`, U+1F600 and `b`
is true on a JVM and false here; and `\101` is group 1 followed by `01` on a JVM and `A` to V8's
legacy octal. `\x{41}` is passed through and V8 would read `x{41}`; that one was read from the
translator's source and not run. Source: `build/batch/v103/results/B2.md`.

## Atomic Groups, Possessive Quantifiers and Nested Classes

Atomic groups and possessive quantifiers are written as a lookahead and a backreference, and a nested
character class or `&&` is written through V8's `v` flag, which reads a class as code points.
Instruments: a JVM-side emitter writes each pattern and input as JSON, and a node harness evaluates
the translated source and flags exactly as `Matcher` drives it (`lastIndex`, advance after an empty
match, the group map). A sweep checks every code point from 0 to 0x10FFFF as a one-character input,
so it answers "which characters does this class match" completely; the row harness answers matches,
spans and groups on the inputs it holds. The reference is OpenJDK 21.0.8, node 26.8.2 is V8 14.6.202.34.

| Measurement                                                      | Result                                                                                                        |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| class sweep, 146 rows over 1,114,112 code points                 | 142 identical to a JVM, 0 differ, 4 refused                                                                   |
| row harness for classes, 222 combinations over 10,656 inputs     | 222 identical, 7 refused, 1 refused by the JVM; 40 rows differ only by an empty match inside a surrogate pair |
| row harness for atomic and possessive, 92 rows over 3,220 inputs | 86 identical, 6 differ (below)                                                                                |
| the same atomic harness on OpenJDK 27 (control)                  | the same six patterns                                                                                         |
| compiled-module transcript, 981 lines per side                   | 851 identical, 130 JVM lines differ, each classified                                                          |
| `samples/standard-library`, `app.wasmbin`                        | 542,180 to 557,341 bytes, +15,161, +2.8%, one build each                                                      |

The 130 differing lines are all by design or documented: 20 for an atomic atom in a lookbehind, which
is refused; 12 for a backreference to a group that is not open yet (`(a)\2`, `(?:\1(a))`), refused
because a JVM never matches it; 78 for `[&&a]`, `[a&&]`, `[a&&&b]`, `[a&&b&c]`, `[a-\Qz\E[b]]` and
`[\S[a]]`, refused; 10 for a stale capture, below; 6 for `(a\1)`, a divergence that predates this; 2
for the empty match inside a surrogate pair; and 2 refusals under `CASE_INSENSITIVE` and
`UNICODE_CASE`.

**A group inside an atom reads null where a JVM keeps a stale span.** `(?>(a)|(b))c` over `abc`: the
attempt at 0 takes `a` and fails at `c`, and the match at 1 has group 1 `a@0-1` on a JVM and `null`
here. V8 restores captures on backtracking, so null is the correct answer, and a JVM-lane test cannot
see it because the JVM runs the translated lookahead with the same leak. Only the group's text and
span differ, in all 35 differing rows of five patterns. The `v` flag with UNICODE_CASE needs V8's
`i`, which folds by Unicode simple case folding: `[a[b]]` agrees, `[a-z&&[^k]]` differs at U+0130 and
U+0131, and anything built on `\w` or `\p{Alpha}` differs at U+017F and U+212A, so the pair is
refused.

Defects fixed in passing, each with a regression test: multi-digit and digit-after backreferences
(`(a)\10`), whitespace and `#` comments inside a class under `COMMENTS`, a V8 refusal surfacing as
`WasmGCExceptionWrapper`, `groupCount()` before a match, and `namedGroups()` numbering when a plain
group precedes a named one. The test counts: `PatternTest` 409 passed, 0 failed, 0 skipped; whole
core 768 passed. Red runs: 86 failed for the atomic group tests, 97 for the class tests, 1 for
`COMMENTS` in a class, 1 for the platform refusal.

**Divergences found that predate the change and are not fixed:** a backreference to a group that did
not take part matches the empty string in V8 and fails on a JVM (`(?:(a)|b)\1` over `b`), a quantified
group is reset on each iteration in V8 and keeps its last span on a JVM (`(?:(a)|b)*` over `ab`), and
`\101`. Backtracking cost is not changed by any of this. Source: `build/batch/v103/results/B3.md`.
Three of the divergences recorded in this and the previous two sections changed afterwards; the next
section says how.

## Regular Expression Refusals After the Sweeps

Reference JVM OpenJDK 21.0.8, V8 14.6.202.34 under node 26.8.2, the same harnesses as the sections
above. Three behaviours became refusals or exact translations, each with a test that fails without
it (`PatternTest`, 473 passed, 0 failed, 0 skipped; whole core module 949 on `test` and 949 on
`testCrlf`). Each was reverted on its own to see the red run: 9, 18 and 8 tests failed, and a fourth
revert, of a block alias, failed 1.

| Case                                                          | Before                                            | Now                                                                                |
| ------------------------------------------------------------- | ------------------------------------------------- | ---------------------------------------------------------------------------------- |
| backreference under `CASE_INSENSITIVE` without `UNICODE_CASE` | kept the platform's folding, `(é)\1` matched `éÉ` | refused: the ASCII fold cannot make a backreference fold as a JVM does             |
| capturing group inside an atomic group or possessive atom     | read null where a JVM kept a stale span, 35 rows  | refused; a group beside or around the atom is read as written, 15 patterns checked |
| `\P{InGreek}` and every negated block, over U+10000 and up    | 1,048,576 of 1,114,112 code points differ         | exact: 346 patterns over all 1,114,112 code points, 0 differ, via the `v` flag     |

A negated block without the `v` flag reads UTF-16 units, so a supplementary character counts as
two. The control arm,
HEAD's translation `[^Ͱ-Ͽ]`, differs from a JVM at every supplementary code point. With the `v` flag
it differs at none. The compiled module now prints the JVM's `all=0:1,1:3,3:4` for `\P{InGreek}`
over `a`, U+1F600 and `b`. A pattern with a negated block now carries the `v` flag and is refused
with `UNICODE_CASE`, as every `v` pattern is. The row harness over 42 negated-block patterns
and 30 inputs found 40 identical, 1 refused and 1 different: `(\P{InGreek})\1` over a lone high
surrogate followed by an emoji, where a JVM compares UTF-16 units and a code-point engine does not.

The block table has a generator in the repository now. `GenerateBlocks` (under `core/src/test`) writes
`Blocks.java`, and `blockTableIsWhatTheGeneratorWrites` runs it in a child JVM and compares its
output with the committed file (0.10 s). The output is identical on JDK 21 and JDK 27. That test
is the only guard for an alias: dropping `GREEKANDCOPTIC` leaves `blockTableMatchesTheJdk` green and
fails the generator test.

**What is still different.** The same families `\P{In...}` belonged to are not closed: `.`, `[^x]`,
`\S`, `\H`, `\V`, `\D`, `\W` and `\P{Posix}` over a character above U+FFFF. Over `a`, U+1F600 and
`b`, V8 returns four matches of one unit each for `.`, `[^x]`, `\S` and `\D`, where a JVM returns
three of lengths 1, 2 and 1; for `\W` and `[^a-zA-Z]`, which is what `\P{Alpha}` translates to, V8
returns two matches of one unit and a JVM one match of two. Closing them means the `v` flag
whenever a pattern holds one, which is most patterns, so it changes the default output of most
samples and refuses `UNICODE_CASE` for them; the size and CPU cost of the `v` flag across a
corpus was not measured. The UNICODE_CASE list of 62 characters is unchanged. Under
`CASE_INSENSITIVE` alone, an octal escape `\0nn`, `\cX`, an incomplete `\x` or `\u` and a hyphen
beside a set or a finished range inside a class (`[\w-a]`, `[a-z-9]`) keep the platform's folding.

**Found while checking, silent and not fixed.** `\x{41}` against `A` matches on a JVM and not here:
V8 reads it as `x` followed by the count `{41}`, and `[\x{41}]` matches `x`, `{`, `4` and `1`. `\0101`,
octal for `A`, does not match `A` here, and `\0153` under `CASE_INSENSITIVE` matches `k` and `K` on a
JVM and neither here. `\x{1F600}` and `\x{6b}` are refused, by accident, because the platform reads
`{1F600}` as an illegal count. `\101` is refused now (a backreference to a group not open yet). The
exact fix is `\x{h+}` to `\uXXXX`, or the `v` flag's `\u{...}` above U+FFFF, and `\0n`, `\0nn`,
`\0mnn` to `\xHH` or `\uXXXX`, about 40 lines in `Translate`; refusing both is two lines. Neither
was done then. Source: `build/batch/v103/results/W2.md`.

**Closed afterwards: the escapes and the wide characters.** `\x{h...h}`, `\0n`, `\0nn`, `\0mnn` and
`\cX` are read as a JVM reads them and written out plainly, and one a JVM refuses is refused. `.`, a
negated class, `\S`, `\H`, `\V`, `\D`, `\W` and `\P{Posix}` take a character above U+FFFF whole: the
matcher runs a second source under the `v` flag over text that holds one. Under `UNICODE_CASE`,
`Pattern.matcher`, `split`, `replaceAll` and `Matcher.reset` throw `UnsupportedOperationException`
for such text when the pattern holds one of those constructs, because the flag changes how case
folds and no agreement was measured for it. Against a JVM, 1,191 of 1,191 sweep patterns over every
code point agree (293 did before), and the row tables show 390 of 392 escape patterns agreeing (131
before) and 137 of 143 wide-character patterns (56 before), with 5 differing and 1 refused. What still differs: a
backreference meeting half of a pair the group captured as a lone surrogate, a lookbehind that holds
`.` or a negated class (a JVM sizes it in UTF-16 units, so `(?<=..)x` over two emoji and `x` finds no
match there and a match here), the empty match in the middle of a pair, and the two `UNICODE_CASE`
rows over U+212A. `samples/standard-library` grew 13,185 bytes (581,969 to 595,154, +2.3%); a program
with no regex is byte-identical. Not measured: the CPU cost of the `v` flag over a corpus, and a
pattern is translated twice when it holds `.` or a negated class. Source:
`build/batch/v103/results/X2.md`.

## Backtracking Costs Far More Here

The two engines backtrack differently, so a pattern's cost does not transfer. Measured by running
`Pattern.compile("(a+)+b").matcher(input).matches()` over inputs of 20 to 30 characters, the same
source both ways.

| Side          | Ladder total, n = 20 to 30                           |
| ------------- | ---------------------------------------------------- |
| Reference JVM | **14 ms**                                            |
| This platform | **11,010 ms** wall, less about 120 ms of module load |

A JDK of this vintage resists the classic patterns outright. Run directly against the platform's
engine to separate it from anything bytebox does, the same shapes cost seconds:

| Pattern     | n=20   | n=26   | n=30      |
| ----------- | ------ | ------ | --------- |
| `(a+)+b`    | 87 ms  | 536 ms | 8,786 ms  |
| `^(a+)+$`   | 114 ms | 615 ms | 10,154 ms |
| `(a\|a)*b`  | 119 ms | 631 ms | 7,760 ms  |
| `(x+x+)+y`  | 59 ms  | 377 ms | 6,263 ms  |
| `(a\|aa)+$` | 3 ms   | 5 ms   | 26 ms     |

The same patterns measure 0 ms on the reference JVM at every one of those sizes.

A free-plan request has 10 ms of CPU, and the cheapest pattern above is already at 87 ms by 20
characters. A Java program carrying a
regular expression that was harmless on a JVM can be a denial-of-service on this platform, and
nothing in the retargeting warns about it, because the pattern is valid and the translation is
correct. Treat any pattern applied to input you do not control as needing its own bound.

## The Isolate Ceiling

Measured 2026-09-17 on a deployed Worker against the free account. A Java array on this target is a
WebAssembly GC array, so it lives in the host engine's collected heap rather than in linear memory,
and that is the heap the isolate limit applies to. One Worker was deployed and driven with escalating
requests; the account was verified back to its starting count of 10 afterwards.

**One request can allocate 254 MiB. 256 MiB fails**, with HTTP 503 and Cloudflare error code 1102.
The boundary was bisected: 240, 248, 252 and 254 all succeed.

| Allocated in one request | Result                       |
| ------------------------ | ---------------------------- |
| 128 MiB                  | ok                           |
| 192 MiB                  | ok                           |
| 254 MiB                  | ok                           |
| 256 MiB                  | `error code: 1102`, HTTP 503 |

**Touching every page changes nothing.** Repeating the ladder while writing one byte per 4 KiB, which
forces the engine to commit each page without the CPU cost of writing all of it, gives the identical
boundary: 254 MiB ok, 256 MiB refused. So this is not sparse pages going uncounted; it is a hard
ceiling, and it sits at roughly twice the 128 MB the platform documents.

**The ceiling is cumulative per isolate, not per request.** A request asking for 200 MiB succeeded on
a fresh isolate and was refused on one already holding retained chunks in a static field. Retained
state accumulates: one isolate reached 128 MiB held across several requests.

**A static field is per isolate, and requests spread across isolates.** Twenty identical retaining
requests landed on at least six, with the retained total restarting at 0 each time a new one was
reached and climbing to 96 MiB on the highest. A leak therefore grows non-deterministically from the
outside, and a single client cannot drive one isolate to the ceiling on purpose.

## cpuTime With the Retargeted Class Library

The `cpuTime` figure in the Summary comes from a hello world. Measured 2026-09-17 by deploying
`samples/standard-library`, which links `java.time`, both HTTP clients, `java.util.regex` and
`String.format`, and reading `wrangler tail`.

| Worker                   | cpuTime                           |
| ------------------------ | --------------------------------- |
| hello world              | 0-1 ms, n=6                       |
| retargeted class library | **43 ms median**, range 2-79, n=8 |

Every reading returned outcome `ok`, including the 79 ms one, so nothing was terminated for exceeding
the 10 ms the free plan documents.

The readings are bimodal, 2 ms three times against 37 to 79 ms otherwise, which reads as warm against
cold rather than as two different workloads. The figure includes one outbound HTTPS request: the
worker answers `upstream 200, 559 bytes` from `java.net.http.HttpClient`, alongside a zoned timestamp,
a date difference through a named regex group, and a `String.format` percentage. Every retargeted API
in the sample produced a correct answer on the edge.

## Inert Bytes Into the Megabytes

Startup Scaling showed that 490 KB of inert bytes cost nothing. Measured 2026-09-17 by padding the
hello world module with an inert custom section and deploying each size, with unique noise in the
padding so no two uploads shared a content hash.

| Inert padding | Wasm bytes | Total Upload  | Startup |
| ------------- | ---------- | ------------- | ------- |
| none          | 26,208     | 66.58 KiB     | 10 ms   |
| 1 MiB         | 1,048,571  | 1,064.98 KiB  | 8 ms    |
| 2 MiB         | 2,097,147  | 2,088.98 KiB  | 8 ms    |
| 4 MiB         | 4,194,300  | 4,136.98 KiB  | 10 ms   |
| 8 MiB         | 8,388,604  | 8,232.98 KiB  | 9 ms    |
| 16 MiB        | 16,777,212 | 16,424.98 KiB | 10 ms   |

**Startup is flat at 8 to 10 ms across a 640x range of bundle size**, and every reading sits inside the
range a bare hello world occupies. For comparison, `samples/standard-library` at 515,199 bytes of real
compiled code measured 15 ms on the same day.

The claim now holds over three orders of magnitude rather than at one point: bundle bytes are free to
startup and compiled code is not.

## What The Wrapper Caches Retain

The compiler's runtime bridges Java and JavaScript object identity through caches that were recorded
here as three `WeakMap`s of `WeakRef`s and five `FinalizationRegistry` instances. Counted by
substituting `Map`, `WeakMap` and `FinalizationRegistry` before the runtime is imported and reading
what it builds: **two `Map`s, three `WeakMap`s and three `FinalizationRegistry` instances.** The
registry count was wrong.

The counts alone do not say which cache can retain anything. Three are `WeakMap`s keyed by the object
they describe, so they release on their own. One is a plain `Map` keyed by primitives, which cannot
be held weakly, so its only cleanup is a `FinalizationRegistry` callback that deletes the entry once
the Java wrapper is collected. Cloudflare documents finalizer callbacks as running in quiet slots
between I/O phases, with non-deterministic timing and no guarantee they run at all.

So the unbounded-growth risk is that one structure. What reaches it was found by wrapping
`Map.prototype.set` to record the key's type, and having the Java side announce each shape it was
about to try, so every cache write is attributed to the operation that caused it.

**Calling a `java.lang.Object` method on a JavaScript primitive is what populates it**, because that
is the point where a Java identity has to exist for a value that has none.

| Java operation on a value from `@JSBody` | Writes to the primitive map |
| ---------------------------------------- | --------------------------- |
| null check only                          | 0                           |
| `hashCode()` on a JS string              | 500, keyed by string        |
| `hashCode()` on a JS number              | 500, keyed by number        |
| `String.valueOf`, `equals`               | 0                           |
| used as a `HashMap` key                  | 0, already cached above     |
| `hashCode()` on a JS object              | 0, goes to a `WeakMap`      |

A JS object lands in a `WeakMap` instead, so only values that cannot be held weakly reach the map that
cannot release them.

The map then drains. It reached 1,000 entries across the sweep and returned to 0 after collection,
with all 1,000 finalizer callbacks firing and the heap back at its starting 4.8 MB. The cleanup is
deferred rather than absent.

What that does not settle is the platform. Node runs the finalizers promptly and Cloudflare documents
its own as running in quiet slots between I/O phases, with non-deterministic timing and no guarantee
they run at all. So the bound is the number of distinct primitives a long-lived isolate ever crosses
into Java, and the release of that bound depends on a callback the platform does not promise.
Hashing unbounded distinct JavaScript strings grows that map without a release you can rely on.
Hashing JavaScript objects does not, because those are held weakly.

## The Refused `@JSBody` Constructs Do Not Reproduce

This report recorded three constructs as refused by the compiler's script parser: argument spread
`f(...args)`, parameter destructuring `({ a }) => a`, and a `BigInt` literal `1n`. It also noted that
an isolated program containing them compiled without complaint.

**Thirty-seven builds later, none of the three is refused.** Twenty-five constructs were each put in
a reachable `@JSBody` method, called from the handler, and built through the plugin: all twenty-five
compile, including the three. The three were then built again in four wrappings each, with
parameters, with an import, and beside a second plain `@JSBody` method: all twelve compile.

The parser is Rhino, which the compiler ships relocated as `teavm-relocated-libs-rhino`, and its
highest language version is `VERSION_ES6`. Parsing the same constructs with that parser directly and
a bare `CompilerEnvirons` does refuse some of them, but it refuses a different set than the report
names and accepts two the report says fail, so that configuration is not the one the compiler uses.

The rule should not be carried as a parser boundary. What was observed was real when it was observed;
it is not reproducible on 0.15.0 by any arrangement tried here, and a constraint nobody can reproduce
should not shape how code is written.

## The Compatibility Date and `nodejs_compat`

Measured 2026-10-08 on the workers lane's workerd. The question was which date the samples,
experiments and tests should carry. **Every `compatibility_date` in the repository was already
`2026-08-22`, which is the newest date the lane's workerd accepted, so no file changed.** The lane
used miniflare `5.20260815.0-alpha` for this measurement, which declares workerd `1.20260815.1`, and
`workerd --version` printed `2026-08-15`. Serving a Worker at 2026-08-04, 08-15, 08-16 and 08-22 starts silently, and 2026-08-26
is refused:

```txt
This Worker requires compatibility date "2026-08-26", but the newest date supported by this server binary is "2026-08-22".
```

The ceiling is the binary's build date plus 7 days: the 1.20260826.1 binary stops at 2026-09-02 and
the 1.20260917 one at 2026-09-24. The samples install their own newer workerd (1.20260911 to
1.20260917), so 2026-08-22 is accepted there too.

**The generator turns `nodejs_compat` off from 2026-08-04.** It writes `no_nodejs_compat` and
`no_nodejs_compat_v2` for a date at or after that, so a project's default is the configuration the
lane already used, and the `nodejs_compat` lane is an additional arm. The arm runs the same Java
module through six routes (`/url`, `/kv`, `/async`, `/json`, `/builtin`, `/collections`), and every one
agrees byte for byte with the default arm, with no change to `npm/src`. The first version of the
suite did not prove the arm: reverting its flags to the default pair left all six tests green,
because the five route tests compare two arms that run the same module and the one flag assertion
read `process` in a separate JavaScript probe Worker. The test that does prove it asks the Java Worker's own wrapper for `typeof process`, which is
`object` with a Node version under the arm and `undefined` without. With the arm's flags set back to
the default pair that test fails (`expected 'undefined' to be 'object'`, 1 failed, 6 passed), and the
workers project passes, 143 tests. The limit is six routes of one fixture: sockets, Durable Objects
and `/fetch` were not driven through the arm.

Measured again on 2026-10-09 with the `workerd` binary of miniflare 5.20261006.1-alpha (build date
2026-10-06), the way A4 measured the old one: 2026-08-22, 2026-10-06, 2026-10-08 and 2026-10-09 are
accepted, 2026-10-10 to 2026-10-13 are refused because they are in the future (`Today's date (UTC) is
"2026-10-09"`), and 2026-10-14 is refused with `the newest date supported by this server binary is
"2026-10-13"`. The ceiling is again the build date plus 7 days, capped by today's date. The lane
ran the older binary when this was written, so its ceiling was 2026-08-22; the lane now runs this
binary (root and `npm/package.json` pin miniflare 5.20261006.1-alpha). The instrument reads the first 200
characters of workerd's output and counts an alarm kill as acceptance. Source: `results/W5.md`.

A project that sets no date gets today's UTC date. `wrangler deploy --dry-run --compatibility-date
2026-10-08` accepted it (`Total Upload: 65.72 KiB`, wrangler 4.134.0), because wrangler does not
validate the date on a dry run. The workerd that sample installs refuses to serve it, with a ceiling
of 2026-09-24. So an unset date works for a deploy dry run and fails against a local workerd older
than a week; whether `wrangler dev` itself falls back with a warning was not run. The same dry run
warns `The package "node:fs/promises" wasn't found` for an import in `app.wasm-runtime.js`, the
compiler runtime's Node branch, which is not taken at run time under `no_nodejs_compat`.

Found while there: `ByteboxPlugin.version()` read the jar manifest's `Implementation-Version`,
which the plugin's build never set, so every published plugin answered `1.0.0` and defaulted
`coreVersion` to `bytebox-core:1.0.0` whatever its own release. The red run caught it: 16 tests, 1
failed, with the core at `1.0.0` against a project at `1.0.2`. The plugin now reads a generated
`version.properties`, and a missing resource throws. The npm range defaults to `^<major>.<minor>.0`,
so `^1.0.0` for 1.0.3: npm has only 1.0.2 until the release publishes, and `^1.0.3` would fail every
sample build before then. Source: `build/batch/v103/results/A4.md`.

## Opt-In Extras

Two additions that cost bytes for every program were tried behind a switch, and the switch is only
free where the addition sits in code the default module already carries or leaves out.

**Zone history ships opt-in and the default module is unchanged.** `classlib { zoneHistory = true }`
makes `ZoneRules.getTransitions()` and `getTransitionRules()` answer from ThreeTen-Backport 1.7.0's
`TZDB.dat`, tzdata 2024b. The generated entry point registers a reader in a `static {}` block before
the handler is constructed. The 108,033-byte file reaches the module through TeaVM's
`ResourcesPolicy` only when `ZoneHistory` is among the classes the program kept, and it is kept only
when the program calls one of the two methods. ThreeTen's own reader is not used, because it builds
`StandardZoneRules`, which extends the substituted `ZoneRules`.

| Module                     | Baseline bytes, md5                         | After, flags unset                          |
| -------------------------- | ------------------------------------------- | ------------------------------------------- |
| `samples/hello-world`      | 25,334, `5ac53895a16e434e8537bcfb2700fa27`  | 25,334, `5ac53895a16e434e8537bcfb2700fa27`  |
| `samples/standard-library` | 564,739, `8fe4557c01ec4d8bc522df92103a8bc4` | 564,739, `4f2cd4ff0909a6c60c84b71d0bf6115b` |

The second md5 differs and the build is not byte-deterministic for that module, so the md5 alone proves
nothing. Four rebuilds of one unchanged tree gave `8fe4557c...` twice and `4f2cd4ff...` twice, with 2
bytes swapped at offsets 519,264 and 519,281; six rebuilds of a tree with none of the time files gave the
same two outputs, three each. The size and the generated entry point md5 are what the claim rests on.
`hello-world` is stable.

| Program                                        | Flag off | `zoneHistory` | Delta    | Share |
| ---------------------------------------------- | -------- | ------------- | -------- | ----- |
| `hello-world`, never calls the history         | 25,334   | 25,829        | +495     | 1.95% |
| `standard-library`, never calls it             | 564,739  | 565,566       | +827     | 0.15% |
| one `getRules().getTransitions().size()`       | 45,662   | 193,379       | +147,717 | 323%  |
| a 10-zone transcript using much of `java.time` | 411,568  | 542,531       | +130,963 | 31.8% |

The 147,717 bytes of the third row are the 108,033-byte file plus about 39.7 KB of reader and
transition classes. Spread across rebuilds of one source is zero in size. No startup or CPU time was
measured, because no deploy was allowed.

A differential transcript of the compiled module under node covered 142 rows over 10 zones against
JDK 21.0.8, 25.0.2 and 27, which agree with each other on all 142:

| Rows                                | Differ with the flag on | Flag off                                           |
| ----------------------------------- | ----------------------- | -------------------------------------------------- |
| `T`, 46 `getTransitions()` rows     | 0                       | all 10 zones throw `UnsupportedOperationException` |
| `R`, 16 `getTransitionRules()` rows | 0                       | all 10 throw                                       |
| `N`, 40 `nextTransition` rows       | 6                       | the same 6                                         |
| `P`, 40 `previousTransition` rows   | 23                      | the same 23                                        |

Every `N` and `P` difference is `null` on the platform against a transition on a JVM, because
`IntlZoneRules` searches 400 days, and the rows are identical with the flag on and off, so they are
the existing limit and not this change. With the history available both can be answered exactly, and
a later change does: with the flag on, `nextTransition` and `previousTransition` answer from the
history at any distance (a port of `StandardZoneRules`' own search). `ZoneHistoryTest` compares them
with a JVM's `ZoneRules` for London, New York, Kolkata, Lord Howe, Casablanca, Tokyo, Moscow and UTC,
at every transition minus one second, at it, and plus one second, with a sub-second reading, and over
a sweep from 1716 to 2350 every 3,199,999 seconds: 25 tests, 7 of them failing before the change. A
zone the bundle lacks keeps the 400-day search. The bundle is 2024b and the JDKs are 2025b and 2026b; the seven zones in the
JVM tests and the ten in the transcript agree with all three, and a zone changed after 2024b could
differ between the bundle and the platform's own database, which was not tested.

**`%t` cannot be made opt-in without changing the default module of every program that calls
`String.format`.** The one mechanism that fits is a hook in `Formatter`, which every `String.format`
call reaches. Measured with the tree restored after each trial:

| Module                                    | Baseline | Hook only, flag off | Final hook with the JVM's checks, flag off |
| ----------------------------------------- | -------- | ------------------- | ------------------------------------------ |
| `hello-world`, no `String.format`         | 25,334   | 25,334, same md5    | 25,334, same md5 `5ac53895...`             |
| `standard-library`, calls `String.format` | 564,739  | 565,393, +654       | 566,513, +1,774 (+0.31%)                   |

A formatting-only Worker went from 113,645 bytes without the patch to 142,546 with the patch and
one `%tF` over a `long`, +28,901 bytes, +25.4%. The implementation exists, tested, outside the tree
under `results/B5-dateformat/`: 7 `DatesTest` tests over a matrix of more than 10,000 rows (3 locales,
29 conversions, 2 cases, about 60 arguments), with `FormatterTest` 158 passing on both lanes. It
supports `long`, `Date`, `Calendar` and temporals as arguments, 29 conversions and the locales
`null`, `Locale.US` and `Locale.ENGLISH`, and it refuses `%tZ` and `%tc`, an epoch before 1582-10-15
and every other locale. The mechanism closed is a hook in `Formatter` that leaves the default module
byte-identical; opt-in `%t` stays open. At the 7 ms per half megabyte of Startup Scaling the hook is
under 0.01 ms, an extrapolation and not a measurement. Source: `build/batch/v103/results/B5.md`.

**`%t` ships opt-in anyway, with a smaller hook.** `classlib { dateFormat = true }` applies the
tested patch. Moving the suffix and flag checks out of `Formatter` and into `Dates`, which only the
flag registers, cut the hook's default cost in `standard-library` from +1,849 bytes to +361 (+0.06%):
581,241 bytes without the hook, 581,602 with it, two rebuilds identical at 581,602. `hello-world`
is byte-identical, 26,562 bytes, md5 `3a4298b79e686348e1b283d4ff64087e`, so the default module is
unchanged for a program that never formats and not for one that does. With the flag on:

| Program                                | Flag off | `dateFormat` | Delta   |
| -------------------------------------- | -------- | ------------ | ------- |
| `hello-world`, never formats           | 26,562   | 27,147       | +585    |
| `standard-library`, formats, no date   | 581,602  | 605,808      | +24,206 |
| `String.format("%s %d", ...)`, no date | 116,416  | 144,569      | +28,153 |

One build per cell, zero spread across rebuilds. Turning the flag on costs 24 to 28 KB in any program
that formats, because the hook makes `Dates` reachable through `Formatter`. A transcript of the
compiled module under node against `java -Duser.timezone=UTC` on JDK 25.0.2, the only JDK of the
earlier set installed on the machine that ran it, has 788 rows: 732 agree and 56 differ, all of them `%tZ`, `%TZ`, `%tc`, `%Tc` or a row that holds one, which refuse by design.
`DatesTest`, 7 tests, covers the matrix on the JDK 21 lane. No startup or CPU time was measured.
The alternative to +361 bytes, a second copy of `Formatter` in another package picked by a marker,
would cost nothing by default and about 870 duplicated lines to keep in step; it was not built.
Source: `build/batch/v103/results/W3.md`.

## Socket Writes

bytebox's `Socket.write` suspended once per call, because each write took a writer from the platform
socket, wrote and released it. `java.net.Socket` is unchanged and holds its own writer. The decision rule was written before
measuring: buffer only if a 50-write dialogue would cost at least 1 ms more under the current
writer, from a per-write difference that holds across at least six requests with non-overlapping
ranges. The arms ran 2,000 writes into an in-memory sink on the free account (`bb103-b11-socket-write`),
one warm request and 8 interleaved rounds per route, `cpuTime` from `wrangler tail`:

| Route                           | `cpuTime` (ms), warm-up first           | n   | Median | Range  |
| ------------------------------- | --------------------------------------- | --- | ------ | ------ |
| `/a`, per-call writer (current) | 117, 51, 49, 151, 107, 48, 64, 121, 120 | 8   | 85.5   | 48-151 |
| `/b`, writer hoisted            | 49, 63, 32, 68, 80, 40, 39, 33, 58      | 8   | 49     | 32-80  |
| `/c`, buffered, one flush       | 1, 8, 2, 7, 3, 2, 8, 2, 2               | 8   | 2.5    | 2-8    |
| `/floor`, the control           | 3, 2, 0, 0, 0, 1, 0, 0, 0               | 8   | 0      | 0-2    |

**The rule says ship.** Every `/a` reading sits above every `/c` reading. The per-write cost is
0.043 ms, which makes a 50-write dialogue about 2.1 ms, an extrapolation from a linear cost and not
measured at 50. The most conservative bound, the `/a` minimum minus the `/c` maximum, gives 1.0 ms,
on the rule's line, and `/a` spans a factor of three, so the median is not a tight figure. Hoisting
alone recovers about 40 percent and buffering about 97 percent, so the suspension per call is most
of the cost. `Socket.write` now appends to a `StringBuilder`, and the next `read`, `readAll`,
`readUntil`, `startTLS`, `unwrap`, `closed()` or `close`, or the new `flush()`, sends it, and so
does holding 64 Ki characters, a limit added later (`SocketTest`, 11 tests, 0 failed). A failed write surfaces
at that later call, and `close` still closes the connection when its flush fails. The JVM lane has 7
tests (5 failed before the change), and the workers lane passes, 137 passed with the fixture
rebuilt. Ordering under `wrangler dev --local` was checked against a local greenmail: the greeting,
the `EHLO` block, `NOOP` and `QUIT` arrived in order, flushed by `readAll`.

The instrument reads whole milliseconds and the sink is in memory, so this isolates the suspension
and the writer churn from the network. **Arm D, a real `connect()` socket, was not run** because no
echo endpoint was given, so a real write promise may cost more or less per call. The mechanism
closed is the per-call writer; the objective, a cheap write path for a real network sink, stays
open. The account held no Worker before or after. Source: `build/batch/v103/results/B11.md`.

## HTMLRewriter Callbacks

A Java functor that the platform calls while no fiber is running traps. The first design passed
`handler::element` lambdas to the rewriter, and all three probes (a plain handler, a throwing one and
one that awaits) failed with `RuntimeError: dereferencing a null pointer`, because the adapter
opens by reading the current `Fiber`, which is null on a platform entry. A second probe showed that
the classification is per lambda class:

| Probe                                                              | Result                                               |
| ------------------------------------------------------------------ | ---------------------------------------------------- |
| lambda that only calls `setAttribute`, nothing else waits          | works                                                |
| same, plus a second lambda of the functor that calls `Async.sleep` | the waiting one traps, the other still works         |
| every callback starts its own fiber and returns its promise        | works for all, including `Async.sleep` and a KV read |

**The shipped shape is a fiber per call**, which lets a handler wait. The cost, in local workerd wall
time at 1 ms resolution with 2,000 `<p>` elements, 3 rounds, two to three samples a cell:

| Arm                                              | n=2000 (ms)     |
| ------------------------------------------------ | --------------- |
| JS rewriter, no handler (control)                | 0.0, 1.0, 2.0   |
| JS rewriter, sync no-op handler                  | 8.0, 7.0, 7.0   |
| JS rewriter, `async` no-op handler               | 8.0, 7.0, 7.0   |
| JS rewriter, sync `setAttribute`                 | 8.0, 9.0, 9.0   |
| Java fiber path, no handler (control)            | 1.0, 0.0        |
| Java fiber path, no-op                           | 17, 16          |
| Java fiber path, `setAttribute`                  | 18, 18          |
| Java direct functor, `setAttribute`, not shipped | 16 (cold), 9, 9 |

That is about 8 to 9 microseconds a callback on the fiber path against about 4 for the platform
alone, and the direct functor lands at the JavaScript figure. The arms are not identical (the direct
arm builds the page and reads it in Java), CPU time as the platform meters it was not measured, and
2,000 callbacks on the fiber path take about 17 ms of wall, so a page matching thousands of elements
could exceed the free plan's 10 ms before the platform's own 8. Eight concurrent requests whose
handlers each park twice on a 10 ms timer peaked at 8 handlers parked at once with no cross-talk. The
mechanism closed is calling a Java handler directly from the platform when it can wait; direct calls
for handlers that cannot wait stay available as a fast path that was not shipped. The spec had
expected a clear message from a waiting handler and got a null pointer instead. A `@JSBody` parse
error (`for (var pair of ...)`) printed `Error in @JSBody script line 0, char 43: missing ; after
for-loop initializer` and the build still ended `BUILD SUCCESSFUL`; the build fails on that error
now, with `the compiler reported 2 error(s) in @JSBody scripts`, in a plain run and under the
configuration cache. The compiler's parser is older than the platform: `for (var x of [1,2,3])` loads
and runs (`joined=2,4,6`), and the compiler's parser does not read it. At first that failed the
build. Each reported script is now parsed again by `node` as a function body, and only a script
`node` refuses too fails the build, so that loop passes; without `node` on the PATH every reported
script fails the build (`results/X5.md`). A
deployed Worker answered `/` and `/text` with the rewritten page. Source:
`build/batch/v103/results/C2.md`, `V2.md` and `W5.md`.

## A Worker Waiting on Its Own Durable Object

Measured 2026-10-09. `samples/durable-object` did not answer under `wrangler dev --local`: three
requests each returned no response after 15 to 20 seconds, on the tree before the change and with the
generator put back to the shape before the constructor work, so that work did not cause it. The same
sample answered 200 on a free account. The cause is the gate. The generated `index.ts` wrote one
`load()` and one `createGate()` for the script and the Durable Object class used that gate. Locally
a Worker and its Durable Object share one isolate, so the Worker's `fetch` ran inside the gate and
parked awaiting the stub, and the Durable Object's `fetch` queued behind it. The platform runs a
Durable Object in an isolate apart from the Worker, which is why the deployed sample answered and the
workers lane, which hosts its Durable Object in a Worker of its own, never saw the hang.

The same files with only the Durable Object moved onto a second `load()` and a second `createGate()`
answered all three requests (`/a is at 1`, `/a is at 2`, `/b is at 1`). The plugin now generates that
shape, and two `GenerateScaffoldTaskTest` tests pin it (red on the old generator). Two workers-lane
tests, a Durable Object and a Workflow whose constructors take a contended lock and join, are red
against construction off the fiber (11 failed in that run with the entry put back, 58 passed). The
lane cannot reproduce the hang itself. Instances of the Durable Object classes of one Worker share the second
heap and gate. The cost: the module compiles and instantiates twice in every isolate that loads the
script. `Worker Startup Time` from `wrangler deploy` read 12 ms before and 13 ms after, one upload
each, so 1 ms is inside what one run can say; `app.wasmbin` is unchanged at 48,918 bytes; the memory
of the second heap was not measured. A Durable Object whose `fetch` awaits another Durable Object of
the same Worker does queue behind itself under `wrangler dev --local`: both go through the one
Durable Object gate, and a call routed through two stubs did not answer in 1.5 s. The hang itself is
pinned by `npm/tests/workers/durable-self.spec.ts`, which times out with the Durable Object put back
on the handlers' gate. Source: `build/batch/v103/results/Y1.md`, `build/batch/v103/results/Z1.md`.

Deployed, it does not happen. One Java Worker with two Durable Object classes, on a free-plan account
on 2026-10-09, three arms: the handler awaiting a Durable Object (control), a Durable Object awaiting
one of the other class, and a Durable Object awaiting another instance of its own class. Each arm took
20 sequential and 10 concurrent requests with new ids and again with one fixed id: 180 of 180
answered, the slowest in 4.4 s under concurrency with new ids, and `wrangler tail` showed no
cancellation. The same Worker under `wrangler dev --local` answered none of the 12 requests in either
Durable Object arm, and once one had hung, the control arm on that server answered none of 12 either.
Whether the deployed instances shared an isolate was not observed. Source:
`build/batch/v103/results/M1.md`.

`main` runs once per instance, so the handler's constructor and the static initialisers it triggers
run twice per isolate for a project with a Durable Object (`GenerateEntryPointTask` starts the
handler's fiber from `main`). A constructor with a side effect, such as a log line, doubles.

## Workflow Instances Hold the Gate

A Workflow's `run` goes through the isolate's gate like every other entry, so while an instance is
inside `run`, a `step.sleep` included, every other call into that Worker waits. Measured locally with
`wrangler dev` on `samples/workflow`, an instance sleeping 2 s and a Java `fetch` route answering
`pong`, three runs:

| Run | Baseline `/ping` | `/ping` sent right after `/start`, instance in its sleep | Later pings    |
| --- | ---------------- | -------------------------------------------------------- | -------------- |
| 1   | 1.5 ms           | 2.013 s                                                  | 3.3 ms, 2.1 ms |
| 2   | 1.5 ms           | 2.008 s                                                  | 2.0 ms, 1.8 ms |
| 3   | 1.5 ms           | 2.006 s                                                  | 2.4 ms, 2.1 ms |

The baseline is the same ping on the same server with no instance running, and the spread over three
runs is 7 ms. A second probe called `Instance.terminate()` 0.4 s after `/start`: the request waited
1.6 s, the rest of the sleep, and then failed with `instance.cannot_terminate ... finite state`
because the instance had finished. From the same Worker, a sleeping instance cannot be terminated,
paused or sent an event until its `run` returns. Not measured: what platform hibernation does to a
parked `run`. The local engine keeps `run` in process, so an abandoned invocation whose promise never
settles would leave the gate held and wedge the isolate until eviction, which is a hypothesis, not a
result. The options form of `createBatch` failed against the local engine on miniflare
5.20260815.0-alpha (`TypeError: batch.map is not a function`, which takes the list form only), and
was first proven against a stand-in service binding. Miniflare 5.20261006.1-alpha accepts
`{count, params}` and `{instances}` and returns `{created, errors}`, and the lane now runs it through
a root `overrides` entry and an `npm/package.json` pin: `workflows.spec.ts` drives a batch of a
count, a batch of ids, a duplicate id and a mixed batch against the real engine (green on
5.20261006.1-alpha, red on 5.20260815.0-alpha). The override stays because
`@cloudflare/vitest-pool-workers` 0.22.0 and 0.23.0, the latest release, both pin the older miniflare
exactly (`npm view` on 2026-10-09). A deployed Workflow on a free account answered `/start` with an instance id
and `/status` with `queued`, `running` and then `complete` with the receipt. The
retried-step test prints `uncaught exception` and `The Workers runtime canceled this request because
it detected that your Worker's code had hung`; a JavaScript `WorkflowEntrypoint` with the same flaky
step printed the same two lines and completed, so that is the local engine's retry logging. Source:
`build/batch/v103/results/C3.md`.

## Generated Wrangler Configuration

Twelve of the fourteen samples deployed to a free account on 2026-10-09 (36 requests in all, in
`results/V2.md`); two were rejected by `wrangler deploy` on the configuration the plugin wrote. Of
the twelve, `cron` and `email-router` answered error 1101 on `/` because they export no `fetch`, and
`tcp-client` answered 502 (`could not reach example.com: Error: Stream was cancelled.`). A third shape was found
afterwards by a dry run:

| Sample           | `wrangler` said                                                                                                  | Cause                                                      |
| ---------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `durable-object` | `Your Worker depends on the following Durable Objects, which are not exported in your entrypoint file: Counter.` | a binding with no exported class and no `migrations` entry |
| `queue-consumer` | `The field "queues" should be an object but got [{"binding":"QUEUE","queue":"orders"}].`                         | `queues` written as an array, and no consumer declaration  |
| `workflow`       | `"workflows[0]" bindings should have a string "binding" field`                                                   | `workflow(String)` writes `name` and `class_name` only     |

The `workflow` sample deployed and ran to `complete` in the same pass, under the generator as it
stood then. The one-argument form was returned to the keys it wrote at the start of the release, and
a dry run of the committed sample now fails with the message above.

The sample check above cannot see any of these, because it loads the module and never reads
`wrangler.jsonc`. The generator now writes `queues` as `{ "producers": [...], "consumers": [...] }`
(a consumer-only Worker writes only `consumers`), `durableObjects(...)` adds the class export and a
`migrations` entry with `new_sqlite_classes`, and a three-argument `workflow(class, binding, name)`
writes the three keys wrangler wants. The one-argument form is unchanged and still writes a
configuration wrangler refuses. Each shape was checked with `wrangler deploy --dry-run` and then
deployed from a scratch copy of the sample with the corrected declarations, under a `bb103-` name:

- The Durable Object Worker deployed with a startup of 8 ms. `/a` answered `/a is at 1`, `/a is at 2`,
  then `/b is at 1` and `/a is at 3`: one instance per path, state kept.
- The queue Worker deployed with both `producers` and `consumers`, and the API listed it as the
  queue's consumer. A message sent through the API reached the Java consumer: `wrangler tail` showed
  one event with `batchSize` 1 and outcome `ok`. The consumer's insert failed with `D1_ERROR: no such
table: orders`, because the sample has no migration, and the retry arrived 30 seconds later as coded.
  `GET /` answered error 1101, since the handler is a consumer only.
- A Worker that is a queue's consumer cannot be deleted until `wrangler queues consumer remove` runs
  (API codes 10064 and 11005).

The committed `samples/durable-object`, `samples/queue-consumer` and `samples/workflow` were not
changed, so they still write the old shapes: the corrected declarations are in the scratch copies. No
check in `.github/workflows/e2e.yml` runs a dry run per sample. Source: `results/V2.md` and
`results/W5.md`.

## Reproducibility

The same source and the same compiler settings emit a byte-identical binary and an identical runtime
across runs for `samples/hello-world`, which has the same md5 in every build measured.
**`samples/standard-library` does not**: rebuilding one unchanged tree gave two outputs, 2 bytes
apart where two adjacent values swap, of the same size, and a tree with none of the 1.0.3 time files
gave the same two. A size figure is attributable to a change, and an md5 comparison of that module
needs this noise floor.

## Configuration Effects

| Setting                    | Effect                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `minDirectBuffersSize = 1` | declared linear memory 2,162,688 to 1,114,112 bytes; wasm size unchanged                    |
| `modularRuntime = true`    | runtime JavaScript 17,268 to 16,425 bytes, and an ES module rather than a global assignment |
