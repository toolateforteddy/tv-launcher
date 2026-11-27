#!/usr/bin/env bash
# Pairs the phone remote's protocol code with the Android TV emulator and sends it a
# link and a key, reading the pairing code off the TV screen. Saves screenshots to shots/.
set -uo pipefail
mkdir -p shots
adb forward tcp:6466 tcp:6466
adb forward tcp:6467 tcp:6467
adb shell 'netstat -ltn 2>/dev/null | grep -E ":646[67]"' || echo "(the TV isn't listening on 6466/6467 yet)"
echo "--- Control: androidtvremote2 (Python) ---"
python3 -m venv /tmp/atvpy && /tmp/atvpy/bin/pip install -q androidtvremote2 >/dev/null 2>&1 && timeout 120 /tmp/atvpy/bin/python scripts/python-remote-control.py 2>&1 | tail -15 || echo "python control failed"
adb shell input keyevent KEYCODE_HOME
sleep 2
echo "--- Kid Remote protocol code ---"
ATV_E2E=1 ATV_SHOTS="$PWD/shots" ./gradlew --no-daemon :phone:testDebugUnitTest --tests '*RealTvTest*' -i \
  | grep -E "TV shows code|Connect|Resumed after|RealTvTest|FAILED|PASSED|Exception|expected on the emulator" || true
echo "--- TV remote service log ---"
PID=$(adb shell pidof com.google.android.tv.remote.service | tr -d '\r')
echo "remote service pid: $PID"
[ -n "$PID" ] && adb logcat -d --pid="$PID" | grep "AtvRemote" | tail -40 || true
test -f phone/build/test-results/testDebugUnitTest/TEST-com.toolateforteddy.kidremote.atv.RealTvTest.xml \
  && ! grep -q "<failure" phone/build/test-results/testDebugUnitTest/TEST-com.toolateforteddy.kidremote.atv.RealTvTest.xml
