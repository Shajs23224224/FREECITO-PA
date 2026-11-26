#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
build="${OMEGA_VISION_BUILD:-$PWD/build/vision-host}"
mkdir -p "$build"
kotlinc vision/src/main/java/com/omega/vision/PerceptionMath.kt tests/VisionSmoke.kt -jvm-target 17 -include-runtime -d "$build/vision-test.jar"
java -jar "$build/vision-test.jar"
