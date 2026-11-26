#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tactical-host
b=core-native/src/main/java/com/omega/core
kotlinc "$b/executive/Tactical.kt" "$b/executive/Executive.kt" "$b/learning/Learning.kt" tests/TacticalSmoke.kt tests/ExecutiveSmoke.kt tests/TacticalExecutiveSmoke.kt -jvm-target 17 -include-runtime -d build/tactical-host/test.jar
java -cp build/tactical-host/test.jar TacticalSmokeKt
java -cp build/tactical-host/test.jar TacticalExecutiveSmokeKt
