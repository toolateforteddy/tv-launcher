#!/usr/bin/env bash
# Pairs the phone remote's protocol code with the Android TV emulator and sends it a
# link and a key, reading the pairing code off the TV screen. Saves screenshots to shots/.
set -uo pipefail
mkdir -p shots
adb forward tcp:6466 tcp:6466
adb forward tcp:6467 tcp:6467
adb shell 'netstat -ltn 2>/dev/null | grep -E ":646[67]"' || echo "(the TV isn't listening on 6466/6467 yet)"
ATV_E2E=1 ATV_SHOTS="$PWD/shots" ./gradlew --no-daemon :phone:testDebugUnitTest --tests '*RealTvTest*' -i \
  | grep -E "TV shows code|Connect|Resumed after|RealTvTest|FAILED|PASSED|Exception" || true
echo "--- TV remote service log ---"
adb logcat -d | grep -iE "atvremote|remote.service|AtvRemote|polo|SSL|TLS|certificate|anymote" | tail -60 || true
test -f phone/build/test-results/testDebugUnitTest/TEST-com.toolateforteddy.kidremote.atv.RealTvTest.xml \
  && ! grep -q "<failure" phone/build/test-results/testDebugUnitTest/TEST-com.toolateforteddy.kidremote.atv.RealTvTest.xml
