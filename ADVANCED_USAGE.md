# Design Notes

The samples in this directory each show one feature. This document is the other half: the rules the
whole thing is built on, why each one is there, and which sample demonstrates it. Read it if you are
deciding whether bytebox fits a problem, or if a sample did something and you want to know why.

Every figure here was measured on the samples in this repository. `cd samples && ../gradlew buildWorkers`
reproduces them.

## The Handler Is the Configuration

A Worker declares what it handles by implementing interfaces. `Worker` gives it `fetch`, `Scheduled`
gives it `scheduled`, `Mail` gives it `email`, and so on. Nothing else declares anything.

The plugin reads which interfaces the handler implements and derives three things from that: which
JavaScript exports to emit, which keys to write into `wrangler.jsonc`, and which entry points the gate
has to guard. A trigger you did not implement produces no export, no configuration key, and no code.

[cron](samples/cron) is the clearest case. It implements only `Scheduled`, so the generated Worker exports
`scheduled` and nothing else, and the generated configuration carries a `[triggers]` block and no
routes. There is no way for the two to drift, because one is derived from the other.

The size consequence follows from the same fact. An unimplemented handler is unreachable code, so
dead-code elimination removes it, and the binary agrees with the configuration about what the Worker
does. `cron` is 38,134 bytes of WebAssembly against `hello-world`'s 25,334 (measured 2026-10-08).

## Blocking Calls, One Thread

`env.kv().get("key")` looks synchronous and returns a `String`. Underneath it is a promise, and the
compiler has rewritten the calling method into a continuation that the host resumes when the promise
settles.

This is not a convenience wrapper. TeaVM compiles `Thread` on WasmGC into a fiber on the host's timer
queue, and every blocking call suspends that fiber rather than an OS thread. So a Java API that reads
like a JVM API is the accurate one here, and a future-returning API would be describing the wrong
machine.

What suspension does not buy is parallelism. Cloudflare documents that Workers run one thread and do
not provide the Web Worker API, and WebAssembly threads need `SharedArrayBuffer` with `Atomics.wait`,
which is callable only on a worker thread. Two fibers never run at once. `ExecutorService` therefore
ships as a single-fiber implementation, and a thread pool is not something that can be built on this
runtime.

[kv-counter](samples/kv-counter) reads and writes KV with plain calls and no callbacks. Each one suspends.

## The Gate

One WebAssembly heap serves an isolate, and an isolate serves many requests. A second request arriving
while the first is parked in a continuation would run on the same heap, in the middle of the first
one's work.

The loader holds a gate on the module, and every entry point goes through it: `fetch`, `scheduled`,
`queue`, `email`, `tail`, and every method on a Durable Object. Durable Objects are the case worth
stating. A Worker that waits on its own Durable Object would queue behind itself if both ran on one
gate, because the Worker's `fetch` holds the gate while the Durable Object's `fetch` waits for it. The
generated entry therefore loads the module a second time for the Durable Object classes, with its own
gate, and every instance of those classes shares that second heap and gate. The cost is a second
compile and instantiate at startup and a second heap in every isolate that loads the script: startup
12 ms against 13 ms on one upload each, and memory not measured. `main` runs once per instance, so a
handler's constructor and the static initialisers it triggers run twice; a constructor with a side
effect, such as a log line, doubles. A Durable Object whose `fetch` awaits another Durable Object of
the same Worker still waits on itself under `wrangler dev --local`, because both use the Durable
Object gate (measured: no answer in 1.5 s).

[durable-object](samples/durable-object) shows the shape. Its methods do not synchronise anything themselves,
because the gate has already serialised them.

## Ordinary Java, Retargeted

The class library TeaVM ships has no `java.time` at all, an HTTP stack wired to `XMLHttpRequest`, and a
`java.util.Formatter` that reaches the locale data behind it from one method. None of those work on
Workers, and a library you did not write is not going to stop using them.

So the compiler rewrites the references. A project writes `java.time.Instant` and the reference
resolves to an implementation that works here; the library does not know it has been retargeted, which
is what makes an unmodified dependency compile. The mechanism is TeaVM's substitution SPI, which its
own class library registers through.

What is retargeted today:

| Written in the project              | Where it goes                                           | What it pulls in                              |
| ----------------------------------- | ------------------------------------------------------- | --------------------------------------------- |
| `java.time.*`                       | ThreeTen-Backport, with the zone rules read from `Intl` | three times as much once a formatter is used  |
| `java.net.URL`, `HttpURLConnection` | `fetch`                                                 | the client below shares most of it            |
| `java.net.http.HttpClient`          | `fetch`                                                 | `java.time`, through `Duration`               |
| `java.net.Socket`                   | `cloudflare:sockets`                                    | nothing beyond the stream plumbing            |
| `java.net.InetAddress`              | DNS over HTTPS through `fetch`                          | included in the client above                  |
| `java.util.regex`                   | the platform's own engine                               | a fifth of the class library's own engine     |
| `java.util.Formatter`               | digits worked out in Java, separators from `Intl`       | under half the class library's own formatter  |
| `java.io.ObjectOutputStream`        | codecs generated at build time                          | varies with the types registered              |
| `java.security.MessageDigest`       | pure Java: MD5, SHA-1, SHA-256, SHA-384, SHA-512        | the algorithms the program names              |
| `java.lang.ClassLoader`             | an empty classpath: every lookup finds nothing          | nothing beyond the class itself               |
| `java.util.concurrent` locks        | `ReentrantLock`, `Condition` and `CompletableFuture`    | 169 bytes over the old lock in `jackson-core` |

