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

mkdir -p "$OUT/films" "$OUT/series" "$OUT/spectacles"

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

# nfo_extra FILE XML: adds lines before the closing tag of an .nfo.
nfo_extra() {
  local file="$1" xml="$2" closing
  closing="$(tail -n 1 "$file")"
  { sed '$d' "$file"; printf '%s\n%s\n' "$xml" "$closing"; } > "$file.tmp" && mv "$file.tmp" "$file"
}

# clip SIZE OUTPUT: a still colour at one frame every 10 s, as long as the longest film. It weighs a
# few kilobytes even in 4K, and each film is a stream copy of it cut at its runtime, so that the
# server reads the real duration (it ignores the runtime of a .nfo). The audio lasts 2 s: only its
# language matters.
clip() {
  "${FFMPEG[@]}" -f lavfi -i "color=c=0x1d3a4a:size=$1:rate=1/10:duration=8700" -f lavfi -i "sine=frequency=440:duration=2" \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p -x264-params keyint=infinite -c:a aac -b:a 64k "$2"
}
clip 640x360 "$WORK/c360.mkv"
clip 1280x720 "$WORK/c720.mkv"
clip 1920x1080 "$WORK/c1080.mkv"
clip 3840x2160 "$WORK/c2160.mkv"

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
# Credits for the details screen (J3), and a trailer on the web: the official trailer of « Big Buck
# Bunny », an open film of the Blender Foundation.
nfo_extra "$dir/movie.nfo" '  <director>Marie Duval</director>
  <credits>Claire Fontaine</credits>
  <actor>
    <name>Camille Martin</name>
    <role>Yann, le gardien</role>
    <type>Actor</type>
  </actor>
  <actor>
    <name>Louis Bernard</name>
    <role>Le capitaine</role>
    <type>Actor</type>
  </actor>
  <trailer>plugin://plugin.video.youtube/?action=play_video&amp;videoid=aqz-KE-bpKQ</trailer>'

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
# A local trailer, found by its « -trailer » suffix (J3).
"${FFMPEG[@]}" -i "$WORK/v720.mkv" -c copy "$dir/Nuit polaire (2020)-trailer.mkv"

# 4. « Double jeu » (2012): two versions of the same film, merged by Jellyfin into one item.
dir="$OUT/films/Double jeu (2012)"
mkdir -p "$dir"
"${FFMPEG[@]}" -i "$WORK/v1080.mkv" -i "$WORK/a_51_fr.wav" -map 0:v -map 1:a -c:v copy -c:a eac3 -b:a 384k \
  -metadata:s:a:0 language=fra "$dir/Double jeu (2012) - 1080p.mkv"
"${FFMPEG[@]}" -i "$WORK/v720.mkv" -i "$WORK/a_stereo.wav" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 128k \
  -metadata:s:a:0 language=fra "$dir/Double jeu (2012) - 720p.mkv"
nfo "$dir/movie.nfo" movie "Double jeu" 2012 "Deux versions d'un même film, pour le choix de la version." Comédie

# episode SERIES_DIR SEASON EPISODE: 42 minutes, Intro and Credits chapters at the start.
episode() {
  local season_dir
  season_dir="$1/Season $(printf '%02d' "$2")"
  mkdir -p "$season_dir"
  "${FFMPEG[@]}" -i "$WORK/c360.mkv" -i "$WORK/chapters.txt" -t 2520 \
    -map 0 -map_chapters 1 -c copy -metadata:s:a:0 language=fra \
    "$season_dir/$(basename "$1" | sed 's/ ([0-9]*)$//') S$(printf '%02d' "$2")E$(printf '%02d' "$3").mkv"
}

# 5. « Les Marées » (2015): two seasons of two episodes.
for s in 1 2; do for e in 1 2; do episode "$OUT/series/Les Marées (2015)" "$s" "$e"; done; done
nfo "$OUT/series/Les Marées (2015)/tvshow.nfo" tvshow "Les Marées" 2015 "Une famille de pêcheurs, au rythme des coefficients." Drame
nfo_extra "$OUT/series/Les Marées (2015)/tvshow.nfo" '  <premiered>2015-03-02</premiered>
  <enddate>2016-03-14</enddate>
  <status>Ended</status>'

