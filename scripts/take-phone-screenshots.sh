#!/usr/bin/env bash
# Opens the phone remote on a phone emulator and saves a screenshot to shots/.
set -euo pipefail
mkdir -p shots
adb install -r phone/build/outputs/apk/release/phone-release.apk
adb shell am start -W -n com.toolateforteddy.kidremote/.MainActivity
sleep 6
adb exec-out screencap -p > shots/phone-01-first-run.png
echo "saved shots/phone-01-first-run.png"
