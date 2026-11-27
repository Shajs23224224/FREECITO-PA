#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
build="${OMEGA_VISION_BUILD:-$PWD/build/vision-host}"
mkdir -p "$build"
kotlinc vision/src/main/java/com/omega/vision/PerceptionMath.kt tests/VisionSmoke.kt -jvm-target 17 -include-runtime -d "$build/vision-test.jar"
java -jar "$build/vision-test.jar"

kotlinc vision/src/main/java/com/omega/vision/PerceptionMath.kt vision/src/main/java/com/omega/vision/YoloMath.kt tests/YoloSmoke.kt -jvm-target 17 -include-runtime -d "$build/yolo-test.jar"
java -jar "$build/yolo-test.jar"

kotlinc vision/src/main/java/com/omega/vision/ExactFrameCache.kt tests/ExactFrameCacheSmoke.kt -jvm-target 17 -include-runtime -d "$build/ocr-cache-test.jar"
java -jar "$build/ocr-cache-test.jar"

kotlinc vision/src/main/java/com/omega/vision/UniformMarginCrop.kt tests/UniformMarginCropSmoke.kt -jvm-target 17 -include-runtime -d "$build/ocr-crop-test.jar"
java -jar "$build/ocr-crop-test.jar"

kotlinc vision/src/main/java/com/omega/vision/ObservationHealth.kt tests/ObservationHealthSmoke.kt -jvm-target 17 -include-runtime -d "$build/observation-health-test.jar"
java -jar "$build/observation-health-test.jar"
