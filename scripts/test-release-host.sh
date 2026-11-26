#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/release-host
base=core-native/src/main/java/com/omega/core
kotlinc "$base/release/SessionGate.kt" "$base/executive/Executive.kt" "$base/learning/Learning.kt" "$base/meta/Meta.kt" "$base/neural/NeuralEngine.kt" tests/ExecutiveSmoke.kt tests/ReleaseSmoke.kt -jvm-target 17 -include-runtime -d build/release-host/test.jar
java -cp build/release-host/test.jar ReleaseSmokeKt
