#!/usr/bin/env bash
# Run as one Bash process: Android emulator runner's `script` splits individual
# YAML lines into independent /bin/sh invocations, which lose shell variables.
set -euo pipefail

mkdir -p tv-smoke-output
trap 'adb logcat -d -v time > tv-smoke-output/adb-logcat.txt 2>/dev/null || true; adb shell dumpsys activity activities > tv-smoke-output/activities.txt 2>/dev/null || true' EXIT

APK="$(find tv-apks -type f -name '*x86*.apk' | head -1)"
test -n "$APK"
test -s "$APK"
echo "APK=$APK"
adb install -r "$APK"
adb logcat -c
adb shell am start -n com.myo.tv/com.movix.tv.MainActivity

# Includes MYO's short intro; a real Android TV home must be drawn after startup.
sleep 14
adb shell input keyevent KEYCODE_DPAD_LEFT
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 4
adb exec-out screencap -p > tv-smoke-output/myo-home.png
adb logcat -d -v time > tv-smoke-output/adb-logcat.txt
adb shell dumpsys activity activities > tv-smoke-output/activities.txt
PID="$(adb shell pidof com.myo.tv | tr -d '\r')"
echo "MYO_PID=$PID" | tee tv-smoke-output/result.txt
test -n "$PID"

# Narrow crash check to MYO; other TV platform processes can have unrelated errors.
if grep -F 'Process: com.myo.tv' tv-smoke-output/adb-logcat.txt; then
  echo "MYO crashed: see Logcat" >> tv-smoke-output/result.txt
  exit 1
fi
echo "PASS: package installed, launched, responded to remote keys and process alive" >> tv-smoke-output/result.txt
