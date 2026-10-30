#!/usr/bin/env bash
# Names the release APK of an app, checks it (version, signature), writes its SHA-256 and the
# changelog into dist/<app>/ (SPEC.md § 4.18 and § 4.26). Env: VERSION (X.Y.Z or X.Y.Z-essai.N), TAG
# (vX.Y.Z…), SIGNED, ROLLBACK_OF (optional), APP (tv, the default, or mobile).
# The TV keeps the name of every release, cine-doury-X.Y.Z.apk; the phone's is cine-doury-mobile-X.Y.Z.apk
# (each app takes its own by name, AppTarget in :core:update).
set -euo pipefail

: "${VERSION:?}" "${TAG:?}"
app="${APP:-tv}"
out="app/$app/build/outputs/apk/release"
dist="dist/$app"
mkdir -p "$dist"
if [ "${SIGNED:-false}" = "true" ]; then
  apk="$out/$app-release.apk"
  [ -f "$apk" ] || apk="$( (ls "$out"/*-release.apk 2>/dev/null || true) | head -1)"
else
  apk="$( (ls "$out"/*-release-unsigned.apk "$out"/*-release.apk 2>/dev/null || true) | head -1)"
fi
[ -f "$apk" ] || { echo "::error::No release APK in $out"; ls -la "$out" || true; exit 1; }
if [ "$app" = "tv" ]; then name="cine-doury-$VERSION.apk"; else name="cine-doury-$app-$VERSION.apk"; fi
cp "$apk" "$dist/$name"

# The version Gradle wrote from the tag: versionName X.Y.Z (or X.Y.Z-essai.N, a trial build),
# versionCode X × 1000000 + Y × 10000 + Z × 100 + N (N = 0 for a release).
trial=0
case "$VERSION" in *-essai.*) trial="${VERSION##*-essai.}" ;; esac
IFS=. read -r major minor patch <<< "${VERSION%%-essai.*}"
code=$((10#$major * 1000000 + 10#$minor * 10000 + 10#$patch * 100 + 10#$trial))
tools="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
badging="$("$tools/aapt2" dump badging "$dist/$name" | sed -n 1p)"
echo "$badging"
grep -q "versionCode='$code'" <<< "$badging" || { echo "::error::versionCode is not $code"; exit 1; }
grep -q "versionName='$VERSION'" <<< "$badging" || { echo "::error::versionName is not $VERSION"; exit 1; }
# Two apps that may live side by side on a device: never the TV's package in the phone's APK.
if [ "$app" = "tv" ]; then package="fr.guim.cinedoury"; else package="fr.guim.cinedoury.$app"; fi
grep -q "package: name='$package'" <<< "$badging" || { echo "::error::package is not $package"; exit 1; }

if [ "${SIGNED:-false}" = "true" ]; then
  "$tools/apksigner" verify --print-certs "$dist/$name" | tee "$dist/certificate.txt"
  # Never the debug key of the repository.
  debug="$(keytool -list -v -keystore app/tv/debug.keystore -storepass android -alias androiddebugkey | grep 'SHA256:' | sed 's/.*SHA256: //; s/://g' | tr 'A-F' 'a-f')"
  if grep -qi "$debug" "$dist/certificate.txt"; then
    echo "::error::The release APK is signed with the debug key"
    exit 1
  fi
fi

(cd "$dist" && sha256sum "$name" > "$name.sha256" && cat "$name.sha256")

# The changelog: docs/releases/vX.Y.Z.md when the maintainer wrote one, else the commits since the
# previous version tag.
if [ -f "docs/releases/$TAG.md" ]; then
  cp "docs/releases/$TAG.md" "$dist/notes.md"
else
  {
    if [ -n "${ROLLBACK_OF:-}" ]; then
      echo "Retour à la version ${ROLLBACK_OF#v} : le code de cette version, publié sous un numéro plus haut pour qu'Android l'installe."
      echo
    fi
    previous="$(git describe --tags --abbrev=0 --match 'v*' HEAD^ 2>/dev/null || true)"
    echo "## Changements"
    echo
    if [ -n "$previous" ]; then
      git log --no-merges --format='- %s' "$previous..HEAD"
    else
      git log --no-merges --format='- %s' -n 30
    fi
  } > "$dist/notes.md"
fi
cat "$dist/notes.md"
