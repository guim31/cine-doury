#!/usr/bin/env bash
# Gives a few items of the test server generated artwork, through the API as the app will (J6):
# posters, backdrops and logos (a gradient and the title in Inter, never a real poster), and photos
# of the people. Called by bootstrap.sh once the library is scanned; rerunnable (an item that already
# has a logo is left alone).
#
# Usage: images.sh BASE_URL TOKEN
set -euo pipefail

BASE="$1"
TOKEN="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
FONT="$HERE/../../core/designsystem/src/main/res/font/inter_bold.ttf"
AUTH="MediaBrowser Client=\"Cine Doury CI\", Device=\"ci\", DeviceId=\"cine-doury-ci-bootstrap\", Version=\"0.0.0\", Token=\"$TOKEN\""
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FFMPEG=(ffmpeg -hide_banner -loglevel error -y)

log() { printf '[images] %s\n' "$*" >&2; }
api() { curl -fsS -X "$1" "$BASE$2" -H "Authorization: $AUTH" -H 'Content-Type: application/json'; }

# upload ID TYPE FILE: Jellyfin 12 takes the image base64-encoded in the body.
upload() {
  base64 -w0 "$3" | curl -fsS -X POST "$BASE/Items/$1/Images/$2" -H "Authorization: $AUTH" \
    -H 'Content-Type: image/png' --data-binary @-
}

# escape TEXT: a title for the drawtext filter.
escape() { sed -e "s/\\\\/\\\\\\\\/g" -e "s/'/\\\\\\\\\\\\'/g" -e 's/:/\\:/g' -e 's/,/\\,/g' <<< "$1"; }

# poster TITLE SEED FILE: 2:3, the title at the bottom left.
poster() {
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=600x900:seed=$2:nb_colors=3" \
    -vf "drawtext=fontfile=$FONT:text='$(escape "$1")':fontcolor=white:fontsize=64:x=48:y=h-th-72:line_spacing=8" \
    -frames:v 1 "$3"
}

# backdrop SEED FILE: 16:9, without text (the logo is drawn over it).
backdrop() {
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=1920x1080:seed=$1:nb_colors=4" -frames:v 1 "$2"
}

# logo TITLE FILE: the title on a transparent background, as a clearlogo, as wide as it can be.
logo() {
  local size=$((1800 / (${#1} + 1)))
  [ "$size" -gt 170 ] && size=170
  "${FFMPEG[@]}" -f lavfi -i "color=c=black@0.0:s=1000x240,format=rgba" \
    -vf "drawtext=fontfile=$FONT:text='$(escape "$1")':fontcolor=white:fontsize=$size:x=(w-tw)/2:y=(h-th)/2:shadowcolor=black@0.5:shadowx=4:shadowy=4" \
    -frames:v 1 "$2"
}

# photo NAME SEED FILE: 2:3, the initials.
photo() {
  local initials
  initials="$(awk '{ for (i = 1; i <= NF; i++) printf "%s", substr($i, 1, 1) }' <<< "$1")"
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=400x600:seed=$2:nb_colors=2" \
    -vf "drawtext=fontfile=$FONT:text='$initials':fontcolor=white@0.85:fontsize=180:x=(w-tw)/2:y=(h-th)/2" \
    -frames:v 1 "$3"
}

# item TYPE NAME: the id of the item of that type and name.
item() {
  api GET "/Items?Recursive=true&IncludeItemTypes=$1&CollapseBoxSetItems=false&SearchTerm=$(jq -rn --arg v "$2" '$v|@uri')" \
    | jq -r --arg n "$2" '.Items[] | select(.Name == $n) | .Id' | head -n 1
}

seed=1
# artwork TYPE NAME: poster, backdrop and logo of an item.
artwork() {
  local id
  id="$(item "$1" "$2")"
  [ -n "$id" ] || { log "no $1 « $2 »"; return; }
  seed=$((seed + 7))
  if [ "$(api GET "/Items/$id/Images" | jq '[.[] | select(.ImageType == "Logo")] | length')" != "0" ]; then return; fi
  log "$1 « $2 »"
  poster "$2" "$seed" "$WORK/poster.png" && upload "$id" Primary "$WORK/poster.png"
  backdrop "$((seed + 50))" "$WORK/backdrop.png" && upload "$id" Backdrop "$WORK/backdrop.png"
  logo "$2" "$WORK/logo.png" && upload "$id" Logo "$WORK/logo.png"
}

for film in "Le Phare" "Retour au phare" "Nuit polaire" "Double jeu" "Fréquences" "Zéphyr" "Rivages" "Givre"; do
  artwork Movie "$film"
done
for series in "Les Marées" "Une saison" "Brume"; do artwork Series "$series"; done

for person in "Camille Martin" "Louis Bernard" "Inès Garnier" "Hugo Lefèvre" "Jeanne Roux" "Marie Duval" "Paul Mercier"; do
  id="$(api GET "/Persons?searchTerm=$(jq -rn --arg v "$person" '$v|@uri')" | jq -r --arg n "$person" '.Items[] | select(.Name == $n) | .Id' | head -n 1)"
  [ -n "$id" ] || continue
  [ "$(api GET "/Items/$id/Images" | jq 'length')" = "0" ] || continue
  seed=$((seed + 3))
  log "photo of $person"
  photo "$person" "$seed" "$WORK/photo.png" && upload "$id" Primary "$WORK/photo.png"
done
