#!/usr/bin/env bash
# The false playback error (SPEC.md § 4.11, « fausse erreur de lecture »): an AVI with MPEG-4 Part 2
# (Xvid) video and MP3 audio, which the server transcodes to H.264 while copying the MP3 into fMP4
# segments (CODECS « mp4a.40.34 » in its playlist). It must start and play, without the error
# « No decoder for audio » (app/tv/src/androidTest, AviOnDeviceTest).
#
# The film (« Magnétoscope », 60 s, 640x480) is added to the test Jellyfin only on this job, after
# the other tests of the emulator: the integration tests count the films of the catalogue.
#
# Usage: emulator-avi.sh SERVER_URL (as the host sees it) MEDIA_DIR JELLYFIN_URL (as the emulator sees it)
set -euo pipefail

server="${1:?usage: emulator-avi.sh SERVER_URL MEDIA_DIR JELLYFIN_URL}"
media="${2:?}"
url="${3:?}"
title="Magnétoscope"
auth='MediaBrowser Client="Cine Doury CI", Device="ci", DeviceId="cine-doury-ci-avi", Version="0.0.0"'

log() { printf '[avi] %s\n' "$*" >&2; }

dir="$media/films/$title (2026)"
if [ ! -f "$dir/$title (2026).avi" ]; then
  log "AVI, MPEG-4 Part 2 (xvid) and MP3, 640x480, 60 s"
  mkdir -p "$dir"
  ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=640x480:rate=25:duration=60" \
    -f lavfi -i "sine=frequency=660:duration=60:sample_rate=44100" -c:v mpeg4 -vtag XVID -q:v 5 \
    -c:a libmp3lame -b:a 128k -ac 2 -metadata:s:a:0 language=fra "$dir/$title (2026).avi"
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<movie>\n  <title>%s</title>\n  <year>2026</year>\n</movie>\n' \
    "$title" > "$dir/movie.nfo"
fi

token="$(curl -fsS -X POST "$server/Users/AuthenticateByName" -H "Authorization: $auth" \
  -H 'Content-Type: application/json' -d '{"Username":"admin","Pw":"cinedoury-test"}' | jq -r .AccessToken)"
api() { curl -fsS -X "$1" "$server$2" -H "Authorization: $auth, Token=\"$token\"" -H 'Content-Type: application/json' ${3:+--data "$3"}; }

api POST /Library/Refresh > /dev/null
found=0
for _ in $(seq 1 60); do
  found="$(api GET "/Items?recursive=true&includeItemTypes=Movie&searchTerm=Magn" | jq '.TotalRecordCount')"
  [ "$found" -ge 1 ] && break
  sleep 3
done
log "« $title » in the library: $found"

adb logcat -c || true
set +e
# Bounded: a test stuck on the device must not hold the job.
timeout 600 adb shell am instrument -w -r -e jellyfinUrl "$url" \
  -e class fr.guim.cinedoury.tv.AviOnDeviceTest \
  fr.guim.cinedoury.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee emulator-avi.txt
set -e
adb logcat -d -s CineDoury:V AndroidRuntime:E > emulator-avi-logcat.txt || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-avi.txt \
  || ! grep -q "OK (1 test)" emulator-avi.txt; then
  adb logcat -d -b crash || true
  adb logcat -d | grep -E "ExoPlayer|MediaCodec|AndroidRuntime|cinedoury" | tail -n 120 || true
  echo "::error::the AVI (MPEG-4 and MP3) did not play on the emulator"
  exit 1
fi
