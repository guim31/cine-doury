#!/usr/bin/env bash
# The job « Baseline Profile and benchmarks »: on one emulator, the Baseline Profile, then the
# measures of the debug APK (what was installed before) and of the optimised one, each step run even
# if one before failed. Usage: emulator-perf.sh JELLYFIN_URL
set -uo pipefail

url="${1:?usage: emulator-perf.sh JELLYFIN_URL}"
failed=0
ci/emulator-profiles.sh "$url" || failed=1
ci/emulator-benchmarks.sh "$url" apk debug ignore || failed=1
ci/emulator-benchmarks.sh "$url" release-apk optimised profile || failed=1
exit "$failed"
