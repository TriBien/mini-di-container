#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/.build/classes"
rm -rf "$ROOT/.build"
mkdir -p "$OUT"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -d "$OUT"
java -cp "$OUT" com.example.minidi.experiments.SelfTest
java -cp "$OUT" com.example.minidi.experiments.Main
