#!/usr/bin/env bash
# The version of a release (SPEC.md § 4.18): the tag `vX.Y.Z` pushed, the version asked for by hand,
# a trial build's `vX.Y.Z-essai.N` (TRIAL=true: X.Y.Z the last version tag, N the number asked for or
# the run number, from 1 to 99), or a dry run's own (pull request). Writes `tag` and `version` to
# $GITHUB_OUTPUT.
set -euo pipefail

if [ "${DRY_RUN:-false}" = "true" ]; then
  tag="v0.0.1"
elif [ "${TRIAL:-false}" = "true" ]; then
  last="$(git tag --list 'v*' --sort=-v:refname | grep -E '^v[0-9]{1,4}\.[0-9]{1,2}\.[0-9]{1,2}$' | head -1 || true)"
  [ -n "$last" ] || { echo "::error::No version tag vX.Y.Z to number a trial build after"; exit 1; }
  if [ -n "${INPUT_TRIAL_NUMBER:-}" ]; then
    number="$INPUT_TRIAL_NUMBER"
  else
    # 1 to 99: 0 is the release itself.
    number=$(( (${GITHUB_RUN_NUMBER:?no run number} - 1) % 99 + 1 ))
  fi
  if ! [[ "$number" =~ ^[0-9]{1,2}$ ]] || [ "$number" -lt 1 ]; then
    echo "::error::$number is not a trial number from 1 to 99"
    exit 1
  fi
  tag="$last-essai.$((10#$number))"
elif [ -n "${INPUT_VERSION:-}" ]; then
  tag="$INPUT_VERSION"
else
  tag="${GITHUB_REF_NAME:?no tag}"
fi

if ! [[ "$tag" =~ ^v[0-9]{1,4}\.[0-9]{1,2}\.[0-9]{1,2}(-essai\.[0-9]{1,2})?$ ]]; then
  echo "::error::$tag is not a version tag vX.Y.Z (Y and Z below 100), nor a trial vX.Y.Z-essai.N"
  exit 1
fi
if [ "${TRIAL:-false}" != "true" ] && [[ "$tag" == *-essai.* ]]; then
  echo "::error::$tag is a trial version: a trial build is asked for with trial=true, and never published"
  exit 1
fi

# A version published by hand must not exist yet (a rollback is a new, higher version).
if [ "${DRY_RUN:-false}" != "true" ] && [ -n "${INPUT_VERSION:-}" ] && git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
  if [ -n "${INPUT_SOURCE:-}" ]; then
    echo "::error::$tag exists already: a rollback takes a new version, higher than the last one"
    exit 1
  fi
fi

echo "tag=$tag" >> "${GITHUB_OUTPUT:-/dev/stdout}"
echo "version=${tag#v}" >> "${GITHUB_OUTPUT:-/dev/stdout}"
