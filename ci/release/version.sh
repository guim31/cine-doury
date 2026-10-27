#!/usr/bin/env bash
# The version of a release (SPEC.md § 4.18): the tag `vX.Y.Z` pushed, the version asked for by hand,
# or a dry run's own (pull request). Writes `tag` and `version` to $GITHUB_OUTPUT.
set -euo pipefail

if [ "${DRY_RUN:-false}" = "true" ]; then
  tag="v0.0.1"
elif [ -n "${INPUT_VERSION:-}" ]; then
  tag="$INPUT_VERSION"
else
  tag="${GITHUB_REF_NAME:?no tag}"
fi

if ! [[ "$tag" =~ ^v[0-9]{1,4}\.[0-9]{1,2}\.[0-9]{1,2}$ ]]; then
  echo "::error::$tag is not a version tag vX.Y.Z (Y and Z below 100)"
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
