#!/usr/bin/env bash
# Plays for real on the running emulator (app/tv/src/androidTest, PlaybackOnDeviceTest): an H.264
# film played directly and an HEVC HDR10 film transcoded, from the test Jellyfin.
# Usage: emulator-playback.sh JELLYFIN_URL (as the emulator sees it: http://10.0.2.2:8096)
set -euo pipefail

url="${1:?usage: emulator-playback.sh JELLYFIN_URL}"
app="$(find apk -name '*.apk' | head -n 1)"
tests="$(find app/tv/build/outputs/apk/androidTest/debug -name '*.apk' | head -n 1)"

adb install -r "$app"
adb install -r -t "$tests"
adb logcat -c || true
set +e
adb shell am instrument -w -r -e jellyfinUrl "$url" \
  -e class fr.guim.cinedoury.tv.PlaybackOnDeviceTest \
  fr.guim.cinedoury.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-playback.txt
set -e
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-playback.txt \
  || ! grep -q "OK (2 tests)" emulator-playback.txt; then
  adb logcat -d -b crash || true
  adb logcat -d | grep -E "ExoPlayer|MediaCodec|AndroidRuntime|cinedoury" | tail -n 120 || true
  echo "::error::playback on the emulator failed"
  exit 1
fi
