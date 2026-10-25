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
  [ "$movies" -ge 4 ] && [ "$episodes" -ge 7 ] && [ "$running" = "Idle" ] && break
  sleep 2
done
log "indexed $movies films and $episodes episodes"
[ "$movies" -ge 4 ] && [ "$episodes" -ge 7 ] || { log "scan incomplete"; exit 1; }

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

collection="Le Phare : la saga"
if [ "$(api GET "/Items?Recursive=true&IncludeItemTypes=BoxSet&Limit=0" | jq -r .TotalRecordCount)" = "0" ]; then
  log "collection $collection"
  ids="$(api GET "/Items?Recursive=true&IncludeItemTypes=Movie&CollapseBoxSetItems=false&SearchTerm=phare" \
    | jq -r '[.Items[].Id] | join(",")')"
  api POST "/Collections?name=$(jq -rn --arg v "$collection" '$v|@uri')&ids=$ids" > /dev/null
fi

log "done"
