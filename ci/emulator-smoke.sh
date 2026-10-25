#!/usr/bin/env bash
# Installs the debug APK on the running emulator, starts it and checks that its activity is in
# front. Usage: emulator-smoke.sh APK_DIR
set -euo pipefail

apk="$(find "${1:?usage: emulator-smoke.sh APK_DIR}" -name '*.apk' | head -n 1)"
package="fr.guim.cinedoury"
activity="$package/fr.guim.cinedoury.tv.MainActivity"

adb install -r "$apk"
adb shell am start -W -n "$activity"
sleep 10
adb exec-out screencap -p > emulator-start.png
if adb shell dumpsys activity activities | grep -E 'ResumedActivity' | grep -q "$package"; then
  echo "$activity is resumed"
else
  # The crash buffer holds the stack trace of an app that died at start.
  adb logcat -d -b crash || true
  adb logcat -d | grep -E "AndroidRuntime|FATAL|$package" | tail -n 80 || true
  echo "::error::$activity is not in front"
  exit 1
fi
