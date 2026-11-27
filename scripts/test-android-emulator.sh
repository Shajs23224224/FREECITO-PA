#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-report
adb wait-for-device
adb shell getprop ro.build.version.sdk | tee emulator-report/api.txt
adb shell getprop ro.product.cpu.abi | tee emulator-report/abi.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put secure accessibility_enabled 0
adb shell settings delete secure enabled_accessibility_services
adb shell am start -W -n com.omega.ai/.MainActivity | tee emulator-report/launch.txt
adb exec-out screencap -p > emulator-report/launch.png
adb logcat -c
# Enable after instrumentation starts: am instrument may restart the target.
adb shell am instrument -w -r com.omega.ai.test/com.omega.ai.OmegaEmulatorTest > emulator-report/instrumentation.txt 2>&1 &
instrument_pid=$!
for i in $(seq 1 90); do
  if grep -q '^ACCESSIBILITY_READY_FOR_BIND' emulator-report/instrumentation.txt; then
    adb shell settings put secure enabled_accessibility_services com.omega.ai/com.omega.ai.accessibility.OmegaTouchService
    adb shell settings put secure accessibility_enabled 1
    adb shell settings get secure enabled_accessibility_services | tee emulator-report/accessibility-setting.txt
    adb shell settings get secure accessibility_enabled | tee -a emulator-report/accessibility-setting.txt
    break
  fi
  kill -0 "$instrument_pid" 2>/dev/null || break
  sleep 1
done
set +e
wait "$instrument_pid"
code=$?
set -e
cat emulator-report/instrumentation.txt
adb shell dumpsys accessibility > emulator-report/accessibility-dump.txt || true
adb logcat -d -v threadtime > emulator-report/logcat.txt
adb logcat -b crash -d -v threadtime > emulator-report/crash.txt
cat emulator-report/crash.txt
grep -E 'OMEGA_EMULATOR|OMEGA_DIAG' emulator-report/logcat.txt | tail -n 100 || true
adb exec-out screencap -p > emulator-report/finish.png
adb shell settings put secure accessibility_enabled 0
adb shell settings delete secure enabled_accessibility_services
test "$code" -eq 0
grep -q '^OMEGA_EMULATOR_PASS' emulator-report/instrumentation.txt
! grep -q 'OMEGA_EMULATOR_FAIL\|Process crashed' emulator-report/instrumentation.txt
 grep -q 'INSTRUMENTATION_CODE: -1' emulator-report/instrumentation.txt
