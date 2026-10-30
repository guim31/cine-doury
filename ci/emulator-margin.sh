#!/usr/bin/env bash
# The margin of the phone on the phone emulator of the full CI (M2, SPEC.md § 4.27), and G (a copied
# H.264 with B-frames, in TS segments: RemuxOnDeviceTest):
# app/mobile/src/androidTest, MarginOnDeviceTest. A film of 20 min with a moving pattern (« Autoroute »,
# only on this job: the integration tests count the films of the catalogue) is added to the test
# Jellyfin, whose throttling is turned on at its default (ThrottleDelaySeconds = 180); the test then
# builds a margin of more than 4 min, cuts the network for 2 min 10 s, and checks that the film goes on
# and that the margin grows again. The margin, second by second, goes to emulator-margin.txt.
#
# Usage: emulator-margin.sh SERVER_URL (as the host sees it) MEDIA_DIR JELLYFIN_URL (as the emulator sees it)
set -euo pipefail

server="${1:?usage: emulator-margin.sh SERVER_URL MEDIA_DIR JELLYFIN_URL}"
media="${2:?}"
url="${3:?}"
package="fr.guim.cinedoury.mobile"
title="Autoroute"
auth='MediaBrowser Client="Cine Doury CI", Device="ci", DeviceId="cine-doury-ci-margin", Version="0.0.0"'

log() { printf '[margin] %s\n' "$*" >&2; }

dir="$media/films/$title (2026)"
if [ ! -f "$dir/$title (2026).mkv" ]; then
  log "film of 20 min, 640x360, 24 fps"
  mkdir -p "$dir"
  ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=24:duration=1200" \
    -f lavfi -i "sine=frequency=440:duration=1200" -c:v libx264 -preset ultrafast -b:v 1500k -g 48 \
    -pix_fmt yuv420p -c:a aac -b:a 96k -metadata:s:a:0 language=fra "$dir/$title (2026).mkv"
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<movie>\n  <title>%s</title>\n  <year>2026</year>\n</movie>\n' \
    "$title" > "$dir/movie.nfo"
fi

# G (M2): « Bretelle », H.264 with B-frames and DTS, which the phone does not decode: the server copies
# the video and encodes the audio (RemuxOnDeviceTest).
remux="Bretelle"
rdir="$media/films/$remux (2026)"
if [ ! -f "$rdir/$remux (2026).mkv" ]; then
  log "H.264 with B-frames and DTS, 60 s"
  mkdir -p "$rdir"
  ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=60" \
    -f lavfi -i "sine=frequency=550:duration=60:sample_rate=48000" -c:v libx264 -preset veryfast -bf 2 -g 48 \
    -pix_fmt yuv420p -c:a dca -strict -2 -ac 2 -metadata:s:a:0 language=fra "$rdir/$remux (2026).mkv"
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<movie>\n  <title>%s</title>\n  <year>2026</year>\n</movie>\n' \
    "$remux" > "$rdir/movie.nfo"
fi

token="$(curl -fsS -X POST "$server/Users/AuthenticateByName" -H "Authorization: $auth" \
  -H 'Content-Type: application/json' -d '{"Username":"admin","Pw":"cinedoury-test"}' | jq -r .AccessToken)"
api() { curl -fsS -X "$1" "$server$2" -H "Authorization: $auth, Token=\"$token\"" -H 'Content-Type: application/json' ${3:+--data "$3"}; }

# Throttling on, at its default: the transcoder pauses 180 s ahead of the last segment asked for. The
# margin asks for the segments ahead, so it is not held at 3 min (measured, SPEC.md § 4.27).
encoding="$(api GET /System/Configuration/encoding | jq -c '.EnableThrottling = true | .ThrottleDelaySeconds = 180')"
api POST /System/Configuration/encoding "$encoding"
log "throttling: $(api GET /System/Configuration/encoding | jq -c '{EnableThrottling, ThrottleDelaySeconds}')"

api POST /Library/Refresh > /dev/null
for _ in $(seq 1 60); do
  found="$(api GET "/Items?recursive=true&includeItemTypes=Movie&searchTerm=$title" | jq '.TotalRecordCount')"
  other="$(api GET "/Items?recursive=true&includeItemTypes=Movie&searchTerm=$remux" | jq '.TotalRecordCount')"
  [ "$found" -ge 1 ] && [ "$other" -ge 1 ] && break
  sleep 3
done
log "« $title » in the library: $found"

adb logcat -c || true
set +e
# Bounded: two passes of about 4 min each, and a test stuck on the device must not hold the job.
timeout 1200 adb shell am instrument -w -r -e jellyfinUrl "$url" -e marginFilm "$title" -e remuxFilm "$remux" \
  -e class "$package.MarginOnDeviceTest,$package.RemuxOnDeviceTest" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee emulator-margin-run.txt
set -e
adb shell cmd connectivity airplane-mode disable || true
adb logcat -d -s CineDoury:V AndroidRuntime:E > emulator-margin-logcat.txt || true
grep -E "margin |remux " emulator-margin-logcat.txt | sed 's/^.*CineDoury[^:]*: //' > emulator-margin.txt || true
{
  echo "### Margin on the phone emulator (M2)"
  echo
  echo '```'
  grep -E "phase=(build|offline|back)" emulator-margin.txt | awk 'NR % 15 == 1' | head -80
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}" || true
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-margin-run.txt \
  || ! grep -q "OK (3 tests)" emulator-margin-run.txt; then
  adb logcat -d -b crash || true
  tail -n 60 emulator-margin.txt || true
  echo "::error::the margin of the phone on the emulator failed"
  exit 1
fi
