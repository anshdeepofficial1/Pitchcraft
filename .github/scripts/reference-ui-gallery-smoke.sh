#!/usr/bin/env bash
set -euo pipefail
PACKAGE="com.anshdeep.pitchcraft.debug"
ACTIVITY="com.inferno.gallery.MainActivity"
echo "Granting runtime media permissions to capture real empty-gallery UI"
adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_IMAGES
adb shell pm grant "$PACKAGE" android.permission.READ_MEDIA_VIDEO
adb shell appops set "$PACKAGE" MANAGE_EXTERNAL_STORAGE allow
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/$ACTIVITY"
sleep 3
# Main onboarding's 'Continue to Gallery' button is near 92% down the screen.
DISPLAY="$(adb shell wm size | tr -d '\r' | tail -n1)"
WIDTH="$(printf '%s' "$DISPLAY" | sed -E 's/.* ([0-9]+)x([0-9]+).*/\1/')"
HEIGHT="$(printf '%s' "$DISPLAY" | sed -E 's/.* ([0-9]+)x([0-9]+).*/\2/')"
adb shell input tap "$(( WIDTH / 2 ))" "$(( HEIGHT * 90 / 100 ))"
sleep 18
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
