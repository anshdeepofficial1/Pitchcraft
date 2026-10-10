#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.anshdeep.pitchcraft.debug"
ACTIVITY="com.inferno.gallery.MainActivity"
LOGFILE="pitchcraft-logcat.txt"

echo "Checking APK: $APK"
test -f "$APK"
echo "Emulator Android API: $(adb shell getprop ro.build.version.sdk | tr -d '\\r')"
echo "Emulator ABI list: $(adb shell getprop ro.product.cpu.abilist | tr -d '\\r')"
echo "APK size (bytes): $(stat -c '%s' "$APK")"

# Must be a genuinely fresh installation. Never hide a conflict using -r.
if adb shell pm list packages "$PACKAGE" | grep -q "package:$PACKAGE"; then
  echo "Emulator has a pre-existing copy. Fresh-install test is invalid."
  exit 1
fi

echo "Testing FIRST-TIME installation (not an update)..."
adb install "$APK"
adb shell pm path "$PACKAGE" | tee /tmp/pitchcraft-pm-path.txt
grep -q "package:" /tmp/pitchcraft-pm-path.txt
adb shell am force-stop "$PACKAGE" || true
adb logcat -c
rm -f "$LOGFILE"

# Start recording BEFORE launch so the first-millisecond crash cannot scroll out.
adb logcat -v threadtime > "$LOGFILE" 2>&1 &
LOGCAT_PID=$!

cleanup() {
  kill "$LOGCAT_PID" 2>/dev/null || true
  wait "$LOGCAT_PID" 2>/dev/null || true
}
trap cleanup EXIT

echo "Launching Pitchcraft..."
adb shell am start -W -n "$PACKAGE/$ACTIVITY" || true

sleep 15

API_LEVEL="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
adb exec-out screencap -p > "pitchcraft-api-${API_LEVEL}.png"

PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
cleanup
trap - EXIT

echo "===== Pitchcraft-focused log ====="
grep -n -E -B 25 -A 100 "PitchcraftCrash|com\.anshdeep\.pitchcraft|com\.inferno\.gallery|FATAL EXCEPTION|AndroidRuntime|Caused by:|SIG(SEGV|ABRT)|Fatal signal" "$LOGFILE" || true

if [ -z "$PID" ]; then
  echo "Pitchcraft main process is not alive after launch."
  exit 1
fi

adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" || true

if grep -q -E "PitchcraftCrash|FATAL EXCEPTION|Process: com\.anshdeep\.pitchcraft\.debug|AndroidRuntime:.*FATAL|Fatal signal" "$LOGFILE"; then
  echo "Fatal runtime crash detected."
  exit 1
fi

echo "Runtime smoke test passed. PID=$PID"
