# Design Notes

The samples in this directory each show one feature. This document is the other half: the rules the
whole thing is built on, why each one is there, and which sample demonstrates it. Read it if you are
deciding whether bytebox fits a problem, or if a sample did something and you want to know why.

Every figure here was measured on the samples in this tree. `../gradlew buildWorkers` reproduces them.

## The Handler Is the Configuration

A Worker declares what it handles by implementing interfaces. `Worker` gives it `fetch`, `Scheduled`
gives it `scheduled`, `Mail` gives it `email`, and so on. Nothing else declares anything.

The plugin reads which interfaces the handler implements and derives three things from that: which
JavaScript exports to emit, which keys to write into `wrangler.jsonc`, and which entry points the gate
has to guard. A trigger you did not implement produces no export, no configuration key, and no code.

[cron](cron) is the clearest case. It implements only `Scheduled`, so the generated Worker exports
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

[kv-counter](kv-counter) reads and writes KV with plain calls and no callbacks. Each one suspends.

## The Gate

One WebAssembly heap serves an isolate, and an isolate serves many requests. A second request arriving
while the first is parked in a continuation would run on the same heap, in the middle of the first
one's work.

The loader holds a gate on the module, and every entry point goes through it: `fetch`, `scheduled`,
`queue`, `email`, `tail`, and every method on a Durable Object. Durable Objects are the case worth
stating, because a Durable Object feels like a separate instance and is not: it shares the heap with
whatever else the isolate is running.

[durable-object](durable-object) shows the shape. Its methods do not synchronise anything themselves,
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

| Written in the project              | Where it goes                                           | What it costs, gzipped                         |
| ----------------------------------- | ------------------------------------------------------- | ---------------------------------------------- |
| `java.time.*`                       | ThreeTen-Backport, with the zone rules read from `Intl` | +24 KB with zones, +73 KB with the formatter   |
| `java.net.URL`, `HttpURLConnection` | `fetch`                                                 | +13 KB                                         |
| `java.net.http.HttpClient`          | `fetch`                                                 | +54 KB, mostly `java.time` through `Duration`  |
| `java.net.Socket`                   | `cloudflare:sockets`                                    | +10 KB                                         |
| `java.net.InetAddress`              | DNS over HTTPS through `fetch`                          | included in the client above                   |
| `java.util.regex`                   | the platform's own engine                               | +13 KB, against +68 KB for the class library's |
| `java.util.Formatter`               | digits worked out in Java, separators from `Intl`       | +33 KB, against +86 KB                         |
| `java.io.ObjectOutputStream`        | codecs generated at build time                          | varies with the types registered               |

[standard-library](standard-library) uses five of those in one Worker, which is why it is the largest
sample at 510,653 bytes. It exists to prove they link together in a real project rather than only in a
test fixture.

The timezone row is the one that pays for itself twice. A compiled copy of the timezone database is
108,033 bytes and the isolate already has one behind `Intl`, so the rules are derived from the offsets
`Intl` reports and neither the database nor its reader enters the binary.

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
being compiled, which is where it should fail. [tcp-client](tcp-client) records the rest of what
`cloudflare:sockets` will not connect to, and that list is what decides whether raw TCP is the right
tool for a given protocol.

The formatter refuses `%t` and `%a`, each naming its replacement. `%t` would make the calendar and
locale graph reachable from every `String.format` call, which is the cost the class exists to avoid.

## Size as an Output

Cloudflare enforces 3 MB on the free plan and 10 MB on paid, applied after its own compression. That
is a hard number, so every feature here has a measured cost rather than an estimated one, and
`sizeReport` prints the figure for a project.

```sh
../gradlew :queue-consumer:sizeReport
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
[standard-library](standard-library) marks the one place it takes a reading.

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

Combine [cron](cron) with [queue-consumer](queue-consumer). The producing side needs a queue binding,
the consuming side implements `Consumer<T>`, and a message body is a structured-clone value up to
128 KB. The consumer acknowledges per message, so one failure retries one message rather than the
batch.

### A Coordination Point

[kv-counter](kv-counter) and [durable-object](durable-object) implement the same feature twice, and
reading them together is the point. KV is eventually consistent, so two regions can read the same value
and both write the next one. A Durable Object is one instance per identifier in one place, so they
cannot.

A Durable Object also holds SQLite, WebSockets with hibernation, and an alarm, which makes it the
coordination point for anything that has to be exact: a counter, a lock, a room, a scheduled retry.
Every method on it goes through the gate.

### A Protocol Client

[tcp-client](tcp-client) connects over `cloudflare:sockets` with TLS and frames a response by
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
separately. [standard-library](standard-library) is the widest of them, since it links `java.time`,
both HTTP clients, `java.util.regex` and `String.format` in one binary.

The measured sizes, as WebAssembly before packing:

| Sample           |     Raw | gzip -6 |
| ---------------- | ------: | ------: |
| hello-world      |  25,330 |  10,708 |
| npm-dependency   |  29,207 |  11,859 |
| cron             |  36,914 |  15,163 |
| durable-object   |  37,661 |  15,457 |
| email-router     |  38,214 |  15,752 |
| tcp-client       |  40,203 |  16,450 |
| kv-counter       |  40,589 |  16,192 |
| queue-consumer   |  49,895 |  20,455 |
| standard-library | 510,653 | 163,478 |

To deploy one and see the figure Cloudflare meters:

```sh
cd hello-world/build/bytebox/worker
bunx wrangler deploy --dry-run
```
