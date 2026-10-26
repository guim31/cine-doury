#!/usr/bin/env bash
# The optimised APK on the running emulator (SPEC.md § 4.16), driven from outside by :benchmark
# (OptimisedApkTest): installed over the debug APK that emulator-signin.sh left signed in, it opens on
# the home without signing in again; then, from a first launch, it signs in and plays a film. R8
# breaks what it breaks at run time only: this is where it shows.
# Usage: emulator-release.sh JELLYFIN_URL (as the emulator sees it: http://10.0.2.2:8096)
set -euo pipefail

url="${1:?usage: emulator-release.sh JELLYFIN_URL}"
package="fr.guim.cinedoury"
release="$(find release-apk -name '*.apk' | head -n 1)"
tests="$(find benchmark-apk -name '*.apk' | head -n 1)"

# Over the debug APK: same key, so no uninstall and the data stays.
adb install -r "$release"
adb install -r -t "$tests"
if adb shell dumpsys package "$package" | grep -q "DEBUGGABLE"; then
  echo "::error::the optimised APK is debuggable"
  exit 1
fi
: > emulator-release.txt
run() {
  set +e
  # Bounded: a test stuck on the device must not hold the job until its own timeout.
  timeout 600 adb shell am instrument -w -r -e jellyfinUrl "$url" \
    -e class "fr.guim.cinedoury.benchmark.OptimisedApkTest#$1" \
    "$package.benchmark/androidx.test.runner.AndroidJUnitRunner" | tee -a emulator-release.txt
  set -e
}
adb logcat -c || true
run keepsTheSessionOfTheDebugApk
# A first launch: no server, no profile.
adb shell pm clear "$package" > /dev/null
run signsInAndPlays
adb exec-out screencap -p > emulator-release.png || true
adb logcat -d -s CineDoury:V AndroidRuntime:E > emulator-release-logcat.txt || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-release.txt \
  || [ "$(grep -c "OK (1 test)" emulator-release.txt)" -ne 2 ]; then
  adb logcat -d -b crash || true
  tail -n 120 emulator-release-logcat.txt || true
  echo "::error::the optimised APK failed on the emulator"
  exit 1
fi
