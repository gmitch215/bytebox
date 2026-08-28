# Samples

Nine Workers, each showing one thing. They build against the bytebox in this tree rather than a
published version, so they double as the end-to-end check for the Gradle plugin.

[ADVANCED_USAGE.md](ADVANCED_USAGE.md) is the design document behind them: the rules the whole thing
is built on, what each one costs, and recipes that combine several samples.

| Sample                               | Shows                                                       |
| ------------------------------------ | ----------------------------------------------------------- |
| [hello-world](hello-world)           | The smallest Worker: one `fetch` handler, no bindings       |
| [kv-counter](kv-counter)             | Workers KV, and why an exact counter does not belong in it  |
| [durable-object](durable-object)     | A Durable Object, where state that has to be exact belongs  |
| [cron](cron)                         | A Worker with no HTTP handler at all                        |
| [email-router](email-router)         | Inbound email, and the disposition a handler has to record  |
| [queue-consumer](queue-consumer)     | A Queue consumer acknowledging each message separately      |
| [npm-dependency](npm-dependency)     | Calling an npm package from Java                            |
| [tcp-client](tcp-client)             | Raw TCP over `cloudflare:sockets`, and what it cannot reach |
| [standard-library](standard-library) | `java.time`, `java.net.http`, regex and `String.format`     |

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

## Measuring

```sh
../gradlew :queue-consumer:sizeReport
```

The figure that binds is gzip, because Cloudflare enforces its ceiling after applying its own. Eight
of these measure under 50 KB raw against a 3 MB free-plan ceiling; `standard-library` is 511 KB,
because it links every retargeted part of the class library at once.

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

**standard-library** writes `java.time`, `java.net.http`, `java.util.regex` and `String.format` the way
they are written anywhere. Nothing in it is a bytebox API; the compiler points each reference at an
implementation that works on this runtime. It is the widest of these, and the check that they link
together in a project rather than only in a test fixture.
