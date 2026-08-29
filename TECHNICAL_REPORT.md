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
| Declared linear memory, `minDirectBuffersSize = 1` | 1,114,112 bytes                            |

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
whole hello world. Reflection, collections and streams are inexpensive. The last three rows are what
the Retargeted Class Library section below replaces; its figures are measured against a different
baseline and are not comparable with this table row for row.

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

## Retargeted Class Library

The conclusion above says size work belongs in the class library. This section is that work, and the
mechanism it uses.

TeaVM 0.15.0 exposes `org.teavm.extension.spi.substitution.SubstitutionPolicy`, discovered through
`META-INF/services`, and its own class library registers through the same interface. A policy names
classes and where their references should resolve instead. A project compiles against the JDK and the
references are rewritten when the program is compiled to WebAssembly, so a library that was never
aware of this platform compiles unchanged.

Two properties of the mechanism are worth stating because both were found by compiling rather than by
reading. A class used as a substitute has its own references translated through the same map, so a
substitute must be written against the names it stands in for rather than the names it resolves to.
And a substituted class loses the annotations that turn a `native` method into a call into JavaScript,
so any such call has to live in a class that is not itself a substitute.

### What Was Retargeted

| Package                                              | State in the class library                    | Retargeted onto                                        |
| ---------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------ |
| `java.time`                                          | absent entirely, so a date is a build failure | ThreeTen-Backport, with zone rules derived from `Intl` |
| `java.util.regex`                                    | a full pattern compiler and matcher           | the platform's engine, through a translator            |
| `java.util.Formatter`                                | present, reaching locale data from one method | digits computed in Java, separators from `Intl`        |
| `java.net.URL`, `URLConnection`, `HttpURLConnection` | present, wired to `XMLHttpRequest`            | `fetch`                                                |
| `java.net.http`                                      | absent                                        | `fetch`                                                |
| `java.net.Socket`                                    | absent                                        | `cloudflare:sockets`                                   |
| `java.net.InetAddress`                               | absent                                        | DNS over HTTPS through `fetch`                         |
| `java.io.ObjectOutputStream`, `ObjectInputStream`    | markers only                                  | codecs generated at build time                         |

`ServerSocket` and `DatagramSocket` are deliberately not retargeted. The platform accepts no inbound
connection and speaks no UDP, so a program using either fails while it is being compiled.

### What It Cost

TeaVM at `AGGRESSIVE`, obfuscated, no debug information or source maps, WebAssembly only. The baseline
is 17,467 raw and 7,388 gzipped, which is the `StringBuilder` fixture from the Size by Feature table
rather than the bare hello world.

| Program                                         | before, raw | before, gzip | after, raw | after, gzip | saved, gzip |
| ----------------------------------------------- | ----------: | -----------: | ---------: | ----------: | ----------: |
| `String.format`, wide spec set                  |     251,721 |       93,793 |     99,497 |      40,435 |  **53,358** |
| `java.util.regex`, groups, replace, split       |     246,052 |       75,571 |     65,397 |      20,713 |  **54,858** |
| `java.time`, `ZoneId` and `ZonedDateTime`       |     249,688 |       78,474 |     98,492 |      31,223 |  **47,251** |
| `java.time`, including the formatter            |     414,300 |      128,391 |    260,569 |      80,414 |  **47,977** |
| `java.net`, both HTTP clients and `InetAddress` |     317,650 |      102,314 |    181,681 |      61,499 |  **40,815** |

The two `java.time` rows and the `java.net` row have no "before" in the sense the first two do: those
packages do not compile at all without the substitution. Their before column is the same fixture with
ThreeTen-Backport supplying `java.time` but with the regular expressions and the formatter still coming
from the class library, which isolates what retargeting those two was worth.

The `java.time` saving is the one nobody aimed at. `ZoneId.of` validates an identifier with a
`Pattern`, so retargeting `java.util.regex` took 47 KB off a path it was not pointed at.

Three more figures, each measured on its own fixture against the same baseline:

| Program                                |    raw |   gzip | delta gzip |
| -------------------------------------- | -----: | -----: | ---------: |
| `java.net.URL` and `HttpURLConnection` | 52,607 | 20,707 |    +13,319 |
| `java.net.Socket` with its streams     | 42,841 | 17,184 |     +9,796 |
| `Locale` alone                         | 19,955 |  8,284 |       +896 |

