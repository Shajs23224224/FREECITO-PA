#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"; export JAVA_HOME
build="${OMEGA_HOST_BUILD:-$PWD/build/host}"
cmake -S core-native/src/main/cpp -B "$build" -DCMAKE_BUILD_TYPE=Debug
cmake --build "$build" --parallel 2
ctest --test-dir "$build" --output-on-failure
kotlinc core-native/src/main/java/com/omega/core/*.kt tests/JniSmoke.kt -jvm-target 17 -include-runtime -d "$build/jni-test.jar"
java -Djava.library.path="$build" -jar "$build/jni-test.jar"
python3 scripts/verify_manifest.py
