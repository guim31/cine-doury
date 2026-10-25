#!/usr/bin/env bash
# Generates the test library served by the CI Jellyfin. Every file is synthetic (ffmpeg test
# sources) and a few seconds long. The cases follow docs/SPEC.md § 5.
#
# Usage: generate-media.sh OUTPUT_DIR
set -euo pipefail

OUT="${1:?usage: generate-media.sh OUTPUT_DIR}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

DURATION=6
FFMPEG=(ffmpeg -hide_banner -loglevel error -y)

mkdir -p "$OUT/films" "$OUT/series"

# Chapters "Intro" (0-2 s), "Film" (2-4 s) and "Credits" (4-6 s), in ffmetadata form.
cat > "$WORK/chapters.txt" <<'EOF'
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=2000
title=Intro
[CHAPTER]
TIMEBASE=1/1000
START=2000
END=4000
title=Film
[CHAPTER]
TIMEBASE=1/1000
START=4000
END=6000
title=Credits
EOF

cat > "$WORK/fr.srt" <<'EOF'
1
00:00:00,500 --> 00:00:02,500
Sous-titres SRT en français.

2
00:00:03,000 --> 00:00:05,500
Deuxième réplique.
EOF

cat > "$WORK/forced.srt" <<'EOF'
1
00:00:01,000 --> 00:00:03,000
[Panneau] Phare de la Pointe
EOF

cat > "$WORK/en.ass" <<'EOF'
[Script Info]
ScriptType: v4.00+
PlayResX: 1280
PlayResY: 720

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,48,&H0000FFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,1,2,20,20,40,1
Style: Sign,Arial,40,&H00FFFFFF,&H000000FF,&H00804000,&H64000000,1,0,0,0,100,100,0,0,1,3,0,8,20,20,40,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.50,0:00:02.50,Default,,0,0,0,,Styled {\i1}ASS{\i0} subtitles in English.
Dialogue: 0,0:00:03.00,0:00:05.50,Sign,,0,0,0,,{\pos(640,80)}A positioned sign
EOF

python3 "$HERE/make_pgs.py" "$WORK/fr.sup" 1280 720 "0.5:2.5:PGS FR 1" "3:5.5:PGS FR 2"

# video SIZE LABEL -> H.264 SDR test pattern with a burnt-in label.
h264() {
  "${FFMPEG[@]}" -f lavfi -i "testsrc2=size=$1:rate=24:duration=$DURATION" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -g 48 "$2"
}

tone() { # tone FREQUENCY CHANNEL_LAYOUT OUTPUT
  "${FFMPEG[@]}" -f lavfi -i "sine=frequency=$1:duration=$DURATION:sample_rate=48000" \
    -af "aformat=channel_layouts=$2" -c:a pcm_s16le "$3"
}

# nfo FILE ROOT_TAG TITLE YEAR PLOT [GENRE...]: local metadata, so the scan needs no internet provider.
nfo() {
  local file="$1" tag="$2" title="$3" year="$4" plot="$5"
  shift 5
  {
    printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<%s>\n' "$tag"
    printf '  <title>%s</title>\n  <year>%s</year>\n  <plot>%s</plot>\n' "$title" "$year" "$plot"
    for genre in "$@"; do printf '  <genre>%s</genre>\n' "$genre"; done
    printf '</%s>\n' "$tag"
  } > "$file"
}

h264 1280x720 "$WORK/v720.mkv"
h264 1920x1080 "$WORK/v1080.mkv"
h264 640x360 "$WORK/v360.mkv"
tone 440 stereo "$WORK/a_stereo.wav"
tone 330 5.1 "$WORK/a_51_fr.wav"
tone 550 5.1 "$WORK/a_51_en.wav"
tone 660 stereo "$WORK/a_jp.wav"

# 1. « Le Phare » (2001): H.264 SDR, AAC stereo, Intro/Credits chapters, external SRT.
dir="$OUT/films/Le Phare (2001)"
mkdir -p "$dir"
"${FFMPEG[@]}" -i "$WORK/v720.mkv" -i "$WORK/a_stereo.wav" -i "$WORK/chapters.txt" \
  -map 0:v -map 1:a -map_chapters 2 -c:v copy -c:a aac -b:a 128k \
  -metadata:s:a:0 language=fra -metadata title="Le Phare" "$dir/Le Phare (2001).mkv"
cp "$WORK/fr.srt" "$dir/Le Phare (2001).fr.srt"
nfo "$dir/movie.nfo" movie "Le Phare" 2001 "Un gardien de phare consigne chaque nuit ce que la mer lui rapporte." Drame

# 2. « Retour au phare » (2004): sequel, grouped with the first film into a collection by the bootstrap.
dir="$OUT/films/Retour au phare (2004)"
mkdir -p "$dir"
"${FFMPEG[@]}" -i "$WORK/v720.mkv" -i "$WORK/a_51_fr.wav" -i "$WORK/chapters.txt" \
  -map 0:v -map 1:a -map_chapters 2 -c:v copy -c:a ac3 -b:a 384k \
  -metadata:s:a:0 language=fra "$dir/Retour au phare (2004).mkv"