That last row is the one that decided the formatter's design. `Locale` costs 896 bytes, so the 85 KB
the Size by Feature table attributes to `String.format` is `java.util.Formatter` itself rather than the
locale graph behind it, and it is paid even for a format string with no floating point in it. One
method dispatches on the conversion character, so every conversion is reachable from every use.

### Where the Zone Rules Come From

A compiled timezone database is 108,033 bytes in ThreeTen-Backport's jar, read at run time through a
service lookup. The isolate already carries a timezone database behind `Intl`, so the rules are derived
from the offsets it reports for a zone and neither the compiled copy nor its reader becomes reachable.
The built module is asserted to carry no trace of either.

`Intl` answers one question: the offset at an instant. Everything else a zone is asked is derived from
that. Which wall-clock readings exist, which happen twice, and where a change falls are worked out by
probing both sides of the reading a day out and keeping the offsets that read back to themselves;
`nextTransition` and `previousTransition` walk a day at a time over a 400-day window and then halve the
interval to the second.

Three limits follow from deriving rather than reading, and each is recorded on the class. The search
window bounds how far ahead a change can be found. The standard offset is the smallest offset the zone
reports across the surrounding year, which is correct because daylight saving only ever moves a clock
forward. And the recorded history is not available at all, so `getTransitions` refuses rather than
answering the empty list a zone with no transitions would give.

### Where the Digits Come From

`String.format` was going to take its digits from `Intl.NumberFormat`, which the isolate already
carries. The conformance suite refused that, and the reason is worth recording because it is not the
obvious one.

Java's `%f` does not round the exact value of a double. It rounds the shortest decimal that reads back
as that double. `0.35` is exactly 0.34999999999999997779553950749686919152736663818359375, so rounding
the exact value to one place gives `0.3` and a JVM answers `0.4`. `(0.35).toFixed(1)` is `0.3`, and
`Intl.NumberFormat` agrees with `toFixed` because both are specified on the mathematical value.
`String.format("%.2f", 1.005)` is `1.01` on a JVM against `1.00` from the platform.

So the digits are computed from `Double.toString`, which is that shortest decimal, in about a hundred
lines of Java. `Intl` supplies only the decimal point and the group separator for a locale.

The same measurement decided the arbitrary-precision path. `BigDecimal` and `BigInteger` would have
made `java.math` reachable, which the Size by Feature table prices at 12,700 gzipped bytes. Both are
read from their `toString` instead, which is exact for the same reason and costs nothing. The price is
that `%x` on a `BigInteger` is refused.

### Refusing Rather Than Approximating

A retargeted API that is nearly the same is worse than one that is missing, because the difference
surfaces as a validation that passes when it should not. Every substitution here either matches a JVM
or refuses at build time.

`java.util.regex` carries most of that weight, because the two syntaxes agree about nearly everything
and disagree about a few constructs that look identical:

| Written                   | Java                      | The platform      | Rewritten to                      |
| ------------------------- | ------------------------- | ----------------- | --------------------------------- |
| `\s`                      | six characters            | a much wider set  | the six, as an explicit class     |
| `\v`                      | vertical whitespace       | a vertical tab    | the vertical whitespace set       |
| `.`                       | excludes `\u0085`         | does not          | an explicit negated class         |
| `$`                       | before a final terminator | end of input only | a lookahead for either            |
| `^`, `$` with `MULTILINE` | `\r\n` is one break       | two positions     | a lookbehind that guards the pair |

Refused, each naming what it was: atomic groups, possessive quantifiers, flags written inside a
pattern, character-class nesting and intersection, `\G`, `\X`, `\N`, `\b{g}`, every character property
except the POSIX names, `CANON_EQ` and `UNICODE_CHARACTER_CLASS`. The formatter refuses `%t` and `%a`.

One difference is documented rather than refused. `CASE_INSENSITIVE` on its own folds only ASCII on a
JVM, and the platform's folding covers the whole of Unicode. Adding `UNICODE_CASE` makes the two agree.

### How the Claims Are Checked

Each retargeted package is compared against the runtime that defines it, on a JVM, so the claim is
checkable rather than asserted.

