#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/executive-host
kotlinc core-native/src/main/java/com/omega/core/executive/Executive.kt tests/ExecutiveSmoke.kt -jvm-target 17 -include-runtime -d build/executive-host/test.jar
java -jar build/executive-host/test.jar
