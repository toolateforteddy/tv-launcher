#!/usr/bin/env bash
# One-time setup so the phone remote can open shows even while another app
# (Netflix, YouTube, ...) is on screen. Android 10 blocks that by default.
# Usage: scripts/enable-kid-remote.sh <tv-ip> [test|home]
set -euo pipefail
TV="${1:?usage: $0 <tv-ip> [test|home]}"
case "${2:-test}" in
  test) PKG=com.toolateforteddy.tvlauncher.preview; PORT=8766 ;;
  home) PKG=com.toolateforteddy.tvlauncher; PORT=8765 ;;
  *) echo "second argument must be test or home" >&2; exit 1 ;;
esac
adb connect "$TV"
adb -s "$TV:5555" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
echo "Done. On a phone on the same Wi-Fi, open http://$TV:$PORT"
