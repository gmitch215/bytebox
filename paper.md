---
title: 'Bytebox: Java on Cloudflare Workers'
tags:
  - Java
  - WebAssembly
  - WasmGC
  - serverless
  - edge computing
  - Cloudflare Workers
  - TeaVM
  - Gradle
authors:
  - name: Gregory Mitchell
    orcid: 0009-0002-3956-4818
    affiliation: 1
affiliations:
  - name: Independent Researcher, Hanover, NH, United States
    index: 1
date: 19 September 2026
bibliography: paper.bib
---

# Summary

Serverless platforms run small programs on demand, starting them when a request
arrives and discarding them afterwards, so that no server has to be kept running
between visits. Cloudflare Workers is one such platform, and it executes two kinds
of program: JavaScript, and WebAssembly, a portable binary format that many
languages can be compiled into [@cfwasm].

Java is not one of those two kinds. A Java program normally runs on a Java Virtual
Machine, which expects a filesystem, operating-system services, real threads, and a
process that outlives any single request. None of those exist on this platform.

Bytebox compiles a Java project into a deployable Worker. A developer writes an
ordinary Java class, applies a Gradle plugin, and runs one command; the build emits
a complete Worker — the WebAssembly module, its configuration, a JavaScript entry
point and a package manifest — with nothing written by hand. Inside that Worker,
Java code that was never written for this platform continues to work: dates and
times, HTTP clients, sockets, regular expressions and string formatting all behave
as they do elsewhere, because the build points each of those references at an
implementation that the platform can actually run. Where no faithful implementation
is possible, the build refuses the program instead of producing one that looks
right and behaves differently.

# Statement of Need

Compiling Java to WebAssembly is a solved problem: TeaVM has done it for years, and
its WebAssembly GC backend produces a module with garbage collection provided by
the host engine [@teavm]. Running that module on Cloudflare Workers was not
possible before this software, for reasons that are properties of the platform
rather than of the compiler.

TeaVM's generated module imports seven functions from the `wasm:js-string`
namespace [@jsstring], two of which take WebAssembly array references that
JavaScript cannot construct. Those imports are satisfied only by passing an option
to the module constructor, and the platform's own pre-compiled module path accepts
no options, so a Worker built that way fails at instantiation. The obvious
alternative — compiling the module when the request arrives — is refused: the
runtime permits synchronous WebAssembly construction while a Worker is starting and
prohibits it during request handling [@cfwebstandards]. TeaVM's own loader cannot be
used either, because it compiles a feature-detection module from bytes, takes a
Node.js branch whenever `process` is defined, and resolves package imports through a
dynamic import of a string stored in a WebAssembly custom section.

Beyond loading, TeaVM's class library has no `java.time` at all, an HTTP stack wired
to `XMLHttpRequest`, and a formatter that reaches locale data unavailable here.
A third-party library does not stop using those APIs because the platform changed.

Bytebox supplies the loader that works, the platform bindings a Worker needs, the
build that ties them together, and a retargeted class library beneath the unchanged
Java programming model. For researchers, it also functions as an instrument: it is
the artifact through which a managed language runtime can be deployed to, and
measured on, a commercial serverless host. Given identical sources and settings it
emits byte-identical output, so committed binaries serve as stable experimental
inputs and a change in a measurement is attributable to a change in the
implementation rather than to build drift.

# State of the Field

Managed and dynamic language runtimes have been brought to JavaScript-hosted
WebAssembly environments before. Pyodide compiles CPython through Emscripten and
supplies the absent host services — a synthesized filesystem and a JavaScript
interoperation layer — from inside the module [@pyodide]. Blazor WebAssembly
executes a .NET runtime in WebAssembly on a browser host [@blazor]. WALL-E
integrates managed-language libraries through external library linking rather than
nesting a runtime inside the module [@walle]. GraalVM Native Image takes a different
target entirely, compiling Java ahead of time to a native executable for a specific
operating system [@graalvm]. It sits with AWS Lambda SnapStart [@snapstart] and CRaC
[@crac] among systems that cut JVM startup cost, by snapshotting or ahead-of-time
compilation, while keeping a conventional operating-system host. Separately,
WebAssembly has been evaluated as a serverless execution target independent of any
particular language
[@murphy; @lumos].

