#!/bin/sh
# copies the production executable's files next to index.mjs under the names it imports
set -e
here=$(cd "$(dirname "$0")" && pwd)
out=$here/../build/compileSync/wasmJs/main/productionExecutable/optimized
cp "$out/bytebox-experiments-kotlin-wasmjs.wasm" "$here/src/kotlin.wasm"
cp "$out/bytebox-experiments-kotlin-wasmjs.import-object.mjs" "$here/src/kotlin.import-object.mjs"
cp "$out/bytebox-experiments-kotlin-wasmjs.js-builtins.mjs" "$here/src/bytebox-experiments-kotlin-wasmjs.js-builtins.mjs"