[standard-library](samples/standard-library) uses five of those in one Worker, which is why it is the largest
sample. It exists to prove they link together in a real project rather than only in a test fixture.
Each row is priced in the [technical report](TECHNICAL_REPORT.md).

Three of the rows exist because a library asked for the class and the class library had none.
`MessageDigest` is the one Commons Codec needed: `DigestUtils.sha256Hex("bytebox")` prints the same
digest `openssl` does. `ClassLoader` answers like a classpath with nothing on it, which is what
SLF4J's provider discovery needs to fall back to its no-op logger: every resource lookup returns
empty and `loadClass` throws `ClassNotFoundException`. The same change supplies a non-blocking
`LinkedBlockingQueue`, whose `take()` on an empty queue throws `IllegalStateException`, so code
written against a queue that waits for a producer fails loudly. The third row is the lock and
future work in [Waiting Inside the Platform's Calls](#waiting-inside-the-platforms-calls).

The timezone row is the one that pays for itself twice. The isolate already carries a copy of the
timezone database behind `Intl`, so the rules are derived from the offsets `Intl` reports and neither
a compiled database nor its reader enters the binary.

## Refusal

Retargeting has a failure mode that matters more than the size: an implementation that is _nearly_ the
same. A regular expression that matches slightly differently, or a number that rounds one way instead
of the other, shows up as a validation that passes when it should not.

So the rule everywhere is that a construct either means here what it means on a JVM, or it is
refused, or it is on the list of measured differences below. Almost every refusal happens when the
program runs: the exception is thrown from the call
that meets the construct, and the build has already succeeded. Only a class that has no counterpart at
all, such as `ServerSocket`, fails the build.

`java.util.regex` is where this is most visible. Java's `\s` is six characters and the platform's is a
much wider set; `\v` is vertical whitespace in Java and a vertical tab in JavaScript; `.` excludes
`\u0085` in Java and does not in JavaScript; `$` matches before a final line terminator in Java and
only at the end in JavaScript. Every one of those is rewritten into something explicit. Atomic
groups, possessive quantifiers and character-class intersection are rewritten too, and a flag group
that opens the pattern is read as the flags it sets. A flag written anywhere else has no exact
equivalent, so `Pattern.compile` throws a `PatternSyntaxException` that names it. Five more regular
expression refusals follow from the same rule. An atomic group is refused inside a lookbehind. A
capturing group inside an atomic group or a possessive quantifier is refused, because the rewrite
would read the group as null where a JVM keeps a stale span; a group beside or around the atom is
read as written. A backreference under `CASE_INSENSITIVE` without `UNICODE_CASE` is refused, because
the ASCII fold the translator writes cannot make a backreference fold the way a JVM does; the way
out is `UNICODE_CASE`, which folds as the platform does, or dropping the flag. A backreference to
a group that is not open yet, which a JVM never matches, is refused, so `\101` without a group 1
throws. And a backreference inside the group it names, as in `(a\1)`, is refused, because a JVM
reads that group as not closed yet and the platform reads the reference as the empty string. The
`Pattern` javadoc lists each.

The rule has measured gaps, where a construct is accepted and still differs. These are all of them
that the 1.0.3 measurements found:

1. `CASE_INSENSITIVE` alone folds ASCII, as a JVM does, and 0 of 2,391 case pairs differ. With
   `UNICODE_CASE` the platform's folding differs at 62 characters, among them U+017F, U+212A, U+0130,
   U+0131 and the Greek letters with iota subscript.
2. `.`, a negated class, `\S`, `\H`, `\V`, `\D`, `\W` and `\P{Posix}` take a character above U+FFFF
   whole, as a JVM does: the matcher runs a second source under the `v` flag over text that holds
   one. Under `UNICODE_CASE`, `Pattern.matcher` throws `UnsupportedOperationException` for such
   text when the pattern holds one of them. A lookbehind that holds `.` or a negated class is sized
   by a JVM in UTF-16 units, so `(?<=..)x` over two emoji and `x` finds no match on a JVM and a
   match here.
3. A backreference to a group that did not take part matches the empty string, and a quantified
   group is reset on each iteration, where a JVM fails the first and keeps the last span for the
   second.
4. A pattern that holds a negated block, a class inside a class or an intersection runs under the
   platform's `v` flag, which reads code points. A group that captured an unpaired surrogate reads
   back differently against half of a pair. The same patterns are refused with `UNICODE_CASE`. A
   search that a JVM starts in the middle of a surrogate pair (after an empty match, from
   `find(int)`, or at each unit when nothing in the pattern can take half of a pair) is made by the
   matcher itself, so `[a[b]]*` over `a😀b` reports the empty match at index 2 as a JVM does.
5. Under `CASE_INSENSITIVE` alone, a hyphen beside a set or a finished range inside a class keeps the
   platform's folding. `\0nn`, `\cX` and `\x{h...}` are exact, and an incomplete `\x`, `\u` or `\c`
   is refused as a JVM refuses it.

A JVM and this platform agree on the rest of what was measured: the regular expression transcripts,
the 788-row `%t` transcript (JDK 25 only) apart from the refusals it names, and `%e`, `%f`, `%g` and
`%a` over every subnormal. Number text agrees as well. `Double.toString`, `Float.toString`,
`String.valueOf`, concatenation and `%s` match JDK 27 for every double and float tested, subnormals
included: 0 of 1,008,011 platform rows differ for doubles, and every positive finite float
(2,139,095,039) agrees when checked on a JVM, with 999,049 sweep rows agreeing on the platform.
`Double.parseDouble` and `Float.parseFloat` accept and refuse what a JVM does, including
hexadecimal floats, type suffixes and surrounding whitespace, read every digit, and a refusal carries
the JVM's message (`For input string: "..."`, `empty String`, `multiple points`). The costs: a
normal `Float.toString` call takes 327 ns where it took 125 (2.6 times), and a typical value 238 ns
where it took 88. One probe app that calls both parse methods once each is 5,208 B smaller; one method alone was not measured. `standard-library`
measured 597,347 B with the exact route for doubles and the class library's for floats, and 595,366 B
with both exact. The technical report has each measurement and says which gaps are fixed.

`java.net` does the same by omission. `Socket` is retargeted and `ServerSocket` deliberately is not,
because the platform accepts no inbound connection. A program using `ServerSocket` fails while it is
being compiled. [tcp-client](samples/tcp-client) records the rest of what
`cloudflare:sockets` will not connect to, and that list is what decides whether raw TCP is the right
tool for a given protocol.

The formatter throws on `%t` unless `classlib { dateFormat = true }` is set, and the message says so.
`%a` and `BigInteger` `%x` and `%o` are always supported. A `null` under `%d`, `%x`, `%o`, `%f`, `%e` or
`%g` prints `null`, the `0` flag pads an infinity with spaces, and `%D`, `%O` and `%F` throw
`UnknownFormatConversionException`, all as a JVM does. `%t` is opt-in because it makes the calendar
and locale graph reachable from every `String.format` call, which is the cost the class exists to
avoid; see Opt-In Extras.

## Java Libraries

An unmodified Java library compiles here or it does not, and which one it is turns on what the
library touches rather than on how big it is. Fourteen library builds were measured against the same
Worker shape.

Five compile and run:

| Library                 | Raw wasm | gzip -6 | Over baseline | What the program calls                 |
| ----------------------- | -------- | ------- | ------------- | -------------------------------------- |
| none, baseline          | 26,193   | 11,087  | 0             | one string constant                    |
| Commons Collections 4.4 | 30,523   | 12,430  | 4,330         | `DualHashBidiMap`, `getKey`            |
| Guava 33.4.0-jre        | 51,862   | 19,693  | 25,669        | `ImmutableList.of`, `Joiner.on().join` |
| Commons Lang 3.17.0     | 89,535   | 30,734  | 63,342        | seven `StringUtils` methods            |
| org.json 20250107       | 125,722  | 43,434  | 99,529        | `JSONObject.put`, `toString`           |
| Jackson Core 2.18.2     | 330,555  | 115,455 | 304,362       | `JsonFactory`, generator and parser    |

Nine were refused while compiling when these were measured, and seven still are. Commons Codec
compiles now that `java.security.MessageDigest` is supplied (MD5, SHA-1, SHA-256, SHA-384 and
SHA-512): `DigestUtils.sha256Hex("bytebox")` prints
`c6cd749b880d987807bcec591c25e422685279d8f48e330d069c57e21a21426c`, and no other codec class was
tried. SLF4J API compiles at 179,191 bytes once `ClassLoader` answers with an empty classpath, and
with no provider it falls back to its no-op logger, so the facade runs and nothing is printed.

For the rest, Missing is the count of distinct classes and methods the build named, and the wall is
which of the three it stopped at. The two Log4j rows are from a rebuild after `ClassLoader` was
supplied and count distinct refusals; the same count on the builds before the change was 12 and 27.
The other rows are the original measurement.

| Library                 | Wall  | Missing | First blocker reported                                  |
| ----------------------- | ----- | ------- | ------------------------------------------------------- |
| Joda-Time 2.13.0        | one   | 1       | `Collections.unmodifiableSortedSet`                     |
| Gson 2.11.0             | three | 5       | `Class.getGenericSuperclass`                            |
| Log4j API 2.24.3        | three | 10      | several, and `ClassLoader.getResources` is now answered |
| Log4j Core 2.24.3       | three | 26      | several, and `ClassLoader.getResources` is now answered |
| Jackson Databind 2.18.2 | three | 9       | `Class.getGenericSuperclass`                            |
| SnakeYAML 2.3           | three | 10      | `java.beans.Introspector`                               |
| OkHttp 4.12.0           | two   | 20      | `android.net.http.X509TrustManagerExtensions`           |

Each figure prices the part of the library the program reaches, not the library. Dead-code
elimination means a project calling one splitter pays for one splitter, and the number moves with
every call added. Commons Lang shows the size of that effect: the same dependency measured 64,656
bytes calling one method and 89,535 calling seven.

What the bytes buy you is startup, which is the only budget they are spent from. Startup Scaling in
the technical report prices compiled code at roughly 7 ms per half megabyte, so Jackson Core's
304,362 bytes are worth a few milliseconds of the one second available.

A refusal comes from one of three places, and they are worth telling apart because only the first two
are anyone's fault.

The first is a class library gap that has nothing to do with the platform. When these were measured
there was no `java.util.concurrent.locks` package at all, and no `java.util.StringJoiner`; the class
library still has no `ReentrantReadWriteLock`, `StampedLock`, `Semaphore` or `CountDownLatch`.
Neither refused library used a lock for parallelism; both guarded caches with one. A missing class
is enough to refuse a whole library whatever it was for.

The second is an optional integration. Whole-program compilation has no idea that a dependency is
optional. A JVM never loads Log4j's OSGi support on a machine without OSGi, because it resolves
classes lazily and that one is never asked for. A closed-world compiler has to resolve every
reference it can see, so `org.osgi.framework.*` and `org.fusesource.jansi.*` become requirements of a
program that will never call them. Jackson carries DOM support the same way, through
`org.w3c.dom.Node`. This is the wall most libraries hit, and it is why a library's own modularity
works against it here.

The third is the one no stub can clear. Jackson resolves generic types at run time, and TeaVM's
`Class` has `getEnclosingClass` but neither `getEnclosingMethod` nor `getGenericSuperclass`; its
`ClassLoader` has no `loadClass`, and the one bytebox supplies throws `ClassNotFoundException`, which
is enough for a library that asks and falls back and no use to one that needs the class. Retaining
generic signatures is a compiler decision somebody could
make. An open world is not, and a closed-world compiler is the thing that makes a 25 KB Worker
possible in the first place.

Commons Lang shows what the boundary looks like when a library gets all the way through and then
meets a retargeted API. `StringUtils` compiles a pattern in its class initialiser for `stripAccents`.
Before 1.0.3 the translator refused that pattern, and the run was measured with this result (the
pattern compiles now and the run was not repeated, so the next wall is not known):

```txt
java.util.regex.PatternSyntaxException: this platform's regular expressions cannot express
\p{InCombiningDiacriticalMarks}, which needs the platform's own property syntax and the flag
that comes with it
```

Six of its seven measured calls work. The seventh names what it cannot do. Two details are worth
knowing before you debug this shape yourself: the refusal surfaces on whichever `StringUtils` call
runs first rather than on the one that needs the pattern, because a class initialiser runs once on
first touch; and calls after that one succeed, where a JVM would answer `NoClassDefFoundError` for
the rest of the program.

### Working Around a Refusal

Two of the refusals above have an answer and one does not, and which is which follows from the three
walls.

**Jackson: use `jackson-core` instead of `jackson-databind`.** The streaming API compiles and runs at
330,555 bytes, writing and parsing real JSON. It needs no reflection, because a `JsonGenerator` is
told what to write rather than working it out from a type. Databind is the half that resolves generic
types at run time, which is the third wall, so no amount of supplied classes moves it.

```java
JsonFactory factory = new JsonFactory();
try (JsonGenerator gen = factory.createGenerator(out)) {
	gen.writeStartObject();
	gen.writeStringField("name", "bytebox");
	gen.writeEndObject();
}
```

For turning a Java object into JSON, the plugin already generates codecs at build time from
`@JSONType`, which is the same job done at the only time this platform can do it.

**SLF4J: yes, and it logs nothing.** `ClassLoader.getResources` has nothing to enumerate here: the
classpath existed at build time and there is no such thing at run time. SLF4J scans for a provider
through it, finds none, and falls back to a no-op logger, so `LoggerFactory.getLogger(...)` and
`log.info(...)` return and print nothing, apart from SLF4J's own no-provider warning.
A binding that finds its provider some other way was not tried. `System.out.println` reaches the
Worker log.

**Log4j: no, and dropping to `log4j-api` does not help.** `LoaderUtil.findUrlResources` calls
`ClassLoader.getResources` to scan the classpath for `log4j2.component.properties` while the property
system initialises, so the scan happens before any logger is asked for. Answering that call did not
clear either half: `log4j-api` still names 10 distinct classes and methods and `log4j-core` 26, among
them `org.osgi.framework`, `Class.getResource`, `Thread.setContextClassLoader` and
`ReentrantReadWriteLock`. The scan was the first thing the build named and not the only one.

**Joda-Time stops at one method, and the method cannot be added alone.** `Collections` is missing 21
of the JDK's 74 public static methods, among them `unmodifiableSortedSet`, which Joda's
`ZoneInfoProvider` calls. A substitute of `Collections` that delegates to the class library ran
`new LocalDate(2026, 1, 1).plusDays(59)` and printed `2026-03-01`, but the substitution rewrites a
whole class, so it would carry all 53 delegating methods and 21 new ones, and a policy shipped by a
project cannot replace a class the class library already has. It did not ship.

**What made the streaming case work** was supplying two classes the class library omits,
`java.util.concurrent.locks.ReentrantLock` and `java.util.concurrent.atomic.AtomicReferenceArray`.
The lock blocks: a fiber that meets a held lock suspends until it is released, and `Condition` and
timed waits work, from a handler's constructor as much as from a request. `CompletableFuture` is
supplied the same way.

## Kotlin

Kotlin compiles to the bytecode TeaVM reads, so a Kotlin Worker goes through the pipeline a Java one
does, and `kotlin-stdlib` reaches the compiler with nothing added. [samples/kotlin](samples/kotlin)
applies `org.jetbrains.kotlin.jvm` beside the plugin and handles requests from a Kotlin `object`.

Three things stood between a Kotlin project and a Worker, and the plugin handles all of them. Each is
listed with the first error a build printed:

| Wall                                          | First error                                                                                                           | What the plugin does                                                                       |
| --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| a Kotlin task reads a generated directory     | `Task ':kotlin-jvm:compileKotlin' uses this output of task ':kotlin-jvm:generateNpmBindings' without declaring ...`   | orders `compileKotlin` after the bindings generator                                        |
| the Kotlin `bytebox` source set does the same | `Task ':kotlin-jvm:compileByteboxKotlin' uses this output of task ':kotlin-jvm:generateCodecs' without declaring ...` | orders `compileByteboxKotlin` after the entry point and both codec generators              |
| a Kotlin `object` has a private constructor   | `ByteboxEntry.java:15: error: ObjectWorker() has private access in ObjectWorker`                                      | the generated entry reads `ObjectWorker.INSTANCE` when the handler has one of its own type |

A handler that is a class with a public no-argument constructor needed none of them. A Durable Object
cannot be an `object`, because there is one instance per id, and the build refuses it with a message
that says so.

What compiles and matches a JVM, by diffing the printed lines of the module against the same source
on a JVM: collections, `Sequence`, string templates, data classes, lambdas, `inline`, `when`, `?.`
and `?:`, `lateinit`, companion objects, `enum class`, sealed classes, `by` delegation, `lazy`,
`runCatching`, `Regex`, `String.format`, `kotlin.random.Random` and extension functions: 29 lines
from a 13-construct transcript for a class handler, the same 29 for an `object` handler, and 21 more
from a probe of 21 constructs, with no difference. `@JSONType` Kotlin classes get a generated codec,
and a Kotlin `DurableObject` class is found and exported. The codec round trip was not run in a
module.

The size is whatever the standard library reaches. `samples/kotlin` is 46,761 bytes. The 13-construct
transcript is 304,569 as a class handler and 304,498 as an `object`, and the probe 359,988, against
26,562 for `hello-world` and 27,417 for the control the transcripts are measured against
(`experiments/dep-none`), all built from one tree on 2026-10-09, one build each. They were 221,364,
222,879 and 275,285 when first measured on 2026-10-08. The growth was not diagnosed; the regular
expression and formatter work and the module-start fiber are the candidates. Do not budget a Kotlin
Worker from the Java samples.

What does not work:

- **kotlinx-coroutines 1.11.0.** All six programs first measured (`runBlocking`, `launch`, `launch`
  and `join`, `delay`, `withContext(Dispatchers.Default)` and `launch(Dispatchers.Unconfined)`) stopped
  in `generateWasmGC`, and five of them (the empty program, `launch`, `delay`, `withContext` and
  `Unconfined`) still do after `ClassLoader` and a blocking `ReentrantLock` were supplied. The first
  refusal is `java.util.concurrent.locks.LockSupport`, and the seven that remain are `LockSupport`,
  `ReentrantReadWriteLock` and its `ReadLock` and `WriteLock`, `RejectedExecutionException`,
  `Thread.setContextClassLoader` and `CoroutineScheduler$Worker.setContextClassLoader`.
  `ClassLoader.loadClass` and `ClassLoader.getResources`, which the first measurement also listed,
  are answered now. They are class library gaps, which is why the objective stays open.
- **A `suspend` lambda started with `startCoroutine` or `createCoroutine`.** It builds and the module
  fails V8 validation: `struct.set[1] expected type (ref null 12), found global.get of type (ref 126)`.
  The vtable type of `ContinuationImpl` is declared with no supertype, which TeaVM 0.15.0 emits and
  the plugin cannot change. `sequence { yield(...) }` works.

The full table, with each program's build, is in the technical report under Kotlin on the JVM
Target.

The other two Kotlin targets load on workerd and are not built by the plugin. Kotlin/Wasm
(`wasmJs` and `wasmWasi`) and Kotlin/JS (IR, ES modules) each ran a hello `fetch` handler on a
Cloudflare free account with a hand-written entry file: 14 and 38 lines for the two Wasm targets, 5
for Kotlin/JS. Neither Wasm target's generated loader runs on workerd (`Invalid URL string`,
`(intermediate value).resolve is not a function`, `The WASI method is not implemented`). Startup was
1 to 2 ms for the Wasm targets and 1 to 3 ms for Kotlin/JS, against 9 to 18 ms for the Java hello
world in the same sessions, which says nothing about a larger program because those modules are 3.5
KB and 9.7 KB, and 309 KiB of JavaScript. Kotlin/JS requests showed `cpuTime` of 6 to 15 ms that the
Java control did not, in clusters near the start, and the cause was not isolated. The technical
report has both measurements.

