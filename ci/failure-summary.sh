#!/usr/bin/env bash
# Prints the lines of a Gradle log that say why the build failed (compiler errors, failed tests,
# lint, detekt and Animal Sniffer findings), so that they sit at the end of the CI log, where they
# are read.
# Usage: failure-summary.sh GRADLE_LOG
set -uo pipefail

log="${1:?usage: failure-summary.sh GRADLE_LOG}"
[ -f "$log" ] || { echo "no $log"; exit 0; }
echo "===== failure summary ====="
grep -E '^e: |FAILED|What went wrong|^> |: Error: |Error:|error:|\.kt:[0-9]+:[0-9]+|Exception|expected:|AssertionError|Unresolved|^\s+at fr\.guim|^\[Undefined reference\]|^  >> ' "$log" \
  | awk '/^> Task / && !/FAILED/ { next } !seen[$0]++' \
  | head -120
# Gradle prints only the first lint error: the text reports hold all of them.
for report in $(find . -path '*/build/*' -name 'lint-results-*.txt' 2> /dev/null); do
  grep -E ': Error: ' "$report" | sed "s|^|[lint] |" | head -40
done
echo "===== end of summary ====="