# 6. « Une saison » (2018): a single season of three episodes.
for e in 1 2 3; do episode "$OUT/series/Une saison (2018)" 1 "$e"; done
nfo "$OUT/series/Une saison (2018)/tvshow.nfo" tvshow "Une saison" 2018 "Une série courte, à saison unique." Comédie
nfo_extra "$OUT/series/Une saison (2018)/tvshow.nfo" '  <premiered>2018-04-01</premiered>
  <enddate>2018-04-15</enddate>
  <status>Ended</status>'

# 7. « Brume » (2019): a single season of four episodes, watched long ago (outside the « À suivre »
#    window of the bootstrap).
for e in 1 2 3 4; do episode "$OUT/series/Brume (2019)" 1 "$e"; done
nfo "$OUT/series/Brume (2019)/tvshow.nfo" tvshow "Brume" 2019 "Un village de montagne disparaît chaque matin dans la brume." Policier Drame
nfo_extra "$OUT/series/Brume (2019)/tvshow.nfo" '  <premiered>2019-10-01</premiered>
  <status>Continuing</status>'

# Episode titles, so that rows and banners have something to say.
# episode_nfo SERIES_DIR SEASON EPISODE TITLE AIRED
episode_nfo() {
  local file
  file="$1/Season $(printf '%02d' "$2")/$(basename "$1" | sed 's/ ([0-9]*)$//') S$(printf '%02d' "$2")E$(printf '%02d' "$3").nfo"
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<episodedetails>\n  <title>%s</title>\n  <season>%s</season>\n  <episode>%s</episode>\n  <aired>%s</aired>\n  <plot>Épisode %s de la saison %s.</plot>\n</episodedetails>\n' \
    "$4" "$2" "$3" "$5" "$3" "$2" > "$file"
}
episode_nfo "$OUT/series/Les Marées (2015)" 1 1 "Grande marée" 2015-03-02
episode_nfo "$OUT/series/Les Marées (2015)" 1 2 "Mortes-eaux" 2015-03-09
episode_nfo "$OUT/series/Les Marées (2015)" 2 1 "Le retour" 2016-03-07
episode_nfo "$OUT/series/Les Marées (2015)" 2 2 "Le chenal" 2016-03-14
episode_nfo "$OUT/series/Une saison (2018)" 1 1 "Printemps" 2018-04-01
episode_nfo "$OUT/series/Une saison (2018)" 1 2 "Été" 2018-04-08
episode_nfo "$OUT/series/Une saison (2018)" 1 3 "Automne" 2018-04-15
episode_nfo "$OUT/series/Brume (2019)" 1 1 "Le col" 2019-10-01
episode_nfo "$OUT/series/Brume (2019)" 1 2 "La source" 2019-10-08
episode_nfo "$OUT/series/Brume (2019)" 1 3 "Le refuge" 2019-10-15
episode_nfo "$OUT/series/Brume (2019)" 1 4 "Le dégel" 2019-10-22

# 8. The catalogue: 41 more films, enough for paging, the A–Z jump, sorting and filtering (J2). Each is
#    a stream copy of a still clip cut at its runtime; its .nfo gives the rest (genres, rating,
#    studio, people, tags, dates). Posters and backdrops are generated gradients, which give each film
#    its own BlurHash.