## Web Applications

A Worker answers HTTP, so the question of whether a Java web framework runs here comes up early. The
answer for the server-side frameworks is no, and the reason is structural rather than a matter of
size or effort.

Spring, Jakarta EE, Micronaut, Quarkus and Dropwizard are all built on assumptions this runtime does
not offer. They scan the classpath at startup and instantiate what they find, which needs an open
world and the reflection that goes with it: the same `getGenericSuperclass` and `loadClass` that
refuse Jackson. They bind a listening socket, and the platform accepts no inbound connection, which
is why `ServerSocket` is left unresolved on purpose. They expect a thread per request, and two fibers
never run at once here. Spring Boot also assumes a JAR launcher and a servlet container, neither of
which exists.

Some of that can be argued with. Spring's AOT compilation exists to do the scanning ahead of time,
which is the same idea as compiling closed-world. What cannot be argued with is the socket: a
framework whose job is to own the listener has nothing to own when the platform hands it a request
instead.

What replaces it is smaller than it sounds. `Router` in `dev.gmitch215.bytebox.http` does method and
path matching with a filter chain, which is the part of a web framework a Worker still needs. The
rest of what Spring supplies is either provided by the platform or unnecessary: a binding is
dependency injection with the lifetime already decided, a Durable Object is the stateful component,
and there is no container to configure because the isolate is the container.