| Package               | The comparison                                                                                                                                                             |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `java.time`           | the derivation is driven over a JVM's own zone rules and every answer compared with the JVM's, for six zones, every minute of five clock-change days                       |
| `java.util.Formatter` | every format string is run through both this formatter and `String.format`, and the two results compared                                                                   |
| `java.util.regex`     | the translated source is run through a JVM's engine and compared against the original pattern run through the same engine, which checks the rewrites mean what they should |
| `java.io`             | every fixture is written both ways and the two byte arrays compared                                                                                                        |

Four defects came out of those comparisons that reading would not have found: `getOffset` on a
wall-clock reading inside a gap returns the offset _before_ the change rather than after it; a
character class followed by `+` was read as a possessive suffix; `\z` and the anchored form were
written with `$`, which means two different things in the two engines; and a brace that is not a
repetition count is refused by a JVM where it had been treated as a literal.

What no test on a JVM can check is whether V8's regular expression engine and a JVM's agree about the
syntax they share, or whether TeaVM's `Double.toString` produces the same shortest decimal a JVM does.
Both are recorded in Not Yet Measured.

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

The module also exports `teavm_processQueue` and `teavm_stopped`. On the WebAssembly GC target
neither drives anything: **`teavm_processQueue` returns -1 for the module's whole life**, and
`teavm_stopped` stays false. The queue those exports read is the compiler's Java-side heap, and the
GC target bypasses it. `EventQueue.offer` routes through an intrinsic that leaves the module
immediately, so every fiber is handed to the host instead of enqueued in Java.

Two return types are worth stating because neither is what the export name suggests.
`teavm_processQueue` returns a Java `long`, which arrives in JavaScript as a **`BigInt`**;
`teavm_stopped` returns a `boolean` as an i32, so it reads as `0` or `1` rather than `false` or
`true`. A host comparing either against a JavaScript primitive gets the wrong answer silently.

So the event queue lives in the host, reached through the `teavmAsync` import:

| Import             | Signature                                  | What the compiler expects |
| ------------------ | ------------------------------------------ | ------------------------- |
| `teavmAsync.offer` | `(arg, callback, dueTimeMillis) => handle` | `callback(arg)` when due  |
| `teavmAsync.kill`  | `(handle) => void`                         | cancel a pending fiber    |

TeaVM's generated runtime satisfies these with `setTimeout` and `clearTimeout`. That does not survive
a request: a timer still outstanding when a handler resolves is cancelled unless something holds the
request open. A host that owns the queue instead can run the callbacks itself before returning.

Fibers arrive one at a time rather than as a set. `Thread.start()` schedules a single fiber due
immediately; the 50 ms `Thread.sleep` inside that thread is only scheduled once the first fiber runs.
The depth of the chain is not knowable in advance, so a host has to keep draining until the queue
empties.

## Fibers and the Frozen Clock

Workers pin `Date.now()` between I/O. A fiber due 50 ms in the future therefore never becomes due
inside a request that performs no I/O, and the sleep that queued it never returns. Two policies were
measured against a `Thread.sleep(50)` inside workerd.

| Policy                                   | 50 ms sleep completes | Wall time spent | Clock the program reads |
| ---------------------------------------- | --------------------- | --------------- | ----------------------- |
| Honour the due time, drain synchronously | no                    | 0 ms            | frozen                  |
| Honour the due time, await the delay     | yes                   | 50 ms           | advances                |
| Move the host clock to the due time      | yes                   | under 1 ms      | advances                |

Awaiting a real timer works, which establishes that **a `setTimeout` does advance workerd's clock**:
the fiber came due after the await and ran. Moving the clock forward instead completes the same sleep
without spending the request's time budget, and is the only one of the three that fits a 10 ms CPU
allowance.

Moving the clock is only sound if the compiled program reads the same one. `System.currentTimeMillis()`
resolves to the `teavmDate.currentTimeMillis` import, so replacing that single member keeps Java and
the queue in agreement; leaving it as `new Date().getTime()` would strand Java's clock behind the
queue that has already moved past it.

**A budget for a drain cannot be measured in time.** `Date.now()` and `performance.now()` are both
pinned inside a request, so neither can measure elapsed work. A count of fibers run is what is left,
and it bounds a runaway rather than approximating a CPU limit.

## Threads and Exception Handling Opcodes

