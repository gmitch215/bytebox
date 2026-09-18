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
from the day it appeared. It does not catch the second, which failed at load rather than at compile,
and that one is now pinned by three generator tests instead.

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

Nine are refused while compiling. Missing is the count of distinct classes and methods the build
named, and the wall is which of the three it stopped at:

| Library                 | Wall  | Missing | First blocker reported                        |
| ----------------------- | ----- | ------- | --------------------------------------------- |
| Joda-Time 2.13.0        | one   | 1       | `Collections.unmodifiableSortedSet`           |
| Commons Codec 1.17.1    | one   | 1       | `java.security.MessageDigest`                 |
| SLF4J API 2.0.16        | three | 4       | `ClassLoader.getResources`                    |
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

The refusals arrive in three layers, and a build hits them in order. First, classes the class library
simply lacks: all of `java.util.concurrent.locks`, the array atomics, and `java.util.StringJoiner`.
Neither refused library uses a lock for parallelism. Second, references into optional integrations
that whole-program compilation cannot know are optional, which is where Log4j stops:
`org.osgi.framework.*` and `org.fusesource.jansi.*` are not on the classpath and are still required.
Third, generic reflection and dynamic loading, which is where Jackson stops: TeaVM's `TClass` has no
`getEnclosingMethod` and no `getGenericSuperclass`, and its `TClassLoader` has no `loadClass`, all
confirmed by reading the classlib jar.

Supplying the first layer was tried and is reported under A Limit of the Substitution SPI. Three
pieces of it shipped: `java.util.StringJoiner`, which unblocked Commons Lang's join APIs, and
`java.util.concurrent.locks.ReentrantLock` with `java.util.concurrent.atomic.AtomicReferenceArray`,
which are the whole of what `jackson-core` was missing.

Two of the refusals have a workaround and one does not. `jackson-core` is the streaming half of
Jackson and stops at the first wall only, so supplying those two classes is enough: it writes and
parses JSON on the runtime. `jackson-databind` is the reflective half and stops at the third wall,
where supplying classes cannot help. Log4j stops in the same place whether the dependency is
`log4j-core` or `log4j-api`, because `LoaderUtil.findUrlResources` calls `ClassLoader.getResources`
to scan the classpath for `log4j2.component.properties` while the property system initialises, which
happens before any logger is requested. There is no classpath at run time to enumerate.

The shipped lock never waits, which is what lets it exist at all. Contention here has one cause, a
fiber suspending while holding the lock, and the lock throws on it rather than admitting a second
fiber to a section written for one. Blocking would need `Object.wait`, and a substituted class cannot
contain a suspending call.

Fourteen builds have now been through this, and the three walls hold across all of them. The two
that stop at wall one stop one class short: Gson needs the two array atomics, which now ship, and
then hits generic reflection exactly where Jackson Databind does. Joda-Time is the narrowest refusal
measured, blocked by a single missing method, `Collections.unmodifiableSortedSet`. Commons Codec is
blocked by `java.security.MessageDigest`. The platform hashes through its own crypto and nothing
retargets `MessageDigest` onto it, so that one is a missing capability rather than a missing class.

Wall two is clearest in OkHttp, which needs `android.net.http.X509TrustManagerExtensions` and
nineteen other classes that are not on the classpath and never would be, because they are its Android
integration. Wall three now has four members: Jackson Databind, Gson and SnakeYAML by reflection, and
every logging facade tried, both Log4j halves and SLF4J, by classpath enumeration. **No logging
facade has compiled.** Each one discovers its provider by scanning for resources, and there is no
classpath at run time to scan.

Commons Lang then shows the fourth thing that can happen, which is a library compiling and meeting a
retargeted API at run time. `StringUtils` compiles `\p{InCombiningDiacriticalMarks}+` in its class
initialiser and the regex translator refuses it by name. Six of seven measured calls work. Two
details came out of reordering them: the refusal lands on whichever `StringUtils` call runs first,
and later calls succeed, where a JVM marks a class whose initialiser threw as erroneous for the rest
of the program. The second is a deviation from the JVM and belongs upstream.

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

A substituted class cannot contain a suspending call. Establishing this took one working substitute
and six builds, each changing one thing.

`java.util.StringJoiner` substitutes in and runs correctly. Adding `Object.wait()` and
`notifyAll()` to it makes the module trap at instantiation with
`RuntimeError: dereferencing a null pointer`, before any user code runs. Removing them restores it.
`synchronized` on its own is fine, and so is `Thread.currentThread()`. The suspending calls are what
break it, and the failing class does not appear in the wasm name section's type list at all.

This closes the mechanism that would have supplied `java.util.concurrent.locks`, since a lock that
cannot block is not a lock. It also belongs beside the already-recorded rule that a substituted class
loses its `@JSBody` methods: both are compiler-level transformations that do not survive
substitution.

Four other explanations were tested and refuted, each by its own build: the interface and
implementation being substituted as a pair; a helper class naming the substitute in its own
descriptors; three source packages sharing one target package; and helpers living inside the
substitution target package. The objective stays open. What is closed is doing it this way.

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

The blast radius is `%s` and `Double.toString` on `Double.MIN_VALUE` and nothing else measured here.

## Regular Expression Agreement

The translator rewrites the constructs where Java and the platform's engine differ, and refuses the
ones with no exact equivalent. What was not established is whether the two engines agree about
everything in between. Measured with the same differential transcript: 20 patterns over 48
pattern and input pairs, comparing `find()`, the match boundaries, every capture group, named
groups, `Pattern.matches` and `split`.

**Every construct the translator accepts produced byte-identical output on both sides.** The only
differences are six lines covering two patterns, and in both the platform refuses:

| Pattern            | Reference JVM | This platform            |
| ------------------ | ------------- | ------------------------ |
| `(?i)HeLLo`        | matches       | `PatternSyntaxException` |
| `[a-z&&[^aeiou]]+` | matches       | `PatternSyntaxException` |

Flags written inside a pattern and character-class intersection are both on the documented refusal
list, so the measurement found no silent divergence at all. Twenty patterns is a corpus and not a
proof, and the constructs already known to be rewritten are the ones most likely to hide one.

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

## Reproducibility

The same source and the same compiler settings emit a byte-identical binary and an identical runtime
across runs. That makes a committed binary usable as a test input with no drift, and makes a size
figure attributable to a change rather than to the build.

## Configuration Effects

| Setting                    | Effect                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------- |
| `minDirectBuffersSize = 1` | declared linear memory 2,162,688 to 1,114,112 bytes; wasm size unchanged                    |
| `modularRuntime = true`    | runtime JavaScript 17,268 to 16,425 bytes, and an ES module rather than a global assignment |
