# Ciné Doury — cahier des charges technique

Ciné Doury est un lecteur vidéo maison pour **Android TV** (puis téléphone, tablette et navigateur),
qui remplace Plex. Il fonctionne au-dessus d'un serveur **Jellyfin non modifié**. Ce document fait
foi sur l'architecture, les contrats et les jalons. Il a deux annexes, qui font foi chacune dans
son domaine :

- [`FONCTIONNALITES.md`](FONCTIONNALITES.md) — **ce que** fait l'appli, par priorité (P0 = version 1.0 TV) ;
- [`UI.md`](UI.md) — **à quoi** elle ressemble et comment on juge l'interface.

## 0. État

| Jalon | État | PR |
|---|---|---|
| J0 — Fondations | à faire | — |

*(Chaque jalon met à jour ce tableau et ajoute ses contrats au § 4.)*

## 1. Pourquoi, et le principe

- Les applis Plex récentes sont lentes et imposent leurs changements d'interface ; l'interface de
  Jellyfin et d'Emby ne convient pas au mainteneur. On garde **le moteur** de Jellyfin
  (bibliothèques, métadonnées, transcodage matériel), on remplace **toute l'interface**.
- **Toute la gestion** (corriger une identification, changer une affiche, supprimer, rescanner,
  utilisateurs) se fait dans l'appli : personne ne doit avoir à ouvrir l'interface web de Jellyfin.
- **Rien n'est imposé** : l'appli propose ses mises à jour, n'en installe aucune seule, et permet de
  revenir en arrière. Zéro télémétrie, aucun contenu extérieur à la bibliothèque.

## 2. Décisions (25/10/2026)

- Nom **Ciné Doury** ; dépôt public `guim31/cine-doury`, licence MIT.
- Paquet et `applicationId` : `fr.guim.cinedoury` (le module mobile, plus tard : même identifiant de base, variantes à définir au J9).
- **Serveur visé : Jellyfin 12.3** (`jellyfin/jellyfin:12.3.20261023-013714`), version minimale acceptée 12.3. Une montée de version du serveur passe d'abord par la CI de ce dépôt.
- Côté serveur sont installés : Intro Skipper (segments intro/générique par analyse audio), Chapter Segments Provider, Playback Reporting (statistiques), TMDb Box Sets (collections), AniDB, Fanart. L'appli lit ces données par l'API standard (`/MediaSegments`, etc.) et **ne dépend d'aucun plugin pour fonctionner** : sans eux, la fonction concernée se masque.
- Couleur d'accent : **jaune projecteur `#F2C14E`** (`UI.md` § 4.1).
- **Musique hors périmètre.**
- **Appareil de référence : NVIDIA Shield TV** (mesures et essais réels). Le Chromecast avec Google TV, plus faible, est essayé à la main par le mainteneur.
- Ordre : **la TV d'abord** (version 1.0 = tout le P0), puis le mobile (P1), puis le reste.

## 3. Architecture

Kotlin, Gradle (Kotlin DSL, catalogue de versions `gradle/libs.versions.toml`), Jetpack Compose.
Modules (noms indicatifs, le J0 les fixe au § 4) :

| Module | Rôle | Dépend d'Android ? |
|---|---|---|
| `:core:model` | modèle du domaine : bibliothèque, élément (film, série, saison, épisode, collection, personne), source média, pistes, données utilisateur (vu, position, favori), segment, image | non (Kotlin pur) |
| `:core:source` | **l'interface « source »** : authentification, bibliothèques, éléments, recherche, lecture (décision et rapports), gestion, sessions. Erreurs typées. Les écrans ne connaissent qu'elle | non |
| `:source:jellyfin` | implémentation Jellyfin de `:core:source` | non de préférence |
| `:core:data` | dépôts, cache local (Room), URL d'images dimensionnées, synchronisation, préférences | oui |
| `:core:playback` | moteur de décision de lecture et profil de l'appareil (codecs, HDR, passthrough), intégration Media3 | oui |
| `:core:designsystem` | jetons de `UI.md` (couleurs, typographie, espacements, durées), composants TV et mobile | oui |
| `:feature:*` | écrans : `auth`, `home`, `library`, `details`, `player`, `search`, `manage`, `settings` | oui |
| `:app:tv` | l'appli Android TV / Google TV | oui |
| `:benchmark` | Macrobenchmark et Baseline Profile | oui |

