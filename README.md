# ☕ bytebox

> Java on Cloudflare Workers

[![Build](https://github.com/gmitch215/bytebox/actions/workflows/build.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/build.yml)
[![Coverage](https://github.com/gmitch215/bytebox/actions/workflows/coverage.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/coverage.yml)
[![End-to-End](https://github.com/gmitch215/bytebox/actions/workflows/e2e.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/e2e.yml)
[![Prettier](https://github.com/gmitch215/bytebox/actions/workflows/prettier.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/prettier.yml)
[![codecov](https://codecov.io/gh/gmitch215/bytebox/branch/master/graph/badge.svg)](https://codecov.io/gh/gmitch215/bytebox)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

bytebox compiles a Java workspace into a Cloudflare Worker. Write a class, apply the Gradle plugin,
and deploy WebAssembly that runs on Cloudflare's edge.

A hello world Worker starts in **10 ms** and answers a request in under a millisecond of CPU.

---

## 📋 Table of Contents

- [Why bytebox](#-why-bytebox)
- [Install](#-install)
- [Quick Start](#-quick-start)
- [Kotlin](#-kotlin)
- [Triggers](#-triggers)
- [Bindings](#-bindings)
- [Cache](#-cache)
- [HTMLRewriter](#-htmlrewriter)
- [Workflows](#-workflows)
- [JSON](#-json)
- [Standard Library](#-standard-library)
- [Opt-In Extras](#-opt-in-extras)
- [Serialization](#-serialization)
- [npm Packages](#-npm-packages)
- [Startup](#-startup)
- [Concurrency](#-concurrency)
- [Platform Limits](#-platform-limits)
- [Out of Scope](#-out-of-scope)
- [License](#-license)

---

## 🎯 Why bytebox

Cloudflare Workers run JavaScript and WebAssembly. Java compiles to WebAssembly through TeaVM, but
the gap between a compiled module and a deployable Worker is wide enough that nothing crossed it:
TeaVM publishes no npm artifact, and its generated loader cannot run on Workers at all. Compilation
from bytes is refused inside a request; the asynchronous form never settles during module evaluation;
streaming compilation is unavailable in both places.

bytebox supplies the loader that does work, the bindings a Worker needs, and the build that ties them
together.

---

## 📥 Install

The Gradle plugin:

```kotlin
plugins {
	java
	id("dev.gmitch215.bytebox") version "1.0.1"
}

dependencies { implementation("dev.gmitch215:bytebox-core:1.0.1") }
```

The loader, for a Worker assembled by hand:

```sh
bun add @gmitch215/bytebox
```

---

## 🚀 Quick Start

```java
package com.example;

import dev.gmitch215.bytebox.*;

public class HelloWorker implements Worker {
	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		return Bytebox.response("hello from Java");
	}
}
```

```kotlin
bytebox {
	handlerClass = "com.example.HelloWorker"

	wrangler {
		name = "hello-world"
		compatibilityDate = "2026-08-22"
	}
}
```

```sh
./gradlew buildWorker
./gradlew workerDeploy
```

`buildWorker` writes a complete Worker into `build/bytebox/worker`: the module, a `wrangler.jsonc`,
a JavaScript entry point and a package manifest. Nothing there is hand-written.

[samples/](samples) has fourteen of these, one per feature, and
[ADVANCED_USAGE.md](ADVANCED_USAGE.md) is the design document behind them.

---

## 🟣 Kotlin

Apply the Kotlin plugin next to bytebox, in either order. The build orders the Kotlin compile tasks
after the generators, and `kotlin-stdlib` is compiled into the module like any other dependency.

```kotlin
plugins {
	id("dev.gmitch215.bytebox")
	kotlin("jvm") version "2.4.20"
}

bytebox {
	handlerClass = "com.example.KotlinWorker"

	wrangler {
		name = "kotlin"
		compatibilityDate = "2026-08-22"
	}
}
```

A handler is a `class` with a public no-argument constructor, or an `object`, which the generated
entry point reads through its `INSTANCE`:

```kotlin
data class Language(val name: String, val year: Int)

/** A Worker written as a Kotlin object; the build reads its instance, so nothing constructs it. */
object KotlinWorker : Worker {
	private val languages = listOf(Language("Java", 1995), Language("Kotlin", 2011), Language("Rust", 2015))

	override fun fetch(request: Request, env: Env, ctx: ExecutionCtx): Response {
		val since = request.query("since", "2000").toInt()
		val released = languages.filter { it.year >= since }.associate { it.name to it.year }
		if (released.isEmpty()) return Bytebox.response("nothing since $since")

		return Bytebox.response(released.entries.joinToString("\n") { "${it.key} was released in ${it.value}" })
	}
}
```

A Durable Object has to be a class, because there is one instance per id; naming an `object` fails
the build with a message that says so. Collections, `Sequence`, string templates, data classes,
sealed classes, `Regex`, `String.format` and `@JSONType` classes print what they print on a JVM, and
the handler is plain blocking code, as in Java.

kotlinx-coroutines does not build. `runBlocking`, `launch`, `delay`, `withContext` and
`Dispatchers.*` are refused by the compiler, and a `suspend` lambda started with `startCoroutine`
compiles to a module the engine rejects. `sequence { yield(...) }` works. The refusals name classes
the class library lacks, so the list shrinks as they arrive; the first wall and the measurements are
in [ADVANCED_USAGE.md](ADVANCED_USAGE.md).

---

## 🎛 Triggers

One interface per trigger, each optional. The build derives which handlers to export and which
Wrangler keys to write from the interfaces a class implements, so an unhandled trigger costs nothing.

| Interface     | Handles                          |
| ------------- | -------------------------------- |
| `Worker`      | HTTP requests                    |
| `Scheduled`   | Cron Triggers                    |
| `Mail`        | Incoming email                   |
| `Consumer<T>` | Queue messages                   |
| `Tail`        | Trace events from another Worker |
| `Alarm`       | Durable Object alarms            |

`InboundMail` records its own disposition, because a message a handler returns without acting on is
dropped by the platform. Call `forward`, `reply` or `reject` at any point and carry on working;
`drop()` states that discarding it was the intent.

---

## 🔌 Bindings

Binding names are optional. Each type has a default, and repeats take a numeric suffix: the first KV
namespace is `KV` and the second `KV_2`, the first D1 database is `DB`, the first R2 bucket is `BLOB`,
and a Durable Object binding is `DO_` followed by the class name.

Remote identifiers are supported wherever Wrangler accepts one, and omitting them lets Wrangler
provision the resource.

```kotlin
bytebox {
	bindings {
		kv()                             // KV
		kv("SESSIONS") { id = "abc123" } // an explicit name and a remote id
		d1()                             // DB
		r2()                             // BLOB
		durableObject("Counter")         // DO_COUNTER
	}
}
```

Or, when every binding takes its default name:

```kotlin
bytebox {
	bindings(KV, D1, D1, KV) // KV, DB, DB_2, KV_2
}
```

`bindingsReport` prints every declared binding and the resource it points at, which is where a name
that drifted shows up. A Workflow binding is `WORKFLOW`; see [Workflows](#-workflows).

`durableObject("Counter")` declares the binding only. `durableObjects("com.example.Counter")` also
writes the exported class and a `new_sqlite_classes` migration, which Wrangler requires before it
accepts the Worker. The generated entry loads the module a second time for the Durable Object classes,
with a gate of their own, so a Worker can wait on its own Durable Object. A project that declares one
compiles and instantiates the module twice and holds two heaps; the memory of the second was not
measured. A queue binding only sends: a Worker receives a queue's messages after
`wrangler { queueConsumers("orders") }`. The generated `wrangler.jsonc` writes `queues` as
`{ "producers": [...], "consumers": [...] }`, and a consumer-only Worker writes only `consumers`. A
Worker that is a queue's consumer cannot be deleted until `wrangler queues consumer remove <queue>
<worker>` runs.

---

## 🗂 Cache

The Workers Cache API needs no binding. `Caches.defaultCache()` returns `caches.default` and
`Caches.open(name)` a named cache. `match`, `put` and `delete` take a `Request` or a URL string and
block; `putAsync`, `matchAsync` and `deleteAsync` return the promise, for `ctx.waitUntil`.

```java
public class CachedWorker implements Worker {

	@Override
	public Response fetch(Request request, Env env, ExecutionCtx ctx) {
		Cache cache = Caches.defaultCache();

		Response stored = cache.match(request);
		if (stored != null) return answer(stored.text(), "HIT");

		Response fresh = answer(compute(request.path()), "MISS");
		fresh.getHeaders().set("Cache-Control", "public, max-age=60");
		ctx.waitUntil(cache.putAsync(request, fresh.copy()));
		return fresh;
	}

	private static Response answer(String body, String cache) {
		TSObject headers = TSObject.object();
		headers.set("X-Cache", TSObject.of(cache));
		return Bytebox.response(body, 200, headers);
	}

	private static String compute(String path) {
		long sum = 0;
		for (int i = 1; i <= 10_000; i++) sum += (long) i * path.length();
		return path + " computed at " + System.currentTimeMillis() + ", sum " + sum + "\n";
	}
}
```

`put` consumes the response body, so `Response.copy()` makes the copy to store while the handler
returns the original. The key must be a `GET` request: a POST key, a `206` response and `Vary: *`
reject with a `TypeError`. A response with `Set-Cookie` is accepted and not stored, and an entry is
local to one data center, so a request routed elsewhere is a miss.
`samples/cache` is this class. Deployed to a free account, two requests for one URL answered
`X-Cache: MISS` and then `X-Cache: HIT` with the same body.

---

## 🧱 HTMLRewriter

`HTMLRewriter.create()` builds the platform's rewriter, and `transform(response)` returns a response
whose body is rewritten as it is read. Handlers are lambdas or classes:

```java
private static Response links(Response page) {
	ContentOptions html = ContentOptions.html();
	return HTMLRewriter.create()
		.on("a[href]", link -> {
			link.setAttribute("target", "_blank");
			link.setAttribute("rel", "noopener");
		})
		.on("body", body -> body.prepend("<aside>Rewritten in Java</aside>", html))
		.onEnd(end -> end.append("<!-- served by bytebox -->", html))
		.transform(page);
}
```

Selectors are the platform's and pass through as strings. `on` takes an element handler; `onText`
and `onComments` take the text and comments inside a match; `onDoctype`, `onDocumentText`,
`onDocumentComments` and `onEnd` are the document-level ones. Content is a string, escaped as text
unless it is wrapped in `ContentOptions.html()`. Text arrives in chunks, so collect them until
`isLastInTextNode()` before matching a word that could span two.

Each handler call runs on its own fiber and the platform waits for it, so a handler can call a
binding or `fetch`. That costs a fiber per call, about twice the platform's own per-callback cost in
a local measurement, so register the kinds you need and match narrowly. A handler that throws stops
the parse and errors the body, and in Java the message arrives as a `JSRejection` that carries the
text and not the exception class. A `Response` or a stream cannot be passed as content.
`samples/html-rewriter` rewrites a static page on `/` and words in text on `/text`. Deployed to a free
account, both routes answered the rewritten page.

---

## 🔁 Workflows

A Java class that implements `WorkflowEntrypoint` is a Workflow. Name it once in the build and the
plugin writes the exported class the platform instantiates and a `workflows` entry in
`wrangler.jsonc`:

```kotlin
bytebox {
	handlerClass = "com.example.OrderWorker"
	workflows("com.example.OrderFlow")

	wrangler {
		name = "workflow"
		compatibilityDate = "2026-08-22"
	}

	bindings {
		workflow("com.example.OrderFlow", "WORKFLOW", "order-flow")
	}
}
```

```java
public class OrderFlow implements WorkflowEntrypoint {

	@Override
	public TSObject run(WorkflowEvent event, Step step, Env env) {
		String item = event.payload().get("item").asString();

		TSObject reserved = step.run("reserve", () -> TSObject.of("reserved " + item));
		step.sleep("wait for payment", "2 seconds");
		TSObject receipt = step.run("ship", () ->
			TSObject.of(reserved.asString() + ", shipped for " + event.instanceId())
		);

		TSObject output = TSObject.object();
		output.set("receipt", receipt);
		return output;
	}
}
```

`Step.run(name, body)` wraps `step.do` and stores the body's result; `Step.run(name, config, body)`
takes Cloudflare's step config as a `TSObject`, for retries and timeouts. `Step.sleep(name, duration)`
takes a duration string such as `"30 seconds"`. A body that throws fails the attempt and the platform
retries it. The method is `run` because `do` is a keyword.

The platform runs `run` again from the top to resume an instance, and a finished step returns its
saved result without running its body. Keep side effects, the clock and anything random inside a
step, and give each step the same name every time. Fields on the class do not survive a resume.

`run` goes through the isolate's gate like every other entry. While an instance is inside `run`, a
sleep included, every other call into the same Worker waits, and a sleeping instance cannot be
terminated, paused or sent an event from that Worker. A Workflow that sleeps for long belongs in a
Worker that serves nothing else. The three-argument `workflow(class, binding, name)` above names the
binding and the Workflow yourself. A project that only calls `workflows(...)` gets
`WORKFLOW_<CLASS_IN_UPPER_SNAKE_CASE>` and a Workflow name in kebab case. The name must be unique per
account. The one-argument `workflow(class)` writes a configuration without the `binding` key, and
Wrangler refuses it.

`createBatch` starts up to 100 instances in one call. The options form takes Cloudflare's object as
it is and reports what was and was not created:

```java
TSObject instances = TSObject.array();
instances.push(Workflow.entry("first", params));
instances.push(Workflow.entry("second", params));
TSObject options = TSObject.object();
options.set("instances", instances);
Workflow.BatchResult result = flow.createBatch(options);
```

`createBatch(count, params)` starts `count` instances with one input. `BatchResult.created()` lists
the instances and `errors()` the entries that were not created, each with an `index`, `id`, `code`
and `message`: 10405 is an id that exists, and 10415 an id the batch repeats. The older list form,
`createBatch(List<TSObject>)`, returns only the created instances and skips a duplicate id silently,
which is why Cloudflare deprecates it. The options form needs Wrangler 4.148.0 or later for local
development. `samples/workflow` starts a Workflow at `/start?item=lamp` and reads it back at
`/status?id=<id>`. Deployed to a free account, `/start` returned an instance id and `/status` read
`queued`, then `running`, then `complete` with the receipt.

---

## 🧾 JSON

Annotate a type and the build writes its codec:

```java
@JsonType
public record Order(String sku, int quantity, long total, List<String> tags) {}
```

```java
Order order = request.json(Order.class);
return Bytebox.json(order, Order.class);
```

Generated rather than reflective, because a reflective decoder needs field metadata for every type
that could arrive through an `Object`-typed field — the closure dead-code elimination exists to prune.
`request.json(mapper)` takes a function instead, for a type with no codec.

A `long` serialises to a JSON string. `JSON.stringify` refuses a `BigInt` outright, and a number would
lose precision above 2^53, which is the range a `long` exists for.

---

## 📚 Standard Library

Write `java.time`, `java.net` and `java.util.regex` the way you write them anywhere. The compiler
points each reference at an implementation that works on this runtime, so a library that never heard
of Cloudflare Workers compiles unchanged.

```java
ZonedDateTime local = Instant.now().atZone(ZoneId.of("America/New_York"));
HttpResponse<String> answer = HttpClient.newHttpClient()
	.send(HttpRequest.newBuilder(URI.create("https://example.com")).build(), ofString());
```

| Package                       | Runs on                                                        |
| ----------------------------- | -------------------------------------------------------------- |
| `java.time`                   | ThreeTen-Backport, with zone rules from `Intl`                 |
| `java.net.URL`                | `fetch`                                                        |
| `java.net.http`               | `fetch`, without `sendAsync`                                   |
| `java.net.Socket`             | `cloudflare:sockets`                                           |
| `java.net.InetAddress`        | DNS over HTTPS                                                 |
| `java.util.regex`             | the platform's own engine                                      |
| `java.util.Formatter`         | digits computed in Java, separators from `Intl`                |
| `java.security.MessageDigest` | pure Java: MD5, SHA-1, SHA-256, SHA-384, SHA-512               |
| `java.util.concurrent`        | `ReentrantLock`, `Condition` and `CompletableFuture` on fibers |
| `java.lang.ClassLoader`       | an empty classpath, so a lookup finds nothing                  |

Each one either matches a JVM, refuses, or is listed below as a known difference. Only `ServerSocket`
and `DatagramSocket` fail the build, because they are left unresolved: the platform accepts no inbound
connection and speaks no UDP. The rest refuse when the program runs. `Pattern.compile` throws a
`PatternSyntaxException` naming a flag written anywhere but the start of a pattern, `\G`, `\X`, a
capturing group inside an atomic group or a possessive quantifier, a backreference inside the group
it names, and a backreference under `CASE_INSENSITIVE` without `UNICODE_CASE`. `String.format` throws on `%t` unless
`classlib { dateFormat = true }` switches it on.

The differences are measured and written down. The clock is pinned between I/O, so `Instant.now()`
gives the time the invocation began. `CASE_INSENSITIVE` with `UNICODE_CASE` folds the whole of
Unicode, and differs from a JVM at 62 characters; with it, `Pattern.matcher` throws
`UnsupportedOperationException` for text holding a character above U+FFFF when the pattern uses `.`
or a negated class. A timezone's recorded history is not available by default, because
the rules are derived from the offsets `Intl` reports rather than read from a table;
`classlib { zoneHistory = true }` reads it from a bundled database instead. `Double.toString`,
`Float.toString` and `Double.parseDouble` and `Float.parseFloat` agree with JDK 27 on everything
tested, including hexadecimal floats and the messages of a refused string; `Float.toString` costs
two to three times what the class library's did. One number difference is known: negating a runtime
`0.0` with `-x` gives `+0.0`, where a JVM gives `-0.0`. The regex engine is the platform's, and a pattern that backtracks badly costs far more than on a
JVM: the technical report measures `(a+)+b` at 14 ms on a reference JVM and 11,010 ms here, over
inputs of 20 to 30 characters.

[ADVANCED_USAGE.md](ADVANCED_USAGE.md) covers the rest, with the measured cost of each.

---

## 🧩 Opt-In Extras

The `classlib` block switches on a part of the class library that costs bytes only when a program
uses it. It has two flags, `zoneHistory` and `dateFormat`, both off by default:

```kotlin
bytebox {
	classlib {
		zoneHistory = true
		dateFormat = true
	}
}
```

With `zoneHistory`, `ZoneRules.getTransitions()` and `getTransitionRules()` answer from the bundled
tzdata 2024b database, and so do `nextTransition` and `previousTransition`, at any distance, as a JVM
does. Without it the first two throw `UnsupportedOperationException` and the other two answer `null`
past 400 days. The 108,033-byte file and about 40 KB of reader code enter the module only when the
program calls one of those methods: a Worker that calls `getTransitions()` once grew from 45,662 to
193,379 bytes, and one that sets the flag and never calls any of them grew by 495 to 827 bytes.

With `dateFormat`, `String.format` accepts `%t` and `%T` for a `long`, `Long`, `Date`, `Calendar` or
`java.time` value in `Locale.US`, `Locale.ENGLISH` or no locale. `%tZ`, `%tc`, any other locale and an
instant before 1582-10-15 (a JVM reads the Julian calendar there) are still refused. The flag adds 24
to 28 KB to a program that formats and 585 bytes to one that does not. With it off, the hook that
makes the flag possible costs 361 bytes in a program the size of `samples/standard-library` that
calls `String.format`, and nothing in one that does not.

---

## 💾 Serialization

`java.io` serialization writes the bytes a JVM writes, checked byte for byte against a real
`ObjectOutputStream`:

```java
byte[] wire = Serial.encode(order);
Order back = Serial.decode(wire, Order.class);
```

Generated at build time, including `serialVersionUID` computed the way the specification defines it,
so a stream written here is read by a JVM and the other way round. The generator refuses at build time
what the format cannot carry: a JDK collection field, a class with no no-argument constructor, and a
custom `writeObject`.

---

## 📦 npm Packages

An npm package is JavaScript, so it never enters the WebAssembly. Declaring it puts it in the
generated manifest and emits the static import that makes it resolvable — the package name otherwise
lives in a wasm custom section that no bundler can follow.

```kotlin
bytebox {
	npm("nanoid", "^5.0.9")
	npmBindings("nanoid")
}
```

`npmBindings` reads the package's TypeScript declarations and writes the Java to call it, so the
functions arrive typed and documented instead of hand-written. A package with no types still binds:
the generator drops through published declarations, DefinitelyTyped, JSDoc, inference, the AST, and
runtime introspection, and reports which of those it landed on. The floor is the module itself as a
`TSObject`, which never fails.

Written by hand, the same call is three lines:

```java
@JSBody(
	params = "size",
	imports = @JSBodyImport(alias = "nanoid", fromModule = "nanoid"),
	script = "return nanoid.nanoid(size);"
)
private static native String id(int size);
```

A script the compiler cannot parse fails the build with `Error in @JSBody script`. The compiler's
parser is older than the engine that runs the module, so each script it reports is parsed again by
`node` as a function body, and only a script `node` refuses too fails the build. A `for...of` loop or
a BigInt literal passes. Without `node` on the PATH every reported script fails the build.

---

## ⚡ Startup

Cloudflare meters the uncompressed bundle against 64 MiB on either plan. Nothing is enforced against
the gzip figure wrangler prints beside it, and a Java Worker sits far under either. What a Worker has
to fit is the one second it gets to parse and compile its module before the first request.

Measured on deployed Workers, that cost tracks compiled code and not bundle size. A hello world
starts in 8 to 11 ms. Half a megabyte of real compiled Java on top of it costs about 7 ms. Padding
the same Worker with half a megabyte of inert bytes instead costs nothing measurable, even though it
uploads the same number of bytes and transfers almost three times as many.

The module ships as raw bytes with no decompressor, so startup pays for compiling it and nothing
else.

Reflection, collections and streams are cheap. `java.net.http` is the most expensive feature here,
because `Duration` pulls `java.time` in behind it. `java.util.regex` and `String.format` are cheap
only because the retargeting below answers both from the platform's own engines; linked from the
class library they are among the largest things a program can reach. Every feature is priced in the
[technical report](TECHNICAL_REPORT.md).

`sizeReport` prints the module against the meter and against your budget. `size { budget = "250KiB" }`
fails the build past a figure of your choosing.

---

## 🧵 Concurrency

Cloudflare Workers run in a single thread and the Web Worker API is unavailable, so there is no
parallelism to be had. A Java thread is a fiber scheduled on the host's queue.

That makes a blocking API the honest one. `env` calls look synchronous and suspend underneath, which
is what a compiled continuation gives you. `Future` is the escape hatch when work needs to overlap or
outlive a response. `java.util.concurrent.CompletableFuture` runs each stage on a fiber, and a thread
pool cannot.

A lock, a join or a get works from the handler's constructor, from a static initialiser it
triggers, and from the constructor of a Durable Object or a Workflow, because the generated entry
builds each of them on a fiber. A constructor that throws fails the request that reached it, with the
exception's message, and the next request builds again. A handler that throws while it is built fails
every request with `module start failed: ...`, and one that never finishes fails them with
`module start has not finished`.

Workers freeze the clock between I/O, so a `Thread.sleep` would never come due. bytebox advances its
own clock to cover one and gives the compiled program the same clock to read, which is what makes a
sleep terminate. Absolute timestamps are sound; a duration taken from two readings is not, and no
CPU meter is readable from inside a request.

---

## 🚧 Platform Limits

| Limit                     | Free     | Paid     |
| ------------------------- | -------- | -------- |
| Worker size, uncompressed | 64 MiB   | 64 MiB   |
| Startup                   | 1 second | 1 second |
| CPU per request           | 10 ms    | 5 min    |
| Memory per isolate        | 128 MB   | 128 MB   |
| Subrequests per request   | 50       | 10,000   |
| Cron Triggers per account | 5        | 250      |

The table is the platform's stated numbers. Measured on one deployed Worker on the free plan, neither
CPU nor memory behaved as written: a Worker linking the retargeted class library reported a median
`cpuTime` of 43 ms (range 2 to 79 ms, n = 8) with nothing terminated, and one request allocated
254 MiB while 256 MiB was refused. The sources are the CPU and isolate memory measurements in the
[technical report](TECHNICAL_REPORT.md). Two limits come from the compiled program itself. Calling `hashCode()` on a JavaScript string or
number caches it in a map the platform does not promise to drain, so a long-lived isolate that hashes
unbounded distinct strings grows that map. A class
initialiser that throws does not leave the class erroneous for the rest of the program, as it does
on a JVM.

---

## 🛑 Out of Scope

- **Subprocesses.** `Process` and `ProcessBuilder` do not exist, and Workers have no equivalent.
- **Threads.** Fibers are real; parallelism is not.
- **Dynamic class loading.** Compilation is closed-world, so reflection resolves against classes
  registered at build time, and `ClassLoader` finds no resources and loads no classes.
- **Kotlin coroutines.** kotlinx-coroutines does not build, and a `suspend` lambda started with
  `startCoroutine` produces a module the engine rejects.
- **Listening.** `ServerSocket` and `DatagramSocket` are left unresolved, because the platform accepts
  no inbound connection and speaks no UDP.
- **A flag written after the start of a pattern, and `%tZ` or `%tc`.** Anything a retargeted API
  cannot render exactly is refused where you can see it rather than approximated where you cannot.
- **A Workflow beside a busy Worker.** While an instance is inside `run`, every other call into
  that Worker waits.

---

## 📄 License

[MIT](LICENSE)