# film INDEX TITLE YEAR GENRES RATING CERTIFICATION RUNTIME STUDIO HEIGHT AUDIO_LANGUAGE SUBTITLES TAGS ADDED [COLLECTION]
film() {
  local index="$1" title="$2" year="$3" genres="$4" rating="$5" certification="$6" runtime="$7" studio="$8"
  local height="$9" audio="${10}" subtitles="${11}" tags="${12}" added="${13}"
  local dir="$OUT/films/$title ($year)"
  mkdir -p "$dir"
  "${FFMPEG[@]}" -i "$WORK/c$height.mkv" -t "$((runtime * 60))" -map 0 -c copy -metadata:s:a:0 "language=$audio" \
    "$dir/$title ($year).mkv"
  [ "$subtitles" = "yes" ] && cp "$WORK/fr.srt" "$dir/$title ($year).fr.srt"
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=200x300:seed=$index:nb_colors=3" -frames:v 1 "$dir/poster.jpg"
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=480x270:seed=$((index + 100)):nb_colors=3" -frames:v 1 "$dir/fanart.jpg"
  local actors=("Camille Martin" "Louis Bernard" "Inès Garnier" "Hugo Lefèvre" "Jeanne Roux")
  local directors=("Marie Duval" "Paul Mercier")
  {
    printf '<?xml version="1.0" encoding="utf-8" standalone="yes"?>\n<movie>\n'
    printf '  <title>%s</title>\n  <year>%s</year>\n  <premiered>%s-06-15</premiered>\n' "$title" "$year" "$year"
    printf '  <plot>%s, %s. Un film du catalogue de test.</plot>\n' "$title" "$year"
    printf '  <runtime>%s</runtime>\n  <rating>%s</rating>\n  <mpaa>%s</mpaa>\n  <studio>%s</studio>\n' \
      "$runtime" "$rating" "$certification" "$studio"
    printf '  <dateadded>%s 20:00:00</dateadded>\n' "$added"
    IFS=';' read -ra list <<< "$genres"
    for genre in "${list[@]}"; do printf '  <genre>%s</genre>\n' "$genre"; done
    IFS=';' read -ra list <<< "$tags"
    for tag in "${list[@]}"; do [ -n "$tag" ] && printf '  <tag>%s</tag>\n' "$tag"; done
    printf '  <director>%s</director>\n  <credits>Claire Fontaine</credits>\n' "${directors[$((index % 2))]}"
    for offset in 0 2; do
      printf '  <actor>\n    <name>%s</name>\n    <role>Rôle %s</role>\n    <type>Actor</type>\n  </actor>\n' \
        "${actors[$(((index + offset) % 5))]}" "$((offset + 1))"
    done
    printf '</movie>\n'
  } > "$dir/movie.nfo"
}

