#!/usr/bin/env bash
# Starts the test Jellyfin locally with Docker, the same way the CI does: generated media,
# then bootstrap. Usage: run-local.sh [up|down]. The server listens on http://localhost:8096.
set -euo pipefail

IMAGE="jellyfin/jellyfin:12.3.20261023-013714"
NAME="cine-doury-jellyfin"
MEDIA="${JF_MEDIA_DIR:-${TMPDIR:-/tmp}/cine-doury-media}"
HERE="$(cd "$(dirname "$0")" && pwd)"

case "${1:-up}" in
  up)
    docker rm -f "$NAME" > /dev/null 2>&1 || true
    rm -rf "$MEDIA"
    "$HERE/generate-media.sh" "$MEDIA" > /dev/null
    docker run -d --name "$NAME" -p 8096:8096 -v "$MEDIA:/media" "$IMAGE" > /dev/null
    "$HERE/bootstrap.sh" http://localhost:8096
    ;;
  down)
    docker rm -f "$NAME" > /dev/null 2>&1 || true
    ;;
  *)
    echo "usage: run-local.sh [up|down]" >&2
    exit 2
    ;;
esac
