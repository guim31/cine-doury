#!/usr/bin/env bash
# A high bitrate played directly (R, SPEC.md § 4.29): the buffer of ExoPlayer sits on the Java heap,
# and the TV once asked for 192 MB, the whole heap of a Shield (OutOfMemoryError with a 4K film at
# 25 Mb/s). A 40 Mb/s film must reach its buffer cap (40 % of the heap), stay under it and play for a
# minute (app/tv/src/androidTest, HighBitrateOnDeviceTest).
#
# The film (« Déferlante », 75 s, 1080p, H.264 at a constant 40 Mb/s: a still colour that x264 pads
# with filler data, real bytes that cost the decoder nothing) is added to the test Jellyfin only on
# this job: the integration tests count the films of the catalogue.
#
# Usage: emulator-bitrate.sh SERVER_URL (as the host sees it) MEDIA_DIR JELLYFIN_URL (as the emulator sees it)
set -euo pipefail

server="${1:?usage: emulator-bitrate.sh SERVER_URL MEDIA_DIR JELLYFIN_URL}"
media="${2:?}"
url="${3:?}"
title="Déferlante"
auth='MediaBrowser Client="Cine Doury CI", Device="ci", DeviceId="cine-doury-ci-bitrate", Version="0.0.0"'

log() { printf '[bitrate] %s\n' "$*" >&2; }

dir="$media/films/$title (2026)"
if [ ! -f "$dir/$title (2026).mkv" ]; then
  log "MKV, H.264 at a constant 40 Mb/s, 1080p, AAC, 75 s"
  mkdir -p "$dir"
  ffmpeg -loglevel error -y -f lavfi -i "color=c=0x204060:size=1920x1080:rate=24:duration=75" \
    -f lavfi -i "sine=frequency=440:duration=75:sample_rate=48000" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -x264-params "nal-hrd=cbr:force-cfr=1" \
    -b:v 40M -minrate 40M -maxrate 40M -bufsize 8M -c:a aac -b:a 128k -metadata:s:a:0 language=fra \
    "$dir/$title (2026).mkv"
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<movie>\n  <title>%s</title>\n  <year>2026</year>\n</movie>\n' \
    "$title" > "$dir/movie.nfo"
fi
log "$(ffprobe -v error -show_entries format=bit_rate,size -of default=nw=1 "$dir/$title (2026).mkv" | tr '\n' ' ')"

token="$(curl -fsS -X POST "$server/Users/AuthenticateByName" -H "Authorization: $auth" \
  -H 'Content-Type: application/json' -d '{"Username":"admin","Pw":"cinedoury-test"}' | jq -r .AccessToken)"
api() { curl -fsS -X "$1" "$server$2" -H "Authorization: $auth, Token=\"$token\"" -H 'Content-Type: application/json' ${3:+--data "$3"}; }

api POST /Library/Refresh > /dev/null
found=0
for _ in $(seq 1 60); do
  found="$(api GET "/Items?recursive=true&includeItemTypes=Movie&searchTerm=ferlante" | jq '.TotalRecordCount')"
  [ "$found" -ge 1 ] && break
  sleep 3
done
log "« $title » in the library: $found"

# The debug APK and its instrumented tests (emulator-avi.sh installed them already; again, alone).
app="$(find apk -name '*.apk' | head -n 1)"
tests="$(find app/tv/build/outputs/apk/androidTest/debug -name '*.apk' | head -n 1)"
adb uninstall fr.guim.cinedoury > /dev/null 2>&1 || true
adb install -r "$app"
adb install -r -t "$tests"
log "heap of the emulator: $(adb shell getprop dalvik.vm.heapgrowthlimit) (growth limit), $(adb shell getprop dalvik.vm.heapsize) (largeHeap)"

adb logcat -c || true
set +e
# Bounded: a test stuck on the device must not hold the job.
timeout 600 adb shell am instrument -w -r -e jellyfinUrl "$url" \
  -e class fr.guim.cinedoury.tv.HighBitrateOnDeviceTest \
  fr.guim.cinedoury.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee emulator-bitrate.txt
set -e
adb logcat -d -s CineDoury:V AndroidRuntime:E HighBitrate:V > emulator-bitrate-logcat.txt || true
cat emulator-bitrate-logcat.txt | grep HighBitrate | tail -n 40 || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-bitrate.txt \
  || ! grep -q "OK (1 test)" emulator-bitrate.txt; then
  adb logcat -d -b crash || true
  adb logcat -d | grep -E "ExoPlayer|DefaultLoadControl|OutOfMemory|AndroidRuntime|cinedoury" | tail -n 120 || true
  echo "::error::the 40 Mb/s film did not play under its buffer cap on the emulator"
  exit 1
fi
