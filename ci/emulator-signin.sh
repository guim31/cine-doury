#!/usr/bin/env bash
# Signs in on the running emulator with the remote, as on the Chromecast of the real-device trial
# (app/tv/src/androidTest: KeystoreTokenCipherTest, SignInJourneyTest): the real Keystore, then from
# the first launch to the home of the test Jellyfin and the player. Run after emulator-playback.sh,
# which installs both APKs. Usage: emulator-signin.sh JELLYFIN_URL (as the emulator sees it)
set -euo pipefail

url="${1:?usage: emulator-signin.sh JELLYFIN_URL}"
package="fr.guim.cinedoury"

# A first launch: no server, no profile.
adb shell pm clear "$package" > /dev/null
adb logcat -c || true
set +e
adb shell am instrument -w -r -e jellyfinUrl "$url" \
  -e class "$package.tv.KeystoreTokenCipherTest,$package.tv.SignInJourneyTest" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee emulator-signin.txt
set -e
adb exec-out screencap -p > emulator-signin.png || true
# Every failure the app caught, with its stack (FailureLog).
adb logcat -d -s CineDoury:V AndroidRuntime:E > emulator-logcat.txt || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-signin.txt \
  || ! grep -q "OK (5 tests)" emulator-signin.txt; then
  adb logcat -d -b crash || true
  tail -n 120 emulator-logcat.txt || true
  echo "::error::the sign-in with the remote on the emulator failed"
  exit 1
fi
