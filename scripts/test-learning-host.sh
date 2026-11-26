#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/learning-host
kotlinc core-native/src/main/java/com/omega/core/executive/Executive.kt core-native/src/main/java/com/omega/core/executive/Tactical.kt core-native/src/main/java/com/omega/core/learning/Learning.kt tests/ExecutiveSmoke.kt tests/LearningSmoke.kt tests/PolicyPauseSmoke.kt -jvm-target 17 -include-runtime -d build/learning-host/test.jar
java -cp build/learning-host/test.jar ExecutiveSmokeKt
java -cp build/learning-host/test.jar LearningSmokeKt
java -cp build/learning-host/test.jar PolicyPauseSmokeKt
