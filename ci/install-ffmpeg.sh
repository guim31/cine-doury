#!/usr/bin/env bash
# Installs ffmpeg on the runner (the test media and the PGS subtitles need it). The apt mirror of
# the runner sometimes hangs: each attempt is bounded, apt gives up a stalled download after 30 s,
# and a new attempt starts over, three at most.
set -uo pipefail

apt_options=(-q -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 -o Acquire::Retries=3)
for attempt in 1 2 3; do
  if timeout 300 sudo apt-get "${apt_options[@]}" update \
    && timeout 300 sudo apt-get "${apt_options[@]}" install -y ffmpeg; then
    exit 0
  fi
  echo "ffmpeg: attempt $attempt failed or hung, again" >&2
done
exit 1
