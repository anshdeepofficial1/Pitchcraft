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

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [ -z "$PID" ]; then
  echo "Pitchcraft process is not alive after launch."
  echo "===== Pitchcraft processes ====="
  adb shell ps -A | grep -i pitchcraft || true
  echo "===== Top activity ====="
  adb shell dumpsys activity top | head -n 120 || true
  adb logcat -d -v threadtime > pitchcraft-logcat.txt || true
  echo "===== Pitchcraft crash log ====="
  grep -n -E -B 40 -A 140 "FATAL EXCEPTION|AndroidRuntime|Process: $PACKAGE|$PACKAGE|PHOTON_CRASH|PITCHCRAFT|GalleryApplication|MainActivity|Caused by:" pitchcraft-logcat.txt || cat pitchcraft-logcat.txt
  exit 1
fi

adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" || true
adb logcat -d -v threadtime > pitchcraft-logcat.txt

if grep -E "FATAL EXCEPTION|Process: com\.anshdeep\.pitchcraft\.debug|AndroidRuntime:.*FATAL" pitchcraft-logcat.txt; then
  echo "Fatal runtime crash detected."
  cat pitchcraft-logcat.txt
  exit 1
fi

echo "Runtime smoke test passed. PID=$PID"