nfo "$dir/movie.nfo" movie "Retour au phare" 2004 "Des années plus tard, la lumière se rallume." Drame

# 3. « Nuit polaire » (2020): HEVC Main 10 HDR10; E-AC3 5.1 fra, AC3 5.1 eng, AAC stereo jpn;
#    subtitles: forced SRT fra, SRT fra, ASS eng, PGS fra.
dir="$OUT/films/Nuit polaire (2020)"
mkdir -p "$dir"
"${FFMPEG[@]}" -f lavfi -i "testsrc2=size=1280x720:rate=24:duration=$DURATION" \
  -vf "format=yuv420p10le" -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le \
  -x265-params "log-level=error:hdr10=1:hdr10-opt=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400" \
  -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc "$WORK/hdr.mkv"
"${FFMPEG[@]}" -i "$WORK/hdr.mkv" -i "$WORK/a_51_fr.wav" -i "$WORK/a_51_en.wav" -i "$WORK/a_jp.wav" \
  -i "$WORK/forced.srt" -i "$WORK/fr.srt" -i "$WORK/en.ass" -i "$WORK/fr.sup" -i "$WORK/chapters.txt" \
  -map 0:v -map 1:a -map 2:a -map 3:a -map 4:s -map 5:s -map 6:s -map 7:s -map_chapters 8 \
  -c:v copy -c:a:0 eac3 -b:a:0 384k -c:a:1 ac3 -b:a:1 384k -c:a:2 aac -b:a:2 128k \
  -c:s:0 srt -c:s:1 srt -c:s:2 ass -c:s:3 copy \
  -metadata:s:a:0 language=fra -metadata:s:a:0 title="Français" \
  -metadata:s:a:1 language=eng -metadata:s:a:1 title="English" \
  -metadata:s:a:2 language=jpn -metadata:s:a:2 title="日本語" \
  -metadata:s:s:0 language=fra -metadata:s:s:0 title="Forcés" \
  -metadata:s:s:1 language=fra -metadata:s:s:2 language=eng -metadata:s:s:3 language=fra \
  -disposition:a:0 default -disposition:s:0 forced -disposition:s:1 0 -disposition:s:2 0 -disposition:s:3 0 \
  "$dir/Nuit polaire (2020).mkv"
nfo "$dir/movie.nfo" movie "Nuit polaire" 2020 "Six mois sans soleil dans une station scientifique." "Science-fiction" Thriller

# 4. « Double jeu » (2012): two versions of the same film, merged by Jellyfin into one item.
dir="$OUT/films/Double jeu (2012)"
mkdir -p "$dir"
"${FFMPEG[@]}" -i "$WORK/v1080.mkv" -i "$WORK/a_51_fr.wav" -map 0:v -map 1:a -c:v copy -c:a eac3 -b:a 384k \
  -metadata:s:a:0 language=fra "$dir/Double jeu (2012) - 1080p.mkv"
"${FFMPEG[@]}" -i "$WORK/v720.mkv" -i "$WORK/a_stereo.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 128k \
  -metadata:s:a:0 language=fra "$dir/Double jeu (2012) - 720p.mkv"
nfo "$dir/movie.nfo" movie "Double jeu" 2012 "Deux versions d'un même film, pour le choix de la version." Comédie

# episode SERIES_DIR SEASON EPISODE
episode() {
  local season_dir
  season_dir="$1/Season $(printf '%02d' "$2")"
  mkdir -p "$season_dir"
  "${FFMPEG[@]}" -i "$WORK/v360.mkv" -i "$WORK/a_stereo.wav" -i "$WORK/chapters.txt" \
    -map 0:v -map 1:a -map_chapters 2 -c:v copy -c:a aac -b:a 96k -metadata:s:a:0 language=fra \
    "$season_dir/$(basename "$1" | sed 's/ ([0-9]*)$//') S$(printf '%02d' "$2")E$(printf '%02d' "$3").mkv"
}

# 5. « Les Marées » (2015): two seasons of two episodes.
for s in 1 2; do for e in 1 2; do episode "$OUT/series/Les Marées (2015)" "$s" "$e"; done; done
nfo "$OUT/series/Les Marées (2015)/tvshow.nfo" tvshow "Les Marées" 2015 "Une famille de pêcheurs, au rythme des coefficients." Drame

# 6. « Une saison » (2018): a single season of three episodes.
for e in 1 2 3; do episode "$OUT/series/Une saison (2018)" 1 "$e"; done
nfo "$OUT/series/Une saison (2018)/tvshow.nfo" tvshow "Une saison" 2018 "Une série courte, à saison unique." Comédie

find "$OUT" -type f | sort