A compiled program that starts a thread uses the WebAssembly exception-handling proposal's
`try_table` instruction, opcode `0x1f`, which the continuation transform emits. workerd compiles it
as shipped. Node 24 refuses it without `--experimental-wasm-exnref`, so a module that runs on the
target platform can fail to compile on the development one.

## Linear Memory

The `teavmMemory.heapOffset` import tells the compiled program where its heap begins. It is not zero:
the module's own data segment occupies the start of linear memory, and the heap begins after it,
rounded up to **256 bytes**. TeaVM's generated runtime computes this with a helper whose second
argument is a shift rather than a multiple, so the `8` that appears in the generated source means
2^8. A host that reads it as a multiple of 8 hands the heap memory the data segment already holds,
and only a program that reaches linear memory ever notices.

`ByteBuffer.allocateDirect` does not work on this target. A program allocating a 64-byte direct
buffer throws a Java exception whose message the runtime cannot retrieve, and the same program fails
identically under TeaVM's own generated loader, so the limitation is upstream rather than a property
of any particular host.

That name covers more than direct buffers. `minDirectBuffersSize` sizes the linear-memory heap the
interop stages a Java array through on its way to a typed array: converting a `byte[]` copies it 4,096
bytes at a time into a buffer taken from that heap, so the heap is on the path of every array crossing
the boundary whether or not the program ever allocates a direct buffer. It is measured in **megabytes**,
and the compiler's Gradle plugin multiplies the value by 1,048,576 before passing it on, so anything at
or above 2,048 overflows a 32-bit int and lands back on zero.

Zero initialises the heap with a negative-sized root record. The first array conversion then walks the
free list forever: no exception, no trap, a module that stops answering. Measured on `core.wasm`
(26,652 bytes of data segment) by calling the module's own `teavm.malloc` export, which never returns
at zero and answers 26,888 at one megabyte.

