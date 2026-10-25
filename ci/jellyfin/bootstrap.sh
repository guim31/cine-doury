#!/usr/bin/env bash
# Bootstraps a fresh Jellyfin 12.3 for the tests: startup wizard, users (an administrator and a
# child profile), the libraries over the media of generate-media.sh, a library scan and a
# collection. Only the public API is used, as the app does.
#
# Usage: bootstrap.sh [BASE_URL]   (default http://localhost:8096)
# The media must be visible in the container under $JF_MEDIA_ROOT (default /media).
# These credentials only exist on a throwaway test server; they are not secrets.
set -euo pipefail

BASE="${1:-${JELLYFIN_URL:-http://localhost:8096}}"
MEDIA_ROOT="${JF_MEDIA_ROOT:-/media}"
ADMIN_USER="${JELLYFIN_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${JELLYFIN_ADMIN_PASSWORD:-cinedoury-test}"
CHILD_USER="${JELLYFIN_CHILD_USER:-enfant}"
AUTH='MediaBrowser Client="Cine Doury CI", Device="ci", DeviceId="cine-doury-ci-bootstrap", Version="0.0.0"'

log() { printf '[bootstrap] %s\n' "$*" >&2; }

# api METHOD PATH [JSON_BODY]
api() {
  local header="$AUTH"
  [ -n "${TOKEN:-}" ] && header="$AUTH, Token=\"$TOKEN\""
  curl -fsS -X "$1" "$BASE$2" -H "Authorization: $header" -H 'Content-Type: application/json' \
    ${3:+--data "$3"}
}

log "waiting for $BASE"
# The server answers /System/Info/Public before it is ready (no version yet, then 503 on the
# startup wizard): wait for a version.
for _ in $(seq 1 90); do
  version="$(curl -fsS "$BASE/System/Info/Public" 2> /dev/null | jq -r '.Version // empty' 2> /dev/null || true)"
  [ -n "$version" ] && break
  sleep 2
done
info="$(curl -fsS "$BASE/System/Info/Public")"
log "server $(jq -r .Version <<< "$info")"

if [ "$(jq -r .StartupWizardCompleted <<< "$info")" != "true" ]; then
  log "startup wizard"
  api POST /Startup/Configuration \
    '{"ServerName":"cine-doury-ci","UICulture":"fr","MetadataCountryCode":"FR","PreferredMetadataLanguage":"fr"}'
  api GET /Startup/User > /dev/null # creates the initial user record
  api POST /Startup/User "$(jq -nc --arg n "$ADMIN_USER" --arg p "$ADMIN_PASSWORD" '{Name:$n,Password:$p}')"
  api POST /Startup/RemoteAccess '{"EnableRemoteAccess":true}'
  api POST /Startup/Complete
fi

TOKEN="$(api POST /Users/AuthenticateByName \
  "$(jq -nc --arg n "$ADMIN_USER" --arg p "$ADMIN_PASSWORD" '{Username:$n,Pw:$p}')" | jq -r .AccessToken)"
log "authenticated as $ADMIN_USER"

# library NAME COLLECTION_TYPE FOLDER. Internet providers are off: the scan stays offline and
# deterministic, and no outside artwork enters the test server.
library() {
  if api GET /Library/VirtualFolders | jq -e --arg n "$1" 'any(.[]; .Name == $n)' > /dev/null; then
    return
  fi
  log "library $1"
  local options
  options="$(jq -nc --arg p "$MEDIA_ROOT/$3" '{LibraryOptions:{
    Enabled:true, EnableInternetProviders:false, EnableRealtimeMonitor:false,
    EnableTrickplayImageExtraction:false, ExtractTrickplayImagesDuringLibraryScan:false,
    EnableChapterImageExtraction:false, ExtractChapterImagesDuringLibraryScan:false,
    AutomaticallyAddToCollection:false, PreferredMetadataLanguage:"fr", MetadataCountryCode:"FR",
    PathInfos:[{Path:$p}]}}')"
  api POST "/Library/VirtualFolders?name=$(jq -rn --arg v "$1" '$v|@uri')&collectionType=$2&refreshLibrary=false" "$options"
}
library "Films" movies films
library "Séries" tvshows series
library "Spectacles" movies spectacles

