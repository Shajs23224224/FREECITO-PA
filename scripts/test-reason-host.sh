#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/reason-host
kotlinc core-native/src/main/java/com/omega/core/reasoning/Reasoner.kt tests/ReasonSmoke.kt -jvm-target 17 -include-runtime -d build/reason-host/test.jar
java -jar build/reason-host/test.jar
