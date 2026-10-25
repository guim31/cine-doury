#!/usr/bin/env bash
# On the running emulator: installs the debug APK, checks that it starts, then runs the
# instrumented tests (Keystore, and the sign-in with the remote against the test Jellyfin, reached
# from the emulator at 10.0.2.2). Usage: emulator-tests.sh APK_DIR TEST_APK_DIR
set -euo pipefail

apk="$(find "${1:?usage: emulator-tests.sh APK_DIR TEST_APK_DIR}" -name '*.apk' | head -n 1)"
test_apk="$(find "${2:?usage: emulator-tests.sh APK_DIR TEST_APK_DIR}" -name '*.apk' | head -n 1)"
package="fr.guim.cinedoury"
activity="$package/fr.guim.cinedoury.tv.MainActivity"

# The logs of the app, kept whatever happens: every caught failure is there with its stack.
dump_logs() {
  adb logcat -d -b crash > emulator-crash.log || true
  adb logcat -d -s CineDoury:V AndroidRuntime:E TestRunner:V > emulator-logcat.log || true
}
trap dump_logs EXIT

adb install -r "$apk"
adb install -r "$test_apk"
adb logcat -c

adb shell am start -W -n "$activity"
sleep 10
adb exec-out screencap -p > emulator-start.png
if ! adb shell dumpsys activity activities | grep -E 'ResumedActivity' | grep -q "$package"; then
  adb logcat -d -b crash || true
  echo "::error::$activity is not in front"
  exit 1
fi
adb shell am force-stop "$package"

# `am instrument` exits 0 even when tests fail: read its report.
adb shell am instrument -w \
  -e jellyfinUrl "${JELLYFIN_EMULATOR_URL:-http://10.0.2.2:8096}" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee instrument.log
adb exec-out screencap -p > emulator-end.png
if grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|Process crashed" instrument.log || ! grep -q "^OK (" instrument.log; then
  echo "--- CineDoury logcat ---"
  adb logcat -d -s CineDoury:V AndroidRuntime:E | tail -n 120 || true
  echo "::error::instrumented tests failed"
  exit 1
fi
