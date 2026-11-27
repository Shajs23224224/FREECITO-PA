#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/diagnostics-host
kotlinc core-native/src/main/java/com/omega/core/diagnostics/DiagnosticJournal.kt tests/DiagnosticJournalSmoke.kt -jvm-target 17 -include-runtime -d build/diagnostics-host/test.jar
java -jar build/diagnostics-host/test.jar