Client-side Java is a different question with a different answer, and the retargeting table above is
where it is answered. `java.net.http.HttpClient` works because it is backed by `fetch`, and a library
that makes HTTP calls rather than serving them has a good chance of compiling.

## Waiting Inside the Platform's Calls

A blocking call suspends a fiber, and the gate admits one entry into the module at a time. Those
two facts combine in four places that change how you build.

**Locks and futures block.** `ReentrantLock` suspends a fiber that meets a held lock, and
`Condition`, `lockInterruptibly` and timed `tryLock` work, as do `CompletableFuture`'s `supplyAsync`,
the `then*` chain, `allOf`, `anyOf`, `get`, `join` and `get(timeout)`. A scenario transcript of 34
`CompletableFuture` cases printed the same text from the module as from JDK 27. The `*Async`
variants of the `then` methods, `*Either`, `*Both`, `orTimeout` and `completeAsync` are absent, and a
program that calls one fails when the build links it. Timed waits count on `System.currentTimeMillis`,
because `System.nanoTime` does not move across a sleep under the loader's virtual clock.

The rule that decides where they are safe is whether a fiber is running. Reaching a method TeaVM
compiled as async with no fiber under it traps with `RuntimeError: dereferencing a null pointer`,
whether or not the call would wait, and that includes `lock()` on a lock nobody holds and `join()` on
a finished future. The generated entry point starts the handler on a fiber, so the handler's
constructor and every static initialiser it triggers may block. A handler that throws while it is
built fails every request with `module start failed: ...`, and one that never finishes fails them
with `module start has not finished`. A Durable Object is built on the fiber of the first call for its
id, and a Workflow on the fiber of each `run`, so their constructors may block too. Two calls for the
same Durable Object id that arrive while its constructor is waiting get one instance. A substituted class can contain a suspending call; what traps is the context.

