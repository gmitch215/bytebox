#!/bin/sh
# copies the production executable's wasm next to index.mjs under the name it imports
set -e
here=$(cd "$(dirname "$0")" && pwd)
cp "$here/../build/compileSync/wasmWasi/main/productionExecutable/optimized/bytebox-experiments-kotlin-wasmwasi.wasm" "$here/src/kotlin.wasm"
