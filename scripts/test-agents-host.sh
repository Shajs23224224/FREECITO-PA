#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/agents-host
kotlinc core-native/src/main/java/com/omega/core/neural/NeuralEngine.kt core-native/src/main/java/com/omega/core/agents/AgentBoard.kt tests/AgentSmoke.kt -jvm-target 17 -include-runtime -d build/agents-host/test.jar
java -jar build/agents-host/test.jar