Pyodide and Blazor ship a language runtime into WebAssembly and emulate what the
host does not provide; the startup-side systems keep a conventional host and change
how the runtime reaches it. Bytebox starts from an existing Java-to-WebAssembly
compiler and makes the opposite move where it can: the class library is bound to
services the
host already implements, work that cannot execute on the platform is removed at
build time, and semantics that cannot be preserved are refused rather than
approximated.

# Software Design

The Gradle plugin derives the Worker from the Java project. A handler class declares
what it handles by implementing interfaces — HTTP requests, cron triggers, inbound
email, queue messages, trace events, Durable Object alarms — and the build derives
the JavaScript exports, the configuration keys and the entry-point guards from the
interfaces present, so an unimplemented trigger produces no code and no
configuration.

The loader embeds the compiled module as binary data, constructs it synchronously
while the Worker is starting, and instantiates it before any handler runs. It holds
a gate through which every entry point passes, because one WebAssembly heap serves
an isolate and an isolate serves many requests; without it, a second request could
run on the same heap in the middle of the first request's work.

The class library is retargeted through the compiler's substitution mechanism rather
than through a wrapper API, which is what allows an unmodified dependency to
compile: `java.time` resolves to ThreeTen-Backport [@threeten] with zone rules read
from the host's internationalization data, `java.net` to the platform's own HTTP and
TCP facilities, `java.util.regex` to the host's regular-expression engine with
differing constructs translated [@davis], and `java.util.Formatter` to digits
computed in Java because the host's rounding answers a different question. JSON
codecs and `java.io` serialization are generated at build time rather than
discovered by reflection, since a closed-world compiler cannot prune a reflective
closure.
Constructs with no faithful equivalent — inbound sockets, subprocesses, dynamic
class loading, several formatter and regular-expression forms — fail at compile time
with the refusal named.

Java and npm dependencies coexist. A binding generator resolves TypeScript and
JavaScript type information in descending order of reliability and falls back to a
dynamic representation when no static shape can be established, so a package without
published types still binds.

Ten samples in the repository each demonstrate one feature and double as the
end-to-end check, and a verification step compiles every built module through the
loader's own options before the build reports success.

# Research Impact Statement

Bytebox produced the measurements in the accompanying study of runtime retargeting
[@retargeting]. Among them: Worker startup tracks executable WebAssembly content
rather than uploaded bytes, remaining flat at 8–10 ms while inert padding grew the
bundle 640-fold; request CPU, not size or startup, is the budget a retargeted class
library spends, measured at a 43 ms median against a documented 10 ms free-plan
allowance without termination; the isolate memory ceiling measured 254 MiB accepted
and 256 MiB refused against a documented 128 MB [@cflimits]; five of fourteen
third-party Java libraries compile and run unmodified, with the refusals falling
into three
identifiable layers; and a regular expression translated to byte-identical results
across 48 pattern–input pairs cost 14 ms on the reference JVM and 11,010 ms on the
host engine, showing that semantic agreement does not imply equivalent cost.

None of those measurements was obtainable without a way to deploy a managed runtime
to this platform, which is the research role the software plays. The study names this
repository at commit `db114b1` as its artifact; the reproducible build makes the
measurements repeatable by others, and the samples provide the workloads.

# AI Usage Disclosure

The implementation was developed with assistance from AI coding agents
directed by the author, under measurement and failure-mode rules recorded in the
repository. All architectural decisions, all measurements and their interpretation,
and all prose in this paper and in the accompanying study are the author's own.
Generated code was reviewed, tested and, where it was wrong, corrected or removed;
several of the defects reported in the accompanying study were found this way.

# Acknowledgements

Bytebox builds on TeaVM, and on ThreeTen-Backport for its date and time
implementation. It descends from Drupflare, an earlier project by the same author
that runs PHP as WebAssembly inside a Cloudflare Durable Object, and shares its
module-loading layer with `cartridge`, the interpreter-hosting library extracted
from that project.

# References
