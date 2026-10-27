#!/usr/bin/env bash
# The job « Baseline Profile and benchmarks »: on one emulator, the Baseline Profile, then the
# measures of the optimised APK now, and of the optimised APK before the change, when built
# (BEFORE_REF). Each step runs even if one before failed. Usage: emulator-perf.sh JELLYFIN_URL
set -uo pipefail

url="${1:?usage: emulator-perf.sh JELLYFIN_URL}"
failed=0
ci/emulator-profiles.sh "$url" || failed=1
# The app now first: its measure is the one that matters.
ci/emulator-benchmarks.sh "$url" release-apk optimised profile || failed=1
# The optimised APK before (BEFORE_REF): the same build, so that the columns compare the code. The
# debug APK before is no longer measured: its rail failed now and then (« No entry Films »), and the
# debug build was only the starting point of perf/fluidite (SPEC.md § 4.16).
if [ -d before-release-apk ]; then
  ci/emulator-benchmarks.sh "$url" before-release-apk before-optimised profile || failed=1
fi
exit "$failed"
