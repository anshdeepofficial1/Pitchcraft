#!/usr/bin/env bash
set -euo pipefail
SIZE="$(adb shell getconf PAGE_SIZE | tr -d '\r')"
echo "Actual emulator kernel page size = $SIZE bytes"
test "$SIZE" = 16384 || { echo "Not a 16KiB test!"; exit 1; }
bash .github/scripts/emulator-smoke.sh
