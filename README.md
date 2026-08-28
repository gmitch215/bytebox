# ☕ bytebox

> Java on Cloudflare Workers

[![Build](https://github.com/gmitch215/bytebox/actions/workflows/build.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/build.yml)
[![Coverage](https://github.com/gmitch215/bytebox/actions/workflows/coverage.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/coverage.yml)
[![Prettier](https://github.com/gmitch215/bytebox/actions/workflows/prettier.yml/badge.svg)](https://github.com/gmitch215/bytebox/actions/workflows/prettier.yml)
[![codecov](https://codecov.io/gh/gmitch215/bytebox/branch/master/graph/badge.svg)](https://codecov.io/gh/gmitch215/bytebox)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

bytebox compiles a Java workspace into a Cloudflare Worker. Write a class, apply the Gradle plugin,
and deploy WebAssembly that runs on Cloudflare's edge.

A hello world compiles to **16,539 bytes** of WebAssembly and **11.82 KiB** of Worker against a 3 MB
ceiling, starts in **8 ms** of a 1 second budget, and answers a request in under a millisecond of CPU.

---

## 📋 Table of Contents

- [Why bytebox](#-why-bytebox)
- [Install](#-install)
- [Quick Start](#-quick-start)
- [Triggers](#-triggers)
- [Bindings](#-bindings)
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
bun add bytebox
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
	workerName = "hello-world"
}
```

```sh
./gradlew buildWorker
bunx wrangler deploy
```

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

---

## 📏 Size

Cloudflare enforces its ceiling after applying its own gzip, so the gzip figure is the one that binds.

Three APIs carry most of the weight of a real program. Each is measured on its own against a hello
world compiled with the same settings:

| Feature                          | Added, gzipped     |
| -------------------------------- | ------------------ |
| streams, collections, reflection | 3.0 to 3.8 KB each |
| threads                          | 6.8 KB             |
| `BigDecimal`                     | 12.7 KB            |
| `java.util.regex`                | 65.3 KB            |
| `Locale` and `NumberFormat`      | 75.7 KB            |
| `String.format`                  | 86.0 KB            |

Reflection, collections and streams are inexpensive. The three costly ones carry locale data and
pattern machinery that the isolate already provides through `Intl` and `RegExp`, which is where they
are headed.

`sizeReport` prints the compiled module on every axis, and splits it between the compiler's runtime,
the class library and your own code.

---

## 🧵 Concurrency

Cloudflare Workers run in a single thread and the Web Worker API is unavailable, so there is no
parallelism to be had. A Java thread is a fiber scheduled on the host's timer queue.

That makes a blocking API the honest one. `env` calls look synchronous and suspend underneath, which
is what a compiled continuation gives you. `CompletableFuture` is the escape hatch when work needs to
overlap or outlive a response.

`System.currentTimeMillis()` does not advance inside a request, because Workers freeze the clock
between I/O. Absolute timestamps are sound; durations taken from two readings are not.

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

---

## 📄 License

[MIT](LICENSE)
