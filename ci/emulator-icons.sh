#!/usr/bin/env bash
# « Épisode précédent » as the Android of the emulator draws it (X, SPEC.md § 4.32): runs
# PlayerIconsOnDeviceTest (app/tv/src/androidTest) and pulls its pictures into emulator-icons/, for the
# artifact of the job. On API 30, the Android of the Shield. The debug APK and the test APK must be
# installed (ci/emulator-playback.sh does it).
set -euo pipefail

dir=pistes-icone-son
mkdir -p emulator-icons
set +e
timeout 300 adb shell am instrument -w -r \
  -e class fr.guim.cinedoury.tv.PlayerIconsOnDeviceTest \
  fr.guim.cinedoury.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-icons/emulator-icons.txt
set -e
# The pictures are in the files of the app (debuggable: run-as reads them).
for name in $(adb shell run-as fr.guim.cinedoury ls "files/$dir" | tr -d '\r'); do
  adb exec-out run-as fr.guim.cinedoury cat "files/$dir/$name" > "emulator-icons/$name"
done
adb logcat -d -s PlayerIcons | tail -n 20 | tee -a emulator-icons/emulator-icons.txt || true
ls -l emulator-icons
# The pictures in the log too, in Base64: a cloud session reads the log of a job but may not reach the
# storage of the artifacts (decode with ci/emulator-icons-decode.py).
for png in emulator-icons/*.png; do
  echo "ICON-PNG $(basename "$png") $(base64 -w0 "$png")"
done
if grep -q -E "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" emulator-icons/emulator-icons.txt \
  || ! grep -q "OK (2 tests)" emulator-icons/emulator-icons.txt; then
  echo "::error::the icons of the player on the emulator failed"
  exit 1
fi