film 1 "2 Soleils" 2019 "Science-fiction" 6.8 "-12" 104 "Studio Lumen" 1080 fra no "" 2026-09-01
film 2 "Après la pluie" 1987 "Drame;Romance" 7.4 "Tous publics" 96 "Films du Phare" 360 fra no "Classique" 2026-01-12
film 3 "L'Arpenteur" 1964 "Policier" 7.9 "-12" 112 "Gaumont Bleu" 360 fra no "Noir et blanc;Classique" 2025-11-03
film 4 "Bleu acier" 1999 "Thriller;Policier" 6.2 "-16" 118 "Studio Lumen" 720 fra no "" 2026-02-20
film 5 "Les Brisants" 2021 "Drame" 7.1 "-10" 101 "Films du Phare" 1080 fra no "" 2026-10-20
film 6 "Le Cerf-volant" 1958 "Comédie;Famille" 7.6 "Tous publics" 88 "Gaumont Bleu" 360 fra no "Noir et blanc;Classique" 2025-12-24
film 7 "Chambre noire" 2008 "Thriller" 6.5 "-16" 93 "Nocturne" 720 eng yes "" 2026-03-15
film 8 "La Dune" 1983 "Aventure" 6.9 "Tous publics" 132 "Studio Lumen" 360 fra no "" 2026-04-02
film 9 "Échappée belle" 2016 "Comédie;Aventure" 6.4 "Tous publics" 99 "Films du Phare" 1080 fra no "" 2026-05-18
film 10 "L'Éclipse" 1972 "Science-fiction;Drame" 7.8 "-10" 115 "Nocturne" 360 fra no "Classique" 2026-06-30
film 11 "Fréquences" 2023 "Science-fiction;Thriller" 7.0 "-12" 107 "Studio Lumen" 2160 eng yes "Coup de cœur" 2026-10-22
film 12 "Le Grand Large" 1991 "Aventure;Famille" 6.7 "Tous publics" 121 "Gaumont Bleu" 720 fra no "" 2026-07-14
film 13 "Givre" 2012 "Drame" 7.3 "-12" 98 "Nocturne" 1080 fra no "" 2026-08-08
film 14 "L'Heure bleue" 1996 "Romance;Drame" 6.6 "Tous publics" 104 "Films du Phare" 720 fra no "" 2026-02-02
film 15 "Horizon perdu" 1979 "Aventure;Science-fiction" 7.2 "-10" 127 "Studio Lumen" 360 eng yes "" 2026-03-03
film 16 "Île de sel" 2005 "Documentaire" 7.7 "Tous publics" 84 "Nocturne" 720 fra no "Coup de cœur" 2026-04-04
film 17 "Jours tranquilles" 1968 "Comédie" 6.1 "Tous publics" 91 "Gaumont Bleu" 360 fra no "Noir et blanc" 2026-05-05
film 18 "Kilomètre zéro" 2018 "Comédie;Aventure" 6.3 "Tous publics" 102 "Films du Phare" 1080 fra no "" 2026-06-06
film 19 "La Lanterne" 1985 "Fantastique;Famille" 7.5 "Tous publics" 95 "Studio Lumen" 360 fra no "" 2026-07-07
film 20 "Marée basse" 2001 "Policier;Drame" 6.8 "-12" 110 "Nocturne" 720 fra no "" 2026-08-09
film 21 "Minuit passé" 2014 "Thriller" 6.0 "-16" 89 "Nocturne" 1080 eng yes "" 2026-09-09
film 22 "Nord magnétique" 1993 "Documentaire;Aventure" 7.4 "Tous publics" 80 "Gaumont Bleu" 720 fra no "" 2026-01-10
film 23 "L'Odyssée des sables" 1962 "Aventure" 8.1 "Tous publics" 141 "Studio Lumen" 360 fra no "Classique;Coup de cœur" 2025-10-11
film 24 "Orage" 2010 "Drame;Thriller" 6.9 "-12" 100 "Films du Phare" 1080 fra no "" 2026-02-12
film 25 "Le Pont suspendu" 1975 "Policier" 7.0 "-12" 106 "Gaumont Bleu" 360 fra no "Classique" 2026-03-13
film 26 "Quatre saisons" 1989 "Comédie;Romance" 6.5 "Tous publics" 97 "Films du Phare" 720 fra no "" 2026-04-14
film 27 "Rivages" 2024 "Drame" 7.6 "-10" 112 "Nocturne" 2160 fra no "" 2026-10-24
film 28 "La Route du nord" 1955 "Aventure;Drame" 7.3 "Tous publics" 118 "Gaumont Bleu" 360 fra no "Noir et blanc;Classique" 2026-05-16
film 29 "Sillage" 2007 "Thriller;Aventure" 6.4 "-12" 103 "Studio Lumen" 720 fra no "" 2026-06-17
film 30 "Les Étoiles filantes" 1981 "Science-fiction;Famille" 7.0 "Tous publics" 94 "Studio Lumen" 360 fra no "" 2026-07-18
film 31 "Les Étoiles filantes 2" 1984 "Science-fiction;Famille" 6.3 "Tous publics" 92 "Studio Lumen" 360 fra no "" 2026-07-19
film 32 "Les Étoiles filantes 3" 1988 "Science-fiction;Famille" 5.9 "Tous publics" 90 "Studio Lumen" 720 fra no "" 2026-07-20
film 33 "Terre ferme" 2015 "Drame" 6.7 "Tous publics" 108 "Films du Phare" 1080 fra no "" 2026-08-21
film 34 "Ultime escale" 1997 "Policier;Thriller" 6.6 "-16" 114 "Nocturne" 720 eng yes "" 2026-09-22
film 35 "Vents contraires" 2003 "Comédie;Drame" 6.2 "Tous publics" 99 "Gaumont Bleu" 720 fra no "" 2026-01-23
film 36 "Le Veilleur" 1977 "Fantastique" 7.1 "-12" 101 "Nocturne" 360 fra no "" 2026-02-24
film 37 "Week-end à la mer" 1966 "Comédie" 6.8 "Tous publics" 87 "Gaumont Bleu" 360 fra no "Classique" 2026-03-25
film 38 "Xénon" 2020 "Science-fiction" 5.8 "-12" 95 "Studio Lumen" 1080 eng yes "" 2026-04-26
film 39 "Yeux fermés" 2011 "Thriller;Drame" 6.9 "-16" 105 "Nocturne" 1080 fra no "" 2026-05-27
film 40 "Zéphyr" 2022 "Animation;Famille" 7.8 "Tous publics" 83 "Films du Phare" 2160 fra no "Coup de cœur" 2026-10-23
film 41 "Zone blanche" 1994 "Thriller" 6.1 "-12" 109 "Nocturne" 720 fra no "" 2026-06-28

# 9. « Spectacles »: a small library that the bootstrap excludes from the home of the administrator.
for show in "Le Cirque d'hiver (2017)" "Concert au phare (2020)"; do
  dir="$OUT/spectacles/$show"
  mkdir -p "$dir"
  "${FFMPEG[@]}" -i "$WORK/c720.mkv" -t 5400 -map 0 -c copy -metadata:s:a:0 language=fra "$dir/$show.mkv"
  "${FFMPEG[@]}" -f lavfi -i "gradients=s=200x300:seed=${#show}:nb_colors=2" -frames:v 1 "$dir/poster.jpg"
done

find "$OUT" -type f | sort
