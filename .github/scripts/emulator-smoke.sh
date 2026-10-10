#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.anshdeep.pitchcraft.debug"
ACTIVITY="com.inferno.gallery.MainActivity"

echo "Checking APK: $APK"
test -f "$APK"

adb logcat -c
adb install -r "$APK"
adb shell am force-stop "$PACKAGE" || true

echo "Launching Pitchcraft..."
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 12

PID="$(adb shell pidof "$PACKAGE" | tr -d '\r')"
if [ -z "$PID" ]; then
  echo "Pitchcraft process is not alive after launch."
  adb logcat -d -t 1500 > pitchcraft-logcat.txt || true
  cat pitchcraft-logcat.txt || true
  exit 1
fi

adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" || true
adb logcat -d -t 2000 > pitchcraft-logcat.txt

if grep -E "FATAL EXCEPTION|Process: com\.anshdeep\.pitchcraft\.debug|AndroidRuntime:.*FATAL" pitchcraft-logcat.txt; then
  echo "Fatal runtime crash detected."
  cat pitchcraft-logcat.txt
  exit 1
fi

echo "Runtime smoke test passed. PID=$PID"
