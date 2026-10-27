#!/usr/bin/env bash
# Generates the Baseline and Startup Profiles of the app on the running emulator (SPEC.md § 4.16),
# against the test Jellyfin: :benchmark (BaselineProfileGenerator) drives a non-minified release build
# through the start and the journeys of every day. The profiles land in
# app/tv/src/release/generated/baselineProfiles/, which the job commits on `[record-profiles]`.
# Usage: emulator-profiles.sh JELLYFIN_URL (as the emulator sees it: http://10.0.2.2:8096)
set -euo pipefail

url="${1:?usage: emulator-profiles.sh JELLYFIN_URL}"
set -o pipefail
if ! ./gradlew :app:tv:generateReleaseBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.jellyfinUrl="$url" 2>&1 | tee gradle.log; then
  # Gradle only points to its report: the failures of the journeys, with what the remote saw.
  find benchmark/build/outputs/androidTest-results -name 'TEST-*.xml' -exec \
    python3 -I -c 'import sys, xml.etree.ElementTree as E
for f in sys.argv[1:]:
    for case in E.parse(f).getroot().iter("testcase"):
        for failure in case.iter("failure"):
            print("=== " + case.get("name") + "\n" + (failure.text or "")[:6000])' {} + | tee -a gradle.log
  exit 1
fi
wc -l app/tv/src/release/generated/baselineProfiles/*.txt
# The emulator and the measures need the memory of the runner: no Gradle left running (twice, the
# runner got a shutdown signal while measuring with the daemons still up).
./gradlew --stop
