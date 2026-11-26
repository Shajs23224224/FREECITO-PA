#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/touch-host
kotlinc core-native/src/main/java/com/omega/core/release/TouchGate.kt tests/TouchSmoke.kt -jvm-target 17 -include-runtime -d build/touch-host/test.jar
java -jar build/touch-host/test.jar
