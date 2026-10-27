#!/usr/bin/env bash
# The 20 gestures of docs/GESTES.md, played and timed on a device where Ciné Doury is signed in, on
# any server (it assumes no title): see ci/couch-gestures.py. Needs adb and python3 on this machine.
# Usage: couch-gestures.sh ADB_SERIAL [--out FILE.md] [--only 1,2,3]
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: couch-gestures.sh ADB_SERIAL [--out FILE.md] [--only 1,2,3]" >&2
  echo "devices:" >&2
  adb devices -l >&2 || true
  exit 2
fi
exec python3 -I "$(dirname "$0")/couch-gestures.py" "$@"
