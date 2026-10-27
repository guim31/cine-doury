#!/usr/bin/env bash
# Measures the app on the running emulator with Macrobenchmark (:benchmark, NavigationBenchmarks):
# a cold start to the home, the rows of the home, a library and back, a fast scroll of a grid.
# The emulator is no Shield: compare its numbers with those of the same job (SPEC.md § 4.16).
# Usage: emulator-benchmarks.sh JELLYFIN_URL APK_DIR LABEL COMPILATION
#   COMPILATION: profile (the Baseline Profile of the APK), none, or ignore (the debug APK).
set -euo pipefail

url="${1:?usage: emulator-benchmarks.sh JELLYFIN_URL APK_DIR LABEL COMPILATION}"
apk="$(find "${2:?}" -name '*.apk' | head -n 1)"
label="${3:?}"
compilation="${4:?}"
package="fr.guim.cinedoury"
runner="$package.benchmark/androidx.test.runner.AndroidJUnitRunner"
tests="$(find benchmark-apk -name '*.apk' | head -n 1)"
out="benchmark-$label"
mkdir -p "$out"

# A clean install, signed in once by the benchmark itself.
adb uninstall "$package" > /dev/null 2>&1 || true
adb install "$apk"
adb install -r -t "$tests"
suppress="EMULATOR,LOW-BATTERY"
[ "$compilation" = "ignore" ] && suppress="$suppress,DEBUGGABLE"
adb shell rm -rf "/sdcard/Android/media/$package.benchmark" || true
set +e
timeout 2400 adb shell am instrument -w -r -e jellyfinUrl "$url" -e compilation "$compilation" \
  -e androidx.benchmark.suppressErrors "$suppress" \
  -e androidx.benchmark.enabledRules Macrobenchmark \
  -e class "$package.benchmark.NavigationBenchmarks" "$runner" | tee "$out/instrumentation.txt"
set -e
adb pull "/sdcard/Android/media/$package.benchmark" "$out/" > /dev/null 2>&1 || true
python3 ci/benchmark-summary.py "$label" "$out" | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$out/instrumentation.txt" \
  || ! grep -q "OK (4 tests)" "$out/instrumentation.txt"; then
  adb logcat -d -b crash || true
  echo "::error::the benchmarks of $label failed"
  exit 1
fi
