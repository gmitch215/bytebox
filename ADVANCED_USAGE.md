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
does. `cron` is 36,914 bytes of WebAssembly against `hello-world`'s 25,330.

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
stating, because a Durable Object feels like a separate instance and is not: it shares the heap with
whatever else the isolate is running.

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

| Written in the project              | Where it goes                                           | What it pulls in                             |
| ----------------------------------- | ------------------------------------------------------- | -------------------------------------------- |
| `java.time.*`                       | ThreeTen-Backport, with the zone rules read from `Intl` | three times as much once a formatter is used |
| `java.net.URL`, `HttpURLConnection` | `fetch`                                                 | the client below shares most of it           |
| `java.net.http.HttpClient`          | `fetch`                                                 | `java.time`, through `Duration`              |
| `java.net.Socket`                   | `cloudflare:sockets`                                    | nothing beyond the stream plumbing           |
| `java.net.InetAddress`              | DNS over HTTPS through `fetch`                          | included in the client above                 |
| `java.util.regex`                   | the platform's own engine                               | a fifth of the class library's own engine    |
| `java.util.Formatter`               | digits worked out in Java, separators from `Intl`       | under half the class library's own formatter |
| `java.io.ObjectOutputStream`        | codecs generated at build time                          | varies with the types registered             |

[standard-library](samples/standard-library) uses five of those in one Worker, which is why it is the largest
sample. It exists to prove they link together in a real project rather than only in a test fixture.
Each row is priced in the [technical report](TECHNICAL_REPORT.md).

The timezone row is the one that pays for itself twice. The isolate already carries a copy of the
timezone database behind `Intl`, so the rules are derived from the offsets `Intl` reports and neither
a compiled database nor its reader enters the binary.

## Refusal at Build Time

Retargeting has a failure mode that matters more than the size: an implementation that is _nearly_ the
same. A regular expression that matches slightly differently, or a number that rounds one way instead
of the other, shows up as a validation that passes when it should not.

So the rule everywhere is that a construct either means here what it means on a JVM, or it does not
compile.

`java.util.regex` is where this is most visible. Java's `\s` is six characters and the platform's is a
much wider set; `\v` is vertical whitespace in Java and a vertical tab in JavaScript; `.` excludes
`\u0085` in Java and does not in JavaScript; `$` matches before a final line terminator in Java and
only at the end in JavaScript. Every one of those is rewritten into something explicit. Atomic groups,
possessive quantifiers, character-class intersection and flags written inside a pattern have no exact
equivalent, so `Pattern.compile` refuses them and names what it refused.

`java.net` does the same by omission. `Socket` is retargeted and `ServerSocket` deliberately is not,
because the platform accepts no inbound connection. A program using `ServerSocket` fails while it is
being compiled, which is where it should fail. [tcp-client](samples/tcp-client) records the rest of what
`cloudflare:sockets` will not connect to, and that list is what decides whether raw TCP is the right
tool for a given protocol.

The formatter refuses `%t` and `%a`, each naming its replacement. `%t` would make the calendar and
locale graph reachable from every `String.format` call, which is the cost the class exists to avoid.

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

Each figure prices the part of the library the program reaches, not the library. Dead-code
elimination means a project calling one splitter pays for one splitter, and the number moves with
every call added. Commons Lang shows the size of that effect: the same dependency measured 64,656
bytes calling one method and 89,535 calling seven.

What the bytes buy you is startup, which is the only budget they are spent from. Startup Scaling in
the technical report prices compiled code at roughly 7 ms per half megabyte, so Jackson Core's
304,362 bytes are worth a few milliseconds of the one second available.

A refusal comes from one of three places, and they are worth telling apart because only the first two
are anyone's fault.

The first is a class library gap that has nothing to do with the platform. There is no
`java.util.concurrent.locks` package at all, and no `java.util.StringJoiner`. Neither library uses a
lock for parallelism; both guard caches with one. A missing class is enough to refuse a whole library
whatever it was for.

The second is an optional integration. Whole-program compilation has no idea that a dependency is
optional. A JVM never loads Log4j's OSGi support on a machine without OSGi, because it resolves
classes lazily and that one is never asked for. A closed-world compiler has to resolve every
reference it can see, so `org.osgi.framework.*` and `org.fusesource.jansi.*` become requirements of a
program that will never call them. Jackson carries DOM support the same way, through
`org.w3c.dom.Node`. This is the wall most libraries hit, and it is why a library's own modularity
works against it here.

The third is the one no stub can clear. Jackson resolves generic types at run time, and TeaVM's
`Class` has `getEnclosingClass` but neither `getEnclosingMethod` nor `getGenericSuperclass`; its
`ClassLoader` has no `loadClass`. Retaining generic signatures is a compiler decision somebody could
make. An open world is not, and a closed-world compiler is the thing that makes a 25 KB Worker
possible in the first place.

Commons Lang shows what the boundary looks like when a library gets all the way through and then
meets a retargeted API. `StringUtils` compiles a pattern in its class initialiser for `stripAccents`:

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

**Log4j: no, and dropping to `log4j-api` does not help.** Both halves stop in the same place.
`LoaderUtil.findUrlResources` calls `ClassLoader.getResources` to scan the classpath for
`log4j2.component.properties` while the property system initialises, so the scan happens before any
logger is asked for. Classpath enumeration has nothing to enumerate here: the classpath existed at
build time and there is no such thing at run time. Anything that discovers providers this way lands
in the same position. `System.out.println` reaches the Worker log.

**What made the streaming case work** was supplying two classes the class library omits,
`java.util.concurrent.locks.ReentrantLock` and `java.util.concurrent.atomic.AtomicReferenceArray`.
The lock is worth knowing about before relying on it: it never waits. One fiber runs at a time, so an
uncontended acquire is already exclusive, and the only way to contend here is for a fiber to suspend
while holding the lock. That case throws instead of blocking, which is a refusal you can see rather
than two fibers quietly sharing a section written for one. Blocking would be the faithful answer and
it is not available, because a substituted class cannot contain a suspending call.

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
nothing, at the price of refusing `%x` on a `BigInteger`.

The regular expression work also paid out somewhere it was not aimed. `ZoneId.of` validates an
identifier with a `Pattern`, so retargeting `java.util.regex` took 47 KB off the `java.time` zone path.

## The Clock

Workers pin the clock between I/O. `System.currentTimeMillis()` and `Instant.now()` return the time
the invocation began and do not advance while a handler runs.

Two consequences. Timing a section of code by subtracting two readings measures zero. And a
`Thread.sleep` is scheduled against a clock that is not moving, so it is either instantly due or never
due, depending on whether an I/O operation intervenes.

`dev.gmitch215.bytebox.builtin.Clock` documents what each reading actually means here.
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
ordinary way works unchanged, and reads block by suspending the fiber.

The platform will not connect to its own address ranges, to localhost, to a private network address, or
to port 25. One consequence is worth knowing before designing around it: `smtp.mx.cloudflare.net` is a
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

Ordered by compiled size, smallest first: `hello-world`, `npm-dependency`, `cron`,
`durable-object`, `email-router`, `tcp-client`, `kv-counter`, `queue-consumer`, then
`standard-library`, which is larger than the rest put together. The measured bytes are in the
[technical report](TECHNICAL_REPORT.md).

To deploy one and see the figure Cloudflare meters:

```sh
cd samples/hello-world/build/bytebox/worker
bunx wrangler deploy --dry-run
```
