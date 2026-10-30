#!/usr/bin/env bash
# The real journey of the phone app on the phone emulator of the full CI (M1, SPEC.md § 4.26):
# app/mobile/src/androidTest, PhoneJourneyTest, from the first launch to a few seconds of playback.
# Usage: emulator-phone.sh APK_DIR TEST_APK_DIR JELLYFIN_URL (as the emulator sees it)
set -euo pipefail

apk_dir="${1:?usage: emulator-phone.sh APK_DIR TEST_APK_DIR JELLYFIN_URL}"
test_dir="${2:?}"
url="${3:?}"
package="fr.guim.cinedoury.mobile"

adb install -r "$(ls "$apk_dir"/*.apk | head -1)"
adb install -r "$(ls "$test_dir"/*.apk | head -1)"
adb shell pm clear "$package" > /dev/null
adb logcat -c || true
set +e
# Bounded: a test stuck on the device must not hold the job until its own timeout.
timeout 900 adb shell am instrument -w -r -e jellyfinUrl "$url" \
  -e class "$package.PhoneJourneyTest" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee emulator-phone.txt
set -e
adb exec-out screencap -p > emulator-phone.png || true
adb logcat -d -s CineDoury:V AndroidRuntime:E > emulator-phone-logcat.txt || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-phone.txt \
  || ! grep -q "OK (1 test)" emulator-phone.txt; then
  adb logcat -d -b crash || true
  tail -n 120 emulator-phone-logcat.txt || true
  echo "::error::the journey of the phone app on the emulator failed"
  exit 1
fi
