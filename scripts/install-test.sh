#!/usr/bin/env bash
# Installs the test build. It shows up as "Home (test)" in the TV's apps list
# and does not change what the Home button does.
# Usage: scripts/install-test.sh <tv-ip> [path/to/tv-launcher-test.apk]
set -euo pipefail
TV="${1:?usage: $0 <tv-ip> [apk]}"
APK="${2:-tv-launcher-test.apk}"
adb connect "$TV"
adb -s "$TV:5555" install -r "$APK"
echo "Installed. Open 'Home (test)' from the Apps row on the TV."
