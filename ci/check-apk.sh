#!/usr/bin/env bash
# Checks what an APK holds for the devices of the house (SPEC.md § 4.20): every native library of a
# 64-bit ABI also exists for armeabi-v7a (the Chromecast with Google TV runs a 32-bit Android), and
# prints the size of the APK and of its parts, for SPEC.md and the summary of the job.
# Usage: check-apk.sh APK
set -euo pipefail

apk="${1:?usage: check-apk.sh APK}"
listing="$(unzip -Z1 "$apk")"
size="$(stat -c %s "$apk")"

missing=0
for lib in $(grep -E '^lib/arm64-v8a/.+\.so$' <<< "$listing" | sed 's|^lib/arm64-v8a/||'); do
  if ! grep -qxF "lib/armeabi-v7a/$lib" <<< "$listing"; then
    echo "::error::$lib exists for arm64-v8a but not for armeabi-v7a (the Chromecast)"
    missing=1
  fi
done

{
  echo
  echo "### APK: $(basename "$apk")"
  echo
  echo "| Part | Size |"
  echo "|---|---|"
  echo "| Whole APK | $(awk -v s="$size" 'BEGIN { printf "%.1f MiB", s / 1048576 }') ($size bytes) |"
  unzip -lv "$apk" | awk '
    $NF ~ /^classes[0-9]*\.dex$/ { dex += $1 }
    $NF == "resources.arsc" || $NF ~ /^res\// { res += $1 }
    $NF ~ /^lib\// { lib += $1 }
    $NF ~ /^assets\// { assets += $1 }
    END {
      printf "| Code (dex, uncompressed) | %.1f MiB |\n", dex / 1048576
      printf "| Resources (uncompressed) | %.1f MiB |\n", res / 1048576
      printf "| Native libraries, all ABIs (uncompressed) | %.0f KiB |\n", lib / 1024
      printf "| Assets (uncompressed) | %.0f KiB |\n", assets / 1024
    }'
  echo
  echo "Native libraries by ABI:"
  echo
  for abi in $(grep -oE '^lib/[^/]+/' <<< "$listing" | sort -u | cut -d/ -f2); do
    echo "- $abi: $(grep -E "^lib/$abi/" <<< "$listing" | sed "s|^lib/$abi/||" | tr '\n' ' ')"
  done
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit "$missing"
