#!/usr/bin/env bash
# Brings back the stock Android TV home screen. Our launcher stays installed
# but the Home button goes back to the original.
# Usage: scripts/switch-to-stock.sh <tv-ip>
set -euo pipefail
TV="${1:?usage: $0 <tv-ip>}"
adb connect "$TV"
ADB=(adb -s "$TV:5555")
"${ADB[@]}" shell pm enable com.google.android.tvlauncher
"${ADB[@]}" uninstall com.toolateforteddy.tvlauncher || true
"${ADB[@]}" shell input keyevent KEYCODE_HOME
echo "Stock home screen restored."
