#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/neural-host
kotlinc core-native/src/main/java/com/omega/core/neural/NeuralEngine.kt tests/NeuralSmoke.kt -jvm-target 17 -include-runtime -d build/neural-host/test.jar
java -jar build/neural-host/test.jar