# generate-media.sh: 45 films and 2 shows (« Spectacles »), 11 episodes.
EXPECTED_MOVIES=47
EXPECTED_EPISODES=11
log "scanning"
api POST /Library/Refresh
# The scan runs as a scheduled task; wait until every expected item is indexed.
count() {
  api GET "/Items?Recursive=true&IncludeItemTypes=$1&CollapseBoxSetItems=false&Limit=0" | jq -r .TotalRecordCount
}
for _ in $(seq 1 120); do
  movies="$(count Movie)"
  episodes="$(count Episode)"
  running="$(api GET /ScheduledTasks | jq -r '[.[] | select(.Key == "RefreshLibrary") | .State] | first')"
  [ "$movies" -ge "$EXPECTED_MOVIES" ] && [ "$episodes" -ge "$EXPECTED_EPISODES" ] && [ "$running" = "Idle" ] && break
  sleep 2
done
log "indexed $movies films and $episodes episodes"
[ "$movies" -ge "$EXPECTED_MOVIES" ] && [ "$episodes" -ge "$EXPECTED_EPISODES" ] || { log "scan incomplete"; exit 1; }

if ! api GET /Users | jq -e --arg n "$CHILD_USER" 'any(.[]; .Name == $n)' > /dev/null; then
  log "child profile $CHILD_USER"
  child="$(api POST /Users/New "$(jq -nc --arg n "$CHILD_USER" '{Name:$n,Password:""}')")"
  child_id="$(jq -r .Id <<< "$child")"
  policy="$(jq -c '.Policy
    | .IsAdministrator = false | .EnableContentDeletion = false
    | .EnableCollectionManagement = false | .EnableSubtitleManagement = false
    | .MaxParentalRating = 10' <<< "$child")"
  api POST "/Users/$child_id/Policy" "$policy"
fi

# Public users (the profile picker lists them): Jellyfin 12 hides new users by default.
for id in $(api GET /Users | jq -r '.[] | select(.Policy.IsHidden) | .Id'); do
  log "user $id made public"
  api POST "/Users/$id/Policy" "$(api GET "/Users/$id" | jq -c '.Policy | .IsHidden = false')"
done

# A picture for the administrator (the child profile keeps the initial on a coloured disc).
admin_id="$(api GET /Users | jq -r --arg n "$ADMIN_USER" '.[] | select(.Name == $n) | .Id')"
if [ "$(api GET "/Users/$admin_id" | jq -r '.PrimaryImageTag // empty')" = "" ] && command -v ffmpeg > /dev/null; then
  log "picture of $ADMIN_USER"
  avatar="$(mktemp --suffix=.png)"
  ffmpeg -loglevel error -y -f lavfi -i "gradients=s=256x256:c0=0x2e6f8e:c1=0xf2c14e:seed=7" -frames:v 1 "$avatar"
  # Jellyfin 12 takes the image base64-encoded in the body.
  base64 -w0 "$avatar" | curl -fsS -X POST "$BASE/UserImage?userId=$admin_id" \
    -H "Authorization: $AUTH, Token=\"$TOKEN\"" -H 'Content-Type: image/png' --data-binary @-
  rm -f "$avatar"
fi

# id_of TYPE NAME: the id of the item of that type and name.
id_of() {
  api GET "/Items?Recursive=true&IncludeItemTypes=$1&CollapseBoxSetItems=false" \
    | jq -r --arg n "$2" '.Items[] | select(.Name == $n) | .Id'
}

