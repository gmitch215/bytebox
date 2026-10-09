#!/bin/sh
set -e
here=$(cd "$(dirname "$0")" && pwd)
out=$here/../build/compileSync/js/main/productionExecutable/kotlin
mkdir -p "$here/src/kotlin"
cp "$out"/*.mjs "$here/src/kotlin/"
