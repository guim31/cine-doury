#!/usr/bin/env bash
# The job « Baseline Profile and benchmarks »: on one emulator, the Baseline Profile, then the
# measures of the optimised APK now, and of the app before the change (its debug APK, as installed
# until now, and its optimised APK, when built: BEFORE_REF). Each step runs even if one before
# failed. Usage: emulator-perf.sh JELLYFIN_URL
set -uo pipefail

url="${1:?usage: emulator-perf.sh JELLYFIN_URL}"
failed=0
ci/emulator-profiles.sh "$url" || failed=1
# The app now first: its measure is the one that matters.
ci/emulator-benchmarks.sh "$url" release-apk optimised profile || failed=1
if [ -d before-debug-apk ]; then
  ci/emulator-benchmarks.sh "$url" before-debug-apk before-debug ignore || failed=1
  ci/emulator-benchmarks.sh "$url" before-release-apk before-optimised profile || failed=1
fi
exit "$failed"
