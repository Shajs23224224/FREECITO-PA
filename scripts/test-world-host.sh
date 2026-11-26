#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/world-host
kotlinc core-native/src/main/java/com/omega/core/world/World.kt tests/WorldSmoke.kt -jvm-target 17 -include-runtime -d build/world-host/test.jar
java -jar build/world-host/test.jar
