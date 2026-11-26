#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/meta-host
kotlinc core-native/src/main/java/com/omega/core/meta/Meta.kt core-native/src/main/java/com/omega/core/neural/NeuralEngine.kt tests/MetaSmoke.kt tests/NeuralBench.kt -jvm-target 17 -include-runtime -d build/meta-host/test.jar
java -cp build/meta-host/test.jar MetaSmokeKt
java -cp build/meta-host/test.jar NeuralBenchKt