Pile visée, chaque choix étant à confirmer par le J0 avec la version exacte :
- Compose for TV (`androidx.tv:tv-material`) ;
- Media3 ExoPlayer (extension FFmpeg pour l'audio si nécessaire) ;
- Coil 3 ;
- Room ;
- kotlinx.serialization et kotlinx.coroutines ;
- injection par Hilt ou Koin ;
- Roborazzi pour les captures ;
- Macrobenchmark.

`minSdk` choisi pour couvrir le Chromecast avec Google TV et la Shield.

Client Jellyfin : **SDK Kotlin officiel** (`org.jellyfin.sdk`) **ou** client généré depuis
l'OpenAPI de la 12.3. Le J0 tranche, écrit sa raison au § 4, et garde ce client **entièrement
caché** derrière `:core:source` : aucun type du SDK ne sort de `:source:jellyfin`.

## 4. Contrats

*(Fixés par le J0, puis complétés par chaque jalon. Tout ce qui suit est la cible ; vérifier chaque
point contre l'OpenAPI de la 12.3, à versionner dans `docs/api/jellyfin-openapi-12.3.json`.)*

- **Authentification** : uniquement l'en-tête
  `Authorization: MediaBrowser Client="Ciné Doury", Device="<nom>", DeviceId="<id>", Version="<x.y.z>", Token="<jeton>"`.
  Jellyfin 12 **refuse** `X-Emby-Token` et `?api_key=` (401). `DeviceId` est stable par installation.
- **Découverte** : diffusion UDP sur le port 7359 (`Who is JellyfinServer?`), réponse JSON (adresse, identifiant, nom).
- **Quick Connect** : `POST /QuickConnect/Initiate` (code à afficher), interrogation de `/QuickConnect/Connect?secret=`, puis `POST /Users/AuthenticateWithQuickConnect`.
- **Images** : toujours demandées à la taille affichée (`fillWidth`/`fillHeight`, `quality`, `tag`). Le BlurHash fourni par le serveur sert d'aperçu.
- **Lecture** :
  - `POST /Items/{id}/PlaybackInfo`, avec un **profil d'appareil construit d'après les capacités réelles** (MediaCodecList, HDR de l'écran, passthrough audio) ;
  - choix lecture directe / remux / transcodage, et la **raison** du choix, exposée à l'écran d'informations ;
  - rapports `POST /Sessions/Playing`, `/Sessions/Playing/Progress` (toutes les 10 s, ainsi qu'à la pause et après un saut), `/Sessions/Playing/Stopped`.
- **Segments** : `GET /MediaSegments/{itemId}` (types Intro, Outro, Recap, Preview, Commercial).
- **Aperçus** (trickplay) : métadonnées dans l'élément, tuiles `/Videos/{id}/Trickplay/{largeur}/{index}.jpg`.
- **Requêtes de films** : un serveur qui a des collections **regroupe** les films par défaut. Passer `CollapseBoxSetItems=false` pour lister ou compter les films.
- **Textes** : aucune chaîne en dur ; ressources `fr` (langue par défaut) et `en`.
- **Versions** : semver, étiquettes `vX.Y.Z`. L'APK de release est **signé hors de la CI** par le mainteneur ; la clé n'entre jamais dans le dépôt. La CI produit un APK de débogage.

## 5. Tests et CI

- GitHub Actions sur `ubuntu-latest` :
  - construction ;
  - analyse statique (ktlint ou Spotless, Detekt, Android Lint) ;
  - tests unitaires ;
  - **vérification des captures Roborazzi** ;
  - **tests d'intégration contre un vrai Jellyfin**.
  - `concurrency` avec `cancel-in-progress`, `timeout-minutes`, cache Gradle.
- **Jellyfin de test** : conteneur de service `jellyfin/jellyfin:12.3.20261023-013714`.
  - Un script d'amorçage versionné fait l'assistant de démarrage, crée les utilisateurs (un administrateur, un profil enfant), les bibliothèques et des collections.
  - Les médias de test sont **générés par ffmpeg dans la CI** (quelques secondes chacun) et couvrent les cas difficiles :
    - H.264 SDR, HEVC 10 bits HDR10 ;
    - AAC stéréo, AC3 5.1, E-AC3 ;
    - plusieurs pistes audio (fra, eng, jpn) ;
    - sous-titres SRT, ASS et PGS, piste forcée ;
    - chapitres « Intro » et « Credits » ;
    - une série à plusieurs saisons, une série à saison unique, deux versions d'un même film, une collection.
  - On peut y ajouter de courts extraits des films ouverts de la fondation Blender. **Aucun autre média.**
- Les tests d'intégration parlent à `:source:jellyfin` uniquement ; l'interface se teste contre une fausse source en mémoire.

## 6. Jalons

Un jalon = une branche `feat/j<N>-<sujet>` = une PR. Chaque jalon met à jour le § 0, ajoute ses
contrats au § 4, joint des captures (`docs/screenshots/j<N>/`) et complète `CLAUDE.md`.

### J0 — Fondations
- Projet Gradle multi-modules du § 3 ; `:app:tv` démarre sur un écran « catalogue du design system » montrant les jetons et composants de base de `UI.md` : couleurs, échelle typographique, affiche et vignette focalisables avec leur focus (§ 3 de `UI.md`), bouton principal, rail latéral fermé et ouvert.
- `:core:model` et `:core:source` : premier jeu d'interfaces (authentification, liste des bibliothèques, éléments d'une bibliothèque paginés, détail d'un élément) ; implémentation Jellyfin de ces premières fonctions.
- Jellyfin de test en CI avec ses médias générés (§ 5) ; tests d'intégration qui se connectent, listent les bibliothèques et lisent un élément.
- OpenAPI 12.3 versionnée dans `docs/api/`.
- CI complète du § 5, verte ; APK de débogage en artefact.
- **Critères d'acceptation** :
  1. `./gradlew check` et la CI sont verts.
  2. L'APK de débogage s'installe et démarre sur Android TV, au moins dans un émulateur API 34 TV en CI ou en local si l'environnement le permet.
  3. Les tests d'intégration passent contre le Jellyfin 12.3 de la CI.
  4. Les captures du catalogue sont dans la PR, en 1080p, thème sombre et noir OLED.
  5. Le choix du client Jellyfin et la liste des modules sont écrits au § 4.

### J1 — Connexion et profils
Découverte LAN, saisie d'adresse, Quick Connect, identifiants. Choix du profil avec avatars et PIN,
mémorisation du serveur et du profil. Cache local et chargement d'images. Basculement entre adresse
locale et distante. Erreurs lisibles (`UI.md` § 10).

### J2 — Accueil et navigation
Rail latéral ; accueil (bandeau à la une, rangées P0, personnalisation, bibliothèques exclues) ;
bibliothèques (vues, tri, filtres, A–Z, collections, genres) ; mémoire du focus et de la position.

### J3 — Fiches
Film, série (saisons aplaties, bouton intelligent, épisodes), personne ; saga ; badges techniques ;
choix de version et de pistes avant la lecture.

### J4 — Lecteur
Décision direct / remux / transcodage ; HDR10, HDR10+ et DV ; passthrough ; fréquence d'images
adaptée ; pistes et sous-titres (SRT, ASS, PGS) ; reprise et rapports ; aperçus à la recherche ;
segments (passer l'intro, le générique) ; épisode suivant ; « toujours là ? » ; écran d'informations
technique.

### J5 — Recherche
Instantanée, tolérante aux accents et aux fautes, vocale ; résultats groupés ; Seerr (facultatif,
masqué s'il n'est pas configuré).

### J6 — Gestion P0
Menu « ⋯ » : actualiser, corriger l'identification (résultats avec affiches), changer les images,
supprimer avec confirmation (chemin et taille), rescanner une bibliothèque.

### J7 — Réglages et mise à jour
Pages de réglages P0 (`FONCTIONNALITES.md` § 12) ; mise à jour proposée depuis les releases GitHub
(jamais imposée, retour arrière) ; rapport de diagnostic exportable.

### J8 — Finitions 1.0
Baseline Profile et Macrobenchmark ; accessibilité ; captures 1080p et 4K, sombre et OLED ;
CHANGELOG ; version 1.0.0 prête à étiqueter (l'étiquette est posée par le mainteneur).

### Après la 1.0
Mobile et tablette, hors ligne, télécommande et multi-appareils, statistiques, gestion P1, mpv,
rangées intelligentes ; puis le web et la TV en direct (P2).

## 7. Hors périmètre

Musique ; iOS et Apple TV ; toute modification du serveur Jellyfin ; tout service en ligne autre
que le serveur de l'utilisateur, à l'exception des fournisseurs que Jellyfin interroge lui-même.