**HTMLRewriter handlers run on fibers.** The platform calls a handler while no fiber is running, and a
Java lambda that can wait traps when called that way. Each handler call therefore starts its own
fiber and hands the platform the promise, so a handler may call a binding or `fetch`. The cost is a
fiber per callback: about 8 to 9 microseconds against about 4 for the platform's own callback, in a
local measurement of 2,000 elements, so a page matching thousands of elements spends more of the
free plan's 10 ms of CPU than the JavaScript rewriter would. Register the handler kinds you need and
match narrowly. [samples/html-rewriter](samples/html-rewriter) rewrites a static page on `/` and
words in text on `/text`, and answered both rewritten when deployed to a free account.

**A Workflow's `run` holds the gate.** The platform runs a Workflow by calling `run` on the Worker
that declares it, and `run` goes through the gate like every other entry. While an instance is
inside `run`, a `step.sleep` included, every other call into that Worker waits: a `fetch` sent during
a 2 second sleep took 2.0 seconds against 1.5 milliseconds, in three local runs. For the same
reason a sleeping instance cannot be terminated, paused or sent an event from that Worker until
`run` returns. A Workflow that sleeps long belongs in a Worker that serves nothing else. Releasing
the gate during a sleep is unsafe, because a fiber resumed by another entry's drain would settle its
promise from the wrong request context. What the platform does to a parked `run` when it hibernates
was not observed. [samples/workflow](samples/workflow) shares one Worker for the handler and the
Workflow, which is fine for a sample, and its Workflow name must be unique per account. Deployed to a
free account, an instance read `queued`, `running` and then `complete` with its receipt.