# collection NAME FILM...: a collection (TMDb Box Sets does this on a real server).
collection() {
  local name="$1"
  shift
  [ -n "$(id_of BoxSet "$name")" ] && return
  log "collection $name"
  local ids=()
  for film in "$@"; do ids+=("$(id_of Movie "$film")"); done
  api POST "/Collections?name=$(jq -rn --arg v "$name" '$v|@uri')&ids=$(IFS=,; echo "${ids[*]}")" > /dev/null
}
collection "Le Phare : la saga" "Le Phare" "Retour au phare"
collection "Les Étoiles filantes : la trilogie" "Les Étoiles filantes" "Les Étoiles filantes 2" "Les Étoiles filantes 3"

# Home preferences of the administrator (J2): libraries in the order Séries, Films, Spectacles (not
# the alphabetical one), and « Spectacles » excluded from the home and from « Ajouts récents ».
library_id() { api GET "/Library/VirtualFolders" | jq -r --arg n "$1" '.[] | select(.Name == $n) | .ItemId'; }
films_id="$(library_id Films)"
series_id="$(library_id Séries)"
shows_id="$(library_id Spectacles)"
config="$(api GET "/Users/$admin_id" | jq -c --arg f "$films_id" --arg s "$series_id" --arg x "$shows_id" \
  '.Configuration | .OrderedViews = [$s, $f, $x] | .MyMediaExcludes = [$x] | .LatestItemsExcludes = [$x]')"
api POST "/Users/Configuration?userId=$admin_id" "$config"

# What the administrator watched (J2), set through the API as a player would: « Reprendre » gets a
# film and an episode, « À suivre » gets « Une saison » (recent) but not « Brume » (two months ago).
# ago DAYS: an ISO date DAYS days ago.
ago() { date -u -d "$1 days ago" +%Y-%m-%dT%H:%M:%S.0000000Z; }
episode_id() {
  api GET "/Items?Recursive=true&IncludeItemTypes=Episode" | jq -r --arg n "$1" --argjson s "$2" --argjson e "$3" \
    '.Items[] | select(.SeriesName == $n and .ParentIndexNumber == $s and .IndexNumber == $e) | .Id'
}
# user_data ID JSON
user_data() { api POST "/UserItems/$1/UserData?userId=$admin_id" "$2" > /dev/null; }
# watched ID DAYS_AGO [PLAY_COUNT]
watched() {
  user_data "$1" "$(jq -nc --arg d "$(ago "$2")" --argjson c "${3:-1}" '{Played:true,PlayCount:$c,LastPlayedDate:$d,PlaybackPositionTicks:0}')"
}
# in_progress ID DAYS_AGO MINUTES
in_progress() {
  user_data "$1" "$(jq -nc --arg d "$(ago "$2")" --argjson t "$(($3 * 600000000))" \
    '{Played:false,PlaybackPositionTicks:$t,LastPlayedDate:$d}')"
}
if [ "$(api GET "/Items?Recursive=true&IncludeItemTypes=Episode&IsPlayed=true&userId=$admin_id&Limit=0" | jq -r .TotalRecordCount)" = "0" ]; then
  log "watch history of $ADMIN_USER"
  for film in "Le Phare" "Bleu acier" "Givre" "Orage"; do watched "$(id_of Movie "$film")" 20; done
  watched "$(id_of Movie "Le Cerf-volant")" 30 3
  in_progress "$(id_of Movie "Fréquences")" 1 40
  in_progress "$(id_of Movie "Marée basse")" 4 25
  watched "$(episode_id "Les Marées" 1 1)" 3
  in_progress "$(episode_id "Les Marées" 1 2)" 2 18
  watched "$(episode_id "Une saison" 1 1)" 5
  watched "$(episode_id "Brume" 1 1)" 61
  watched "$(episode_id "Brume" 1 2)" 60
  for film in "Zéphyr" "L'Odyssée des sables"; do
    api POST "/UserFavoriteItems/$(id_of Movie "$film")?userId=$admin_id" > /dev/null
  done
fi

log "done"
