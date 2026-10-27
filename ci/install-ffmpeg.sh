#!/usr/bin/env bash
# Installs ffmpeg on the runner (the test media and the PGS subtitles need it). The apt mirror of
# the runner is sometimes very slow (8 MB in 5 min on 27/10, up to 36 min for the install): no
# bound on the whole, which would only start over, but apt gives up a connection silent for 30 s
# and tries again, and an attempt that fails starts over with what it already downloaded.
set -uo pipefail

apt_options=(-q -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 -o Acquire::Retries=5)
for attempt in 1 2 3; do
  if sudo apt-get "${apt_options[@]}" update \
    && sudo apt-get "${apt_options[@]}" install -y --no-install-recommends ffmpeg; then
    exit 0
  fi
  echo "ffmpeg: attempt $attempt failed, again" >&2
done
exit 1
