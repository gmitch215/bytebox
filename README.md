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

A hello world Worker compiles to **25,330 bytes** of WebAssembly against a 3 MB ceiling, starts in
**8 ms** of a 1 second budget, and answers a request in under a millisecond of CPU.

---

## 📋 Table of Contents

- [Why bytebox](#-why-bytebox)
- [Install](#-install)
- [Quick Start](#-quick-start)
- [Triggers](#-triggers)
- [Bindings](#-bindings)
- [JSON](#-json)
- [Standard Library](#-standard-library)
- [Serialization](#-serialization)
- [npm Packages](#-npm-packages)
- [Size](#-size)
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
	id("dev.gmitch215.bytebox") version "1.0.0"
}

dependencies { implementation("dev.gmitch215:bytebox-core:1.0.0") }
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

[samples/](samples) has nine of these, one per feature, and
[samples/ADVANCED_USAGE.md](samples/ADVANCED_USAGE.md) is the design document behind them.

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
that drifted shows up.

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

| Package                | Runs on                                         |
| ---------------------- | ----------------------------------------------- |
| `java.time`            | ThreeTen-Backport, with zone rules from `Intl`  |
| `java.net.URL`         | `fetch`                                         |
| `java.net.http`        | `fetch`, without `sendAsync`                    |
| `java.net.Socket`      | `cloudflare:sockets`                            |
| `java.net.InetAddress` | DNS over HTTPS                                  |
| `java.util.regex`      | the platform's own engine                       |
| `java.util.Formatter`  | digits computed in Java, separators from `Intl` |

Each one either matches a JVM or refuses while you are building. A pattern using an atomic group, a
possessive quantifier or a flag written inside it will not compile, and `Pattern.compile` names what
it refused. `String.format` refuses `%t` and `%a`. `ServerSocket` and `DatagramSocket` are left
unresolved, because the platform accepts no inbound connection and speaks no UDP.

Nothing about this is free of consequences, and they are written down. The clock is pinned between
I/O, so `Instant.now()` gives the time the invocation began. `CASE_INSENSITIVE` folds the whole of
Unicode here where a JVM folds ASCII. A timezone's recorded history is not available, because the
rules are derived from the offsets `Intl` reports rather than read from a table.

[samples/ADVANCED_USAGE.md](samples/ADVANCED_USAGE.md) covers the rest, with the measured cost of each.

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

---

## 📏 Size

Cloudflare enforces its ceiling after applying its own gzip, so the gzip figure is the one that binds.

Each feature is measured on its own against a hello world compiled with the same settings:

| Feature                                | Added, gzipped     |
| -------------------------------------- | ------------------ |
| streams, collections, reflection       | 3.0 to 3.8 KB each |
| threads                                | 6.8 KB             |
| `java.net.Socket`                      | 9.8 KB             |
| `BigDecimal`                           | 12.7 KB            |
| `java.util.regex`                      | 13.3 KB            |
| `java.net.URL` and `HttpURLConnection` | 13.3 KB            |
| `java.time` with zones                 | 23.8 KB            |
| `String.format`                        | 33.0 KB            |
| `java.net.http`                        | 54.1 KB            |

Reflection, collections and streams are inexpensive. The rows that were costly are the ones the
retargeting below fixed: `java.util.regex` was 65.3 KB and `String.format` 86.0 KB when they came from
the class library.

`sizeReport` prints the compiled module on every compression axis against your budget and the two
plan ceilings. `size { budget = "250KiB" }` fails the build past a figure of your choosing.

---

## 🧵 Concurrency

Cloudflare Workers run in a single thread and the Web Worker API is unavailable, so there is no
parallelism to be had. A Java thread is a fiber scheduled on the host's queue.

That makes a blocking API the honest one. `env` calls look synchronous and suspend underneath, which
is what a compiled continuation gives you. `Future` is the escape hatch when work needs to overlap or
outlive a response — `java.util.concurrent.CompletableFuture` does not exist on this platform, and a
thread pool cannot.

Workers freeze the clock between I/O, so a `Thread.sleep` would never come due. bytebox advances its
own clock to cover one and gives the compiled program the same clock to read, which is what makes a
sleep terminate. Absolute timestamps are sound; a duration taken from two readings is not, and no
CPU meter is readable from inside a request.

---

## 🚧 Platform Limits

| Limit                     | Free     | Paid     |
| ------------------------- | -------- | -------- |
| Worker size, after gzip   | 3 MB     | 10 MB    |
| Startup                   | 1 second | 1 second |
| CPU per request           | 10 ms    | 5 min    |
| Memory per isolate        | 128 MB   | 128 MB   |
| Subrequests per request   | 50       | 10,000   |
| Cron Triggers per account | 5        | 250      |

---

## 🛑 Out of Scope

- **Subprocesses.** `Process` and `ProcessBuilder` do not exist, and Workers have no equivalent.
- **Threads.** Fibers are real; parallelism is not.
- **Dynamic class loading.** Compilation is closed-world, so reflection resolves against classes
  registered at build time.
- **Listening.** `ServerSocket` and `DatagramSocket` are left unresolved, because the platform accepts
  no inbound connection and speaks no UDP.
- **`%t` and `%a`, atomic groups, possessive quantifiers.** Anything a retargeted API cannot render
  exactly is refused where you can see it rather than approximated where you cannot.

---

## 📄 License

[MIT](LICENSE)
