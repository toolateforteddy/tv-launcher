#!/usr/bin/env bash
# Drives the test build on a connected device/emulator and saves screenshots to shots/.
set -euo pipefail
PKG=com.toolateforteddy.tvlauncher.preview
mkdir -p shots

shot() {
  sleep 3
  adb exec-out screencap -p > "shots/$1.png"
  echo "saved shots/$1.png"
}
key() { adb shell input keyevent "$@"; }

adb install -r app/build/outputs/apk/preview/release/app-preview-release.apk
adb shell am start -W -n "$PKG/com.toolateforteddy.tvlauncher.MainActivity"
shot 01-home

# Open the tile menu on the first app and add it to Favorites.
key KEYCODE_MENU
shot 02-tile-menu
key KEYCODE_DPAD_CENTER
shot 03-with-favorite

# Scroll down to the TV inputs and Settings.
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
shot 04-bottom