**A write is sent later.** `Socket.write` appends to a buffer and the next `read`, `readAll`,
`readUntil`, `startTLS`, `unwrap`, `closed()` or `close()`, or an explicit `flush()`, sends it, as
does holding 64 Ki characters. The cap is one stream chunk, far above any protocol line (SMTP lines
stop at 512), so the coalescing below is untouched while a large upload stays bounded in the
isolate's memory; it is a judgement and not a measured optimum. Writing 2,000 lines
to an in-memory sink cost a median 85.5 ms of CPU when each write suspended and 2.5 ms buffered, so
a 50-write dialogue is about 2 ms, extrapolated. A write that fails now fails at the later call, and
`close` closes the connection even when its flush fails. Call `flush()` when a protocol needs bytes on the wire
before the program does anything but read: a sleep, a second connection, `ctx.waitUntil`.
`java.net.Socket` is unchanged. The measurement is against an in-memory sink, so a real network
write may cost more or less per call.

## Opt-In Extras

The `classlib` block keeps a part of the class library out of every module that does not use it.
`classlib { zoneHistory = true }` makes `ZoneRules.getTransitions()` and `getTransitionRules()`
answer from ThreeTen-Backport's 2024b timezone database, read by a reader written for the purpose
because ThreeTen's own builds a class that extends the substituted `ZoneRules`. The generated entry
point registers the reader in a static block before it constructs the handler, so a handler that
asks in its constructor finds it.

The cost lands only where the program asks. The 108,033-byte database reaches the module through
TeaVM's resources policy when `ZoneHistory` is among the classes the program kept, and the compiler
keeps it only when a program calls one of the two methods. Measured:

| Program                                        | Flag off | `zoneHistory` | Delta    |
| ---------------------------------------------- | -------- | ------------- | -------- |
| `hello-world`, never calls the history         | 25,334   | 25,829        | +495     |
| `standard-library`, never calls it             | 564,739  | 565,566       | +827     |
| one `getRules().getTransitions().size()`       | 45,662   | 193,379       | +147,717 |
| a 10-zone transcript using much of `java.time` | 411,568  | 542,531       | +130,963 |

With `zoneHistory` off, the entry point is the one it was before the flag existed; the
`standard-library` md5 alternates between two outputs 2 bytes apart on rebuilds of one unchanged
tree, so the size is what to compare. With the flag on, `nextTransition` and `previousTransition`
answer from the history too, at any distance, and agree with a JVM at every transition, one second
either side, and over a sweep from 1716 to 2350 for eight zones (London, New York, Kolkata, Lord
Howe, Casablanca, Tokyo, Moscow and UTC). A zone the bundle lacks keeps the 400 day search from
`Intl`, and so does every zone with the flag off. The history is 2024b and the platform's database
is newer, so a zone changed since could disagree between the two; no zone tested did. No startup
cost was measured.

`classlib { dateFormat = true }` switches on `%t` and `%T`. The only way to keep the conversion out
of a module is a hook in `Formatter`, which every `String.format` call reaches, so the hook has a
price even with the flag off, and the measured price is the reason for the flag. Checks that sat in
`Formatter` moved into `Dates`, which cut the hook from 1,849 bytes in `standard-library` to 361. The
flag works for `long`, `Long`, `Date`, `Calendar` and `java.time` arguments, in `Locale.US`,
`Locale.ENGLISH` or no locale, and covers `H I k l M S L N p z s Q B b h A a C Y y j m d e R T r D F`.
`%tZ` and `%tc` need a zone's short name and are refused, as is any other locale and an epoch value
before 1582-10-15, which a JVM reads on the Julian calendar.

| Program                                | Flag off | `dateFormat` | Delta   |
| -------------------------------------- | -------- | ------------ | ------- |
| `hello-world`, never formats           | 26,562   | 27,147       | +585    |
| `standard-library`, formats no date    | 581,602  | 605,808      | +24,206 |
| `String.format("%s %d", ...)`, no date | 116,416  | 144,569      | +28,153 |

The `hello-world` module is byte-identical to the one built without the hook (md5
`3a4298b79e686348e1b283d4ff64087e` both). `standard-library` is not: 581,241 bytes without the hook
and 581,602 with it. A 788-row transcript of the module under node against JDK 25.0.2 differs in 56
rows, all `%tZ`, `%TZ`, `%tc` and `%Tc`, which refuse; the JDK 21 Gradle lane compares the matrix
(3 locales, 29 conversions, about 60 arguments) with `String.format` directly. Startup and CPU cost
were not measured.

## Size as an Output

Cloudflare meters the uncompressed bundle against 64 MiB on either plan, which no Java Worker
approaches. The limit a growing module does reach is the one second a Worker has to start, so every
feature here has a measured cost rather than an estimated one, and `sizeReport` prints the figure for
a project.

```sh
cd samples && ../gradlew :queue-consumer:sizeReport
```

Two design decisions came directly out of measuring rather than reasoning.

The first: `java.util.Formatter` was going to have its digits supplied by `Intl.NumberFormat`, which
the isolate already carries. The conformance suite refused it. Java's `%f` does not round the exact
value of a double, it rounds the shortest decimal that reads back as that double, so `%.1f` of `0.35`
is `0.4` on a JVM while `(0.35).toFixed(1)` is `0.3`. Backing it on the platform would have been wrong
on the cases people notice. The digits are worked out in Java instead, from `Double.toString`.

The second: an argument of arbitrary precision would naturally be handled through `java.math`, which
costs 22 KB. It is read from its `toString` instead, which is exact for the same reason and costs
1,371 bytes for the class-name check, and `%x`, `%X` and `%o` on a `BigInteger` convert from the
digit string with a JVM's sign handling.

The regular expression work also paid out somewhere it was not aimed. `ZoneId.of` validates an
identifier with a `Pattern`, so retargeting `java.util.regex` took 47 KB off the `java.time` zone path.

## The Clock

Workers pin the clock between I/O. `System.currentTimeMillis()` and `Instant.now()` return the time
the invocation began and do not advance while a handler runs.

Two consequences. Timing a section of code by subtracting two readings measures zero. And a
`Thread.sleep` is scheduled against a clock that is not moving, so it is either instantly due or never
due, depending on whether an I/O operation intervenes.

`dev.gmitch215.bytebox.builtin.Clock` documents what each reading means here.
[standard-library](samples/standard-library) marks the one place it takes a reading.

## Generated Code Instead of Reflection

Two features need to know the shape of a type: JSON conversion and `java.io` serialization. Both are
generated at build time rather than discovered at run time.

The reason is dead-code elimination. A field typed `Object` means any serializable class could flow
through it, so a run-time implementation's conservative closure is every serializable class in the
program, with metadata for all of them. That is the exact set we most want pruned.

The generator knows the fields, so it emits direct field access and needs no reflection at all. It also
computes `serialVersionUID` the way the specification defines it, which is what makes a stream a JVM
reads. One input it cannot get from reflection is whether a class has a static initialiser, so the
generator walks the class file's constant pool for it.

The boundary: a class arriving on the wire that was not registered at build time throws
`ClassNotFoundException`, which is what a JVM does for a class missing from the classpath.

## Recipes

Each of these combines several samples. None needs a framework.

