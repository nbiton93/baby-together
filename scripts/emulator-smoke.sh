#!/usr/bin/env bash
set -euo pipefail
mkdir -p out/android-smoke
set +e
gradle -p android --no-daemon connectedDebugAndroidTest
test_status=$?
set -e
adb pull /sdcard/Android/data/com.natibiton.babytogether/files/screenshots out/android-smoke/ || true
adb logcat -d -s AndroidRuntime:E chromium:E > out/android-smoke/runtime-errors.txt
exit "$test_status"
