#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/training-host
kotlinc core-native/src/main/java/com/omega/core/executive/TrainingObservation.kt tests/TrainingObservationSmoke.kt -jvm-target 17 -include-runtime -d build/training-host/test.jar
java -jar build/training-host/test.jar
