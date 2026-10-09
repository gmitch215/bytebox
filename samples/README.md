# Samples

Fourteen Workers, each showing one thing. They build against the bytebox in this tree rather than a
published version, so they double as the end-to-end check for the Gradle plugin.

[ADVANCED_USAGE.md](../ADVANCED_USAGE.md) is the design document behind them: the rules the whole thing
is built on, what each one costs, and recipes that combine several samples.

| Sample                                   | Shows                                                       |
| ---------------------------------------- | ----------------------------------------------------------- |
| [hello-world](hello-world)               | The smallest Worker: one `fetch` handler, no bindings       |
| [kv-counter](kv-counter)                 | Workers KV, and why an exact counter does not belong in it  |
| [durable-object](durable-object)         | A Durable Object, where state that has to be exact belongs  |
| [cron](cron)                             | A Worker with no HTTP handler at all                        |
| [email-router](email-router)             | Inbound email, and the disposition a handler has to record  |
| [queue-consumer](queue-consumer)         | A Queue consumer acknowledging each message separately      |
| [npm-dependency](npm-dependency)         | Calling an npm package from Java                            |
| [mixed-dependencies](mixed-dependencies) | A Java library and an npm package in one project            |
| [tcp-client](tcp-client)                 | Raw TCP over `cloudflare:sockets`, and what it cannot reach |
| [standard-library](standard-library)     | `java.time`, `java.net.http`, regex and `String.format`     |
| [kotlin](kotlin)                         | A Worker written as a Kotlin `object`                       |
| [cache](cache)                           | The Workers Cache API, with a hit and a miss                |
| [html-rewriter](html-rewriter)           | `HTMLRewriter` with Java handlers                           |
| [workflow](workflow)                     | A Java class as a Workflow, and starting it from a Worker   |

## Building

```sh
cd samples
../gradlew buildWorkers      # every sample
../gradlew :cron:buildWorker # one of them
```

`buildWorker` compiles the Java to WebAssembly and writes a complete Worker into
`build/bytebox/worker`: the module, the Wrangler configuration, the JavaScript entry point and a
package manifest. Nothing in that directory is hand-written, so it is safe to delete.

## Running

```sh
cd hello-world/build/bytebox/worker
bunx wrangler dev
```

`wrangler dev` serves it through workerd, which is the runtime Cloudflare deploys. The plugin also
wraps the CLI, so `../gradlew :hello-world:workerDev` does the same without leaving Gradle.

Bindings run on local stand-ins, so no account is needed. Eleven samples answer an ordinary request:
`hello-world`, `kv-counter`, `cache`, `html-rewriter`, `npm-dependency`, `mixed-dependencies`,
`kotlin`, `tcp-client`, `standard-library`, `durable-object` and `workflow`. `tcp-client` and the
`java.net.http` call in `standard-library` need network access. `durable-object` answers
`/a is at 1` and then `/a is at 2` for two requests to `/a`, and `workflow` takes
`/start?item=lamp` and then `/status?id=<id>`.

The other three have no HTTP handler or need a different trigger:

- `cron`: start `bunx wrangler dev --test-scheduled`, then `curl "localhost:8787/__scheduled"`.
- `email-router`: `curl -X POST "localhost:8787/cdn-cgi/handler/email?from=a@b.c&to=d@e.f"` with a
  raw message as the body. The message needs a `Message-ID` header, or Wrangler answers 400.
- `queue-consumer`: it exports no `fetch`, so a request answers 500. A message has to come from a
  producer.

`node verify-workers.mjs` loads every built sample through `npm/dist/loader.js` and calls it, so run
`bun run --cwd npm build` first, on Node 24 or later. It never reads `wrangler.jsonc`, so a
configuration Wrangler refuses passes it. `bunx wrangler deploy --dry-run` in a sample's
`build/bytebox/worker` is the check for that.

## Measuring

```sh
../gradlew :queue-consumer:sizeReport
```

The figure that binds is the uncompressed one, which Cloudflare meters against 64 MiB on either plan.
Every sample here is orders of magnitude under it. `standard-library` is the largest by a wide margin,
because it links every retargeted part of the class library at once, which also makes it the one
where a change to the class library shows up first.

## What Each One Is For

**hello-world** is the floor: what a Worker costs before it does anything.

**kv-counter** and **durable-object** are the same feature twice. KV is eventually consistent, so two
regions can read the same value and both write the next one; a Durable Object is one instance per id
in one place, so they cannot. Reading them together is the point.

**cron** implements only `Scheduled`. The generated Worker exports only `scheduled` and the generated
configuration carries only the trigger, which is what deriving from the handler's interfaces buys.

**email-router** shows the disposition rule: Cloudflare drops a message a handler returns without
acting on, so `InboundMail` records what was done and raises if nothing was.

**queue-consumer** acknowledges per message rather than letting one failure retry the batch.

**npm-dependency** calls `nanoid` through `@JSBodyImport`. The package stays JavaScript and never
enters the WebAssembly, so it costs bundle bytes rather than module bytes.

**tcp-client** connects with TLS and frames a response by delimiter. Its comments record what
Cloudflare refuses to connect to, which is the part that decides whether raw TCP is the right tool.

**mixed-dependencies** takes a Java library and an npm package in one project. Guava is compiled into
the module; nanoid stays JavaScript beside it. The two arrive by different routes and are paid for in
different currencies, which is the point of having them side by side.

**standard-library** writes `java.time`, `java.net.http`, `java.util.regex` and `String.format` the way
they are written anywhere. Nothing in it is a bytebox API; the compiler points each reference at an
implementation that works on this runtime. It is the widest of these, and the check that they link
together in a project rather than only in a test fixture.

**kotlin** applies the Kotlin plugin beside bytebox and handles requests from an `object`, which the
generated entry point reads through its `INSTANCE`. It uses a data class, `filter`, `associate` and
string templates, all of which compile into the module with `kotlin-stdlib`. Coroutines do not build.

**cache** answers from `caches.default` when it can. The first request for a URL computes the answer
and returns `X-Cache: MISS`, and the write goes through `ctx.waitUntil`, so the response does not wait
for it. A later request for the same URL in the same data center returns `X-Cache: HIT`. It needs no
binding.

**html-rewriter** rewrites a page that lives in the Worker, so it needs no network. `/` opens links
in a new tab, adds a banner and appends a footer, and `/text` rewrites words in text, which the
parser hands over in chunks.

**workflow** has two files: `OrderFlow`, a Workflow with two steps and a sleep between them, and
`OrderWorker`, which starts it at `/start?item=lamp` and reads it back at `/status?id=<id>`. They
share one Worker, so a request that arrives during the sleep waits for it. A Workflow that sleeps for
long belongs in a Worker of its own.
