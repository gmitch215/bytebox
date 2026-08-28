# Samples

Each directory is a standalone Gradle build that applies the bytebox plugin. They are not part of the
root build, so a sample is run from inside its own directory.

| Sample                     | Shows                                                     |
| -------------------------- | --------------------------------------------------------- |
| [hello-world](hello-world) | The smallest Worker: one `fetch` handler and nothing else |

## Running One

```sh
cd samples/hello-world
../../gradlew buildWorker
bunx wrangler dev
```

`buildWorker` compiles the Java to WebAssembly and writes a Worker into `build/worker`, including a
`wrangler.jsonc` and an entry point. `wrangler dev` serves it locally through workerd, which is the
same runtime Cloudflare deploys.

## Measuring One

```sh
../../gradlew sizeReport
```

The report prints the compiled module on every compression axis. The figure that matters is gzip,
because Cloudflare enforces its ceiling after applying its own.
