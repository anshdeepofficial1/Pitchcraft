#!/usr/bin/env bash
set -euo pipefail
PACKAGE="com.anshdeep.pitchcraft.debug"
ACTIVITY="com.inferno.gallery.MainActivity"
echo "Granting runtime media permissions to capture real empty-gallery UI"
adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_IMAGES
adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_VIDEO
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 4
# Wait for permissions/DataStore composition. Tap the real UI node bounds,
# never a guessed screen fraction (the previous fixed tap raced permission refresh).
DISPLAY="$(adb shell wm size | tr -d '\r' | tail -n1)"
WIDTH="$(printf '%s' "$DISPLAY" | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1/')"
HEIGHT="$(printf '%s' "$DISPLAY" | sed -E 's/.* ([0-9]+)x([0-9]+).*/\2/')"
for attempt in $(seq 1 12); do
  adb shell uiautomator dump /sdcard/pitchcraft-onboarding.xml >/dev/null || true
  adb pull /sdcard/pitchcraft-onboarding.xml pitchcraft-onboarding.xml >/dev/null || true
  if grep -q 'text="Your story starts here"\|text="Your collection"\|text="Your people"' pitchcraft-onboarding.xml; then
    break
  fi
  COORD="$(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
try:
    root=ET.parse('pitchcraft-onboarding.xml').getroot()
    for node in root.iter('node'):
        if node.get('text') in ('Enter Gallery', 'Continue to Gallery'):
            numbers=[int(v) for v in re.findall(r'\d+',node.get('bounds',''))]
            if len(numbers)==4:
                print((numbers[0]+numbers[2])//2,(numbers[1]+numbers[3])//2)
                break
except (OSError,ET.ParseError):
    pass
PY
)"
  if [ -n "$COORD" ]; then
    read -r TAP_X TAP_Y <<< "$COORD"
    echo "Enter Gallery button is at $TAP_X,$TAP_Y (attempt $attempt)"
    adb shell input tap "$TAP_X" "$TAP_Y"
  fi
  sleep 4
done
sleep 4
adb exec-out screencap -p > pitchcraft-gallery-zero-images.png
adb shell uiautomator dump /sdcard/pitchcraft-reference-ui.xml >/dev/null
adb pull /sdcard/pitchcraft-reference-ui.xml pitchcraft-reference-ui.xml >/dev/null
echo "Visible labels from the actual after-onboarding screen:"
grep -o 'text="[^"]*"' pitchcraft-reference-ui.xml | head -40 || true
if ! grep -q 'text="Your collection"\|text="Your story starts here"\|text="Your people"' pitchcraft-reference-ui.xml; then
  echo "::error::Onboarding did not reach gallery UI; screenshot cannot validate the requested screen."
  exit 1
fi
if [ -z "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]; then
  echo "::error::Gallery process died after onboarding"
  exit 1
fi
echo "Empty-media gallery launched and stayed alive"

# Test-only images stay inside the ephemeral CI emulator; nothing is packaged into the APK.
# This captures the photo-card layout that the empty-gallery test cannot exercise.
python3 - <<'PY'
import math, pathlib, struct, zlib
for seed in range(3):
    width, height = 420, 550
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            nx, ny = (x-width/2)/width, (y-height/2)/height
            distance = math.sqrt(nx*nx + ny*ny)
            bloom = max(0, 1-3.3*distance) ** 2
            wave = max(0.0, math.sin((x+y+seed*43)/27)) * 0.35
            raw.extend((
                min(255, int(18 + 170*bloom + 48*wave + seed*8)),
                min(255, int(14 + 83*bloom + 16*wave)),
                min(255, int(12 + 24*bloom)),
            ))
    def chunk(code, data):
        return struct.pack('!I', len(data))+code+data+struct.pack('!I',zlib.crc32(code+data)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'
    png+=chunk(b'IHDR', struct.pack('!2I5B',width,height,8,2,0,0,0))
    png+=chunk(b'IDAT',zlib.compress(bytes(raw),6))
    png+=chunk(b'IEND',b'')
    pathlib.Path('/tmp/pitchcraft-visual-'+str(seed)+'.png').write_bytes(png)
PY
adb shell mkdir -p /sdcard/Pictures/PitchcraftDesignCheck
for n in 0 1 2; do
  adb push "/tmp/pitchcraft-visual-$n.png" "/sdcard/Pictures/PitchcraftDesignCheck/reference-$n.png" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file:///sdcard/Pictures/PitchcraftDesignCheck/reference-$n.png" >/dev/null || true
done
sleep 5
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/$ACTIVITY" >/dev/null
sleep 18
adb exec-out screencap -p > pitchcraft-gallery-with-test-images.png
adb shell uiautomator dump /sdcard/pitchcraft-reference-photos.xml >/dev/null
adb pull /sdcard/pitchcraft-reference-photos.xml pitchcraft-reference-photos.xml >/dev/null
if [ -z "$(adb shell pidof "$PACKAGE" | tr -d '\r')" ]; then
  echo "::error::Gallery process died after test-photo indexing"
  exit 1
fi
echo "Reference photo-card design screenshot captured."

# Smoke-navigation uses the same interactive dock/chips as a person.
tap_normalized() {
  local fx="$1" fy="$2"
  adb shell input tap "$(( WIDTH * fx / 100 ))" "$(( HEIGHT * fy / 100 ))"
  sleep 4
  test -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')"
}
tap_normalized 32 93
adb exec-out screencap -p > pitchcraft-albums-reference.png
tap_normalized 65 93
adb exec-out screencap -p > pitchcraft-search-reference.png
tap_normalized 17 93
tap_normalized 62 15
adb exec-out screencap -p > pitchcraft-people-reference.png
adb shell input keyevent KEYCODE_BACK
sleep 3
test -n "$(adb shell pidof "$PACKAGE" | tr -d '\r')"
echo "Photos / Albums / Search / People navigation survived."


