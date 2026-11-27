#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/lifecycle-host
kotlinc core-native/src/main/java/com/omega/core/lifecycle/SessionLifecycle.kt tests/SessionLifecycleSmoke.kt -jvm-target 17 -include-runtime -d build/lifecycle-host/test.jar
java -jar build/lifecycle-host/test.jar