### An HTTP API

`Router` in `dev.gmitch215.bytebox.http` matches a method and a path pattern with `:params` and a
trailing wildcard, runs a filter chain, and falls through to a not-found default. Matching compares
segments rather than compiling a regular expression.

```java
public class Api implements Worker {

	private final Router routes = new Router()
		.filter(Api::requireToken)
		.get("/things", (request, env, ctx) -> Bytebox.json(env.d1().query("select * from things")))
		.get("/things/:id", (request, env, ctx) -> one(env, request.param("id")))
		.post("/things", Api::create);

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return routes.handle(request, env, ctx);
	}
}
```

A JavaScript router in front of this would cross the boundary twice per request and split the routing
table from the handlers that serve it. Reaching one from Java through generated bindings puts the
table on the wrong side of the same boundary. Routing in Java is the shape that fits.

### A Job That Fans Out

A cron handler that does the work inline is bounded by the 15 minutes a `scheduled` invocation gets.
Sending a message per unit of work and letting a consumer take them gives each unit its own budget and
its own retry.

Combine [cron](samples/cron) with [queue-consumer](samples/queue-consumer). The producing side needs a queue binding,
the consuming side implements `Consumer<T>`, and a message body is a structured-clone value up to
128 KB. The consumer acknowledges per message, so one failure retries one message rather than the
batch.

### A Coordination Point

[kv-counter](samples/kv-counter) and [durable-object](samples/durable-object) implement the same feature twice, and
reading them together is the point. KV is eventually consistent, so two regions can read the same value
and both write the next one. A Durable Object is one instance per identifier in one place, so they
cannot.

A Durable Object also holds SQLite, WebSockets with hibernation, and an alarm, which makes it the
coordination point for anything that has to be exact: a counter, a lock, a room, a scheduled retry.
Every method on it goes through the gate.

### Both Kinds of Dependency at Once

[mixed-dependencies](samples/mixed-dependencies) takes a Java library and an npm package in one
Worker, which is the case that shows where each one lands. Guava is compiled into the WebAssembly and
only the reached parts survive. nanoid is never compiled: it stays JavaScript beside the module, the
plugin writes it into the generated manifest, and the generated entry point imports it and hands it
to `load`.

Choose between them on what the call costs rather than on taste. A Java library costs module bytes,
which is what startup is paid in, and calls inside it are ordinary calls. An npm package costs bundle
bytes, which are metered and nothing else, but every call into it crosses the interop boundary. For
something called once per request the boundary is free; for something called in a loop it is not.

### A Protocol Client

[tcp-client](samples/tcp-client) connects over `cloudflare:sockets` with TLS and frames a response by
delimiter. `java.net.Socket` is retargeted onto the same thing, so a library that opens a socket the
ordinary way works unchanged, and reads block by suspending the fiber. bytebox's own `Socket`
buffers writes until the next read, close or `flush()`; see Waiting Inside the Platform's Calls.

The platform will not connect to its own address ranges, to localhost, to a private network address, or
to port 25. One consequence shapes what you can build on it: `smtp.mx.cloudflare.net` is a
Cloudflare address, so a Worker cannot SMTP to Cloudflare Email Sending even with valid credentials.
Outbound mail goes through the `send_email` binding or the REST API. External SMTP, IMAP and POP3 hosts
are fine.

## Running the Samples as a Check

The samples build against the bytebox in this tree rather than a published version, so `buildWorkers`
compiles every one of them through the real plugin and fails if any task, generator or substitution
broke.

```sh
cd samples
../gradlew buildWorkers
```

That covers what a unit test cannot: the plugin's task wiring, the generated entry point, the generated
Wrangler configuration, and the substitution policies resolving against a project that was compiled
separately. [standard-library](samples/standard-library) is the widest of them, since it links `java.time`,
both HTTP clients, `java.util.regex` and `String.format` in one binary.

`node verify-workers.mjs` then loads each built module through the npm loader with a stub for every
platform module it imports, so a module that compiles and cannot start fails there.

Ordered by compiled size, smallest first, as `wc -c` counted `app.wasmbin` after one
`../gradlew buildWorkers` on 2026-10-09, after the Durable Object, parse and `Float.toString` changes:

| Sample               | `app.wasmbin` bytes |
| -------------------- | ------------------- |
| `hello-world`        | 26,562              |
| `npm-dependency`     | 31,013              |
| `html-rewriter`      | 37,605              |
| `cron`               | 40,332              |
| `cache`              | 40,714              |
| `email-router`       | 41,624              |
| `kv-counter`         | 44,615              |
| `tcp-client`         | 45,586              |
| `workflow`           | 46,345              |
| `kotlin`             | 46,761              |
| `durable-object`     | 48,918              |
| `queue-consumer`     | 53,227              |
| `mixed-dependencies` | 99,878              |
| `standard-library`   | 595,366             |

`durable-object` grew the most since the previous table (40,931 to 48,918 bytes) and `workflow` by
133; the other rows are unchanged to the byte except `standard-library` (581,969 to 595,366). None of
the growth is split further. `hello-world` is 26,562 bytes, of which the report measures 807 as the
module-start fiber. `standard-library` is close to the other thirteen together (603,180 bytes). The
older sizes in the [technical report](TECHNICAL_REPORT.md) are from earlier builds.

To deploy one and see the figure Cloudflare meters:

```sh
cd samples/hello-world/build/bytebox/worker
bunx wrangler deploy --dry-run
```
