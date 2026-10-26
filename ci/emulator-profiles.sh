#!/usr/bin/env bash
# Generates the Baseline and Startup Profiles of the app on the running emulator (SPEC.md § 4.16),
# against the test Jellyfin: :benchmark (BaselineProfileGenerator) drives a non-minified release build
# through the start and the journeys of every day. The profiles land in
# app/tv/src/release/generated/baselineProfiles/, which the job commits on `[record-profiles]`.
# Usage: emulator-profiles.sh JELLYFIN_URL (as the emulator sees it: http://10.0.2.2:8096)
set -euo pipefail

url="${1:?usage: emulator-profiles.sh JELLYFIN_URL}"
set -o pipefail
./gradlew :app:tv:generateReleaseBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.jellyfinUrl="$url" 2>&1 | tee gradle.log
wc -l app/tv/src/release/generated/baselineProfiles/*.txt
