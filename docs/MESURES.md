# Mesurer Ciné Doury sur un appareil réel

Procédure de l'orchestrateur sur la NVIDIA Shield TV (référence, `UI.md` § 12) et sur le Chromecast
avec Google TV (le plus faible). L'émulateur de la CI dessine en logiciel : ses chiffres ne comparent
l'appli qu'à elle-même (`SPEC.md` § 4.16 et § 4.20). Seuils : `FONCTIONNALITES.md` § 13.

## 1. Installer la version optimisée

Toujours la version **optimisée** (release), jamais l'APK de débogage : débogable, il tourne sans
code compilé d'avance, plusieurs fois plus lent sur le fil principal (`CLAUDE.md`, « Performances »).

```sh
adb connect <adresse de la TV>:5555        # débogage réseau activé dans les options pour les développeurs
adb install -r cine-doury-X.Y.Z.apk        # ou l'artefact `cine-doury-release-apk` de la CI
adb shell am start -n fr.guim.cinedoury/.tv.MainActivity   # un premier lancement : profileinstaller écrit le profil
adb shell cmd package compile -m speed-profile -f fr.guim.cinedoury
adb shell dumpsys package fr.guim.cinedoury | grep -E "DEBUGGABLE|status="   # pas de DEBUGGABLE, status=speed-profile
```

Une release et un APK de la CI ne sont pas signés par la même clé : passer de l'un à l'autre demande
`adb uninstall fr.guim.cinedoury` (les profils se reconnectent ensuite).

## 2. Images au défilement (`dumpsys gfxinfo`)

1. Lancer l'appli, ouvrir un profil, attendre l'accueil chargé (« Lire » focalisé).
2. Remettre les compteurs à zéro :
   ```sh
   adb shell dumpsys gfxinfo fr.guim.cinedoury reset
   ```
3. Faire le parcours à la télécommande, toujours le même :
   - **accueil** : 8 × ↓ (toutes les rangées), puis 5 × ↑, 4 × →, 4 × ← ;
   - **fiche** : OK sur une carte, ↓ jusqu'à « Titres similaires », 6 × → ; Retour ;
   - **grille** : rail → une bibliothèque, ↓ maintenu 3 s, ↑ maintenu 3 s.
4. Lire le résultat :
   ```sh
   adb shell dumpsys gfxinfo fr.guim.cinedoury > gfxinfo.txt
   grep -E "Total frames|Janky|percentile|Slow|High input|GPU" gfxinfo.txt
   ```
5. Noter : images en retard (« Janky frames », en %), percentiles 50/90/95/99, **GPU** 50/90/95/99
   (« GPU 90th percentile »), « Slow UI thread », « Slow bitmap uploads », « Slow issue draw
   commands », « High input latency ». Un parcours par mesure : refaire `reset` avant chaque.

Lecture : un 90e percentile **GPU** au-delà de 16 ms dit que le GPU ne suit plus (un écran 4K se
remplit quatre fois plus qu'un 1080p) ; « Slow UI thread » élevé dit que c'est le code de l'appli
(composition, mise en page). Le 27/10/2026 sur la Shield (avant le J8) : accueil 15 % en retard,
GPU 8 / 18 / 22 / 24 ms.

### Qui coûte au GPU ?

```sh
adb shell setprop debug.hwui.overdraw show     # surimpression : bleu 1×, vert 2×, rose 3×, rouge 4× et plus
adb shell setprop debug.hwui.profile visual_bars   # barres de rendu à l'écran
adb shell setprop debug.hwui.overdraw false; adb shell setprop debug.hwui.profile false   # pour finir
```

Relancer l'appli après chaque `setprop`. Une trace Perfetto (`adb shell perfetto -o /data/misc/perfetto-traces/t.pftrace -t 10s gfx view am`)
montre, sur le fil `RenderThread`, ce que dessine chaque image.

## 3. Démarrage et ouverture d'une fiche

```sh
adb shell am force-stop fr.guim.cinedoury
adb shell am start -W -n fr.guim.cinedoury/.tv.MainActivity    # TotalTime : jusqu'à la première image
```

L'ouverture d'une fiche et celle des réglages sont des sections de la trace système
(`CineDoury.openDetails`, `CineDoury.openSettings`, de la touche à la première image de l'écran) :
dans Perfetto, sous le processus de l'appli ; `NavigationBenchmarks` les mesure sur l'émulateur.

## 4. Les 20 gestes au canapé

`ci/couch-gestures.sh ADB_SERIAL` (voir [`GESTES.md`](GESTES.md)) joue les gestes, les chronomètre
et imprime aussi le `dumpsys gfxinfo` de toute la séance.

## 5. Mémoire (Chromecast)

```sh
adb shell dumpsys meminfo fr.guim.cinedoury | sed -n '1,40p'    # après 5 min de navigation
adb shell getprop ro.config.low_ram; adb shell dumpsys activity | grep -i "memoryClass"
```

Les caches d'images suivent `ActivityManager.memoryClass` et `isLowRamDevice` (`ImageBudget`,
`SPEC.md` § 4.20) ; le rapport de diagnostic les donne aussi.
