#!/usr/bin/env bash
# Makes our launcher the real home screen: installs the home build, then
# turns off the stock Android TV home screen (the one with the ads).
# Undo any time with scripts/switch-to-stock.sh.
# Usage: scripts/switch-to-custom.sh <tv-ip> [path/to/tv-launcher-home.apk]
set -euo pipefail
TV="${1:?usage: $0 <tv-ip> [apk]}"
APK="${2:-tv-launcher-home.apk}"
adb connect "$TV"
ADB=(adb -s "$TV:5555")
"${ADB[@]}" install -r "$APK"
"${ADB[@]}" shell pm disable-user --user 0 com.google.android.tvlauncher
"${ADB[@]}" shell input keyevent KEYCODE_HOME
echo "Done. Press Home on the remote to check it opens the new home screen."
