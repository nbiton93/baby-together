#!/usr/bin/env bash
set -euo pipefail
mkdir -p out/installer-test
gradle -p installer --no-daemon connectedDebugAndroidTest -PupdateSha256="$UPDATE_SHA256"
adb pull /sdcard/Android/data/com.natibiton.babytogether.installer/files/test-signed-update.apk "$RUNNER_TEMP/test-signed-update.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$RUNNER_TEMP/test-signed-update.apk"
adb install "$RUNNER_TEMP/test-signed-update.apk"
adb shell am start -W -n com.natibiton.babytogether/.MainActivity > out/installer-test/app-launch.txt
adb shell dumpsys package com.natibiton.babytogether > out/installer-test/installed-package.txt
grep -F 'versionCode=3' out/installer-test/installed-package.txt
adb shell am force-stop com.natibiton.babytogether
adb install -r "$RUNNER_TEMP/test-signed-update.apk"
adb logcat -d -s AndroidRuntime:E > out/installer-test/runtime-errors.txt
if grep -F 'FATAL EXCEPTION' out/installer-test/runtime-errors.txt; then exit 1; fi
rm -f "$RUNNER_TEMP/test-signed-update.apk"