| `minDirectBuffersSize`         | Declared linear memory | 4,096-byte allocation |
| ------------------------------ | ---------------------- | --------------------- |
| 0                              | 65,536 bytes           | never returns         |
| 1                              | 1,114,112 bytes        | succeeds              |
| 2 (the compiler's own default) | 2,162,688 bytes        | succeeds              |

## Java Exceptions Crossing Into JavaScript

An uncaught Java throwable does not surface as a `WebAssembly.Exception`. TeaVM's runtime catches it
and rethrows an `Error` subclass whose `message` getter calls back into the instance to read the Java
message.

Identifying it takes care, because two of the three obvious signals are unusable: the wrapper's class
name is minified, and its `name` is plain `Error`. What remains is a symbol the runtime brands the
instance with, whose description is `javaException`. The symbol is unregistered, so it cannot be
recovered with `Symbol.for`; matching on the description is the available test.

Reading the message requires the instance to still be alive, since the getter calls an export.

## Lazily Resolved Globals

The Codegen section records that `new Function` is permitted during module evaluation and refused in
a request. TeaVM's JavaScript interop uses it to resolve a global by name — `new Function("return
Promise;")` — caching the result, which makes the timing of the first resolution decide whether a
program works.

Nothing about a program's source says when that first resolution happens. It is the first time a
particular global is needed, which depends on the route a request takes. A handler can pass every
test and fail in production on a path nobody exercised.

Four kinds of operation resolve a global this way:

| Operation                                       | Global resolved |
| ----------------------------------------------- | --------------- |
| constructing a `Promise` for a handler's result | `Promise`       |
| enumerating an object's properties              | `Object`        |
| a rejected promise, before the waiter resumes   | `Error`         |
| a cast to a JavaScript class                    | that class      |

The third is the one that costs the most, because it is not a thrown error. The runtime tests a
rejection value against `Error` from inside the rejection handler. If resolving `Error` throws there,
the handler never resumes the fiber that was waiting, so **a failed asynchronous operation hangs the
request instead of reporting anything**. What is observable is `The Workers runtime canceled this
request because it detected that your Worker's code had hung`, which names neither the cause nor the
operation.

The fourth is subtler than it reads, because the cast is often one nobody wrote. A method declared
`<T extends JSObject> T await(...)` compiles to a cast at each call site, to whatever `T` resolved to
there. So `ArrayBuffer bytes = await(digest())` casts to `ArrayBuffer` and resolves that global,
while the same call assigned to an interface type resolves nothing — an interface has no runtime
identity to test against. Two calls that look alike behave differently, and only one of them fails.

A property walk from `globalThis` answers the same question, dotted names included, so replacing the
interop's lookup removes the whole class of failure rather than making it unlikely. Nothing about a
program has to change, and no startup step has to be remembered.

## Bundling

A bundler targeting Workers refuses TeaVM's generated runtime outright:

```text
✘ [ERROR] Could not resolve "node:fs/promises"
   app.wasm-runtime.js:30:3102
```

The specifier sits inside a dynamic `import()` on a branch that reads a module off disk, which never
runs on this platform. esbuild resolves a dynamic import's literal specifier whether the branch is
reachable or not, so unreachable code fails the whole build. Stubbing that one import is enough;
scoping the stub to the generated runtime leaves a project's own Node imports alone.

The runtime's JavaScript interop is the other bundling constraint, and it is a runtime one. `@JSBody`
bodies are materialised with `new Function`, which workerd permits during module evaluation and
refuses inside a request. Bodies built during instantiation are therefore fine; a `@JSBody` whose
expression reads a JavaScript global builds its accessor lazily, on first call, and fails with
`EvalError` if that call happens in a request.

## One Heap, Many Requests

A compiled module is created once, at startup, and every request the isolate serves shares its heap.
Combined with suspension that is real, two requests overlap by default: the first parks in a
continuation, the runtime delivers the second, and both are inside the same Java program at once.

workerd detects one consequence and reports it:

```text
A promise was resolved or rejected from a different request context than the one it was created in.
However, the creating request has already been completed or canceled. Continuations for that request
are unlikely to run safely and have been canceled.
```

The mechanism is worth stating exactly, because the wording suggests a race and it is not one. The
second request's work runs the first request's queued continuations, so a promise created for the
first is settled in the second's context. workerd cancels it, and the first request hangs rather than
failing. Serialising entry so a second request does not begin until the first has genuinely finished
is what removes it; the heap is shared either way, but only one request is ever inside it.

## Handler Shape

A handler cannot be a function that returns a value, because it suspends. Three constraints decide
the shape, and each rules out the obvious approach:

- A promise's executor runs synchronously with a JavaScript frame on the stack, and a stack holding
  one cannot suspend. So the handler cannot run inside `new Promise(...)`.
- An entry point returning a value returns before its suspensions finish, so the host has no way to
  know when the handler is actually done.
- A static method exported as `fetch` cannot sit on a class implementing an interface that declares
  an instance `fetch`. The exported entry point has to be a separate class.

What satisfies all three: capture a promise's settle functions without running anything inside the
executor, start the handler on a fiber, and hand the promise back immediately. The fiber is free to
suspend, and the promise settles only when the handler has genuinely returned. The host calls the
entry point, drains its queue once to start the fiber, and awaits the promise.

Fibers a handler starts after its first suspension arrive with nothing draining, so the queue has to
notice them itself. Without that, work started late never runs and the handler waits on it forever.

## What A `@JSBody` Script May Contain

A script written into a `@JSBody` annotation is parsed by the compiler at build time, not by the
runtime. So what the platform supports is not the constraint; what the compiler's own parser reads
is. Three constructs are refused, and each fails the whole build with a syntax error naming a
character offset into a string the source does not contain verbatim:

| Construct                               | What the compiler reports                       |
| --------------------------------------- | ----------------------------------------------- |
| argument spread, `f(...args)`           | `syntax error`, `missing ) after argument list` |
| parameter destructuring, `({ a }) => a` | `invalid object initializer`                    |
| a `BigInt` literal, `1n`                | `missing ; before statement`                    |

`var`, `const`, `let`, arrow functions, template literals, classes, `for...of` and array-literal
spread all parse. The three above have direct equivalents — `f.apply(target, args)`, a named
parameter with property reads, `BigInt('1')` — so the constraint costs nothing beyond knowing about
it.

The offsets are shifted because the compiler wraps a script in a function before parsing, which is
why an offset can exceed the script's own length. The method name in the accompanying stack line is
what identifies the script.

A script is only parsed if it is reachable, so an unreachable one can carry a syntax error
indefinitely and fail the first build that reaches it.

## A JSO Interface Cannot Also Be A Java Interface

A JSO interface is an overlay type, and the compiler treats its methods as final. So one cannot
implement a Java interface that declares a method of the same name:

```text
JS final method Socket.close()V overrides java.lang.AutoCloseable.close()V.
Overriding final method of overlay types is prohibited.
```

Which decides a shape rather than a detail. An ergonomic Java surface over a platform object — one
that is `AutoCloseable`, or `Iterable`, or `Comparable` — has to be a class wrapping the JSO
interface rather than the interface itself. So a socket is a final class holding the platform object,
and try-with-resources works because of that split.

## A Rejected Promise

Waiting on a promise that rejects does not resume the waiting fiber. The compiler's helper reads the
rejection reason, asks its runtime for the Java throwable behind it, and passes that to the waiter's
callback. For a rejection that did not come from Java the runtime answers `undefined`, and the import
is declared to return a `Throwable`, so the conversion throws inside the callback. Nothing resumes.
The invocation stops answering until the platform kills it.

Every binding waits the same way, so this reached the whole surface: a KV read that failed, a
subrequest that was refused, a stream that broke. The rejection shape decides it, which is why it went
unseen — a rejection carrying a Java exception works, and that is the one a Java test produces.

| Rejected with                      | Before             | After                         |
| ---------------------------------- | ------------------ | ----------------------------- |
| `new Error('...')`                 | never returns      | `JSRejection` with the stack  |
| a string                           | `RuntimeException` | `JSRejection` with the string |
| `undefined`                        | `RuntimeException` | `JSRejection`                 |
| an error carrying a Java throwable | the original       | the original                  |

The fix settles the promise in JavaScript before Java waits on it. `promise.then(value => ({failed:
false, value}), reason => ({failed: true, reason, message}))` cannot reject, so what crosses the
boundary is a value the Java side reads rather than an outcome the boundary has to throw.

## Generic Interop Helpers Do Not Scale

`JSArray.of` builds a JavaScript array from a Java one. Its body is generic, the compiler specialises
it per call site, and past some number of specialisations the WasmGC backend emits

```text
local.set[0] expected type externref, found array.get of type (ref null 2)
```

and the module does not compile. The error names whichever method called the helper, so it reads as a
fault in that method. Two different call sites produced it while being made reachable for the first
time, on code that had not changed.

A replacement whose scripts take and return `JSObject` has one shape to compile however many element
types call through, and the failure does not recur.

## A Long In JSON

`JSON.stringify` refuses a `BigInt` outright: `TypeError: Do not know how to serialize a BigInt`.
Since a Java `long` crosses the boundary as a `BigInt`, any object holding one would be
unserialisable, which leaves two options and no third.

Writing it as a number loses precision above 2^53, silently, which is the range a `long` exists for.
Writing it as a string is exact. So a long serialises to a JSON string, and the long reader accepts a
string of digits so the round trip closes.

One direction cannot be fixed from inside: a long that arrives as a JSON **number** above 2^53 has
already been rounded by `JSON.parse` before any bytebox code runs. `9007199254740993` reads back as
`9007199254740992`. Nothing downstream can recover it, so the string form is the interchange shape
rather than a preference.

## Size Of A Real Worker

Eight Workers built through the plugin, each doing one thing, measured as raw WebAssembly:

| Worker         | Raw    | Uses                                 |
| -------------- | ------ | ------------------------------------ |
| hello world    | 25,330 | one handler                          |
| npm dependency | 29,207 | one handler, one npm package         |
| cron           | 36,914 | a scheduled handler and KV           |
| durable object | 37,661 | routing to a Durable Object over RPC |
| email router   | 38,214 | an email handler and KV              |
| tcp client     | 40,203 | raw TCP with TLS                     |
| KV counter     | 40,589 | KV reads and writes                  |
| queue consumer | 49,895 | a queue consumer and D1              |

A hello world through the plugin measures 25,330 rather than the 16,539 in the Summary, because the
plugin's entry point carries the suspension machinery a handler needs and a bare `main` does not.
Every one of these sits under 50 KB against a 3 MB free-plan ceiling, and each stays below the
compression crossover, so all eight ship as raw bytes with no decompressor.

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
- **The crossover between raw and compressed packaging**, which the Packaging table brackets between
  56 KB and 216 KB of raw wasm but does not pin.
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
- **Startup and `cpuTime` for a Worker that uses the retargeted class library.** The deployed figures
  in the Summary come from a hello world. A binary sixteen times its size has neither figure measured.
