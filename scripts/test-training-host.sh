#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/training-host
kotlinc core-native/src/main/java/com/omega/core/executive/TrainingObservation.kt tests/TrainingObservationSmoke.kt -jvm-target 17 -include-runtime -d build/training-host/test.jar
java -jar build/training-host/test.jar
kotlinc vision/src/main/java/com/omega/vision/AutoHud.kt vision/src/main/java/com/omega/vision/IconHud.kt tests/AutoHudSmoke.kt -jvm-target 17 -include-runtime -d build/training-host/hud.jar
java -jar build/training-host/hud.jar

kotlinc vision/src/main/java/com/omega/vision/IconHud.kt tests/IconHudSmoke.kt -jvm-target 17 -include-runtime -d build/training-host/icons.jar
java -jar build/training-host/icons.jar
