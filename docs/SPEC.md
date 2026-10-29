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
| J0 — Fondations | terminé, fusionné | [#1](https://github.com/guim31/cine-doury/pull/1) |
| J1 — Connexion et profils (et retouches du design system) | terminé, fusionné | [#2](https://github.com/guim31/cine-doury/pull/2) |
| J2 — Accueil et navigation | terminé, fusionné | [#3](https://github.com/guim31/cine-doury/pull/3) |
| J3 — Fiches (et retouches du J2 : raccord du bandeau, vignettes, index local et filtres HDR/audio, icônes de bibliothèque, clé de débogage) | terminé, fusionné | [#4](https://github.com/guim31/cine-doury/pull/4) |
| J4 — Lecteur (et retouches du J3 : pastilles des pistes, groupes insécables, logo à la place de l'affiche) | terminé, fusionné (captures dans `docs/screenshots/j4/`, numéros 90 à 108 ; ajout : taille des sous-titres en cours de lecture) | [#5](https://github.com/guim31/cine-doury/pull/5) |
| J5 — Recherche (index des titres, clavier à l'écran, recherche vocale, Seerr) | terminé, fusionné (captures dans `docs/screenshots/j5/`, numéros 110 à 120) | [#7](https://github.com/guim31/cine-doury/pull/7) |
| Essai réel 1 (25/10/2026, Chromecast avec Google TV) : connexion, Quick Connect, saisie à la télécommande, « Plus d'infos », journal | terminé, fusionné (§ 4.13 ; captures 35 et 36) | [#6](https://github.com/guim31/cine-doury/pull/6) |
| J6 — Gestion (P0, plus éléments à problème et actions en série) | terminé, fusionné (§ 4.14 ; captures dans `docs/screenshots/j6/`, numéros 130 à 147) | [#8](https://github.com/guim31/cine-doury/pull/8) |
| Essai réel 2 (26/10/2026, NVIDIA Shield TV, Android 11) : plantage à la première image (API Java absente de l'API 30), clavier resté ouvert, garde-fou Animal Sniffer, émulateur de la CI en API 30 et 34 | terminé, fusionné (§ 4.15) | [#9](https://github.com/guim31/cine-doury/pull/9) |
| Fluidité (partie performances du J8) : APK optimisé (R8, Baseline et Startup Profiles), `:benchmark` (Macrobenchmark, profils), mise en page à 960 × 540 dp sur toute TV (Shield en 4K), rail, recompositions | terminé, fusionné (§ 4.16 ; captures dans `docs/screenshots/perf/` et `j6/`) | [#10](https://github.com/guim31/cine-doury/pull/10) |
| J7 — Réglages et mise à jour (pages de réglages P0, mise à jour proposée depuis les releases, publication signée, rapport de diagnostic) | terminé, fusionné (§ 4.17 à 4.19 ; captures dans `docs/screenshots/j7/`, numéros 150 à 174) | [#11](https://github.com/guim31/cine-doury/pull/11) |
| J8 — Finitions 1.0 (coût GPU, petits appareils, profils et mesures, accessibilité, captures 4K et grand texte, 20 gestes au canapé, 1.0.0 prête à étiqueter) | prêt, en revue (§ 4.20 ; captures dans `docs/screenshots/j8/`, numéros 180 à 198 ; CI complète verte sur `24243cb`) | [#13](https://github.com/guim31/cine-doury/pull/13) |
| Essai réel 3 (27/10/2026, Shield en 4K, v0.7.0) : → depuis le rail ouvert, « Taille de l'interface » (Compacte par défaut), commandes du lecteur plus petites, barre des sous-titres à trois réglages, barre de la bibliothèque, bandeau de l'accueil fixe ; deuxième passage (28/10) : bandeau au-dessus des rangées, tailles Très compacte / Compacte / Normale, 27 icônes de bibliothèque | en cours (§ 4.21 ; captures dans `docs/screenshots/r3/`, numéros 200 à 208, et « avant ») | [#14](https://github.com/guim31/cine-doury/pull/14) |
| Essai réel 4 (28/10/2026, Shield en 4K, v0.9.0) : « Très compacte » par défaut, bandeau de l'accueil plus bas (une deuxième rangée se voit), titre de rangée immobile, « Retour » qui quitte sans confirmation, versions d'essai signées installables par-dessus une release | terminé, fusionné (§ 4.22 et § 4.18 ; captures dans `docs/screenshots/r4/` : `avant-*` / `apres-*` pour A, B et D) | [#15](https://github.com/guim31/cine-doury/pull/15) |
| Essai réel 5 (29/10/2026, Shield en 4K et Chromecast, v0.9.1) : banc de mesures réparé (libellés du rail lus par l'accessibilité), compte à rebours qui ne coupe plus la fin des épisodes, épisode précédent / suivant dans le lecteur, menu d'options rapides à l'appui long sur une jaquette, mosaïque « Ce soir » en haut de l'accueil | terminé, en revue (§ 4.23 ; captures dans `docs/screenshots/r5/` : `avant-*` / `apres-*` pour A, B, C et D) | [#16](https://github.com/guim31/cine-doury/pull/16) |

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

*(Fixés par le J0, puis complétés par chaque jalon. Vérifier chaque point contre l'OpenAPI de la
12.3, versionnée dans [`docs/api/jellyfin-openapi-12.3.json`](api/jellyfin-openapi-12.3.json) ; la
CI échoue si le serveur de test en sert une autre.)*

### 4.1 Modules (J0)

| Module | Plugin de convention | Contenu au J0 |
|---|---|---|
| `:core:model` | `cinedoury.jvm.library` | `Library`, `MediaItem`, `MediaSource`, `MediaStream`, `ImageRef`, `UserData`, `User`, `ServerInfo`, `Page`, `PageRequest` |
| `:core:source` | `cinedoury.jvm.library` | `SourceConnector` (informations publiques, connexion, ouverture d'une session), `LibrarySource` (bibliothèques, éléments paginés, détail), `SourceException` (erreurs typées) ; fausse source `InMemorySource` en *test fixtures* |
| `:source:jellyfin` | `cinedoury.jvm.library` | `JellyfinConnector` ; tests unitaires (MockWebServer, réponses enregistrées) et suite `integrationTest` |
| `:core:designsystem` | `cinedoury.android.library` + `.compose` | jetons (`CineColors`, `CineTypography`, `CineSpacing`, `CineSizes`, `CineScale`, `CineMotion`), `CineTheme`, composants TV |
| `:app:tv` | `cinedoury.android.application` + `.compose` | `MainActivity`, écran « catalogue du design system », captures Roborazzi |

Ajoutés au J1 :

| Module | Plugin de convention | Contenu |
|---|---|---|
| `:core:data` | `cinedoury.android.library` + KSP | Room (serveurs, profils), DataStore (préférences), Keystore (jetons), `PinHasher`, `AddressInput`, `AddressResolver`, `SessionManager`, `DeviceIdentity`, module Koin `dataModule` |
| `:core:ui` | `cinedoury.android.library` + `.compose` | ce que partagent les écrans et qui connaît `:core:source` : messages d'erreur lisibles (`toErrorText`, `SourceErrorPanel`), événements ponctuels (`Events`), indicateur « À distance », images de la session |
| `:feature:auth` | idem | premier lancement, serveurs du réseau, saisie d'adresse, Quick Connect, identifiants, choix du profil, PIN, serveur injoignable |
| `:feature:home` | idem | accueil **provisoire** (rail et tuiles des bibliothèques), remplacé au J2 par l'accueil réel et sa personnalisation |
| `:feature:settings` | idem | réglages du J1 (profil, PIN, démarrage, adresses, oubli du serveur, catalogue en débogage), complétés au J7 |

Ajouté au J2 : `:feature:library` (`cinedoury.android.library` + `.compose`), une bibliothèque :
onglets, vues, tri, filtres, saut A–Z. Ajouté au J3 : `:feature:details`, les fiches (§ 4.10).
Ajoutés au J5 (§ 4.12) : `:feature:search` et `:source:seerr`. Ajoutés au J4 (§ 4.11) : `:core:playback` (`cinedoury.android.library`, sans Compose) — lecture
des capacités de l'appareil, ExoPlayer derrière l'interface `VideoPlayer`, rapports, réglages de
l'appareil, modes de l'écran — et `:feature:player` (`.compose`) — l'écran du lecteur, ses
panneaux, les pages « Lecture et son », « Apparence des sous-titres » et le profil de l'appareil.
Le profil, la décision et les règles (vu, épisode suivant, « toujours là ») sont du Kotlin pur dans
`:core:model`, testés sans Android. Ajouté au J6 (§ 4.14) : `:feature:manage` (`.compose`), la
gestion depuis l'appli ; `ManageSource` dans `:core:source`, `ManageRepository` dans `:core:data`.
Ajoutés au J7 (§ 4.18) : `:core:update` (`cinedoury.jvm.library`, OkHttp et kotlinx.serialization
JSON déjà présents) — versions, client des releases GitHub, SHA-256, journal des changements — et
`:feature:update` (`.compose`) — le dépôt de la mise à jour, `PackageInstaller`, l'écran.

`:core:ui` n'était pas prévu au § 3 : les messages d'erreur servent à tous les écrans, mais
`:core:designsystem` ne doit pas connaître `:core:source`. Le design system reste sans dépendance
au domaine.

Les plugins de convention vivent dans `build-logic/` (build inclus) ; les versions dans
`gradle/libs.versions.toml`. Paquets Kotlin : `fr.guim.cinedoury.<module>`.

**Écart au § 3** : `:core:data`, `:core:playback`, `:feature:*` et `:benchmark` ne sont pas créés
vides au J0 ; chacun naît avec le jalon qui le remplit (J1 pour `:core:data` et `:feature:auth`, J4
pour `:core:playback`, la partie performances du J8 pour `:benchmark`, plugin `cinedoury.android.test`,
§ 4.16). Raison : un module vide ne teste rien et ralentit la
configuration.

### 4.2 Pile (versions vérifiées le 25/10/2026)

| Rôle | Choix | Version |
|---|---|---|
| Construction | Gradle (Kotlin DSL), Android Gradle Plugin avec Kotlin intégré (*built-in Kotlin*, pas de plugin `kotlin-android`) | 9.8.2, AGP 9.4.1 |
| Langage | Kotlin | 2.4.21 |
| SDK Android | `compileSdk` / `targetSdk` 37, **`minSdk` 30** (Android 11 : Shield TV ; le Chromecast avec Google TV à jour est en Android 12 ou plus) | — |
| Interface | Compose (BOM), Compose for TV `tv-material` | BOM 2026.09.00 (ui 1.12.1), 1.1.0 |
| Police | Inter, fichiers statiques 400/500/600/700, SIL OFL 1.1 (`core/designsystem/INTER-LICENSE.txt`) | 4.1 |
| Client Jellyfin | SDK Kotlin officiel `org.jellyfin.sdk:jellyfin-core` (variante JVM, OkHttp) | 1.9.3 |
| Coroutines | kotlinx.coroutines | 1.11.0 |
| Tests | JUnit 4, kotlin-test, MockWebServer 3, Robolectric, Roborazzi | 4.13.2, —, 5.5.1, 4.17, 1.77.0 |
| Analyse statique | Spotless + ktlint (`ktlint_official`), Detekt, Android Lint | 8.10.4 + 1.8.0, 1.23.8 |
| API Android des modules Kotlin purs (essai réel 2) | Animal Sniffer (`ru.vyarus.animalsniffer`, MIT) et la signature `com.toasttab.android:gummy-bears-api-30` (Apache 2.0) ; ni l'un ni l'autre n'entre dans l'APK | 2.0.1, 0.16.0 |
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`) | 3.6.3 |
| Stockage | Room (avec KSP, qui fonctionne avec le Kotlin intégré d'AGP 9), DataStore Preferences | 2.8.5 (KSP 2.3.12), 1.3.0 |
| Injection | Koin (`koin-android`, `koin-androidx-compose`), plutôt que Hilt : pas de traitement d'annotations | 4.2.2 |
| JSON (découverte UDP) | kotlinx.serialization JSON (déjà apportée par le SDK Jellyfin) | 1.12.0 |
| Lecture (J4) | Media3 : `media3-exoplayer`, `-exoplayer-hls`, `-ui` (`PlayerView`, sous-titres) | 1.11.1 |
| Tests sur l'émulateur (J4) | `androidx.test:runner`, `androidx.test.ext:junit` | 1.7.0, 1.3.0 |
| Version optimisée et mesures (perf/fluidite, § 4.16) | R8 (AGP), `androidx.profileinstaller:profileinstaller`, plugin `androidx.baselineprofile`, `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator:uiautomator` (Apache 2.0 ; seul `profileinstaller` entre dans l'APK) | —, 1.4.1, 1.5.0, 1.5.0, 2.4.0 |

**Extension FFmpeg de Media3 : écartée au J4.** Google ne la publie pas (il faut la construire
avec le NDK et un FFmpeg compilé à part) ; la seule publiée, `org.jellyfin.media3:media3-ffmpeg-decoder`
(Maven Central), est sous **GPL-3.0**, que `CLAUDE.md` exclut de l'APK, et suit Media3 1.9 (nous
sommes en 1.11). Ce qu'elle apporterait (décoder DTS, TrueHD sans ampli) est fait par le serveur :
le profil ne déclare pas ces codecs quand ni l'appareil ni l'ampli ne les prennent, et Jellyfin
transcode l'audio seul (vidéo copiée). À revoir si une version LGPL, construite par la CI, devient
utile.

### 4.3 Client Jellyfin : le SDK officiel

Décision du J0 : **SDK Kotlin officiel 1.9.3**, plutôt qu'un client généré depuis l'OpenAPI.
- Il est **généré par le projet Jellyfin depuis l'OpenAPI 12.3.0** (« Recommended API Version
  12.3.0 », minimum 12.0) et suit chaque version du serveur : on profite de sa génération sans en
  maintenir une (gabarits, sérialisation, cas particuliers de l'OpenAPI de Jellyfin).
- Il envoie l'en-tête `Authorization: MediaBrowser …` exigé par Jellyfin 12, jamais `X-Emby-Token`
  ni `api_key` (vérifié dans ses sources et par les tests unitaires).
- Sa variante JVM garde `:source:jellyfin` en Kotlin pur. Dans l'appli, Gradle choisit sa variante
  Android, dont `createJellyfin` exige un `Context` : depuis le J1, les clients sont construits par
  `OkHttpFactory` (`jellyfin-api-okhttp`), sans passer par `createJellyfin`.
- Ses exceptions (`InvalidStatusException`, `TimeoutException`…) se traduisent une fois pour toutes
  en `SourceException` (`JellyfinCall.kt`).
- **Licence : LGPL-3.0.** Ce n'est ni GPL ni AGPL (exclues par `CLAUDE.md`). Le code de l'appli
  restant public, quiconque peut la reconstruire avec une version modifiée du SDK, ce que demande la
  LGPL. La mention de licence est affichée dans Réglages → Appareil → « À propos » (J7). *À confirmer
  par le mainteneur.*
- Il est **entièrement caché** derrière `:core:source` (dépendance `implementation`, types
  `internal`) : le remplacer par un client généré ne toucherait que `:source:jellyfin`.

### 4.4 Source (J0)

- `SourceConnector.serverInfo(adresse)` lit `/System/Info/Public` et refuse un serveur antérieur à
  la 12.3 (`SourceException.Incompatible`).
- `signIn(adresse, Credentials.Password)` : `POST /Users/AuthenticateByName` ; un 401 devient
  `InvalidCredentials`. La `Session` (adresse, serveur, utilisateur, jeton) masque le jeton dans
  `toString()`.
- `LibrarySource.libraries()` : `GET /UserViews`. Les bibliothèques de musique, livres, photos,
  listes de lecture et TV en direct sont écartées (hors périmètre, § 2).
- `items(bibliothèque, PageRequest)` : `GET /Items?parentId=…&sortBy=SortName&collapseBoxSetItems=false`,
  avec le nombre total. Les films d'une collection restent listés un par un.
- `item(id)` : `GET /Items/{id}?userId=…`, avec les sources média et leurs pistes (codec, langue,
  forcé, canaux, plage dynamique).
- Les identifiants Jellyfin circulent sous leur forme serveur : 32 chiffres hexadécimaux sans tirets.
- Le client s'annonce `Client="Cine Doury"` : le SDK recopie ce nom dans `User-Agent`, où OkHttp
  refuse tout caractère non ASCII (voir `CLAUDE.md`, « Pièges »). **Écart** à la forme
  `Client="Ciné Doury"` ci-dessous.

### 4.5 Design system (J0)

- `CineTheme(variant = ThemeVariant.Dark | OledBlack, reducedMotion)` fournit les jetons de
  `UI.md` § 4, 5 et 7 et alimente le `MaterialTheme` de Compose for TV.
- Focus (`Modifier.cineFocusable`, état `CineFocus` lu de façon synchrone par `onFocusChanged`, que les composants acceptent en paramètre pour qu'un parent sache qui a le focus ; `CineFocus.preview()` le force pour les captures) : échelle 1,08 (affiches) ou 1,05 (boutons, vignettes), contour
  de 3 dp écarté de 2 dp, ombre ; 150 ms en décélération à l'arrivée, 120 ms en accélération au
  départ ; l'animation ne recompose pas l'élément (lecture dans `graphicsLayer` et au dessin).
- Composants : `PosterCard` (3 tailles, progression, triangle « non vu », pastille d'épisodes,
  titre au focus seulement), `ThumbnailCard`, `GeneratedArtwork` (carte sans image : titre sur un
  dégradé tiré du nom), `PrimaryButton`, `RoundIconButton` (libellé au focus), `NavigationRail`
  (72 dp fermé, 240 dp ouvert au focus, par-dessus le contenu), `Modifier.readabilityScrims`,
  `CineIcons`.
- Captures de référence : `app/tv/src/test/screenshots/` (vérifiées par la CI), copiées dans
  `docs/screenshots/j0/` : 9 vues du catalogue × 2 thèmes (sombre, noir OLED), en 1920 × 1080.
  L'élément focalisé y est dessiné par `CineFocus.preview()` (Robolectric ne donne pas le focus en
  rendu natif) ; la navigation réelle à la croix est testée par `CatalogNavigationTest`.

### 4.6 Retouches du design system (J1)

- **Titre une seule fois** : une carte générée ne montre dessous que la ligne secondaire ; une carte
  avec image garde titre et ligne secondaire (`UI.md` § 6).
- **Halo de focus** : `BlurMaskFilter` (flou de 14 dp, blanc à 40 %, jeton `focusHalo`) dessiné
  derrière le contour, dans `Modifier.cineFocusable` ; il marche dès l'API 28 avec l'accélération
  matérielle, sans `RenderEffect` (API 31).
- **Fond d'ambiance** : `AmbientBackdrop(Ambience)`, BlurHash décodé en 32 × 18 (`BlurHash`,
  décodeur maison de 150 lignes plutôt qu'une dépendance), étiré en bilinéaire, assombri à 60 %
  (`CineAmbient`), sous les voiles ; 250 ms d'arrêt puis fondu de 300 ms. Une carte générée donne
  son dégradé.
- **Images** : `RemoteImage(ImageSource, blurHash)` sur Coil 3 ; l'URL est demandée à la taille de
  la mise en page en pixels ; le BlurHash sert d'aperçu et reste si l'image échoue.
- Composants ajoutés pour le J1 : `CineTextField`, `SecondaryButton`, `PrimaryButton(destructive)`,
  `ProfileAvatar`, `ProfileTile`, `KeypadKey`, `PinDots`, `ListRow`, `ConfirmDialog`,
  `MessagePanel`, `StatusPill`, `FormScreen`, `Wordmark`, `InitialFocus`, `LocalPreviewFocus`.

### 4.7 Connexion et profils (J1)

**Source** (`:core:source`) : `SourceConnector` gagne `publicUsers`, `quickConnectEnabled`,
`startQuickConnect`, `isQuickConnectApproved`, `signIn(Credentials.QuickConnect)`, `signOut`,
`imageUrl`, `userImageUrl` ; `ServerDiscovery.discover()` ; erreurs `QuickConnectDisabled` et
`QuickConnectExpired`.

**Stockage** :

| Où | Quoi |
|---|---|
| Room `servers` | identifiant Jellyfin du serveur, nom, version, adresse locale, adresse distante (facultative), date d'ajout |
| Room `profiles` (clé : serveur + utilisateur) | nom, administrateur, étiquette de l'avatar, public ou non, **jeton chiffré** (Base64), **empreinte du PIN**, dernière utilisation |
| Android Keystore | **seulement la clé** AES-256-GCM `cinedoury.tokens`, qui ne sort jamais du Keystore. Le jeton chiffré est `IV (12 octets) + texte chiffré + étiquette`, en Base64. Une clé perdue (jeton illisible) déconnecte le profil |
| DataStore `settings` | dernier serveur, dernier profil, mode de démarrage |
| `noBackupFilesDir/device-id` | le `DeviceId` |

- La base n'est pas sauvegardée (`allowBackup="false"`). Version 1 au J1 ; version 2 et schéma
  exporté depuis le J2 (§ 4.9).
- **`DeviceId`** : 32 chiffres hexadécimaux tirés au hasard au premier lancement (UUID sans tirets),
  gardés dans `noBackupFilesDir` : stable pour toute la vie de l'installation, jamais copié sur un
  autre appareil, effacé avec l'appli. **Nom d'appareil** : le nom donné à la TV dans ses réglages
  (`Settings.Global.DEVICE_NAME`), à défaut le modèle.
- **PIN** : 4 chiffres, local à l'appareil (Jellyfin 12 n'a plus de PIN), stocké en
  `pbkdf2-sha256:20000:<sel>:<empreinte>` (sel de 16 octets), calculé hors du fil principal. Quatre
  chiffres ne résistent pas à une recherche par qui détient la base : le PIN protège un profil d'un
  enfant, pas d'un voleur. Un nouveau PIN se saisit deux fois ; le retirer demande l'actuel.
- **Plusieurs comptes** restent connectés sur l'appareil, un jeton par utilisateur. Se déconnecter
  appelle `POST /Sessions/Logout` avec le jeton du profil (si le serveur répond) puis l'oublie ;
  oublier le serveur déconnecte tous ses profils puis efface serveur et profils.

**Découverte** : « `Who is JellyfinServer?` » en UDP vers le port 7359, sur l'adresse de diffusion
de chaque interface active et sur `255.255.255.255`, trois fois à une seconde d'intervalle ; chaque
réponse JSON (`Id`, `Name`, `Address`) donne un serveur, émis une fois, au fur et à mesure, pendant
3 s. Sans `Address`, l'adresse de l'émetteur et le port 8096.

**Saisie d'adresse** : `http://` supposé sans schéma, port 8096 supposé en `http` sans port, chemin
conservé (`https://example.com/jellyfin`). L'adresse est vérifiée par `serverInfo` (Jellyfin 12.3
ou plus) avant d'être gardée. Les adresses en `http://` exigent `usesCleartextTraffic="true"` dans
le manifeste (un serveur de maison répond en clair sur le réseau local).

**Quick Connect** :
1. `GET /QuickConnect/Enabled` ; s'il est désactivé, l'écran du mot de passe prend la place.
2. `POST /QuickConnect/Initiate` (en-tête `Authorization` sans jeton, avec notre `DeviceId`) : le
   code à 6 chiffres s'affiche en deux groupes de trois.
3. `GET /QuickConnect/Connect?secret=…` toutes les 2 s, sans bloquer l'écran.
4. Approuvé : `POST /Users/AuthenticateWithQuickConnect` avec le secret ; la session est celle de
   l'utilisateur qui a validé, quel que soit le profil choisi.
5. Le serveur oublie la demande après 10 min (réponse 404 « Unknown secret ») : « Ce code a
   expiré », bouton « Nouveau code ».

**Choix de l'adresse** au démarrage (`AddressResolver`) : l'adresse locale d'abord (3 s au plus),
puis la distante (10 s au plus). Une adresse ne compte que si le serveur qui y répond a le même
identifiant que le serveur enregistré. Si aucune ne répond : écran « Le serveur ne répond pas »
(« Réessayer », « Changer de serveur »), avec l'erreur la plus parlante (serveur trop ancien, puis
autre serveur, puis silence). En mode distant, un indicateur discret « À distance » s'affiche
(choix du profil, accueil, réglages).

**Démarrage** (`SessionManager.start`) : sans serveur, l'accueil du premier lancement ; sinon,
le serveur est joint, puis selon le réglage « Au démarrage » : **« Choix du profil »** (par défaut,
une TV de salon est partagée) ou **« Dernier profil »** (ouvert directement, derrière son PIN s'il
en a un).

**Profils** : les utilisateurs publics du serveur (`/Users/Public`, rafraîchis à chaque passage
sur l'écran) et tous ceux qui se sont connectés sur l'appareil (même cachés), triés par nom. Avatar :
l'image de l'utilisateur (`/UserImage?userId=…&tag=…`, servie en une seule taille), sinon l'initiale
sur un disque coloré d'après le nom. Un profil connecté s'ouvre directement (après son PIN) ;
sinon Quick Connect, puis mot de passe.

**Captures** (`docs/screenshots/j1/`, 1920 × 1080, sombre et noir OLED, 26 vues × 2) : le catalogue
retouché (01 à 11, dont le fond d'ambiance en 10 et 11), puis premier lancement (20), serveurs trouvés
(21), saisie d'adresse (22), Quick Connect (23), identifiants (24), choix du profil (25), PIN (26,
27), erreurs : serveur injoignable (28), identifiants refusés (29), code expiré (30), serveur trop
ancien (31), accueil provisoire (32), réglages (33), oubli du serveur (34). Tests : `J1ScreenshotTest`,
`J1NavigationTest` (croix directionnelle), `AppFlowTest` (parcours complet sur une source en
mémoire, avec Room, DataStore et les vrais modèles de vue).

**Navigation** : une pile d'écrans maison (`Navigator`, une douzaine d'écrans, aucun lien profond),
chaque entrée ayant son `ViewModelStore` et son état sauvegardé ; « Retour » dépile, un parcours
achevé (connecté, serveur oublié) remplace toute la pile.

### 4.8 Contrats Jellyfin

- **Authentification** : uniquement l'en-tête
  `Authorization: MediaBrowser Client="Ciné Doury", Device="<nom>", DeviceId="<id>", Version="<x.y.z>", Token="<jeton>"`.
  Jellyfin 12 **refuse** `X-Emby-Token` et `?api_key=` (401). `DeviceId` est stable par installation.
- **Découverte** : diffusion UDP sur le port 7359 (`Who is JellyfinServer?`), réponse JSON (adresse, identifiant, nom). Détail au § 4.7.
- **Quick Connect** : `POST /QuickConnect/Initiate` (code à afficher), interrogation de `/QuickConnect/Connect?secret=`, puis `POST /Users/AuthenticateWithQuickConnect`. Détail au § 4.7.
- **Images** : toujours demandées à la taille affichée (`fillWidth`/`fillHeight`, `quality`, `tag`). Le BlurHash fourni par le serveur sert d'aperçu.
- **Lecture** :
  - `POST /Items/{id}/PlaybackInfo`, avec un **profil d'appareil construit d'après les capacités réelles** (MediaCodecList, HDR de l'écran, passthrough audio) ;
  - choix lecture directe / remux / transcodage, et la **raison** du choix, exposée à l'écran d'informations ;
  - rapports `POST /Sessions/Playing`, `/Sessions/Playing/Progress` (toutes les 10 s, ainsi qu'à la pause et après un saut), `/Sessions/Playing/Stopped`.
- **Segments** : `GET /MediaSegments/{itemId}` (types Intro, Outro, Recap, Preview, Commercial).
- **Aperçus** (trickplay) : métadonnées dans l'élément, tuiles `/Videos/{id}/Trickplay/{largeur}/{index}.jpg`.
- **Requêtes de films** : un serveur qui a des collections **regroupe** les films par défaut. Passer `CollapseBoxSetItems=false` pour lister ou compter les films.
- **Textes** : aucune chaîne en dur ; ressources `fr` (langue par défaut) et `en`.
- **Versions** : semver, étiquettes `vX.Y.Z`. **Écart (J7)** : l'APK de release est signé **par le workflow « Release »** avec la clé de release lue dans les secrets du dépôt (§ 4.18), plutôt qu'à la main ; la clé n'entre jamais dans le dépôt. La CI produit en plus un APK de débogage.

### 4.9 Accueil et navigation (J2)

**Source** (`:core:source`, `LibrarySource`) :

| Fonction | Appel Jellyfin | Paramètres notables |
|---|---|---|
| `libraries()` | `GET /UserViews` | `includeHidden=true` : toutes les bibliothèques, dans l'ordre `OrderedViews` de l'utilisateur ; celles de `MyMediaExcludes` restent joignables par le rail |
| `homeExclusions()` | `GET /Users/Me` | `Configuration.MyMediaExcludes` (exclues de l'accueil) et `LatestItemsExcludes` (pas d'« Ajouts récents ») |
| `items(ItemQuery, PageRequest)` / `count(ItemQuery)` | `GET /Items` | voir ci-dessous ; `count` envoie `limit=0` et lit `TotalRecordCount` |
| `resume(bibliothèque?, n)` | `GET /UserItems/Resume` | `mediaTypes=Video` (sinon saisons et séries entamées s'y ajoutent), `parentId` |
| `nextUp(bibliothèque?, depuis?, n)` | `GET /Shows/NextUp` | `nextUpDateCutoff`, `enableResumable=false` (les épisodes entamés sont dans « Reprendre »), `enableRewatching=false` |
| `latest(bibliothèque, n)` | `GET /Items/Latest` | `parentId`, `groupItems=true` (les épisodes se regroupent en série) |
| `filterOptions(bibliothèque, types)` | `GET /Items/Filters`, `/Items/Filters2`, `/Studios`, `/Persons`, `/Items?includeItemTypes=BoxSet` | genres, années, classifications, étiquettes ; langues audio et de sous-titres ; studios ; 200 personnes (acteurs, réalisateurs) ; collections |

Les listes demandent `SortName`, `Overview`, `Genres`, `DateCreated`, `Taglines`, `ChildCount`,
`PrimaryImageAspectRatio`, une image par type (Primary, Backdrop, Logo, Thumb) et les données
utilisateur. Un épisode porte aussi les images de sa série (`ParentBackdropImageTags`,
`ParentLogoImageTag`, `SeriesPrimaryImageTag`), lues dans `ItemImages`.

**Requête** (`ItemQuery`, `:core:model`) → `GET /Items` :
- sans type, les enfants directs de la bibliothèque ; avec types (`Movie`, `Series`, `BoxSet`), toute
  la bibliothèque (`recursive=true`) ;
- **tri** : `SortName`, `DateCreated`, `PremiereDate`, `CommunityRating`, `Runtime`, `DatePlayed`,
  `PlayCount`, `Random`, croissant ou décroissant ; à égalité, par titre, pour que deux pages ne se
  recoupent jamais ;
- **films regroupés en collections** : `collapseBoxSetItems=true` (avec `recursive`), sinon `false` ;
- **saut A–Z** : `nameLessThan=<lettre>` et `limit=0` donnent le nombre de titres avant la lettre,
  donc l'index où sauter ; « # » est l'index 0 (les titres à chiffre précèdent « A ») ;
- **« Récemment sortis »** : `maxPremiereDate` = aujourd'hui, tri par date de sortie décroissante.

**Filtres** (`ItemFilters`), tous appliqués par le serveur ; les valeurs d'un filtre s'additionnent,
les filtres se combinent :

| Filtre | Paramètre |
|---|---|
| vu / non vu / en cours | `isPlayed=true` / `isPlayed=false` / `filters=IsResumable` |
| genre, classification, étiquette | `genres`, `officialRatings`, `tags` (clé répétée) |
| année ou décennie | `years` (une décennie donne ses dix années) |
| note minimale | `minCommunityRating` |
| résolution | `minWidth` / `maxWidth` sur la largeur de l'image : SD < 1200, 720p < 1900, 1080p < 3800, 4K au-delà (un film en 2,39:1 en 4K fait 3840 × 1608) |
| langue audio, sous-titres | `audioLanguages`, `subtitleLanguages` (ISO 639-2) |
| studio, personne | `studioIds`, `personIds` |
| collection | `parentId` = la collection (au lieu de la bibliothèque) |
| favori | `isFavorite=true` |

Les filtres **HDR / Dolby Vision**, **format audio** et **codec vidéo**, que Jellyfin 12.3 ne sait
pas appliquer, passent par l'index local du J3 (§ 4.10).

**Rangées** (`:core:data`, `home`) :
- `HomeRowConfig(id, type, bibliothèques, style, fenêtre)` ; `type` ∈ `Resume`, `NextUp`, `Latest`,
  `RecentlyReleased` ; `style` ∈ `Poster`, `Thumbnail` ; `bibliothèques` vide = toutes ; `fenêtre`
  (« À suivre » seulement) : 7, 14 (défaut, comme le `OnDeckWindow = 2` de Plex), 30, 90 jours ou
  sans limite.
- Accueil par défaut : Reprendre (vignettes), À suivre (vignettes), Ajouts récents (affiches, **une
  rangée par bibliothèque**), Récemment sortis (affiches).
- `HomeRows.plan` déroule la disposition en rangées affichées (`HomeRowSpec`, clé stable
  `id` ou `id:bibliothèque`) : les bibliothèques de `MyMediaExcludes` n'apparaissent dans aucune
  rangée, celles de `LatestItemsExcludes` n'ont pas d'« Ajouts récents », la vue « Collections »
  n'a pas de rangée. Une rangée sans bibliothèque possible disparaît. Types de bibliothèques par
  rangée : À suivre → séries ; Récemment sortis → films et séries ; Reprendre, Ajouts récents →
  films, séries, vidéos personnelles.
- Lecture : sans exclusion ni choix de bibliothèques, un appel pour tout le serveur ; sinon un appel
  par bibliothèque, en parallèle, fusionnés (Reprendre par dernière lecture, Récemment sortis par
  date de sortie). 24 éléments par rangée.
- **Personnalisation** (écran « Personnaliser l'accueil », depuis l'accueil ou les réglages) :
  ajouter, retirer, monter, descendre une rangée ; affiches ou vignettes ; bibliothèques de la
  rangée ; fenêtre d'« À suivre » ; rétablir l'accueil par défaut ; bibliothèques **cachées du
  rail** (FONCTIONNALITES § 3, « visibilité réglable »). Gardée **par profil, sur l'appareil**.

**Rail** : Recherche, Accueil, une entrée par bibliothèque (ordre `OrderedViews`, moins celles
cachées par l'utilisateur), puis le profil (son avatar et son nom ; il mène au choix du profil) et
Réglages. Fermé
(72 dp) avec les icônes, il s'ouvre au focus ; y entrer redonne le focus à la dernière entrée
choisie, sinon à l'écran courant. **Écart** : les bibliothèques de `MyMediaExcludes` restent dans le
rail (exclues de l'accueil seulement), parce que FONCTIONNALITES § 2 veut qu'elles « restent
accessibles par la navigation » ; les cacher du rail est un choix local.

**Mémoire du focus et de la position** :
- une rangée garde l'index de la carte quittée (`rememberSaveable`, qui survit à la sortie de la
  rangée de l'écran) et le rend quand le focus y revient (`focusRestorer`, sur la carte de cet
  index) ; elle défile pour garder la carte focalisée à une place fixe : deuxième colonne pour les
  affiches, première pour les vignettes (`FocusPivot`) ;
- l'accueil garde la rangée focalisée et la position de défilement ; « Retour » depuis une fiche ou
  une bibliothèque y ramène exactement (état sauvegardé par entrée de la pile) ;
- une bibliothèque garde **en base**, par profil : onglet, vue, tri, filtres, regroupement des
  collections et **index** de l'élément focalisé (écrit 500 ms après le dernier déplacement) ; elle
  rouvre la grille à cet index, sa page lue en premier ;
- « Retour » sur l'accueil : en haut de page (focus sur « Lire »), puis le rail, puis l'appli se
  ferme, **sans confirmation** depuis l'essai réel 4 (§ 4.22 ; « Quitter Ciné Doury ? » auparavant).
  Depuis un autre écran de premier niveau (bibliothèque, recherche, réglages), « Retour » ramène à
  l'accueil ; ailleurs, il remonte d'un niveau.

**Pagination** : `ItemPager` lit des pages de 60 ; la première donne le total et la grille pose
d'emblée toutes ses cases (squelettes de la forme exacte des cartes) : rien ne bouge quand une page
arrive. Une case qui entre à l'écran demande sa page, et la suivante passé le milieu de la page.

**Stockage** (Room, version 2, schéma exporté dans `core/data/schemas/` et contrôlé par la CI) :

| Table | Clé | Contenu |
|---|---|---|
| `cached_items` | serveur, utilisateur, élément | ce qu'une carte et le bandeau montrent (titre, années, durée, notes, genres, images avec leur BlurHash, données utilisateur, épisode), jamais les sources média |
| `home_row_items` | serveur, utilisateur, rangée, position | l'élément à cette place de la rangée |
| `cached_libraries` | serveur, utilisateur, bibliothèque | position, nom, type, image, exclusions |
| `home_layout` | serveur, utilisateur, rangée | position, type, bibliothèques, style, fenêtre |
| `library_state` | serveur, utilisateur, bibliothèque | onglet, vue, tri, filtres (`FiltersCodec`), regroupement, position |
| `rail_hidden` | serveur, utilisateur, bibliothèque | bibliothèque cachée du rail |

- **Migration 1 → 2** : automatique (`@AutoMigration`), testée par `CineDatabaseMigrationTest`, qui
  crée la base du J1 d'après `1.json` (reconstitué à l'identique du J1 : même hachage d'identité) et
  l'ouvre en version 2.
- **Politique du cache** : l'accueil s'affiche depuis Room tout de suite, puis
  `HomeRepository.refresh` relit bibliothèques, exclusions et rangées et **remplace tout le cache du
  profil dans une transaction** ; Room rend les nouvelles rangées à l'écran, aux mêmes clés, donc
  sans saut ni perte du focus. Rafraîchi à l'ouverture de l'accueil et à chaque retour sur lui. Un
  échec laisse le cache en place ; l'erreur ne s'affiche que s'il n'y a rien à montrer. Les éléments
  qu'aucune rangée n'utilise sont supprimés à chaque remplacement. Se déconnecter oublie le cache du
  profil ; oublier le serveur oublie aussi ses préférences. Les pages d'une bibliothèque ne sont pas
  mises en cache (la première arrive en une requête).
- **Images** : les premières images de la rangée suivante sont chargées d'avance, à la taille des
  cartes (`PrefetchImages`, mêmes URL que `RemoteImage`).

**Performances** (60 images/s visées) : clés stables et `contentType` dans toutes les listes ;
états immuables (`@Immutable`), un nouvel objet seulement quand il change ; images des cartes
mémorisées (`rememberArt`) pour ne pas passer une lambda neuve à chaque composition ; animation du
focus lue au dessin, jamais en composition ; fondus du bandeau et du fond indexés par identifiant ;
un seul fond d'ambiance (BlurHash 32 × 18). Mesures sur la Shield : § 4.16 et § 4.20.

**Captures** (`docs/screenshots/j2/`, 1920 × 1080, sombre et noir OLED) : accueil, bandeau sur un
épisode à reprendre (40) et sur un film (41), rail ouvert (42), squelettes (43), « Quitter » (44),
personnalisation (45) ; bibliothèque en grille (50), petites affiches (51), liste (52), vignettes
(53), filtres et résumé (54), valeurs d'un filtre (55), tri (56), A–Z (57), Collections (58), Genres
(59), squelettes (60). Les réglages (33, 34) gagnent la section « Accueil ». En bas du rail, l'entrée du
profil porte son nom et son avatar (« Changer de profil » ne tient pas dans le rail ouvert). Tests :
`J2ScreenshotTest`, `J2NavigationTest` (croix directionnelle et « Retour »), `J2FlowTest` (« Retour »
depuis une fiche rend la carte quittée, sur l'appli entière), `HomeRepositoryTest`,
`HomeRowsTest`, `HomeLayoutsTest`, `ItemPagerTest`, `FiltersCodecTest`, `CineDatabaseMigrationTest`,
`JellyfinBrowseIntegrationTest` (rangées, filtres, tri, pagination, exclusions, A–Z).

**Design system** (ajouts du J2) : `FeaturedBanner` et `FeaturedBackdrop` (bandeau qui suit le
focus après 250 ms d'arrêt), `FocusPivot` (place fixe du focus dans une liste), `SkeletonCard`,
`SkeletonLine`, `SkeletonListRow`, `RowTitle`, `CineTab`, `AlphabetBar`, `SidePanel` (glissement et
fondu de 200 ms), `MediaListRow`, `PrefetchImages` ; `RailDestination.avatar`, le rail qui rend le
focus à son entrée choisie, `ConfirmDialog(destructive = false)`, `ListRow(trailingIcon)`, le
« non vu » sur les vignettes. Jetons : `heroTextWidth`, `tabHeight`, `alphabetKey`,
`alphabetWidth`, `panelWidth`, `listRowPoster`, `listRowHeight`, `skeletonLine`,
`HERO_HEIGHT_FRACTION`.

**Écrans provisoires** : Recherche (J5) s'ouvrait sur un écran « arrive avec une prochaine
version » (`ComingSoonScreen`) ; remplacée au J5 (§ 4.12). Le lecteur provisoire du J3 a laissé la place au vrai lecteur
au J4 (§ 4.11).

### 4.10 Fiches (J3)

**Module** : `:feature:details` (`cinedoury.android.library` + `.compose`) : fiches film, série,
épisode, personne et collection, panneaux (pistes, « ⋯ », informations sur le fichier) et l'écran
provisoire du lecteur (`PlaybackPreviewScreen`, remplacé au J4).

**Source** (`:core:source`, `LibrarySource`) :

| Fonction | Appel Jellyfin | Notes |
|---|---|---|
| `item(id)` | `GET /Items/{id}?userId=…` | tout : sources média et pistes (`Profile`, `DisplayTitle`, `AudioSpatialFormat`, `ChannelLayout`), `People` (avec `PrimaryImageTag` et BlurHash), `Studios`, `Status`, `EndDate`, `RemoteTrailers`, `LocalTrailerCount`, `ProductionLocations` |
| `seasons(série)` | `GET /Shows/{id}/Seasons` | données utilisateur (`UnplayedItemCount`, `Played`) |
| `episodes(série)` | `GET /Shows/{id}/Episodes` | **toutes** les saisons en un appel : le bouton intelligent et les onglets en dépendent ; `Overview`, une image par type |
| `similar(id, n)` | `GET /Items/{id}/Similar` | seulement des titres de la bibliothèque (12) |
| `collectionsOf(id)` | `GET /Items/{id}/Collections` | les collections (sagas) qui contiennent le film, en un appel |
| `collectionItems(collection)` | `GET /Items?parentId=<collection>` | tri `PremiereDate, ProductionYear, SortName` croissant : ordre de sortie |
| `filmography(personne)` | `GET /Items?personIds=…&recursive=true&includeItemTypes=Movie,Series` | limitée à la bibliothèque par construction, du plus récent au plus ancien |
| `localTrailers(id)` | `GET /Items/{id}/LocalTrailers` | fichiers `*-trailer.*` ou `trailers/` à côté du film |
| `setPlayed(id, vu)` | `POST` / `DELETE /UserPlayedItems/{id}` | une saison ou une série marque tous ses épisodes ; rend les nouvelles données utilisateur |
| `setFavorite(id, favori)` | `POST` / `DELETE /UserFavoriteItems/{id}` | idem |
| `playbackPreferences()` | `GET /Users/Me` | `Configuration` : `AudioLanguagePreference`, `SubtitleLanguagePreference`, `SubtitleMode`, `PlayDefaultAudioTrack` |
| `itemIds(requête)` | `GET /Items` (`fields` vide, sans images ni données utilisateur) | les identifiants d'une requête, dans son ordre (≈ 400 octets par film) |
| `items(ItemQuery(ids = …))` | `GET /Items?ids=…` | 100 identifiants au plus par requête (`MAX_IDS`) : le serveur refuse une adresse de plus de 8 Ko (414) |
| `technicalItems(bibliothèque, depuis, page)` | `GET /Items?parentId=…&recursive=true&includeItemTypes=Movie,Episode&fields=MediaSources,SortName&enableImages=false&enableUserData=false[&minDateLastSaved=…]` | les pages de l'index local, triées `SortName, DateCreated` |

**Fiche** (`UI.md` § 8) :
- s'ouvre **tout de suite** sur la carte choisie (titre, images, métadonnées du cache de l'accueil),
  avec les badges de l'index local s'il connaît le titre ; le détail du serveur complète ensuite
  (sources, pistes, distribution, saga, similaires, bande-annonce) sans déplacer le focus ;
- fond plein écran sous les voiles (`readabilityScrims`) et fond d'ambiance du titre ; affiche
  (190 × 285) à gauche, qui **vole** depuis la carte ouverte ; colonne de 45 % (432 dp) : logo (texte
  à défaut), ligne de métadonnées avec **heure de fin**, genres, badges, synopsis sur 3 lignes
  dépliable (OK), réalisation et scénario ; les **boutons** sous l'affiche et le texte, sur toute la
  largeur (écart au cahier : dans la colonne de texte, « Reprendre — 1 h 09 restantes » et six
  boutons ronds débordaient de l'écran) ;
- **focus initial** sur « Lire » / « Reprendre — 1 h 09 restantes » (série : bouton intelligent) ;
  personne et collection : leur première carte ; boutons ronds dans cet ordre : Depuis le début
  (titre entamé), Bande-annonce, Audio et sous-titres, Marquer comme vu, Favori, Aller à la série
  (épisode), ⋯ ;
- sous le héros : saisons et épisodes (série), Distribution (photos, rôle sous le nom), saga (dans
  l'ordre de sortie), Titres similaires ; personne : Films, Séries de la bibliothèque ;
- « Retour » ferme un panneau (le focus revient au bouton qui l'a ouvert), sinon revient à l'écran
  d'où l'on vient. La fiche garde l'élément focalisé (état sauvegardé par entrée de la pile), et
  chaque rangée la carte quittée : revenir d'une personne ou d'un épisode y ramène.
- **Vu / favori** : changés à l'écran tout de suite (moins de 100 ms), puis envoyés au serveur ; une
  série relit ses épisodes une fois la réponse reçue.

**Heure de fin** : maintenant + (durée − position si le titre est entamé), arrondie à la minute la
plus proche ; elle suit l'horloge (`LocalClock`) tant que la fiche est ouverte. « se termine à
22 h 47 » (« ends at 10:47 PM »).

**Bouton intelligent d'une série** (`SmartPlay`, `:core:model`), sur la liste de tous ses épisodes :
1. un épisode entamé et non fini (position > 0, non vu) : le dernier lu → « Reprendre S02E05 — 12 min
   restantes » ;
2. sinon le premier épisode non vu **après** le dernier vu (par date de lecture) → « Lire S02E05 » ;
3. sinon le premier épisode non vu ;
4. sinon (tout vu, ou rien de connu) le premier épisode.
Les spéciaux (saison 0) ne comptent que si la série n'a rien d'autre.

**Saisons** : en onglets (`CineTab`), avec « Marquer la saison comme vue » ; la saison affichée est
celle de l'épisode du bouton intelligent. **Une série à saison unique est aplatie** : ses épisodes
viennent directement sous le titre « Épisodes », sans onglet. Épisode : vignette 16:9, numéro et
titre, durée et date de diffusion, résumé sur deux lignes, progression ; un bouton rond à droite le
marque vu ou non vu.

**Pré-sélection des pistes** (`TrackPreselection`, `:core:model`), d'après les préférences du
profil sur le serveur ; la version est la première source (Jellyfin met la meilleure en tête) :
- **audio** : la langue préférée (sa piste par défaut, sinon la première) ; sans langue préférée, la
  piste par défaut du fichier si `PlayDefaultAudioTrack`, sinon la première ;
- **sous-titres**, selon le mode :
  - *Aucun* : aucun ;
  - *Forcés seulement* : une piste forcée dans la langue des sous-titres préférée ou dans celle de
    l'audio (sinon une piste forcée sans langue) ;
  - *Toujours* : une piste complète (non forcée) dans la langue préférée — sa piste par défaut, sinon
    une non « malentendants » —, sinon une forcée ; sans langue préférée, la piste par défaut ;
  - *Intelligent* : comme *Toujours* si l'audio n'est pas dans la langue des sous-titres préférée,
    sinon comme *Forcés seulement* ;
  - *Par défaut* : une piste forcée (comme *Forcés seulement*), sinon une piste marquée par défaut
    dans le fichier (dans la langue préférée d'abord).
- Les codes ISO 639-2 bibliographiques (« fre ») valent les terminologiques (« fra »).
- Panneau « Audio et sous-titres » (un seul, à droite) : « Lire avec ces choix », la **version**
  (s'il y en a plusieurs : « 4K — HEVC — Dolby Vision — 48,3 Go »), l'**audio** (« Français — 5.1 —
  E-AC3 — par défaut »), les **sous-titres** (« Sans sous-titres », « Français — SRT — forcés »).
  Changer de version refait la pré-sélection pour elle. Le choix part au lecteur dans un
  `PlaybackRequest(item, TrackSelection(sourceId, audioIndex, subtitleIndex), resume)` ; l'écran
  provisoire du J4 montre ce qui serait lu.

**Badges** (`TechnicalSummary.badges`, `:core:model`), toutes versions confondues, dans l'ordre :
résolution (4K, 1080p, 720p, SD, d'après la largeur), meilleure plage dynamique (Dolby Vision >
HDR10+ > HDR10 > HLG), meilleur audio (Atmos > DTS:X > TrueHD > DTS-HD > DTS), canaux (7.1, 5.1),
langues : **VF** si une piste audio est dans la langue du profil (sa langue audio préférée, à défaut
celle de l'appareil), **VOST** si une autre langue a des sous-titres dans cette langue, **VO** sinon.
Atmos : `AudioSpatialFormat = DolbyAtmos` ou « Atmos » dans le profil du codec ; DTS-HD : profil
« DTS-HD MA/HRA » ; DTS:X : `AudioSpatialFormat = DTSX`.

**Bande-annonce** : la bande-annonce locale d'abord (jouée par le lecteur, comme un film), sinon la
première distante, ouverte par un intent : `vnd.youtube:<id>` (appli YouTube de la TV), à défaut
l'adresse web ; bouton masqué s'il n'y en a pas.

**Menu « ⋯ »** (vide de gestion jusqu'au J6) : « Marquer comme non vu » (ou « vu »), « Informations
sur le fichier » (chemin, conteneur, taille, débit ; chaque piste décrite, et sa description
serveur).

**Transition d'ouverture** (`UI.md` § 7) : `SharedTransitionLayout` + `AnimatedContent` autour de
la pile d'écrans ; seule l'ouverture d'une fiche s'anime (fondu de 250 ms, l'affiche vole de sa carte
à sa place, `Modifier.sharedPoster(id)`) ; tout autre changement d'écran est instantané, et aucun
avec les animations réduites. La fiche prend le focus à l'image suivante : une touche pressée pendant
le vol s'applique à la fiche. Une même affiche n'est partagée qu'une fois par écran (première rangée
qui la montre ; sur la fiche, le héros).

**Index local** (Room, `:core:data`, `LibraryIndex`) — retouche A3 :

| Table | Clé | Contenu |
|---|---|---|
| `media_index` | serveur, élément | bibliothèque, `Movie` ou `Episode`, série (épisode), titre, largeur maximale, **masques de bits** des plages dynamiques, codecs vidéo et caractéristiques audio (`DynamicRange`, `VideoCodec`, `AudioFeature`), langues audio, de sous-titres et de sous-titres forcés, nombre de versions, taille, date de la dernière lecture |
| `index_state` | serveur, bibliothèque | début de la dernière mise à jour, fin de la dernière lecture complète |
| `library_icons` | serveur, bibliothèque | l'icône choisie dans les réglages (retouche A4) |

- **Par serveur**, pas par profil : les fichiers sont les mêmes pour tous ; chaque réponse est
  recoupée avec celle du serveur, qui applique les droits du profil.
- **Remplissage** : en arrière-plan (`IndexScheduler`, `Dispatchers.IO`), 5 s après l'ouverture d'un
  profil puis toutes les 6 h tant que l'appli tourne ; par pages de 100, bibliothèque par bibliothèque
  (films, séries, vidéos personnelles, mixtes). Jamais bloquant : les écrans ne l'attendent pas.
- **Mise à jour par différence** : `minDateLastSaved` = début de la dernière mise à jour − 1 h (marge
  pour l'écart d'horloge entre serveur et TV) ; puis le nombre d'éléments de la bibliothèque sur le
  serveur est comparé à celui de l'index ; s'il diffère (un élément supprimé), la bibliothèque est
  relue entière, et les lignes que cette lecture n'a pas vues sont effacées. Relecture complète au
  moins tous les 7 jours, ou à la demande (réglages).
- **Politique de cache** : une bibliothèque que le profil ne voit plus est retirée de l'index ;
  oublier le serveur efface l'index et les icônes ; se déconnecter d'un profil les garde.
- **Filtres** (`IndexedQuery`) : sans filtre de l'index, le serveur fait tout (§ 4.9). Avec : 1. le
  serveur donne les identifiants qui passent ses propres filtres, dans l'ordre du tri demandé
  (`itemIds`) ; 2. l'index garde ceux qui passent les siens (une série passe si l'un de ses épisodes
  passe ; les valeurs d'un filtre s'additionnent, les filtres se combinent) ; 3. chaque page est lue
  par identifiants, 100 à la fois, et remise dans l'ordre. Le saut A–Z compte de même. Les films
  d'une collection ne sont pas regroupés sous ces filtres. Tant que l'index n'est pas complet, le
  panneau de filtres le dit sous ces filtres (« Index de la bibliothèque en cours : 3 200 sur 11 523
  titres »).
- **Valeurs** : HDR et Dolby Vision (Dolby Vision, HDR10+, HDR10, HLG, SDR) ; format audio (Stéréo,
  5.1, 7.1, Dolby Atmos, DTS — DTS-HD et DTS:X compris —, Dolby TrueHD) ; codec vidéo (H.264, HEVC,
  AV1, VP9, MPEG-2, VC-1, autre).
- **Badges** des fiches : lus dans l'index dès l'ouverture, remplacés par ceux des sources du
  détail.
- **Réglages** : « Index de la bibliothèque » dit où il en est (« À jour : 1 523 films et
  10 000 épisodes, le 25 oct. 2026 à 14 h 02 », « Mise à jour : 3 200 sur 11 523 titres », un échec)
  ; OK relit tout.
- **Mesures** (Jellyfin 12.3 de test en Docker, appli sur la JVM avec Room et SQLite embarqué, sur
  le poste de la session cloud) : 58 éléments (47 films, 11 épisodes) indexés en 0,17 à 0,33 s ;
  lecture et analyse ≈ 1,5 ms par élément (pages de 100, ≈ 2,7 Ko de JSON par film de test) ;
  écriture de 11 500 lignes 0,26 s ; un filtre sur 11 500 lignes 11 ms. **Extrapolation** pour
  1 500 films et 10 000 épisodes : un vrai fichier décrit 2 à 3 fois plus de pistes (≈ 6 à 8 Ko),
  soit ≈ 70 à 90 Mo de JSON en ≈ 115 requêtes ; ≈ 30 à 50 s sur un poste, **2 à 4 min sur la Shield**
  (processeur ≈ 4 à 5 fois plus lent pour l'analyse JSON, serveur sur un NAS) pour le premier
  remplissage, en arrière-plan ; ensuite, une mise à jour ne relit que ce qui a changé (quelques
  secondes) ; un filtre ≈ 50 ms sur la Shield, plus une requête d'identifiants (≈ 600 Ko pour 1 500
  films). À mesurer sur la Shield (§ 4.20, « À vérifier sur appareil »).
- **Migration 2 → 3** : automatique (`@AutoMigration`, trois tables ajoutées), testée par
  `CineDatabaseMigrationTest` (base créée d'après `2.json`, index SQL compris).

**Icônes des bibliothèques** (retouche A4) : `LibraryIcon` (films, séries, animation, animes,
documentaires, spectacles, sport, vidéos en ligne, famille et enfants, collections, dossier) ;
`LibraryIcon.defaultFor(type, nom)` cherche d'abord des **mots du nom**, sans accents ni casse,
français et anglais (dans cet ordre : animation — « animation », « dessin(s) animé(s) », « cartoon » ;
animes — « anime(s) », « manga(s) » ; sport — « sport… », « MMA », « UFC », « boxe », « foot »,
« rugby », « F1 »… ; spectacles — « spectacle… », « concert », « théâtre », « stand-up », « humour »,
« opéra » ; documentaires — « docu… », « reportage… », « nature » ; vidéos en ligne — « YouTube »,
« Twitch », « web », « podcast », « chaînes » ; famille — « enfant(s) », « kids », « jeunesse »,
« famille ») : un mot court compte entier, un préfixe (« docu ») en début de mot ; sinon le type
(films, séries, collections, dossier). Le choix se modifie dans les réglages (« Bibliothèques », OK
sur une bibliothèque, « Automatique » en tête), gardé par serveur (`library_icons`). Tracés Material
(Apache 2.0) recopiés dans `CineIcons`, sans la dépendance `material-icons-extended` (plusieurs Mo).

**Retouches du J2** :
- **Raccord du bandeau** (A1) : le fond du bandeau ne se fond plus vers la couleur `fond` (opaque,
  d'où une ligne nette là où il s'arrêtait au-dessus du fond d'ambiance) mais vers la
  **transparence**, par un masque alpha (`BlendMode.DstIn`, couche hors écran) : à gauche de 8 % à
  100 % sur 60 % de la largeur, en bas sur une courbe douce (100 % jusqu'à 40 % de la hauteur, 0 % au
  bord). Le fond d'ambiance garde ses voiles sous le texte.
- **Vignettes** (A2) : 176 × 99 dp (au lieu de 300 × 169) : quatre entières et le début de la
  cinquième (64 dp) dans une rangée de 1080p ; légende inchangée (Légende, 16 sp), titre d'une carte
  générée en Légende.
- **Clé de débogage** (A5) : `app/tv/debug.keystore` (PKCS12, `android` / `androiddebugkey`), versionnée
  et utilisée par le `signingConfig` de débogage partout (CI et postes) : chaque APK de débogage
  s'installe par-dessus le précédent. Ce n'est pas un secret (README). L'APK de release reste signé
  hors du dépôt.

**Serveur de test** : générique de « Le Phare » (réalisation, scénario, deux rôles), bande-annonce
distante (« Big Buck Bunny », fondation Blender), bande-annonce locale de « Nuit polaire », statut et
dates de diffusion des séries ; affiches, fonds et logos **générés** (dégradés et titres en Inter)
pour huit films et trois séries, photos (initiales) des sept personnes, posés par l'API
(`ci/jellyfin/images.sh`, appelé par `bootstrap.sh`).

**Tests** : unitaires `SmartPlayTest`, `TrackPreselectionTest`, `PlaybackTest` (heure de fin),
`LibraryIconTest`, `TechnicalSummaryTest` (`:core:model`), `LibraryIndexTest` (remplissage, filtres,
différence, suppression, requête filtrée), `FiltersCodecTest`, `CineDatabaseMigrationTest`
(`:core:data`) ; intégration `JellyfinDetailsIntegrationTest` (deux versions, pistes multiples et
HDR10, générique et saga, similaires, série à deux saisons et son bouton intelligent, saison unique,
vu et favori, filmographie, préférences, index complet puis par différence, identifiants) ; interface
`J3NavigationTest` (croix directionnelle), `J3FlowTest` (appli entière : pistes choisies jusqu'au
lecteur provisoire, « Retour », vu envoyé au serveur), `J2FlowTest` (« Retour » depuis une fiche).

**Captures** (`docs/screenshots/j3/`, 1920 × 1080, sombre et noir OLED ; images générées comme
celles du serveur de test, dessinées par le gestionnaire d'aperçu de Coil) : accueil (70), rail et
icônes (71), filtres HDR et audio (72 à 74), fiche film (75), synopsis déplié (76), distribution et
saga (77), pistes (78), choix de la version (79), informations sur le fichier (80), fiche série (81),
saisons et épisodes (82), saison unique (83), fiche épisode (84), fiche personne (85), lecteur
provisoire (86), réglages des bibliothèques et de l'index (87 à 89) ; les captures du J0 au J2 sont
réenregistrées (vignettes, raccord, panneau de filtres).

### 4.11 Lecteur (J4)

**Modules** : `:core:model` (Kotlin pur) porte le profil, la décision et les règles ;
`:core:playback` lit l'appareil et enveloppe ExoPlayer ; `:feature:player` est l'écran.

**Source** (`:core:source`, `LibrarySource.playback: PlaybackSource`) :

| Fonction | Appel Jellyfin | Notes |
|---|---|---|
| `playbackInfo(requête)` | `POST /Items/{id}/PlaybackInfo` | `DeviceProfile` construit du profil (ci-dessous), `MaxStreamingBitrate`, `StartTimeTicks`, `AudioStreamIndex`, `SubtitleStreamIndex` (`-1` : aucun), `MediaSourceId`, `EnableDirectPlay`/`EnableDirectStream` (faux après un échec), `AllowVideoStreamCopy`/`AllowAudioStreamCopy` |
| `reportStart` / `reportProgress` / `reportStopped` | `POST /Sessions/Playing`, `/Playing/Progress`, `/Playing/Stopped` | version, pistes, position, pause, `PlayMethod`, `PlaySessionId` |
| `segments(id)` | `GET /MediaSegments/{id}` | Intro, Outro, Recap, Preview, Commercial ; vide sans plugin |
| `trickplayTileUrl(id, version, largeur, tuile)` | `/Videos/{id}/Trickplay/{largeur}/{tuile}.jpg` | métadonnées dans `Trickplay` de l'élément (version → largeur → tuiles) |
| `streamHeaders` | — | `Authorization: MediaBrowser …, Token="…"`, envoyé par ExoPlayer et Coil à chaque requête du lecteur (flux, sous-titres, tuiles) |

`item(id)` donne aussi les **chapitres** (`Chapters`) et, par version, le **trickplay** ; chaque
piste vidéo sa fréquence d'images (`RealFrameRate`) et son profil Dolby Vision (`DvProfile`).

**Profil de l'appareil** — ce qui est détecté (`DeviceCapabilitiesReader`, relu à chaque lecture :
un ampli a pu s'allumer) :
- décodeurs vidéo de `MediaCodecList` (H.264, HEVC, AV1, VP9, MPEG-2, VC-1) : nom, matériel ou
  logiciel, profils (« high », « main 10 »…), niveau maximal à l'échelle du serveur (H.264 5.1 → 51,
  HEVC 5.1 → 153), taille maximale, 10 bits ; profils du décodeur Dolby Vision (4 à 9) ;
- écran : taille et fréquences du mode courant, HDR (`Display.getHdrCapabilities` : HDR10, HDR10+,
  HLG, Dolby Vision) ;
- son : décodeurs audio de l'appareil ; passthrough HDMI (`AudioCapabilities` de Media3 : AC3,
  E-AC3, E-AC3 JOC, DTS, DTS-HD, TrueHD) ; canaux PCM ;
- sous-titres dessinés par Media3 : SubRip, SSA/ASS, WebVTT, TTML, PGS, VobSub, DVB.

Ce qui est envoyé (`PlaybackProfiles.build`, puis `toDeviceProfile` dans `:source:jellyfin`) :
- **lecture directe** : conteneurs lus par ExoPlayer (mkv, webm, mp4, m4v, mov, ts, m2ts, mts, avi,
  flv, 3gp, ogv), un `CodecProfile` par codec vidéo décodé (profils — « baseline » emporte
  « constrained baseline » —, niveau, largeur, hauteur, profondeur, **plages dynamiques**), les
  codecs audio décodés **ou** passés à l'ampli (passthrough autorisé dans les réglages), les canaux ;
  un décodeur logiciel de HEVC, AV1 ou VP9 est limité à 1080p ;
- **plages** : SDR toujours ; HDR10 et HDR10+ (sa base est HDR10) si l'écran prend HDR10 et le
  décodeur 10 bits ; HLG de même ; Dolby Vision selon les profils du décodeur **et** un écran Dolby
  Vision. Profil 5 (`DOVI`) : seulement avec Dolby Vision, sinon le serveur fait le tone mapping
  (paramétré sur le serveur) ; profil 7 (`DOVIWithEL`) : seulement si le décodeur déclare le profil
  7 ; profil 8 (`DOVIWithHDR10`, `…HLG`, `…SDR`) : Dolby Vision, sinon **sa couche de base** sur le
  décodeur HEVC (Media3 sait la lire ; sans écran Dolby Vision, le sélecteur de décodeurs écarte le
  décodeur Dolby Vision pour que la base soit lue en HDR10, HLG ou SDR) ;
- **transcodage** : HLS ; HEVC puis H.264 en fMP4 (`mp4`) quand un décodeur HEVC Main 10 matériel
  existe, sinon H.264 en MPEG-TS ; audio AC3 d'abord quand l'appareil ou l'ampli prend du 5.1,
  sinon AAC ; `MaxAudioChannels` ;
- **sous-titres** : `Embed` pour tout format dessiné, `External` pour les formats texte ; le reste
  (PGS, VobSub en transcodage) est incrusté par le serveur (`Encode`). L'option « ASS incrustés par
  le serveur » retire ASS/SSA : le serveur les dessine, styles compris ;
- débit : 120 Mb/s par défaut, ou la qualité choisie (40 à 1 Mb/s).

**Décision** : le serveur décide d'après le profil. `SupportsDirectPlay` → **lecture directe**
(`/Videos/{id}/stream?static=true`) ; sinon `TranscodingUrl` (HLS) : **flux direct** (remux) si
`SupportsDirectStream`, **transcodage** sinon — « audio seulement » quand toutes les raisons sont
audio (la vidéo est copiée). La **raison** vient de `TranscodeReasons=` dans l'adresse
(`TranscodeReason`, montrée en clair par l'écran d'informations). Un flux HLS de Jellyfin couvre
toute la durée : le lecteur se place lui-même à la position de départ.

**Repli** (`PlaybackFallback`) : une erreur de **décodage** (codes Media3 4xxx, piste audio 5xxx,
ou aucune piste vidéo — ou audio — prise en charge par un décodeur) ou de **format** (3xxx) pendant
une lecture directe ou un remux redemande le flux au serveur, sans lecture directe, **à la même
position**, sans écran d'erreur, une fois ; l'écran d'informations dit « Lecture directe impossible,
transcodage : <erreur> ». Toute autre erreur, ou un transcodage qui échoue, montre l'erreur (deux
phrases, « Réessayer » qui repart de la position). **Réseau** : les chargements sont réessayés
(20 fois, jusqu'à 5 s d'écart, ≈ 90 s) pendant que la mémoire tampon (30 à 90 s, 192 Mo) joue ;
après 3 s d'attente, « Connexion au serveur perdue. Nouvel essai en cours… » s'affiche.

**Rapports** (`PlaybackReporter`) : « démarré » une fois par flux ; « progression » toutes les
10 s, à chaque pause ou reprise, après chaque saut (les 10 s repartent de là) et à chaque
changement de piste dans le flux ; « arrêté » à la sortie. Un changement de qualité, de piste
hors du flux ou le repli arrêtent le flux courant et en démarrent un autre. Les rapports partent
dans l'ordre, un à la fois, sur une portée qui survit à l'écran ; un échec est ignoré (le suivant
porte la position). Une bande-annonce locale n'envoie aucun rapport et ne touche pas au « vu ».

**« Vu »** (`PlaybackRules.isWatched`) : à 90 % de la durée (réglable : 80, 85, 90, 95 %), ou dès le
début du segment Outro. Alors le dernier rapport porte la **fin** de la durée, et le serveur marque
vu de lui-même. Le seuil du serveur (`MaxResumePct`, 90 % par défaut) s'applique aussi. À la sortie,
la position (ou « vu », position 0, une lecture de plus) part tout de suite dans le cache de
l'accueil (`HomeRepository.updateUserData`, table `cached_items`) et vers les fiches ouvertes
(`UserDataUpdates`), avant tout rafraîchissement.

**Segments** : « Passer l'intro », « Passer le récapitulatif » (aperçu, publicité) en bas à droite,
focalisé d'office, tant que le segment joue ; réglage « Bouton », « Passer automatiquement » (une
fois par segment), « Rien ». Le générique (Outro) mène à l'épisode suivant.

**Épisode suivant** : le suivant dans l'ordre de la série (les spéciaux seulement après un
spécial). La carte apparaît au début du segment Outro, sinon 30 s avant la fin, focalisée, avec un
compte à rebours de 10 s (qui ne court que pendant la lecture) ; à zéro, ou à la fin du fichier,
l'épisode suivant démarre si « Épisode suivant automatique » est actif. **Sans segment Outro**
(essai réel 5, § 4.23), le compte à rebours ne lance rien : il commence quand il reste exactement sa
durée et finit à la fin réelle de l'épisode, dont la fin lance le suivant. « Retour » écarte la carte.

**« Vous êtes toujours là ? »** (`StillWatching`) : l'épisode lancé par l'utilisateur compte 1,
chaque épisode lancé par le compte à rebours ajoute 1, toute touche repart de 1 ; quand 3 épisodes
(réglable : jamais, 2 à 5) ont joué ainsi, le suivant attend la réponse au lieu de démarrer
(« Continuer la lecture », focus initial, ou « Arrêter »).

**Image** : fréquence d'images adaptée (activée par défaut) : `Surface.setFrameRate` par Media3
quand le changement est sans coupure, et changement de **mode** de l'écran
(`preferredDisplayModeId`) vers le mode de même taille dont la fréquence est la cadence du film ou
un multiple (`DisplayModes`), rendu à la sortie. Format : adapter, remplir, zoom (`PlayerView`).

**Son** : passthrough selon ce que la sortie HDMI prend, réglable format par format ; un format
refusé est décodé par l'appareil (le fournisseur de sortie audio le déclare non pris en charge), à
défaut converti par le serveur. Piste audio de départ : le choix de la fiche, sinon la
pré-sélection du J3.

**Sous-titres** : dans le fichier (lecture directe : n-ième piste de sous-titres du fichier ↔ n-ième
groupe de pistes de Media3), externes (`DeliveryUrl`, chargés à part avec l'en-tête du serveur), ou
incrustés par le serveur (changer pour l'une d'elles redemande le flux). Apparence (couleur,
contour) appliquée au `SubtitleView` ; aperçu sur l'image en pause dans le panneau « Réglages » du
lecteur, et dans « Apparence des sous-titres ».

**Taille, position et fond des sous-titres** *(ajout au J4, demandé par le mainteneur)* : réglables
en cours de lecture, sans pause, depuis une barre posée en haut de l'image (le panneau latéral
cacherait les sous-titres) : la taille va de 60 % à 200 % (crans de 10 % jusqu'à 100 %, puis de
20 %), ← et → la changent aussitôt ; puis la position (en bas, relevée, haute) et le fond (aucun,
ombré derrière le texte, bandeau sur toute la largeur). Sans sous-titre à l'écran, une phrase
d'exemple s'affiche le temps du réglage. Accès : la ligne « Taille, position et fond » des panneaux
Audio et sous-titres et Réglages, le bouton « Taille des sous-titres » de l'interface, et un
**raccourci : ↑ ou ↓ maintenu, interface masquée**, qui grossit ou réduit d'un cran à chaque demi-
seconde en montrant la barre (un appui bref montre toujours l'interface). Le réglage est mémorisé
pour le profil sur cet appareil (DataStore `player`, clés suffixées par serveur et utilisateur ; un
profil qui ne l'a jamais touché part de celui de l'appareil), devient la valeur par défaut, et se
retrouve dans « Apparence des sous-titres ».

- **Jamais de pixels** : la taille du texte est une fraction de la hauteur de la zone du lecteur
  (5,33 % à 100 %, comme les sous-titres d'Android), et le contour d'aperçu une fraction de la
  taille du texte. Un réglage donne le même rendu en 1080p et en 4K : sur une Shield en 4K,
  l'interface est en 960 × 540 dp à une densité quatre fois plus forte, et `SubtitleView` mesure en
  fraction de sa hauteur, pas en dp (captures `106-sous-titres-160` et `108-sous-titres-160-4k`).
  Plex dessine en points fixes, d'où des sous-titres trop petits sur une sortie 4K.
- **Formats** : nos sous-titres passent par notre `SubtitleView` (celui de `PlayerView` est caché),
  qui reçoit les répliques de Media3 transformées par `SubtitleCues` : un texte sans taille (SRT,
  WebVTT) prend la taille réglée ; un texte qui a la sienne (styles ASS) est multiplié par le
  facteur, si bien que les styles gardent leurs proportions (et leurs couleurs, positions) ; une
  image (PGS, VobSub) grandit autour du milieu de son bord bas et remonte avec la position
  (`SubtitleBox`), sans sortir de l'image.
- **Ce qui ne s'agrandit pas** : des sous-titres **incrustés par le serveur** (PGS ou VobSub quand
  il transcode, ASS si le réglage « ASS incrustés » est actif) font partie de l'image : ni leur
  taille ni leur place ne peuvent changer. La barre le dit (« La taille de ces sous-titres ne peut
  pas être modifiée. Le serveur les incruste dans l'image. »), le − / + est éteint et les autres
  réglages masqués.

**Interface** (`UI.md` § 8) : touches de la télécommande — interface masquée : ← et → sautent
(10 s, 30 s, réglables), OK et ↑ ↓ (appui bref) montrent l'interface, ↑ ↓ maintenus changent la
taille des sous-titres ; sur la barre, ← → recherchent (pas de
10 s, puis 30 s, 1, 2, 5 min, 6 répétitions de touche chacun), la vignette trickplay au-dessus, OK
ou 1,2 s sans touche y va ; touches média : lecture, pause, lecture/pause, avance et retour rapides
(recherche), suivant et précédent (épisode suivant ou précédent pour un épisode qui en a un, essai réel
5 ; sinon chapitres), stop ; Info : écran d'informations. L'interface
disparaît 4 s après la dernière touche pendant la lecture. Panneaux à droite, l'image visible :
Audio | Sous-titres (deux colonnes), Qualité, Réglages, Chapitres ; en haut, la barre des
sous-titres. L'écran reste allumé, la lecture
se met en pause quand l'appli passe en arrière-plan.

**Écran d'informations** : moteur, mode et raison, repli, conteneur, codec vidéo reçu, résolution
et cadence, plage dynamique (et profil Dolby Vision), décodeur, audio (codec, canaux, fréquence),
passthrough oui/non, débit, bande passante mesurée, mémoire tampon, images perdues, appareil et
écran. **Profil de l'appareil** (réglages, versions de débogage) : chaque décodeur, l'écran, le
son, puis le profil envoyé, en entier.

**Réglages de l'appareil** (DataStore `player`, pas par profil) : passthrough par format,
fréquence d'images, intro, épisode suivant, « toujours là », seuil « vu », sauts, ASS par le
serveur, format, apparence des sous-titres. Au J7, ceux qui tiennent à la personne passent au profil
(§ 4.17).

**Serveur de test** : `bootstrap.sh` installe **Chapter Segments Provider** depuis le dépôt de
plugins de jellyfin.org (redémarrage du serveur), puis lance la tâche d'analyse des segments : les
chapitres « Intro » et « Credits » des médias générés deviennent des segments Intro et Outro. Sans
Internet (session cloud), le plugin manque : le test des segments le dit et ne vérifie que l'appel ;
la CI, elle, passe `JELLYFIN_SEGMENTS=true` quand les segments existent.

**Tests** : `PlaybackRulesTest` (`:core:model` : vu, épisode suivant, intro, « toujours là »,
repli, trickplay, raisons), `PlaybackProfilesTest` (Shield, Chromecast, appareil SD, passthrough,
ASS), `DeviceProfilesTest` (forme Jellyfin), `PlaybackReporterTest` et `DisplayModesTest`
(`:core:playback`), `JellyfinPlaybackIntegrationTest` (lecture directe pour le profil complet,
transcodage HLS et sa raison pour le profil limité, repli, la reprise déplacée par un rapport de
progression, « vu » posé, segments), `J3FlowTest` (de la fiche au lecteur avec les pistes choisies),
`PlaybackOnDeviceTest` sur l'émulateur de la CI (H.264 lu directement, HEVC HDR transcodé : la
position avance de 2 s), `SubtitleStyleTest` (crans, fraction de l'image, images agrandies) et
`PlayerSettingsStoreTest` (apparence par profil, le reste par appareil). Captures :
`J4ScreenshotTest`, `J4Screenshot4kTest`.

**Captures** (`docs/screenshots/j4/`, 1920 × 1080, sombre et noir OLED, image vidéo générée) :
interface du lecteur (90), recherche et vignette (91), audio et sous-titres (92), qualité (93),
réglages et aperçu des sous-titres (94), « Passer l'intro » (95), « Épisode suivant » (96),
« Vous êtes toujours là ? » (97), informations en lecture directe (98) et en transcodage avec sa
raison (99), erreur (100), « Lecture et son » (101), « Apparence des sous-titres » (102), profil de
l'appareil (103), barre de taille des sous-titres (104), un même sous-titre à 100 % (105) et à
160 % (106), sous-titres incrustés (107), 160 % sur une sortie 4K, 3840 × 2160 (108) ; les retouches du J3 se voient sur la fiche (75 : logo sans affiche, heure de fin
d'un bloc) et les pistes (78, 79 : pastilles). Le lecteur provisoire (86) disparaît.

**Limites connues** (à reprendre plus tard) :
- Le mode des sous-titres et la langue préférée se règlent dans l'appli depuis le J7 (§ 4.17) ; la
  qualité choisie pendant la lecture vaut pour la séance, la qualité par défaut est un réglage du J7.
- Le passage d'un Dolby Vision profil 5 sur un appareil qui ne le décode pas passe par le
  transcodage du serveur (tone mapping) ; seul le profil 8 se replie sur HEVC dans l'appareil.
- Cadence, passthrough réel et HDR sur la Shield : § 4.20, « À vérifier sur appareil ».
- Sous-titres incrustés par le serveur : ni taille ni position ne changent (voir plus haut).

**Retouches du J3** :
- **Pastilles** : « par défaut », « forcés », « SDH », « externe » suivent le libellé d'une piste
  en pastilles (`TrackPill`) ; le libellé passe à la ligne, jamais tronqué (`ListRow(wrapTitle)`).
- **Groupes insécables** : espaces insécables dans « se termine à 22 h 07 », « 1 h 49 », « 42 min »,
  « S01 · E02 », « 10:47 PM » (`UI.md` § 5).
- **Logo ou affiche** : un film, une série ou une collection qui a un logo n'affiche plus son
  affiche ; l'affiche de la carte vole vers le logo en s'y fondant (`sharedBounds`, des deux côtés).

### 4.12 Recherche (J5)

**Modules** : `:feature:search` (`cinedoury.android.library` + `.compose`) — l'écran, le clavier à
l'écran, la recherche vocale, la rangée Seerr et la page « Demandes (Seerr) » ; `:source:seerr`
(`cinedoury.jvm.library`, OkHttp et kotlinx.serialization JSON, sans greffon de compilation) — le
client Seerr derrière l'interface `MediaRequests` de `:core:source`. Le moteur (normalisation,
tolérance, classement) est du Kotlin pur dans `:core:model` (`Search.kt`), testé sans Android.

**Source** (`:core:source`, `LibrarySource`) :

| Fonction | Appel Jellyfin | Notes |
|---|---|---|
| `search(terme, types, n)` | `GET /Search/Hints?searchTerm=…&includeItemTypes=…`, puis `GET /Items?ids=…` | `/Search/Hints` ignore accents et casse et trouve le début de n'importe quel mot (« ines » → « Inès Garnier », « degel » → « Le dégel ») ; `/Items?searchTerm=` et `/Persons?searchTerm=` ne trouvent pas « ines ». Les cartes (images, données utilisateur) viennent de `/Items?ids=`, remises dans l'ordre du serveur |
| `titles(bibliothèque, depuis, page)` | `GET /Items?parentId=…&recursive=true&includeItemTypes=Movie,Series&fields=SortName,OriginalTitle,DateCreated&enableImageTypes=Primary,Backdrop&collapseBoxSetItems=false[&minDateLastSaved=…]` | les pages de l'index des titres, sans sources média (≈ 1 Ko par titre) |

**Index des titres** (Room, version 4, `:core:data`, `TitleIndex`) :

| Table | Clé | Contenu |
|---|---|---|
| `search_titles` | serveur, élément | bibliothèque, `Movie` ou `Series`, titre, titre original, titre de tri, année, date d'ajout, affiche et fond (avec leur BlurHash) |
| `search_state` | serveur, bibliothèque | début de la dernière mise à jour, fin de la dernière lecture complète |
| `recent_searches` | serveur, utilisateur, texte normalisé | le texte tel que tapé, date |

- Rempli **avec l'index technique** du J3 (`IndexScheduler`, en arrière-plan, 5 s après l'ouverture
  d'un profil puis toutes les 6 h), avant lui (il est plus léger), et de la même façon : lecture
  complète la première fois et tous les 7 jours, sinon par différence (`minDateLastSaved` − 1 h),
  relecture complète si les nombres diffèrent. Par serveur ; oublier le serveur l'efface.
- Les épisodes, les personnes et les collections n'y sont pas : 10 000 épisodes pèseraient pour peu
  de recherches, et les personnes et collections ne sont pas sous les bibliothèques. Le serveur les
  trouve.
- **Migration 3 → 4** : automatique (`@AutoMigration`, trois tables), testée par
  `CineDatabaseMigrationTest`. Le schéma 4 a été produit par KSP sur la JVM (projet jetable, voir
  `CLAUDE.md`) ; la CI vérifie qu'il correspond.

**Normalisation** (`SearchText`) : minuscules (`Locale.ROOT`), ligatures écrites en toutes lettres
(œ → oe, æ → ae, ß → ss, ø → o…), décomposition NFD et suppression des diacritiques (« Amélie » →
« amelie »), tout autre caractère qu'une lettre ou un chiffre devient une espace, espaces réduites.
« L'Éclipse » donne les mots « l », « eclipse » ; « Spider-Man » « spider », « man ».

**Tolérance et score** (`SearchMatcher`, plus haut = meilleur, rien = pas de correspondance) :
1. titre identique à la requête : 1 000 ; titre qui commence par la requête : 900 moins un point par
   caractère de plus (40 au plus) ;
2. sinon, mot à mot : chaque mot de la requête doit trouver un mot du titre (chaque mot du titre
   servant une fois) — entier, ou son début (le mot en train d'être tapé), ou **avec des fautes** :
   distance d'édition de Damerau (insertion, suppression, substitution, deux lettres inversées : une
   faute chacune), **0 faute jusqu'à 2 lettres, 1 de 3 à 4, 2 à partir de 5**, et la première lettre
   juste (« marée » ne trouve pas « durée »). Un mot en train d'être tapé se compare aux débuts du mot
   du titre de longueur voisine (« frequan » → « fréquences »). Les petits mots de liaison de la
   requête (« le », « la », « les », « l », « un », « une », « des », « de », « du », « d », « au »,
   « aux », « et », « en », « the », « a », « an », « of », « and ») peuvent ne rien trouver. Une
   requête d'un mot se compare aussi au titre écrit d'un bloc (« spiderman » → « Spider-Man ») ;
3. score : 700 − 60 par faute − 10 par mot seulement commencé − 15 par mot de liaison sans
   correspondance − 5 par mot du titre resté seul (50 au plus), + 20 quand le premier mot de la
   requête est le premier mot du titre (articles mis à part).
« seigneur anaux » trouve « Le Seigneur des anneaux » (590 : deux fautes sur « anaux ») ;
« amelie » trouve « Amélie » (1 000). Un élément est comparé par son titre, puis son titre original,
son titre de tri et, pour un épisode, « série + titre », chacun 5 points de moins que le précédent.

**Classement** (`SearchRanking.rows`) : un élément une fois (son meilleur score) ; une rangée par
groupe — Films (et vidéos), Séries, Épisodes, Personnes, Collections ; dans une rangée, le meilleur
score, puis le plus récent, puis le titre ; les rangées dans l'ordre de leur meilleur élément, à
égalité dans l'ordre ci-dessus ; 24 éléments par rangée. Ce que le serveur trouve sans que le moteur
le reconnaisse (il a cherché sur autre chose) prend le score 1, en fin de rangée.

**Part de l'index et part du serveur** (`SearchRepository`, `SearchViewModel`) :
1. **À chaque frappe**, sans réseau : l'index des titres du serveur, gardé en mémoire déjà
   normalisé (quelques milliers de titres, quelques millisecondes), contre la requête, limité aux
   bibliothèques que le profil voit (celles du cache de l'accueil) ; les 100 meilleurs s'affichent
   tout de suite, depuis la première lettre.
2. **Après 250 ms sans frappe** (la frappe suivante annule) : en parallèle, le serveur **recoupe**
   ces titres (`/Items?ids=`, qui applique les droits du profil : un titre interdit, par exemple par
   la classification d'un profil enfant, disparaît ; les cartes reviennent avec ce que le profil a vu)
   et **complète** par `/Search/Hints`, une requête par groupe pour qu'aucun n'en évince un autre
   (films et séries : 40 ; épisodes, personnes, collections : 24). Les clés des cartes ne changent
   pas : rien ne saute.
3. Un échec du serveur garde les résultats de l'index (« Le serveur n'a pas répondu… », « Réessayer »).

**Écran** (`UI.md` § 8, « Recherche ») : le rail (entrée « Recherche ») ; à gauche, la ligne de la requête
(non focalisable, un curseur jaune) et le **clavier à l'écran** ; à droite, les rangées. Le fond
d'ambiance suit la carte focalisée. Sans requête : **recherches récentes** (puces, « Effacer
l'historique »), **Ajouts récents** (les derniers ajouts des bibliothèques de films et de séries,
18) et **Genres** (puces : un genre montre ses films et séries, les plus récents d'abord). « Retour »
ferme le dialogue, l'écoute ou le clavier du système, sinon revient à l'écran d'avant ; revenir d'une
fiche retrouve la requête, les résultats et la carte quittée (état du modèle de vue et de la pile).

**Clavier à l'écran** (`SearchKeyboard`) : 7 touches par rangée (40 dp, `CineSizes.keyboardKey`),
l'alphabet dans l'ordre (plus rapide à la croix qu'un AZERTY), « ' » et « - », les chiffres ; puis
8, 9, 0, espace, effacer ; puis la **page des accents** (à â ä ç é è ê ë î ï ô ö ù û ü ÿ œ æ ñ et
quelques signes, à la place des lettres), le micro, le **clavier du système**, « Tout effacer ».
Focus initial sur « a » ; chaque touche a un voisin dans les quatre directions ; à droite de la
dernière colonne, les résultats ; à gauche de la première, le rail. Le clavier du système remplace la
ligne de la requête par un champ (`CineTextField`) qu'il ouvre aussitôt ; ↑ et ↓ en sortent (un champ
garde le focus sur ↓ : contourné dans l'écran, en attendant la correction de `fix/essai-reel-1`).
**Écart** : pas d'accents en appui long, la page des accents en tient lieu (le cahier laissait le
choix).

**Recherche vocale** (`rememberVoiceSearch`) : `SpeechRecognizer` dans l'appli, dans la langue de
l'interface (`fr-FR`), résultats partiels affichés pendant l'écoute (micro jaune, anneau qui suit le
volume), après une demande de `RECORD_AUDIO` la première fois ; sans reconnaisseur dans l'appli,
l'activité vocale du système (`RecognizerIntent`) ; sans l'un ni l'autre, pas de touche micro. Le
manifeste déclare `<queries>` (`RecognitionService`, `RECOGNIZE_SPEECH`, nécessaires depuis
Android 11) et le micro comme facultatif. **Touches de la télécommande** (`MainActivity.dispatchKeyEvent`) :
`KEYCODE_SEARCH` ouvre la recherche (celle de la pile si elle y est) depuis l'accueil, une
bibliothèque, une fiche, les réglages ; `KEYCODE_VOICE_ASSIST`, quand le système la laisse à
l'appli, l'ouvre en écoute. Jamais depuis le lecteur ni la connexion.

**Recherches récentes** : les 10 dernières **par profil** (Room `recent_searches`), enregistrées quand
un résultat est ouvert, une fois chacune (texte normalisé), la plus récente d'abord ; « Effacer
l'historique » ; se déconnecter les efface ; oublier le serveur aussi.

**Seerr** (facultatif) :
- **Réglages** (« Recherche » → « Demandes (Seerr) ») : adresse (`http://` supposé) et clé d'API,
  vérifiées avant d'être gardées (`GET /api/v1/status` sans clé : la version ; `GET /api/v1/auth/me`
  avec : la clé), **par serveur Jellyfin**, dans le DataStore `requests` ; la clé **chiffrée comme les
  jetons** (AES-256-GCM, clé du Keystore `cinedoury.tokens`). « Retirer Seerr » efface ; oublier le
  serveur aussi. Sans réglage, rien de Seerr ne s'affiche.
- **Contrat** (`SeerrClient`, en-tête `X-Api-Key`, délais 3 s / 10 s) :

| Fonction | Appel | Notes |
|---|---|---|
| `check()` | `GET /api/v1/status`, `GET /api/v1/auth/me` | 401/403 → clé refusée ; 404 ou réponse non JSON → pas un Seerr |
| `search(requête, langue)` | `GET /api/v1/search?query=…&page=1&language=fr` | requête entièrement encodée (`%20`, apostrophes : Seerr refuse le reste) ; `mediaType` `movie` ou `tv` (les personnes sont écartées) ; `mediaInfo.status` : 2, 3 → demandé, 4 → en partie disponible, 5 → disponible, 6 (liste noire) → écarté ; `mediaInfo.jellyfinMediaId` |
| `seasons(id, langue)` | `GET /api/v1/tv/{id}` | `seasons` sans la saison 0, état de chacune dans `mediaInfo.seasons` |
| `request(titre, saisons)` | `POST /api/v1/request` `{"mediaType":"movie"\|"tv","mediaId":id[,"seasons":[…]]}` | 201 → demandé ; 409 (déjà demandé) → demandé |
| utilisateur | `GET /api/v1/user?take=100` | l'utilisateur Seerr dont `jellyfinUserId` est celui du profil : les demandes partent en son nom (`X-Api-User`), sinon au nom du propriétaire de la clé |

- **Affiches** : `/imageproxy/tmdb/t/p/w300_and_h450_face/<chemin>` **sur le serveur Seerr** : la TV
  ne parle qu'aux serveurs de la maison (§ 7), jamais à TMDb. Sans affiche, la carte générée.
- **Rangée « Pas dans la bibliothèque ? Demander »**, en fin de résultats, une fois la frappe arrêtée :
  les titres de Seerr **absents des résultats** de la bibliothèque (par `jellyfinMediaId`, sinon titre
  normalisé et année), avec affiche, année et état sous la carte (« 2001 · Demandé », « Disponible »).
  OK sur un film : « Demander « … » ? », **focus sur « Annuler »** (aucune demande par mégarde) ; sur
  une série : le panneau des saisons, celles qui manquent cochées d'avance, « Demander 2 saisons ». Un
  titre déjà demandé ou disponible ne s'ouvre pas. Échec : « La demande n'a pas abouti », « Réessayer ».
  Seerr injoignable : une ligne « Seerr n'a pas répondu. », la recherche continue.

**Tests** : unitaires `SearchTest` (`:core:model` : normalisation, première lettre, fautes, mots de
liaison, classement, distance), `SeerrClientTest` (`:source:seerr`, contre une **fausse API Seerr**
MockWebServer : vérification, clé refusée, autre serveur, injoignable, recherche et états, encodage,
`X-Api-User`, saisons, demandes et 409), `SearchRepositoryTest` (`:core:data` : index des titres et
suppressions, recherche locale, recoupement par le serveur, personnes, collections, épisodes,
recherches récentes, suggestions), `CineDatabaseMigrationTest` (3 → 4) ; intégration
`JellyfinSearchIntegrationTest` (titres accentués sans accents et avec fautes, personnes, collections,
épisodes, types demandés) ; interface `J5NavigationTest` (clavier à la croix, accents, résultats),
`J5FlowTest` (appli entière : saisie à la télécommande, fiche, « Retour » qui retrouve requête et
carte, recherche récente ; demande à un faux Seerr après confirmation).

**Captures** (`docs/screenshots/j5/`, 1920 × 1080, sombre et noir OLED) : recherche vide avec récents
et suggestions (110), page des accents (111), saisie partielle et résultats groupés (112), faute
corrigée (113), personne (114), rangée Seerr (115), sa confirmation (116), ses saisons (117), écoute
vocale (118), rail ouvert sur « Recherche » (119), réglages Seerr (120) ; les réglages (33) gagnent
la section « Recherche ».

**Limites connues** :
- Fautes de frappe : seulement sur les films et séries de l'index. Personnes, collections et épisodes
  viennent du serveur, qui tolère accents et casse mais pas les fautes.
- Un profil enfant voit un instant, avant le recoupement par le serveur (≈ 250 ms), un titre de ses
  bibliothèques que sa classification lui interdit.
- La touche micro des télécommandes Shield et Google TV est gardée par l'assistant du système : elle
  n'arrive à l'appli que sur certaines télécommandes ; le bouton micro de l'écran marche partout où un
  reconnaisseur existe.
- L'image proxy de Seerr (`/imageproxy/tmdb`) n'existe pas sur un Overseerr ancien : les cartes y sont
  générées. À vérifier sur le Seerr de la maison.
- Temps de la recherche locale sur 11 500 titres : § 4.20, « À vérifier sur appareil ».
### 4.13 Essai réel 1 : diagnostic et saisie (25/10/2026)

Premier essai sur un appareil réel (Chromecast avec Google TV, Android 14, Gboard) branché au
Jellyfin 12.3 de la maison, avec l'APK du J3. Défauts et corrections :

| Défaut | Cause | Correction |
|---|---|---|
| Connexion par mot de passe : « erreur inattendue » alors que le serveur l'accepte | le client OkHttp du SDK lit le corps de la réponse dans la coroutine appelante, après `enqueue` ; appelé depuis un modèle de vue (fil principal), Android lève `NetworkOnMainThreadException` dès que le corps n'arrive pas avec les en-têtes (Kestrel répond en morceaux, `Transfer-Encoding: chunked`) ; `jellyfinCall` en faisait un `SourceException.Unexpected`, sans journal | `TracingApiClient` enveloppe le client du SDK : chaque requête s'exécute sur `Dispatchers.IO`, le décodage sur `Dispatchers.Default` |
| Quick Connect échoue d'emblée | même cause, dès `GET /QuickConnect/Enabled` (corps `true` en morceaux), que le serveur ne journalise pas | idem |
| Erreur passagère au choix d'un serveur découvert | même cause sur `/System/Info/Public` : l'échec dépend de l'arrivée du dernier morceau dans le premier paquet ou non (et non de `AddressResolver`, que ce chemin n'emprunte pas) | idem |
| « Plus d'infos » vide et hors de l'écran | le détail s'ouvrait sous le pli d'un écran qui ne défile pas, sans focus possible | `FormScreen` défile ; le détail est focalisable, est ramené à l'écran (`BringIntoViewRequester`) et reçoit le focus à l'ouverture et dit la classe de l'exception, son message, la requête (méthode et chemin, `SourceException.request`), le code HTTP (`httpStatus`) et chaque cause |
| Aucun journal | les erreurs étaient converties sans trace | `FailureLog` (`:core:source`) : chaque échec attrapé y passe, avec sa pile ; l'appli l'envoie à logcat, balise `CineDoury` (W, ou E pour un échec local après la réponse du serveur : Keystore, Room, DataStore, désormais montré au lieu de fermer l'appli). Jamais de secret : une requête s'y nomme par sa méthode et son chemin |
| Piège à focus dans les champs | Compose ne laisse sortir la croix d'un champ que pour un appareil qui se déclare « D-pad », ce que la télécommande Bluetooth ne fait pas ; le clavier s'ouvrait dès l'arrivée du focus et prenait la croix ; « Suivant » était remplacé par une action vide ; le clavier, ouvert à l'appui sur OK, recevait le relâchement et tapait la touche sous son focus (le « a ») | `CineTextField` à deux niveaux : le focus arrive sur le cadre, où la croix circule ; OK, au relâchement, ouvre la saisie ; en saisie, haut et bas quittent le champ, gauche et droite aux extrémités du texte ; « Suivant » passe au champ du dessous, « OK » / « Terminé » lance l'action (connexion, adresse). Champs sur `TextFieldState` |
| Focus initial sur « Saisir une adresse » malgré un serveur trouvé | choix délibéré du J1 | le premier serveur trouvé prend le focus dès qu'il apparaît, tant qu'aucune touche n'a été pressée |
| Compte masqué : seul « Autre compte » | Jellyfin cache les nouveaux utilisateurs, dont l'administrateur créé par l'assistant | « Votre compte n'apparaît pas ? Choisissez Autre compte. » sous le titre ; sans aucun profil listé, « Se connecter avec Quick Connect » et « Utiliser un mot de passe » directement |

- **Serveur de test** : `bootstrap.sh` crée un utilisateur masqué `parent` (mot de passe
  `cinedoury-parent`, serveur jetable), avec image, politique complète (bibliothèques choisies,
  étiquettes, horaires, limites) et préférences : la forme des réponses d'un vrai serveur. Ses
  réponses (`/Users/AuthenticateByName`, `/QuickConnect/*`) sont enregistrées dans
  `source/jellyfin/src/test/resources/fixtures/` (jeton et adresse remplacés).
- **Tests** : `RealServerAnswersTest` rejoue ces réponses en morceaux et lentement, vers un
  connecteur appelé d'un fil « principal » dont les sockets refusent de lire, comme Android (il
  échoue avec l'ancien code, exactement comme l'appareil) ; `JellyfinHiddenUserIntegrationTest`
  contre le Jellyfin de la CI ; `SessionManagerTest` (Keystore en échec) ; `RemoteInputTest`
  (champs, focus des serveurs, « Plus d'infos », compte masqué). Sur l'émulateur API 34 TV de la
  CI (`ci/emulator-signin.sh`, après la lecture du J4) : `KeystoreTokenCipherTest` (vrai Keystore) et
  `SignInJourneyTest`, le parcours du premier lancement à l'accueil, puis au lecteur par « Lire »,
  contre le Jellyfin de la CI (`10.0.2.2:8096`), au
  clavier virtuel par le vrai circuit d'entrée d'Android (touches de la croix et OK) et à l'IME
  (texte, « OK »). Les journaux `CineDoury` de l'émulateur sont joints aux artefacts.
- **Captures** (`docs/screenshots/j4/`, où la CI les copie) : erreur avec « Plus d'infos » déplié
  (35), « Qui regarde ? » sans profil listé (36) ; « Qui regarde ? » (25) porte désormais la phrase sur les comptes masqués.

### 4.14 Gestion (J6)

**Module** : `:feature:manage` (`cinedoury.android.library` + `.compose`) : `ItemManager` (toutes les
actions, par la source du profil ouvert), « Corriger l'identification », « Changer les images »,
« Éléments à problème », la confirmation de suppression (`DeleteDialog`), les messages
(`ManageNoticePill`, `ManageErrorDialog`). `:feature:details` et `:feature:library` en dépendent
pour leurs menus « ⋯ » ; l'appli y ajoute trois écrans (`Route.Identify`, `Route.Images`,
`Route.Problems`).

**Source** (`:core:source`, `LibrarySource.manage: ManageSource`) :

| Fonction | Appel Jellyfin | Notes |
|---|---|---|
| `rights()` | `GET /Users/Me` | `Policy.IsAdministrator`, `EnableContentDeletion`, `EnableContentDeletionFromFolders` (identifiants de bibliothèques) |
| `refresh(id, mode)` | `POST /Items/{id}/Refresh` | toujours `FullRefresh` (métadonnées et images) ; « Compléter ce qui manque » : rien n'est remplacé ; « Tout remplacer » : `replaceAllMetadata` ; « …, images comprises » : `replaceAllImages` en plus. Le serveur répond 204 et travaille en arrière-plan |
| `identifyProviders(id, type)` | `GET /Items/{id}/ExternalIdInfos` | les fournisseurs de recherche installés parmi TheMovieDb, AniDB, TheTVDB (TMDb d'abord) |
| `searchIdentity(id, recherche)` | `POST /Items/RemoteSearch/Movie`, `/Series`, `/BoxSet` | `SearchInfo` (titre, année), `SearchProviderName`, `IncludeDisabledProviders=true` (un fournisseur coupé dans la bibliothèque répond quand même à l'administrateur) |
| `applyIdentity(id, résultat, images)` | `POST /Items/RemoteSearch/Apply/{id}?replaceAllImages=` | le `RemoteSearchResult` choisi (identifiants des fournisseurs) ; le serveur relit les métadonnées |
| `remoteImages(id, type, fournisseur, toutes langues)` | `GET /Items/{id}/RemoteImages` | `type`, `providerName`, `includeAllLanguages` ; résolution, langue, fournisseur de chaque image, et la liste des fournisseurs |
| `setImage(id, type, url)` | `POST /Items/{id}/RemoteImages/Download` | le serveur télécharge l'image ; l'étiquette (`tag`) de l'image change, donc son URL : Coil la relit |
| `deletionPreview(élément)` | `GET /Items/{id}` puis, pour une saison ou une série, `GET /Shows/{id}/Episodes?fields=MediaSources` | chemins et tailles des fichiers, épisodes (nombre et identifiants) |
| `delete(id)` | `DELETE /Items/{id}` | **fichiers compris** : Jellyfin supprime le fichier et le dossier du film |
| `scan(bibliothèque, mode)` | « Rechercher les nouveaux fichiers » : `POST /Library/Refresh` ; « Tout actualiser » : `POST /Items/{bibliothèque}/Refresh` (`FullRefresh`, `replaceAllMetadata`) | voir l'écart ci-dessous |
| `scanStatus()` | `GET /ScheduledTasks?isHidden=false` | la tâche `RefreshLibrary` (« Scan Media Library ») : `State`, `CurrentProgressPercentage` |
| `problems(bibliothèque)` | `GET /Items?parentId=…&recursive=true&includeItemTypes=Movie,Series&fields=ProviderIds,Path&enableImageTypes=Primary` | **non identifiés** : aucun identifiant de fournisseur ; **sans affiche** : pas d'image `Primary` |
| `missingFiles(bibliothèque)` | la liste des films et épisodes, puis `GET /Items/{id}/File` avec `Range: bytes=0-0`, 8 à la fois | **fichiers disparus** : 404 (le serveur garde l'élément jusqu'à la prochaine analyse, qui l'efface ; `HEAD` répond 405) |

`item(id)` donne aussi le chemin (`Path`), les identifiants des fournisseurs (`ProviderIds`) et
`CanDelete` (le serveur supprimerait l'élément pour ce profil).

**Droits** (`ManageRights`, `:core:model`) : toute l'entrée « Gestion » est réservée aux
administrateurs ; « Supprimer » exige en plus `EnableContentDeletion`, ou une bibliothèque dans
`EnableContentDeletionFromFolders`, **et** `CanDelete` sur l'élément. Un profil sans droit ne voit
aucune entrée de gestion (ni dans « ⋯ », ni le bouton « Gestion » d'une bibliothèque). En attendant
la réponse du serveur, le drapeau administrateur du profil (Room) suffit à montrer les entrées, sauf
« Supprimer ». Le serveur vérifie de toute façon chaque appel (403 → `SourceException.Forbidden`).

**Journal** : chaque appel de gestion passe par `LoggingManageSource`, qui écrit une ligne par appel
dans logcat (balise `CineDoury`, `JellyfinConnector(log = …)`) : « manage delete item=… ok »,
« manage refresh item=… mode=FillMissing failed: Forbidden ». Identifiants et modes seulement :
jamais de jeton, de chemin ni d'adresse. `scanStatus`, interrogé chaque seconde, n'est pas journalisé.

**Invalidation** (`ManageRepository`, `ManageUpdates`, `:core:data`) :
- après une suppression, l'élément, ses saisons et ses épisodes quittent **tout de suite** le cache
  de l'accueil de tous les profils du serveur (`HomeCacheDao.forgetItem`) et l'index local
  (`IndexDao.forget`, épisodes d'une série et de la saison compris), puis `ItemChange.Deleted` est
  publié ;
- après une actualisation, une identification, une image ou une action en série, `ItemChange.Changed`
  (avec sa nature) est publié ;
- les écouteurs : la bibliothèque relit ses pages à la même place ; une fiche relit l'élément changé
  (nouvelle étiquette d'image, donc nouvelle image) ou retire de ses rangées ce qui a été supprimé ;
  « Éléments à problème » relit sa liste ou retire l'élément supprimé ; l'accueil se rafraîchit en y
  revenant (§ 4.9). Aucun schéma Room ne change (requêtes ajoutées seulement).

**Interface** (`UI.md` § 8, « Gestion ») :
- **« ⋯ » d'une fiche** (film, série, saison, épisode, collection ; le bouton « ⋯ » apparaît dès
  que le profil gère, même sans lecture possible) : « Marquer comme vu », « Informations sur le
  fichier », puis la section « Gestion » : les trois actualisations, « Corriger l'identification »
  (film, série, collection), « Changer l'affiche, le fond ou le logo », « Supprimer » en `danger`.
  **Deux appuis** : « ⋯ » puis l'action (la croix ne compte pas).
- **Actualiser** : le panneau se ferme, « Actualisation demandée. Le serveur la fait en
  arrière-plan. » s'affiche en bas ; 5 s plus tard la fiche est relue et « Métadonnées actualisées »
  s'affiche. Jellyfin ne publie pas la progression d'une actualisation d'élément.
- **Corriger l'identification** : titre et année pré-remplis par `SearchTitle` (le nom du dossier
  nettoyé : points et soulignés, crochets, année, étiquettes de version comme « 1080p », « BluRay »,
  « x265-GROUPE », « MULTi » ; un titre-nombre comme « 1917 » reste ; l'orthographe n'est pas
  corrigée), modifiables ; onglets des fournisseurs (s'il y en a plusieurs) ; « Remplacer aussi les
  images » (coché) ; résultats en **galerie d'affiches** avec l'année, et sous la galerie le titre,
  l'année, le fournisseur et trois lignes de résumé du résultat focalisé ; OK l'applique et ferme
  l'écran ; la fiche se relit (« Identification corrigée »). Sans résultat : deux phrases, et le
  focus sur le titre.
- **Changer les images** : onglets Affiche, Fond, Logo ; « Tous les fournisseurs » et chaque
  fournisseur ; « Toutes les langues » ; galerie (affiches 2:3, fonds et logos 16:9) avec résolution,
  langue (« Sans texte ») et fournisseur ; OK la pose et ferme l'écran ; la fiche se relit
  (« Image changée »).
- **Supprimer** : boîte `surface-2` (`ConfirmDialog`) : « Supprimer « Le Phare » ? », les chemins,
  la taille, le nombre d'épisodes, « Ces fichiers seront supprimés des disques du serveur. Cette
  action est définitive. » ; **focus initial sur « Annuler »** ; une série demande une **seconde
  confirmation** (« Supprimer toute la série ? », focus de nouveau sur « Annuler ») ; après la
  suppression, retour à l'écran précédent. « Retour » annule.
- **Informations sur le fichier** : par version, chemin, conteneur, taille, débit, chaque piste ;
  puis « La fiche » : chemin (série), date d'ajout, identifiants TMDb, IMDb, TVDB, AniDB (« Aucun
  identifiant de fournisseur » sinon).
- **« Gestion » d'une bibliothèque** (bouton de la barre, administrateurs seulement) : « Rechercher
  les nouveaux fichiers » (sous lui, « Analyse en cours : 42 % » tant que la tâche tourne ; à la fin
  « Analyse terminée » et la bibliothèque relue), « Tout actualiser » (« Actualisation demandée »),
  « Éléments à problème », « Sélectionner des titres ».
- **Éléments à problème** : onglets « Non identifiés (n) », « Sans affiche (n) », « Fichiers
  disparus » (vérification à la demande, « Vérification des fichiers : 3 200 sur 11 523 ») ; liste
  (affiche, titre, raison, chemin) ; OK ouvre un panneau : « Corriger l'identification »,
  « Supprimer de la bibliothèque » (la même confirmation).
- **Actions en série** : **OK maintenu** sur une carte (`Modifier.onLongPress` : la première
  répétition de la touche, le relâchement qui suit est absorbé) ou « Sélectionner des titres » ;
  chaque carte porte une case (`SelectionMark`), OK la coche ; la barre d'outils devient « 3 titres
  sélectionnés », « Marquer comme vus », « Marquer comme non vus », « Actualiser » (compléter ce qui
  manque), « Terminer » ; « Retour » termine.
- **Échec** : une boîte en deux phrases (`toErrorText`) et « Fermer » ; jamais de message de succès
  avant la réponse du serveur.

**Écarts** :
- « Rechercher les nouveaux fichiers » analyse **toutes** les bibliothèques (`/Library/Refresh`) :
  c'est la seule analyse dont Jellyfin publie la progression (`/ScheduledTasks`) ; l'analyse d'une
  seule bibliothèque (`/Items/{id}/Refresh`) n'en publie aucune. Elle ne relit que ce qui a changé.
- « Tout actualiser » et l'actualisation d'un élément n'ont pas de progression : une confirmation.
- Les actions de bibliothèque sont dans « ⋯ » de la bibliothèque seulement ; les réglages
  « Bibliothèques » gardent le choix de l'icône (J7 les reprendra avec les pages de réglages).
- « Envoyer une image depuis le téléphone » (FONCTIONNALITES § 9) attend l'appli mobile.

**Serveur de test** : sans fournisseur en ligne (`bootstrap.sh`), une recherche d'identité répond
vide (en CI, qui atteint TMDb, elle peut répondre) et les galeries d'images sont vides ; les tests
appliquent donc une identité construite (identifiant TMDb factice) et posent une image que le
serveur télécharge **de lui-même** (`http://localhost:8096/Items/{autre}/Images/Primary`).
**« Tout remplacer » vide les métadonnées** d'un film du serveur de test (il ne relit pas son
`.nfo`) : les tests n'actualisent qu'un film créé pour eux. `run-local.sh` monte les médias en
écriture (la suppression efface des fichiers) ; la CI donne `JELLYFIN_MEDIA_DIR`.

**Tests** : unitaires `ManageTest` (droits, double confirmation), `SearchTitleTest` (nettoyage),
`ManageRepositoryTest` (invalidation : rangées de l'accueil, index, épisodes d'une série,
annonces) ; intégration `JellyfinManageIntegrationTest` (droits de l'administrateur et du profil
enfant, refus 403, fournisseurs et galeries, analyse et sa progression, puis un film **créé exprès**
sous un nom de dossier mal orthographié, `Le.Phrae.2001.1080p` : actualisé, non identifié, titre de
recherche nettoyé, identité appliquée, sans affiche puis affiche posée, fichier disparu puis revenu,
aperçu de la suppression, supprimé avec son dossier) ; interface `J6NavigationTest` (« Supprimer »
en deux appuis, focus sur « Annuler », seconde confirmation d'une série, profil enfant sans gestion,
OK maintenu), `J6FlowTest` (appli entière : actualiser puis supprimer depuis la fiche, retour).

**Captures** (`docs/screenshots/j6/`, 1920 × 1080, sombre et noir OLED) : « ⋯ » d'un film (130),
d'une série (131), d'un profil enfant (132), identification (133) et sans résultat (134), galerie
d'affiches (135), onglet des fonds sans image (136), suppression d'un film (137), d'une série (138)
et sa seconde confirmation (139), informations sur le fichier (140), « Gestion » d'une bibliothèque
avec une analyse en cours (141), éléments à problème (142) et leurs actions (143), vérification des
fichiers (144), sélection multiple (145), actualisation demandée (146), échec (147).

**Pour le J7** : les réglages « Bibliothèques » peuvent reprendre `ItemManager.scan` et
`ManageRights` ; les utilisateurs, tâches planifiées et journal d'activité (FONCTIONNALITES § 9,
« Serveur ») ont leur place dans `ManageSource`.

### 4.15 Essai réel 2 : la Shield et le `minSdk` (26/10/2026)

Premier essai sur l'appareil de référence (NVIDIA Shield TV, Android 11, API 30, notre `minSdk`)
avec l'APK de `b1aea97` : Quick Connect aboutit, puis l'appli se ferme aussitôt, et à chaque
relance. Le même APK marche sur le Chromecast avec Google TV (API 34).

**Cause** : `java.lang.NoSuchMethodError: URLEncoder.encode(String, Charset)` dans `imageUrlOf`
(`:source:jellyfin`), à la première image de l'accueil. Cette surcharge est une API Java 10
qu'Android n'a qu'à partir de l'API 33. L'APK comparé appel par appel à l'`android.jar` de
l'API 30 en avait deux autres dans notre code : `URLEncoder.encode(String, Charset)` dans le client
Seerr (plantage à la première recherche Seerr) et `URLDecoder.decode(String, Charset)` dans
`JellyfinPlaybackSource.reasonsOf` (plantage à la lecture d'un média transcodé). Les autres appels
au-delà de l'API 30 viennent des bibliothèques (androidx, Media3, OkHttp, kotlinx) et sont gardés
par une vérification de version.

**Pourquoi rien ne l'a vu** : les modules Kotlin purs (`:core:model`, `:core:source`,
`:source:jellyfin`, `:source:seerr`) se compilent avec le JDK 21 et Android Lint ne les vérifie pas
(`NewApi` est sauté hors d'un module Android) ; D8 ne rétroporte qu'une partie des API Java ; les
tests JVM et Robolectric tournent sur un JDK ; l'émulateur de la CI était en API 34.

**Correction** : les surcharges à nom de jeu de caractères, présentes depuis toujours
(`URLEncoder.encode(s, "UTF-8")`, `URLDecoder.decode(s, "UTF-8")`) ; les URL produites ne changent
pas.

**Garde-fous** :
- **Animal Sniffer** sur chaque module Kotlin pur (plugin de convention `cinedoury.jvm.library`) :
  `animalsnifferMain`, dans `check`, compare le code principal compilé à la signature de l'API 30
  d'Android publiée par Toast (`gummy-bears-api-30`, qui connaît les rétroportages de D8). Un appel
  absent d'Android 11 fait échouer la CI (« [Undefined reference] … », repris par le résumé
  d'échec). Les tests ne sont pas vérifiés : ils tournent sur la JVM. Preuve : le run [40299467089](https://github.com/guim31/cine-doury/actions/runs/40299467089) échoue sur `:source:jellyfin:animalsnifferMain` seul quand l'appel fautif est réintroduit.
- **Android Lint écarté** : `com.android.lint` sur les modules JVM et `checkDependencies = true`
  dans l'appli font bien analyser leur code par `:app:tv:lintDebug`, mais l'analyse partielle d'un
  module JVM n'enregistre aucun `NewApi` : l'appel fautif passe (essayé en session).
- **Deux émulateurs en CI** : le job « Emulator » est une matrice **API 30** (Android TV 11, la
  Shield) et **API 34** (le Chromecast), mêmes scripts (démarrage, lecture directe et transcodée,
  connexion jusqu'à l'accueil puis « Lire »). Sur l'API 30, `SignInJourneyTest` et la lecture
  transcodée de `PlaybackOnDeviceTest` auraient planté avec l'ancien code.

**Le clavier qui restait ouvert** (révélé par l'émulateur API 30) : après la saisie de l'adresse,
OK sur un profil de « Qui regarde ? » restait sans effet. `CineTextField` remplace l'action du
clavier de Compose (qui le fermait sur « Terminé ») et ne le fermait pas en fin de saisie ;
Android 14 le ferme quand le focus quitte le champ, Android 11 le laisse ouvert par-dessus l'écran
suivant, où il prend le OK de la télécommande. Correction : `CineTextField` ferme le clavier à
l'action du clavier et quand le focus quitte la saisie, par `SoftwareKeyboardController.hide()` et
par `InputMethodManager.hideSoftInputFromWindow` : en Android 11, la première voie (encarts de la
fenêtre) est parfois ignorée juste après l'ouverture du clavier (vu sur l'émulateur). Diagnostic :
`SignInJourneyTest` relève l'état du clavier (`WindowInsets`), les touches qui atteignent
l'activité et les changements de focus de la fenêtre ; la touche OK perdue n'atteignait jamais
l'activité, le clavier la prenait. Le test attend aussi que le clavier soit affiché avant d'y
saisir, comme une main sur la télécommande : sinon, au premier lancement de l'émulateur (clavier
démarré à froid), saisie et validation 1 s après OK croisaient l'ouverture et la fermeture du
clavier, qui prenait encore la touche suivante. Vérifié : quatre connexions de suite sur chaque
émulateur, dont la première après le démarrage (run 40338655999).
Tests : `RemoteInputTest` (le clavier se ferme à « OK » et en sortant du champ par le bas) ;
`SignInJourneyTest` vérifie sur l'émulateur que le clavier est fermé avant OK sur le profil.
Le passage par « Autre compte », sans saisie préalable sur l'écran, échappait au défaut.

**Tests instables corrigés au passage** : `J3FlowTest` (environ un échec sur cinq, `main`
compris) attendait le lecteur sans faire tourner le *looper* principal de Robolectric, où reprend
la lecture DataStore des réglages ; l'attente le fait tourner. Les scripts d'émulateur bornent
`am instrument` (15 min) et relèvent `dumpsys` depuis l'hôte.

**Reste à vérifier sur la Shield** : saisie d'une adresse puis OK sur un profil public (le clavier
doit se fermer), connexion et accueil avec images, lecture directe et
transcodée (HEVC HDR, Dolby Vision, passthrough audio vers l'ampli), recherche Seerr.

### 4.16 Fluidité (partie performances du J8, 26/10/2026)

Constat du mainteneur : « l'interface est belle, mais la navigation lague, encore plus sur le
Chromecast ». Mesuré par l'orchestrateur sur une Shield TV (`dumpsys gfxinfo`, 859 images d'une
session réelle) : 47 % d'images en retard, percentiles 50/90/95/99 à 15/77/150/600 ms, GPU sain
(90e percentile à 10 ms) : le goulot est le **fil principal**. Les appareils faisaient tourner
l'**APK de débogage** de la CI : `DEBUGGABLE`, `run-from-apk` (aucun code compilé d'avance, un JIT
bridé par le mode débogable), ni R8 ni profil.

**Version optimisée** (`release`, artefact `cine-doury-release-apk` de la CI) :
- R8 sur le code et les ressources (`proguard-android-optimize.txt`), **sans renommage**
  (`-dontobfuscate`, `app/tv/proguard-rules.pro`) : les piles du journal (`adb logcat -s CineDoury`)
  et du futur rapport de diagnostic (J7) restent lisibles sans fichier de correspondance ; le
  renommage ne fait rien gagner en vitesse. Les bibliothèques apportent leurs règles
  (kotlinx.serialization, Room, Media3, Coil, OkHttp, Koin, SDK Jellyfin) : aucune règle ajoutée,
  et le test de bout en bout ci-dessous le vérifie. APK de 59 Mo (débogage) à 10 Mo.
- **Baseline Profile et Startup Profile** générés sur l'émulateur API 34 de la CI par `:benchmark`
  (`BaselineProfileGenerator` : démarrage jusqu'à l'accueil ; accueil, bibliothèque, fiche, grille),
  versionnés dans `app/tv/src/release/generated/baselineProfiles/`, enregistrés par un commit
  `[record-profiles]` (comme les captures). Le Startup Profile range les classes du démarrage en
  tête du dex (`dexLayoutOptimization`). `profileinstaller` écrit le profil au premier lancement
  d'un APK installé par `adb install` ; Android le compile ensuite à la maintenance (`bg-dexopt`),
  ou tout de suite par `adb shell cmd package compile -m speed-profile -f fr.guim.cinedoury`.
- **Signature** : depuis le J7, la clé de release quand le workflow « Release » l'a (§ 4.18) ;
  sinon (CI d'une PR, poste de développement), la clé de débogage versionnée, pour que l'APK
  optimisé s'installe par-dessus celui de débogage sur les émulateurs de la CI. Un APK signé avec
  la clé de débogage n'est jamais publié. Passer de l'une à l'autre demande une désinstallation.
- `:benchmark` (plugin `cinedoury.android.test`, `com.android.test` auto-instrumenté) pilote l'appli
  **de l'extérieur**, comme une télécommande : UiAutomator, touches injectées, étiquettes de test
  exposées en identifiants de ressource (`testTagsAsResourceId`, `MainActivity`). Il ne connaît
  aucune classe de l'appli, donc vaut pour l'APK de débogage comme pour l'optimisé.
- Job « Emulator » (API 30 et 34) : après les tests du débogage, `ci/emulator-release.sh` installe
  l'APK optimisé **par-dessus** (`OptimisedApkTest` : la session est gardée), puis, depuis un premier
  lancement, se connecte, lance un film et vérifie que la position avance.

**Mise en page à 960 × 540 dp sur toute TV.** En 4K, la Shield met les applis en page sur
1920 × 1080 dp (3840 px en xhdpi) : tout y était dessiné à moitié de la taille prévue, avec deux
fois plus de cartes à composer. `CineTheme` fixe désormais la densité d'après la largeur de la
fenêtre (`TvLayoutDensity`, largeur en px ÷ 960). Choix de faire la mise à l'échelle dans Compose
(`LocalDensity`) plutôt qu'en surchargeant la configuration de l'activité : elle suit la fenêtre
si l'écran change de mode pendant que l'appli tourne, ce qu'une densité fixée au lancement de
l'activité ne ferait pas. Effets de bord vérifiés :
- **images** : demandées au serveur à la taille de leur place **plafonnée à celle du 1080p**
  (`TvLayout.imageScale`, 0,5 en 4K) : une affiche demandée en 4K pèserait quatre fois plus en
  mémoire (3 Go sur la Shield) et chasserait les autres du cache de Coil, pour une différence
  invisible du canapé ; l'image est agrandie par le GPU ;
- **texte** : en dp et sp, il suit (l'échelle de police du système est gardée) ;
- **sous-titres** : déjà relatifs à la hauteur de l'image (`SubtitleView`, fractions), inchangés ;
- **lecteur** : la vidéo est une surface, ses commandes sont en Compose ;
- **boîtes de dialogue** : dessinées dans la même fenêtre (`ConfirmDialog`), elles suivent ;
- **clavier** : celui du système, hors de l'appli, garde sa taille.
Test : `TvLayoutScreenshotTest` prend la grille d'une bibliothèque en `w1920dp-h1080dp-xhdpi`
(capture 150, 3840 × 2160) et la compare, réduite de moitié, à la capture 50 en 1080p : moins de
1 % des pixels diffèrent (bords des lettres) ; sans la mise à l'échelle, 57,7 %.

**Rail** : le contour de l'entrée focalisée débordait de la barre et se faisait rogner (par le
défilement de la liste et son fondu, `Offscreen`). Les entrées sont désormais à 16 dp des bords
(`CineSizes.railInset`), la liste défilante couvre toute la barre, l'entrée focalisée ne grandit
pas (`CineScale.RAIL`, elle se remplit déjà de blanc) et son halo est réduit (`railFocusHalo`,
6 dp) : contour et halo tiennent dans la barre, ouverte comme fermée. Icônes et libellés gardent
leur place. Avant / après : `docs/screenshots/perf/rail-avant.png`, `rail-apres.png`.

**Corrections guidées par la lecture du code** (le rendu ne change pas) :
- `RemoteImage` n'utilise plus `BoxWithConstraints` (une sous-composition par carte, la plus
  coûteuse des mises en page) : l'URL est faite par un *mapper* de Coil (`ImageSourceMapper`) à la
  taille que Coil résout lui-même ; les aperçus BlurHash décodés sont gardés en cache (256).
- **Bibliothèque** : l'index de l'élément focalisé (écrit à chaque pas) sortait de l'état de
  l'écran : chaque pas recomposait l'écran et toutes les cartes visibles de la grille. Il est gardé
  hors de l'état (`LibraryActions.focusedIndex`) et lu à l'ouverture de la grille.
- **Accueil** : le titre focalisé n'est plus lu au sommet de l'écran, mais par le bandeau et les
  fonds seulement ; un pas du focus ne recompose plus l'accueil et ses rangées.
- **Cartes** : une seule animation de focus par carte (la légende la partage) au lieu de deux.
- **Éléments partagés** : seule la carte focalisée en dernier porte la transition affiche → fiche
  (`sharedPosterSource`) ; une grille en portait une par carte, chacune avec son calque et son état
  dans la scène de transition, même sans transition en cours.

**Effets allégés** : aucun. Le halo flou (`BlurMaskFilter`), le fond d'ambiance (BlurHash 32 × 18),
le fondu du bandeau et la transition d'éléments partagés sont gardés : la mesure de l'orchestrateur
montre un GPU sain (90e percentile à 10 ms sur la Shield), et le coût était sur le fil principal. Si
le Chromecast montre encore des images lentes au GPU (`dumpsys gfxinfo`, « Slow issue draw
commands »), la première candidate est le halo, dessinable en contour sans flou pour les cartes
(le rail a déjà le sien réduit).

**Pour le J7 et le J8** :
- J7 : seule la `signingConfig` de `release` doit changer (clé de release, hors du dépôt) ; garder
  `isMinifyEnabled`, `isShrinkResources`, `-dontobfuscate` et le bloc `baselineProfile`. Après le
  changement de clé, l'APK de release ne s'installe plus par-dessus celui de débogage : le dire
  dans les notes de version. Le rapport de diagnostic peut citer les piles telles quelles (pas de
  renommage).
- J8 : régénérer les profils (`[record-profiles]`) après tout changement notable des écrans ;
  `BEFORE_REF` est vidé dans `ci.yml` depuis le 27/10 (la comparaison avec l'avant ne valait que
  pour cette PR, et sa mesure de l'ancien APK échouait par intermittence sur `main`) ;
  les mesures sur appareil remplacent celles de l'émulateur pour décider d'alléger un effet. Le
  J8 l'a renseigné le temps de sa PR, puis revidé : la passe « avant » s'est figée (§ 4.20).

**Mesures** (Macrobenchmark, `NavigationBenchmarks`, émulateur Android TV API 34 de la CI, rendu
logiciel : comparer les colonnes entre elles, jamais avec un vrai appareil) :

Médianes de `NavigationBenchmarks` (5 démarrages, 3 passages par parcours). Run
[40424966656](https://github.com/guim31/cine-doury/actions/runs/40424966656) (`526966f`), un seul
émulateur pour les trois colonnes :
- **avant, débogage** : l'APK de débogage installé jusqu'ici (code de `main`) ;
- **avant, optimisé** : le même code en version optimisée (R8, profils des bibliothèques seuls) ;
- **après** : la version optimisée de cette PR, avec le profil propre à l'appli (`c5a1f70`).

| Mesure | Avant, débogage | Avant, optimisé | Après |
|---|---|---|---|
| Démarrage à froid, première image (`timeToInitialDisplay`) | 1 311 ms | 648 ms | **588 ms** |
| Démarrage à froid jusqu'à l'accueil chargé, profil choisi (`timeToFullDisplay`) | 5 445 ms | 4 081 ms | **3 870 ms** |
| Accueil à la croix : temps CPU par image, P50 / P90 / P99 | 264 / 438 / 586 ms | 259 / 390 / 498 ms | **242 / 374 / 472 ms** |
| … images dessinées pour le même geste | 64 | 88 | **102** |
| Accueil → bibliothèque → fiche → retour : P50 / P90 / P99 | 160 / 303 / 581 ms | 166 / 251 / 495 ms | 160 / 265 / 562 ms |
| … images dessinées | 82 | 91 | **96** |
| Grille, touche maintenue : P50 / P90 / P99 | 160 / 225 / 329 ms | 159 / 226 / 340 ms | 161 / 205 / 273 ms |
| … images dessinées | 39 | 73 | **78** |

Un run plus ancien, sans le profil de l'appli (40403784211, tentative 3), donnait le même ordre :
première image 1 012 / 505 / 496 ms, accueil P99 382 / 302 / 335 ms. Les absolus varient d'un
runner à l'autre (de 20 à 30 %) : seules les colonnes d'un même run se comparent.

Lecture :
- **La version optimisée divise par plus de deux le premier affichage** (1 311 → 588 ms) et retire
  1,6 s au démarrage jusqu'à l'accueil. Pour le même geste, l'appli dessine bien plus d'images
  (accueil : 64 → 102 ; grille à touche maintenue : 39 → 78) : l'APK de débogage en sautait la
  moitié, ce sont les saccades signalées.
- **Le profil de l'appli et les corrections du code** ajoutent sur « avant, optimisé » : −9 % sur la
  première image, −5 % jusqu'à l'accueil, −5 à −7 % sur les images de l'accueil, P90 et P99 de la
  grille en baisse (226 → 205, 340 → 273 ms). L'aller-retour vers la fiche reste dans le bruit
  (P99 495 contre 562 ms, P90 en baisse). L'émulateur dessine en logiciel (SwiftShader), ce qui
  domine le temps de chaque image : l'effet du code sur le fil principal se lira mieux sur les
  appareils.
- Les temps absolus ne disent rien d'un vrai appareil ; seul l'ordre entre colonnes compte.

**À mesurer sur les appareils** (après la fusion, mêmes gestes que l'orchestrateur le 26/10) :
installer `cine-doury-release-apk` par-dessus l'APK de débogage (`adb install -r`), lancer l'appli
une fois, puis `adb shell cmd package compile -m speed-profile -f fr.guim.cinedoury` ; vérifier
`dumpsys package fr.guim.cinedoury` (plus de `DEBUGGABLE`, `status=speed-profile`). Puis
`adb shell dumpsys gfxinfo fr.guim.cinedoury reset`, la même session de navigation, et
`dumpsys gfxinfo fr.guim.cinedoury` : images en retard, percentiles 50/90/95/99, « Slow UI
thread », « High input latency ». Sur la Shield en 4K, vérifier en plus que l'accueil montre
exactement les captures du 1080p (`am get-config` inchangé : `w1920dp-h1080dp`).

### 4.17 Réglages (J7)

**Pages** (`:feature:settings`, `SettingsPageScreen`) : une première page qui liste les thèmes, chacun
avec une ligne qui dit ce qu'il contient, puis une page par thème : Lecture, Image, Son, Sous-titres,
Intro et générique, Accueil et navigation, Bibliothèques, Apparence, Profil, Réseau, Recherche (mène
directement à Seerr, § 4.12), Appareil, Avancé. Chaque ligne dit ce qu'elle fait **et sa valeur par
défaut** (« … Par défaut : 90 %. »). Les lignes sont groupées par **à qui** elles appartiennent : « Pour
vous (Léa) » — suivent le profil — et « Pour cette télévision » — tous les profils de cet appareil, de
son ampli et de son réseau. Un choix s'ouvre dans un panneau à droite, la valeur courante cochée et
focalisée ; OK choisit, « Retour » referme et rend le focus à la ligne. ← → règlent un pas à pas
(taille des sous-titres, décalage audio). Focus initial sur la première ligne.

**Où chaque réglage est gardé et ce qu'il fait** :

| Page | Réglage (défaut) | Gardé | Effet |
|---|---|---|---|
| Lecture | sauts ← / → (10 s / 30 s), seuil « vu » (90 %) | profil, sur l'appareil (DataStore `player`, clés suffixées `@serveur/utilisateur`, repli sur la valeur de l'appareil) | lecteur (§ 4.11) |
| Lecture | qualité sur le réseau de la maison (Originale), qualité à distance (8 Mb/s), débit maximal (120 Mb/s) | appareil | `MaxStreamingBitrate` et `maxBitrate` du profil : la qualité selon que l'adresse en service est locale ou distante, plafonnée par le débit maximal ; l'« Originale » vaut le débit maximal. Le panneau « Qualité » du lecteur part de cette valeur |
| Lecture | lecture directe (Automatique, Forcée, Jamais) | appareil | Forcée : le fichier tel quel (`/Videos/{id}/stream?static=true`) même quand le serveur ne l'aurait pas envoyé ainsi ; ses raisons restent affichées, et le repli vers le transcodage suit un échec (§ 4.11). Jamais : `EnableDirectPlay`/`EnableDirectStream` à faux, le serveur transcode |
| Lecture | ASS incrustés par le serveur (non) | appareil | § 4.11 |
| Image | fréquence d'images adaptée (oui), résolution adaptée (non), HDR autorisé (oui), Dolby Vision autorisé (oui), format (Adapter) | appareil | résolution : le mode de l'écran de la plus petite taille qui contient le film (`DisplayModes`), à sa cadence si la fréquence est adaptée ; HDR non autorisé : HDR10, HDR10+ et HLG retirés du profil (le serveur fait le tone mapping) ; Dolby Vision non autorisé : profils DV retirés, profil 8 lu par sa couche de base, et le décodeur Dolby Vision écarté par ExoPlayer |
| Son | langue audio préférée (celle du fichier) | **profil, sur le serveur** (`AudioLanguagePreference`) | pré-sélection des pistes (§ 4.10), sur tous les appareils du profil |
| Son | sortie stéréo forcée (non), décalage audio (0 ms, ± 500 par 25), passthrough par format (tous) | appareil | stéréo : deux canaux, rien vers l'ampli, l'audio du serveur en AAC ; décalage : l'horloge du son vue par Media3 est décalée (`AudioOffset`, `ForwardingAudioSink`), l'image suit |
| Sous-titres | mode (Ceux du fichier), langue (Aucune) | **profil, sur le serveur** (`SubtitleMode`, `SubtitleLanguagePreference`) | pré-sélection (§ 4.10) |
| Sous-titres | taille, couleur, contour, fond, position | profil, sur l'appareil | **les mêmes clés** que la barre du lecteur (§ 4.11) : un réglage fait en lecture devient la valeur par défaut, et inversement ; aperçu sous le titre de la page |
| Intro et générique | intro et récapitulatif (Bouton), épisode suivant automatique (oui), compte à rebours (10 s), « toujours là ? » (après 3 épisodes) | profil, sur l'appareil | lecteur |
| Accueil et navigation | « Personnaliser l'accueil » (rangées, ordre, bibliothèques, fenêtre, § 4.9), bandeau à la une (oui), vue par défaut (Affiches), titres sous les affiches (non), indicateur « non vu » (oui) | profil, sur l'appareil (DataStore `settings`) | bandeau : sans lui, les rangées partent du haut, focus sur la première ; vue : une bibliothèque jamais ouverte s'ouvre dans cette vue ; titres et « non vu » : `CardPreferences` du thème, sur toutes les cartes |
| Bibliothèques | place dans le menu (1re, 2e… ou masquée), icône, index local | ordre : **profil, sur le serveur** (`OrderedViews`) ; masquée : profil, sur l'appareil (`rail_hidden`) ; icône et index : serveur, sur l'appareil | le rail et les rangées de l'accueil suivent l'ordre du serveur (§ 4.9) |
| Apparence | thème (Sombre), animations réduites (non), taille du texte (Normale ; Grande 115 %, Très grande 130 %), taille de l'interface (Compacte ; Très compacte, Normale, § 4.21) | appareil | `CineTheme(variant, reducedMotion, textScale, layoutSize = …)` ; animations réduites aussi quand le système coupe les siennes (`CineMotion.isReduced`) |
| Profil | code PIN, changer de profil, se déconnecter ; au démarrage (Choix du profil, appareil) | § 4.7 | § 4.7 |
| Réseau | adresses locale et distante, basculement (Locale puis distante ; Locale seulement ; Distante seulement), oublier le serveur | appareil | `AddressResolver.resolve(server, mode)`, au démarrage |
| Appareil | nom dans Jellyfin (celui de la télévision), mise à jour (§ 4.18), à propos (licences) | appareil | le nom part dans `Authorization` (`Device="…"`) dès la requête suivante, flux du lecteur compris : la liste des appareils du serveur le prend aussitôt ; une session déjà ouverte garde le nom de son ouverture |
| Avancé | journaux de l'appli, rapport de diagnostic (§ 4.19), vider le cache | — | vider : mémoire et disque de Coil, puis l'index local relu |

**Source** (`LibrarySource`, Jellyfin) : `setPlaybackPreferences` et `setLibraryOrder` lisent
`GET /Users/Me` et réécrivent toute la configuration par `POST /Users/Configuration?userId=…` (le
reste inchangé) ; `SourceConnector.setDeviceName` change le `DeviceInfo` de tous les clients ouverts.

**Écarts et choix** :
- Mode et langues des sous-titres, langue audio et ordre des bibliothèques sont gardés **sur le
  serveur** : Jellyfin les a déjà par profil, et l'appli les lisait depuis le J3. Ils suivent la
  personne sur tous ses appareils, l'interface web comprise.
- Sauts, seuil « vu », intro, épisode suivant et apparence des sous-titres passent de l'appareil
  (J4) **au profil sur l'appareil** ; la valeur gardée avant le J7 devient le défaut de chaque profil.
- Au démarrage seulement, le choix de l'adresse ; en cours de session, l'adresse ne change pas.
- **Laissés de côté** : moteur mpv, téléchargements, restrictions des profils enfants, statistiques
  (P1, hors périmètre) ; mode nuit, règles par bibliothèque, couleur d'accent, fond animé, écran de
  veille, démarrage avec la TV, limite sur données mobiles, taille des affiches par défaut : aucune
  fonction P0 ne les applique encore, et un réglage sans effet ne s'affiche pas.

### 4.18 Mise à jour, versions et publication (J7)

**Versions** : `versionName` = `X.Y.Z` de l'étiquette `vX.Y.Z`, `versionCode` = X × 1 000 000 +
Y × 10 000 + Z × 100 + N (Y et Z sous 100 : 1.10.0 > 1.9.99 ; N, de 1 à 99, le numéro d'une
**version d'essai**, 0 pour une release), écrits par Gradle depuis `CINE_DOURY_VERSION` (workflow
« Release »). Jusqu'à la 0.9.0, le code valait X × 10 000 + Y × 100 + Z (900 pour la 0.9.0) : le
nouveau reste au-dessus (0.9.1 = 90 100). **Version d'essai** (essai réel 4, § 4.22) :
`X.Y.Z-essai.N`, le code d'une branche signé avec la clé de release, qui s'installe par-dessus la
release `X.Y.Z` sans désinstaller (donc sans se reconnecter), et sous laquelle la release suivante
s'installe à son tour. Elle n'est jamais publiée, et l'appli ne propose jamais une version d'essai :
installée, elle se voit proposer la release suivante (`UpdatePolicy`, `ReleaseClient`). Sans elle (CI, postes) : `0.1.0-dev`, code 1, une **version de développement** : elle
le dit (Réglages → Appareil, écran de mise à jour) et ne se voit rien proposer, sans interroger
GitHub.

**Source** : `GET https://api.github.com/repos/guim31/cine-doury/releases?per_page=30`, sans jeton (60
requêtes par heure et par adresse). Brouillons, préversions, étiquettes hors `vX.Y.Z` et releases sans
APK écartés ; la plus haute version au-dessus de l'installée est proposée. 403 et 429 :
« GitHub limite les vérifications », avec l'heure de `X-RateLimit-Reset` ; réponse illisible,
injoignable : deux phrases et « Réessayer ». **Écart au § 7** : GitHub est le seul service en ligne
hors du serveur de la maison ; il n'est interrogé que pour lire les releases, et rien de la maison ne
lui est envoyé.

**Quand** : au premier affichage de l'accueil, une fois par lancement et **une fois par jour au plus**
(`last_update_check`) ; une version proposée et non ignorée ouvre l'écran de mise à jour,
« Installer » focalisé. À la demande : Réglages → Appareil → « Mise à jour de l'appli », qui montre
aussi une version ignorée (marquée telle).

**Écran** (`UpdateScreen`) : version, date, journal des changements (Markdown simple : titres, puces,
paragraphes ; gras, code et liens réduits à leur texte ; ↑ ↓ le font défiler), puis « Installer »,
« Plus tard », « Ignorer cette version ». Puis :
1. **Autorisation** : sans `canRequestPackageInstalls()`, l'écran explique où activer « Sources
   inconnues » sur Google TV et sur Shield et y mène (`ACTION_MANAGE_UNKNOWN_APP_SOURCES` de l'appli,
   à défaut les réglages de sécurité, à défaut les réglages) ; au retour, l'installation reprend seule.
2. **Téléchargement** dans `cacheDir/updates`, avec sa progression.
3. **SHA-256** comparé au fichier `cine-doury-X.Y.Z.apk.sha256` publié avec l'APK ; différent ou
   absent : rien n'est installé, le fichier est effacé.
4. **Signature** : les certificats de l'APK (`getPackageArchiveInfo`, `GET_SIGNING_CERTIFICATES`) et
   ceux de l'appli installée, comparés par empreinte SHA-256 ; différents (APK de débogage installé,
   release signée par la clé de release) : « Désinstallez d'abord cette version » en deux phrases, et
   « Ouvrir les réglages de l'appli ».
5. **Prête** : « Installer maintenant » donne l'APK à `PackageInstaller` (session `MODE_FULL_INSTALL`,
   `REQUEST_INSTALL_PACKAGES` dans le manifeste de `:feature:update`) ; Android demande confirmation,
   puis l'appli redémarre dans sa nouvelle version. Un refus d'Android s'affiche, son message journalisé.

**Signature et publication** : workflow `.github/workflows/release.yml`.
- Étiquette `v*` posée par le mainteneur : construit `:app:tv:assembleRelease` (configuration
  `release` de la PR « fluidité » : R8, ressources, Baseline Profile), le signe avec la **clé de
  release** — keystore PKCS12 décodé de `RELEASE_KEYSTORE_BASE64` dans `$RUNNER_TEMP`, effacé ensuite ;
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` —, vérifie `versionName`,
  `versionCode` (`aapt2`), la signature (`apksigner`) et qu'elle n'est pas celle de la clé de débogage,
  écrit le SHA-256 et le journal des changements (`docs/releases/vX.Y.Z.md` s'il existe, sinon les
  commits depuis l'étiquette précédente), puis publie la release (`gh release create`).
- Sans secrets (fork, secrets non posés) : construit et vérifie, **ne signe pas et ne publie pas**,
  le dit par un avertissement, sans échouer.
- **Chaque PR le joue à blanc** (job « Release (dry run) ») : version `v0.0.1`, jamais de secret
  (même sur une PR du dépôt), jamais publié ; APK, SHA-256 et journal en artefact.
- **Version d'essai**, à la main : « Run workflow » de « Release » avec `trial: true` et `source` =
  une branche (`gh workflow run release.yml -f trial=true -f source=<branche>`). Le workflow
  construit le code de la branche, le signe avec la clé de release (secrets, comme une release), le
  numérote `X.Y.Z-essai.N` (X.Y.Z la dernière étiquette, N `trial_number` ou le numéro du run ramené
  de 1 à 99) et le dépose **en artefact seulement** (`release-X.Y.Z-essai.N`) : ni release, ni
  étiquette. Seul un mainteneur peut lancer un workflow à la main : les secrets ne sont jamais donnés
  à une PR. `version.sh` refuse une version `-essai` hors de ce mode.
- La clé de débogage versionnée (`app/tv/debug.keystore`) ne signe que les APK de débogage.

**Retour arrière** : Android refuse un `versionCode` inférieur pour une appli non système, et
désinstaller efface tout (profils, jetons chiffrés par une clé du Keystore qui ne s'exporte pas,
réglages). Pistes étudiées :
- *garder l'APK précédent et guider une désinstallation* : perd les sessions et les réglages à chaque
  retour, et les jetons ne peuvent pas être exportés (Keystore) ;
- *exporter et réimporter les réglages* : sans les jetons, un export n'éviterait pas de se reconnecter
  à chaque profil ; beaucoup de code pour un geste rare ;
- **retenu : la version de retour.** Revenir à la 1.1.0 après une 1.2.0 défectueuse, c'est publier une
  **1.2.1 qui reprend le code de la 1.1.0** : « Run workflow » de « Release », `version: v1.2.1`,
  `source: v1.1.0` ; le workflow construit le code de `source` avec le numéro de `version`, et le
  journal dit « Retour à la version 1.1.0 ». L'appli la propose comme toute mise à jour : rien n'est
  perdu, aucune désinstallation. Un `version` déjà publié est refusé.

### 4.19 Journaux et rapport de diagnostic (J7)

- **Journaux** : `FailureLog` (§ 4.13) garde aussi en mémoire les **50 derniers** échecs (heure,
  niveau, message, classe et message de l'erreur) ; « Avancé » → « Journaux de l'appli » les montre,
  le plus récent en haut. `PlaybackLog` garde les **20 dernières décisions de lecture** (titre, mode,
  raisons, conteneur, codecs, débit, repli, lecture directe forcée), écrites par le lecteur.
- **Rapport** (`DiagnosticReport`, `:core:source`, texte brut en anglais technique, comme les
  journaux du serveur qu'il rejoint) : l'appli (version, code, type de build), l'appareil (modèle,
  Android, mode de l'écran, densité et taille en dp), ses capacités (écran et HDR, décodeurs, Dolby
  Vision, décodeurs audio, sortie audio et passthrough, profil envoyé), le serveur (nom, version,
  accès local ou distant, http ou https), les réglages de lecture et ceux du profil sur le serveur,
  le réseau et l'apparence, les échecs et les décisions de lecture. **Aucun secret** : ni jeton, ni mot
  de passe, ni clé ni adresse de Seerr, ni adresse du serveur ; le jeton du profil, la clé et l'adresse
  de Seerr et les adresses du serveur sont en plus **masqués** (`***`) s'ils apparaissaient dans un
  message.
- **Envoi** : « Envoyer au serveur » → `POST /ClientLog/Document` (texte, SDK `systemApi.logFile`) ; le
  serveur répond le nom du fichier créé dans ses journaux (« upload_Cine Doury_… .log »), que l'écran
  affiche ; le mainteneur le lit dans Tableau de bord → Journaux. Refusé si le serveur désactive
  `AllowClientLogUpload` (403 : « Le serveur n'a pas reçu le rapport »).

**Tests du J7** : unitaires `PlayerSettingsStoreTest` (profil ou appareil, qualités, plafond, défauts
du J4), `AppPreferencesTest`, `AddressResolverTest` (basculement), `HomeRepositoryTest` (vue par
défaut), `PlaybackProfilesTest` (HDR, Dolby Vision, stéréo, qualité locale ou distante),
`DisplayModesTest` (résolution adaptée), `AudioOffsetTest`, `ReleaseClientTest` (fausse API GitHub :
à jour, nouvelle version, ignorée, brouillons, réponse invalide, 403 et 429, injoignable,
téléchargement et SHA-256), `UpdateLogicTest` (versions, une fois par jour, sommes, journal Markdown),
`UpdateRepositoryTest` (autorisation, SHA-256, signature, version ignorée, version de
développement, installation), `MemoryLogsTest`, `DiagnosticReportTest` (contenu ; **aucun secret**) ;
intégration `JellyfinSettingsIntegrationTest` (préférences sur le serveur lues par un autre appareil,
ordre des bibliothèques, nom de l'appareil, **rapport envoyé par `ClientLog` et relu dans les journaux
du serveur**) ; interface `J7NavigationTest` (toutes les pages à la télécommande, panneau de choix,
« Retour », focus initial), `AppFlowTest` (le PIN depuis la page « Profil »).

**Captures** (`docs/screenshots/j7/`, 1920 × 1080, sombre et noir OLED) : réglages (150), Lecture
(151) et son choix de qualité ouvert (152), Image (153), Son (154), Sous-titres (155), Intro et
générique (156), Accueil et navigation (157), Bibliothèques (158), Apparence (159), Profil (160),
Réseau (161), Appareil (162), Avancé (163), nom de l'appareil (164) ; mise à jour disponible (165),
téléchargement (166), prête (167), signature différente (168), autorisation manquante (169), erreur
(170), à jour (171), version de développement (172) ; rapport de diagnostic (173), journaux (174). Les
réglages du J1 (33, 34) et du J3 (87 à 89) sont refaits sur les nouvelles pages ; les pages « Lecture
et son » et « Apparence des sous-titres » du J4 (101, 102) disparaissent, rangées dans Son et
Sous-titres.

**Limites connues** :
- Rien n'est essayé sur un appareil : installation par `PackageInstaller`, autorisation « Sources
  inconnues » sur Google TV et Shield, décalage audio en passthrough, résolution adaptée (J8 et essais
  réels).
- Le nom d'appareil n'atteint pas une session déjà ouverte côté serveur (elle garde son nom jusqu'à sa
  fin) ; la liste des appareils, si.
- Le changement d'adresse automatique n'a lieu qu'au démarrage.
- La première release publiée et signée (et donc la première mise à jour réelle) attend la clé et les
  secrets posés par le mainteneur.

### 4.20 Finitions 1.0 (J8, 27/10/2026)

**Point de départ** : mesures de l'orchestrateur sur la Shield (Android 11, 4K, version optimisée de
`main` après le J7, `dumpsys gfxinfo`, parcours à la télécommande) : accueil 15 % d'images en retard
(8 / 19 / 23 / 32 ms), « Slow UI thread » 24, **GPU 8 / 18 / 22 / 24 ms** ; fiche 11 % en retard, GPU
au 90e percentile 20 ms ; démarrage à froid 1,3 s jusqu'à « Qui regarde ? » (0,6 s à chaud). Le fil
principal n'est plus le goulot depuis § 4.16 : c'est le **GPU**, qui remplit un écran 4K.

**Coût GPU, sans changer l'aspect** :
- **Halo de focus** (`FocusHalo`) : le `BlurMaskFilter` était flouté par le GPU à chaque image de
  l'animation du focus, sur tout le contour de la carte. Il est désormais flouté **une fois**, en
  logiciel, dans un bitmap partagé par taille de carte (cache de 24 entrées) et découpé comme un
  9-patch : les coins tels quels, les bords droits étirés (le flou y est le même tout du long). Une
  affiche, une vignette, un bouton : une petite image chacun, quelle que soit leur longueur. Il est
  préparé à la résolution du 1080p au plus (`TvLayout.imageScale`, 0,5 en 4K : 4 fois moins de
  mémoire, invisible sur un flou de 14 dp). Captures : écart d'au plus 2/255 sur un canal, aucun
  pixel au-delà (03 à 07, 150).
- **Fond d'ambiance** : l'assombrissement de 60 % (`CineAmbient.DIM_ALPHA`) était un rectangle plein
  écran dessiné à chaque image par-dessus le BlurHash ; il est fondu une fois dans les 32 × 18 pixels
  du BlurHash (ou dans les deux couleurs d'un dégradé), en sRGB comme le GPU : une couche plein écran
  de moins. Captures identiques à 2/255 près, sauf l'antialiasing de quelques bords (< 0,01 % des
  pixels).
- **Laissés tels quels**, après lecture : les voiles de lisibilité et les dégradés du bandeau sont
  déjà construits une fois par taille (`drawWithCache`) ; le masque du bandeau (`bannerFade`) est un
  calque hors écran que HWUI garde tant que l'image ne change pas (une texture par image, pas un
  nouveau rendu) ; les ombres (`shadowElevation`) sont analytiques dans HWUI.
- **Mesure sur l'émulateur** : l'émulateur de la CI dessine en logiciel (SwiftShader, hors de
  l'invité) ; `dumpsys gfxinfo` n'y donne pas de temps GPU utilisable, et `FrameTimingMetric` ne voit
  que le temps CPU des images (`frameDurationCpuMs`) et leur dépassement (`frameOverrunMs`, API 31+).
  Le job « Baseline Profile and benchmarks » devait mesurer aussi la version optimisée de `main`
  (avant, `BEFORE_REF`) sur le même émulateur ; cette passe s'est figée jusqu'à la limite du job
  (run du 27/10), elle est retirée et `BEFORE_REF` revidé : **pas de colonne « avant »**. Le gain
  sur le GPU, seul visé par l'allègement, ne se lit de toute façon que sur la Shield
  (`docs/MESURES.md`, « À vérifier sur appareil »).

Version optimisée de cette PR, émulateur Android TV API 34 de la CI, rendu logiciel : médianes de
trois runs du 27/10 (de la plus basse à la plus haute ; indicatif, à ne comparer qu'à lui-même) :

| Mesure | Médiane |
|---|---|
| `startToHome` : première image (`timeToInitialDisplay`) | 663 à 724 ms |
| `startToHome` : accueil complet (`timeToFullDisplay`) | 3 704 à 4 097 ms |
| `browseHome` : temps CPU d'une image (P90) | 229 à 240 ms (345 à 354) |
| `libraryRoundTrip` : temps CPU d'une image | 139 à 155 ms |
| `scrollGrid` : temps CPU d'une image | 131 à 146 ms |
| `openDetails` : de la touche à la première image de la fiche | 108 à 113 ms (max 157) |
| `openDetails` : temps CPU d'une image | 163 à 175 ms |
| `openSettings` : de la touche à la première image des réglages | 66 à 79 ms (max 103) |
| `openSettings` : temps CPU d'une image | 62 à 68 ms |

Les temps par image de l'émulateur (logiciel, sans GPU) sont dix fois ceux d'un appareil : seuls
comptent les écarts d'un run à l'autre et les sections `openDetails` / `openSettings`, qui tiennent
sous les 300 ms de `FONCTIONNALITES.md` § 13 même ainsi.

**Appareils faibles** (Chromecast avec Google TV : armeabi-v7a, 2 Go, Android 14) :
- `ImageBudget` (`:core:designsystem`) fixe les caches de Coil d'après `ActivityManager` : mémoire
  25 % du tas (`memoryClass`), disque 256 Mo et 6 cartes de la rangée suivante chargées d'avance ; tas
  sous 192 Mo : 20 %, 128 Mo, 3 cartes ; appareil « low RAM » (`isLowRamDevice`) : 15 %, 64 Mo,
  **aucun préchargement** (il chasserait du cache ce que montre l'écran). Avant, Coil prenait 20 % du
  tas et 2 % du disque, et l'accueil préchargeait toujours 6 cartes. Le rapport de diagnostic donne le
  tas, « low RAM » et les ABI.
- **Bibliothèques natives** : l'APK n'en a que deux, `libandroidx.graphics.path.so` et
  `libdatastore_shared_counter.so`, présentes pour les quatre ABI (armeabi-v7a, arm64-v8a, x86,
  x86_64) ; Media3 et Room (SQLite du système) n'en apportent pas. `ci/check-apk.sh` (job « Build »)
  échoue si une bibliothèque arm64 n'a pas sa version 32 bits, et publie la taille de l'APK. Rien
  dans le code ne suppose 64 bits (tailles de fichiers et positions en `Long`).
- **Taille de l'APK optimisé** : 10 960 098 octets avant (`main` du J7, `ci/check-apk.sh`), environ
  10 988 538 après (+28 Ko : `ImageBudget`, `FocusHalo`, journal des gestes, sémantique) ; les profils
  régénérés la font varier de quelques Ko.

**Profils et mesures** :
- `BaselineProfileGenerator` parcourt en plus une fiche ouverte depuis l'accueil, les réglages, la
  page « Appareil » et l'écran de mise à jour (J7) ; les profils sont régénérés (`[record-profiles]`).
- `NavigationBenchmarks` gagne **`openDetails`** (OK sur la première carte de l'accueil) et
  **`openSettings`** (OK sur « Réglages » dans le rail) : `TraceSectionMetric` sur les sections
  asynchrones `CineDoury.openDetails` et `CineDoury.openSettings` que l'appli ouvre à la touche
  (`Navigator.push`) et ferme à la première image de l'écran (`OpeningTrace`), plus `FrameTimingMetric`.
  Gratuit hors d'une trace ; Perfetto les montre aussi sur un appareil.
- Entrées du rail étiquetées (`RailTags` : `rail-search`, `rail-home`, `rail-library-<id>`,
  `rail-profiles`, `rail-settings`) : les tests et les scripts les trouvent sans lire leur libellé,
  qui suit la langue.

**Accessibilité** (`FONCTIONNALITES.md` § 13) :
- **Contrastes** : `ContrastTest` calcule le rapport WCAG 2.2 de chaque paire des jetons, sombre et
  noir OLED : `texte`, `texte-2`, `texte-3` et `accent` sur `fond`, `surface-1`, `surface-2` ; texte sur
  l'accent (normal et pressé) ; `danger` sur `fond` et `surface-1` ; `succès` ; `fond` sur `texte`
  (ligne focalisée) ; texte sur les panneaux et les voiles du lecteur et sur le fond d'ambiance,
  au-dessus d'une image blanche ; le contour du focus (3:1, élément d'interface). Seuil 4,5:1 (texte),
  3:1 (contour). **Corrigé** : `texte-3` passe de `#6E7480` (4,2:1 sur `fond`, 3,4:1 sur `surface-2`) à
  `#858B96` (4,7:1 au pire) ; le texte d'un bouton destructif était à 3,5:1 sur `danger` : son fond
  passe à `dangerButton` `#C9373C` (4,6:1), `danger` reste le rouge du texte. Les éléments désactivés
  (`surface-3`, flèches éteintes d'un pas à pas) sont exemptés par WCAG.
- **Focus toujours visible** : `J8FocusTest` (appli entière sur une source en mémoire) ouvre l'accueil,
  chaque entrée du rail (Recherche, une bibliothèque, Réglages, « Qui regarde ? »), une fiche, la
  boîte « Quitter », un panneau de bibliothèque, et vérifie qu'un élément a le focus après chaque
  ouverture et chaque « Retour ». **Défaut trouvé et corrigé** : « Retour » sur un panneau de
  bibliothèque (Trier, Filtres, Affichage, Gérer) rendait le focus au rail ; il revient au bouton du
  panneau, comme « Fermer ». Au retour d'une fiche ouverte par « Fiche » du bandeau, le focus revient
  sur « Lire » (premier bouton du bandeau) : choix gardé.
- **TalkBack** : les entrées du rail ne sont plus lues deux fois (l'icône répétait le libellé) et
  disent laquelle est ouverte (`selected`) ; une carte avec image dit son titre et sa ligne secondaire
  (sa légende est hors de la partie focalisable) ; les boutons ronds gardent leur libellé (description
  de l'icône), leur légende visible au focus n'est plus lue en double, un favori ou « vu » actif est
  `selected` ; une case de sélection dit son état ; les lignes des réglages sont des boutons, des
  interrupteurs (`Role.Switch`, état) ou des choix (`Role.RadioButton`, `selected`) ; les titres d'écran
  et de rangée sont des titres (`heading()`, `Modifier.asHeading()`). Fonds, halos, voiles, BlurHash et
  illustrations restent sans description.
- **Taille du texte** au plus grand réglage (130 %), captures 190 à 198 : les titres d'écran (« Affiche »,
  « Titre 1 ») grandissent de 15 % au plus (`HEADING_MAX_SCALE`, **écart** au § 4.17 : à 130 %,
  « Apparence » se coupait en « Apparenc/e » dans sa colonne) ; la barre d'une bibliothèque passe sur
  deux lignes au lieu de sortir de l'écran (`FlowRow`) ; les touches du clavier de recherche gardent leur
  taille et réduisent leur libellé (`TextAutoSize`, jusqu'à 14 sp) ; le nom d'un profil tient sur deux
  lignes ; l'explication d'une ligne de réglage sur trois. Restent coupés, par construction et à toute
  taille : la légende d'une carte (une ligne, le titre complet est sur la fiche) et le synopsis replié
  (dépliable par OK).

**Focus jamais rogné** (signalé par l'orchestrateur sur la Shield, v0.7.0) : sur Quick Connect, le
contour et le halo de « Utiliser un mot de passe » étaient coupés en bas par la colonne défilante de
`FormScreen` (une colonne qui défile coupe ce qui dépasse, et le défilement n'amène à l'écran que les
bornes de l'élément, pas son contour). Jeton `CineSizes.focusRoom` (écart + contour + halo, 19 dp) :
`FormScreen` laisse cette place sous son dernier élément et défile pour montrer l'élément focalisé
**avec** elle (`KeepFocusRoom`, un `BringIntoViewSpec`) ; le code de Quick Connect est un peu moins
espacé pour que la page tienne sur un écran (capture 23, avant / après). Les autres conteneurs qui
coupent et portent des éléments focalisables à leur bord ont reçu la même place : colonnes des
panneaux du lecteur, panneaux des fiches, des bibliothèques et des réglages, profil de l'appareil
(captures 92 à 94, 103, 130 à 132, 140, 141, 152…).

**Captures** (`docs/screenshots/j8/`) : les écrans clés en **4K** (qualificateurs d'une Shield,
`w1920dp-h1080dp-xhdpi`, 3840 × 2160, mis en page sur 960 × 540 dp) : « Qui regarde ? » (180), accueil
(181), bibliothèque (182), fiche film (183), fiche série (184), lecteur et son interface (185), recherche
(186), une page de réglages (187), mise à jour (188) ; les mêmes en 1080p au texte « Très grande »
(190 à 198) ; sombre et noir OLED. Toutes les captures 1080p existaient déjà en sombre et en noir OLED.
Avant / après des changements d'aspect : `avant-*.png` et `apres-*.png` (bibliothèque 50, suppression
137, « Qui regarde ? » 25, Apparence 159, Quick Connect 23).

**Changements d'aspect** (voulus, vérifiés sur les captures) :
- `texte-3` plus clair ; fond des boutons destructifs plus sombre (contrastes AA) ;
- barre d'une bibliothèque sur deux lignes quand elle ne tient pas sur une : c'était déjà le cas en
  1080p avec trois onglets et quatre boutons (capture 50 : « Affichage » réduit à son icône, rogné) ;
- explication d'une ligne de réglage sur trois lignes au plus (deux avant) ;
- 19 dp de plus en haut et en bas des panneaux et sous les formulaires (focus jamais rogné).

**Les 20 gestes au canapé** : `docs/GESTES.md` (gestes, appuis, cibles) et `ci/couch-gestures.sh
ADB_SERIAL` : touches par `adb shell input keyevent`, fin de chaque geste lue dans le journal de
l'appli (`GestureLog` : touche reçue, écran dessiné, image qui bouge, interface et panneaux du lecteur,
focus dans le lecteur, sous-titres choisis), muet sauf après `adb shell setprop log.tag.CineDoury
DEBUG`, ou à défaut dans `uiautomator dump` ; aucun titre supposé, gestes impossibles sautés, rien
changé sur le serveur. Joué par le job « Emulator » en API 34 contre le Jellyfin de test (temps
indicatifs, résumé du job). **Rouge de l'après-midi du 27/10** : la cause n'était pas l'appli mais le
script et l'émulateur. Les étiquettes des réglages (`setting-<Section>` pour la ligne,
`settings-<Section>` pour la page), le tampon de logcat qui tourne pendant la lecture, et le clavier
de la TV resté attaché après les tests de connexion, qui garde certains « Retour » : le script
réappuie. CI complète verte ensuite (`24243cb`).

**CI rapide** (écart au § 5, décidé avec le mainteneur le 27/10 : les jobs sur émulateur API 34
prennent 40 min et plus) : une PR ne lance que construction, analyse et tests, captures,
intégration et l'émulateur API 30. L'émulateur API 34 (et les gestes), les profils et les mesures
tournent sur un push vers `main`, sur `workflow_dispatch`, ou quand le commit de tête de la PR
contient `[full-ci]` ; `[record-profiles]` lance aussi le job des profils (job `mode` de `ci.yml`).

**Version 1.0.0** : `CHANGELOG.md` (une section par étape, du point de vue de qui regarde),
`docs/releases/v1.0.0.md` (notes publiées par `release.yml`), README (installation par la release,
mise à jour, passage depuis un APK de débogage). L'étiquette `v1.0.0` est posée par le mainteneur.

**À vérifier sur appareil** (l'orchestrateur, avec `docs/MESURES.md` et `docs/GESTES.md`) :
1. Shield, version optimisée de ce jalon : `dumpsys gfxinfo` sur l'accueil, la fiche, la grille ;
   comparer le **GPU** au 27/10 (accueil 8 / 18 / 22 / 24 ms) ; images en retard sous 5 % visées.
2. Chromecast avec Google TV : mêmes mesures, démarrage à froid sous 2 s (`FONCTIONNALITES` § 13),
   `dumpsys meminfo` après 5 min, `memoryClass` et « low RAM » dans le rapport de diagnostic.
3. `ci/couch-gestures.sh` sur la Shield et le Chromecast ; reporter les tableaux dans `docs/GESTES.md`.
4. Ouverture d'une fiche sous 300 ms (geste 13, ou Perfetto `CineDoury.openDetails`).
5. Lecteur (§ 4.11) : cadence de l'écran adaptée, HDR10 et Dolby Vision, passthrough vers l'ampli
   (AC3, E-AC3, TrueHD, DTS), décalage audio en passthrough (§ 4.17), résolution adaptée.
6. Index local (§ 4.10) : premier remplissage sur la vraie bibliothèque (estimé 2 à 4 min sur la
   Shield), filtre HDR ; recherche locale sur ≈ 11 500 titres (§ 4.12).
7. Mise à jour (§ 4.18) : la première release signée, puis une 1.0.1 proposée et installée par
   `PackageInstaller`, l'autorisation « Sources inconnues » sur Google TV et sur Shield ; passage
   depuis un APK de débogage (désinstallation, README).
8. TalkBack activé sur le Chromecast : rail, cartes, réglages (interrupteurs, choix) lus comme ici.
9. Taille du texte « Très grande » sur la Shield en 4K : écrans des captures 190 à 198.
10. Seerr de la maison : affiches par `/imageproxy/tmdb` (§ 4.12), demande d'un film.
11. Mention de licence du SDK Jellyfin (LGPL) dans « À propos » : confirmée par le mainteneur (§ 4.3).
12. Shield (§ 4.15) : clavier fermé après la saisie d'une adresse puis OK sur un profil public.

**Limites connues** :
- Aucune mesure GPU possible en CI (rendu logiciel) : le gain du halo et de l'ambiance se lit sur la
  Shield ; si le GPU y reste au-delà de 16 ms au 90e percentile, les candidats suivants sont le masque
  du bandeau (un calque hors écran plein écran, 33 Mo en 4K) et les deux voiles de lisibilité.
- Les temps de `ci/couch-gestures.sh` comptent l'envoi des touches par `adb` (0,1 à 0,3 s chacune) dans
  le total ; la réponse (dernier appui → écran) est la mesure à comparer.
- Le retour d'une fiche ouverte par « Fiche » du bandeau rend le focus à « Lire », pas à « Fiche ».

### 4.21 Essai réel 3 : la Shield au canapé (27/10/2026)

Six retours du mainteneur sur la v0.7.0 (Shield, Android 11, écran 4K, mise en page de § 4.16).

1. **→ depuis le rail ouvert ne faisait rien** sur l'accueil. Ouvert, le rail (240 dp) recouvre le
   début du contenu : la recherche 2D vers la droite ne trouvait rien, ou un élément caché sous lui.
   `NavigationRail` (et `AppRail`) prend désormais `onContentFocus` : → y rend le focus au contenu
   et referme le rail. Chaque écran le rend **où il l'avait laissé**, par la mémoire qu'il avait
   déjà : l'accueil à la rangée quittée (qui rend sa carte), à défaut à « Lire » ; la bibliothèque à
   sa grille (qui rend sa case) ; la recherche à la rangée de résultats quittée, à défaut à la touche
   quittée du clavier (un `focusRestorer` sur le clavier). Pas de `focusRestorer` sur tout l'écran :
   il interceptait aussi les demandes de focus de l'écran (retour d'une fiche : « Lire » au lieu de
   la carte), et un `focusGroup` sur la barre et la grille faisait arrêter ← sur « Tout » au lieu du
   rail. Test : `R3NavigationTest` (accueil, bibliothèque, recherche).
2. **« Taille de l'interface »** (Apparence ; Très compacte, Compacte, Normale ; appareil, DataStore
   `settings`, clé `interface_size`). **Écart à `UI.md` § 2 et § 5, demandé par le mainteneur le
   27/10** : « tout est trop gros », il veut voir plus d'éléments. La largeur de l'écran de référence
   devient un paramètre (`LayoutSize`, `TvLayoutDensity(size)`), et **toute** la mise en page suit
   (texte, cartes, marges, rail), pas seulement le texte ; les jetons du design system restent pensés
   en dp sur l'écran de `UI.md`. Valeurs :
   | Taille | Écran de référence | Échelle par rapport à `UI.md` | Corps / minimum sur un 1080p |
   |---|---|---|---|
   | **Très compacte** (28/10 ; défaut depuis l'essai réel 4, § 4.22) | 1 440 × 810 dp, texte × 1,1 | 67 % (texte 73 %) | 29 px / 21 px |
   | Compacte (défaut jusqu'à l'essai réel 4) | 1 200 × 675 dp | 80 % | 32 px / 22 px |
   | Normale | 1 080 × 608 dp | 89 % | 36 px / 25 px |
   Pourquoi 1 200 dp par défaut : c'est la plus compacte qui reste lisible à 3 m sans retouche. Le
   plus petit texte (« Minimum absolu », 14 sp) y fait 22 px sur un 1080p, un peu sous les 24 px que
   Google donne pour une TV (12 sp à 2 px par dp), et seuls badges et pastilles l'emploient ; le
   corps (20 sp) fait 32 px. Une affiche normale passe de 300 à 240 px de large sur un 1080p : la
   grille d'une bibliothèque montre 6 colonnes (5 en Normale, 7 en Très compacte, 4 en 0.7), et
   l'écran gagne 25 % de hauteur (675 dp).
   **Deuxième passage (28/10, après l'essai de `d3a2d45`)** : « Grande » (960 dp, celle de `UI.md`
   et de la 0.7) n'avait aucun intérêt pour le mainteneur, qui voulait plus compact encore. Elle est
   retirée ; une valeur « Grande » gardée sur l'appareil se lit « Normale » (`AppPreferencesTest`).
   « Très compacte » met la page sur 1 440 dp ; à cette échelle seule, le plus petit texte
   tomberait à 19 px, en dessous de ce qui se lit à 3 m : son texte est donc agrandi de 10 %
   (`LayoutSize.textScale`, multiplié à « Taille du texte »), 21 px au minimum et 29 px pour le
   corps, quand cartes, marges et images restent à 67 %.
   « Taille du texte » (§ 4.17) reste à part : elle multiplie le texte dans la taille choisie. La
   hauteur de l'écran suit la largeur (16:9). Images : demandées à la taille du 1080p au plus quelle
   que soit la taille (`LocalImageScale`, 1 920 px ÷ largeur de la fenêtre en px), plus d'après la
   densité. Les captures de référence suivent la valeur par défaut (Compacte) ; les trois tailles :
   captures 200 à 203 (accueil, bibliothèque, fiche, lecteur), et `avant-*` / `apres-*` dans
   `docs/screenshots/r3/` (les mêmes écrans en 0.7 et dans la taille par défaut).
   `TvLayoutScreenshotTest` (4K contre 1080p) tolère 2,5 % de pixels différents au lieu de 1 % : à
   1,6 px par dp, les bords tombent entre deux pixels en 1080p et pas en 4K (2 % mesurés) ; une carte
   déplacée en couvre 4 %. Les tests de navigation qui comptent des colonnes passent
   `LayoutSize.Normal` (5 colonnes). L'accueil met désormais son bandeau au-dessus d'une zone de
   rangées de hauteur fixe (point 6) : il reste en haut dans toutes les tailles. Test :
   `R3NavigationTest` (largeur mise en page dans chaque taille).
3. **Lecteur** : boutons ronds de 40 dp (au lieu de 52) et icônes de 20 dp (au lieu de 24)
   (`CineSizes.playerRoundButton`, `playerIcon`, paramètre `small` de `RoundIconButton`) ; « Passer
   l'intro » en pastille de 40 dp, texte en Légende (`playerButtonHeight`, `PrimaryButton(small)`) ;
   barre de progression de 4 dp (8 au focus) et curseur de 16 dp (6, 10 et 20 auparavant). Captures
   203 et 205.
4. **Barre des sous-titres** : ← → ne réglaient que la taille, position et fond étaient
   inatteignables. Trois réglages côte à côte (Taille, Position, Fond), chacun un `StepperRow` en
   mode `vertical` : **← → passent d'un réglage à l'autre, ↑ ↓ changent la valeur du réglage
   focalisé**, aussitôt (↑ : plus grand, plus haut, fond plus marqué ; ▲ ▼ estompés aux bouts). Choix : sur une TV, ← → déplacent le focus partout ailleurs dans
   l'appli ; ↑ ↓ sont libres dans une barre d'une seule ligne, et ce sont déjà les touches du
   raccourci de la taille. Les raccourcis ↑ ↓ maintenus, interface cachée, restent : un appui
   commencé avant que la barre ait le focus (la touche maintenue qui l'ouvre) n'est pas pris par le
   réglage et va jusqu'au lecteur (`StepperRow` ne traite que les appuis qu'il a vus commencer).
   Chaque réglage montre son titre au-dessus de sa valeur, ▲ et ▼ l'un sur l'autre à droite : sur
   une ligne, « Position − En bas + » ne tenait pas dans un tiers de la barre. Test :
   `R3NavigationTest` (atteindre et modifier chacun) ; capture 206.
5. **Barre de la bibliothèque** : « Gestion » s'affichait sans texte, collée à « Affichage ». La
   barre était un `FlowRow` avec un espaceur à poids entre les onglets et les boutons : le dernier
   bouton pouvait n'y recevoir aucune largeur. Elle est désormais faite de deux groupes (onglets,
   boutons) répartis par `SpaceBetween`, chaque bouton à sa largeur entière ; si les deux groupes ne
   tiennent plus sur une ligne (grand texte), les boutons passent dessous et se
   replient entre eux. Captures 204 (les trois tailles en 4K) et 201.
6. **Bandeau de l'accueil qui sautille** : la hauteur du texte changeait d'un titre à l'autre (logo
   ou titre sur une ou deux lignes, ligne d'infos absente, accroche courte). Mise en page fixe
   (`FeaturedText`) : zone du logo ou du titre de 96 dp (`CineSizes.heroTitleHeight`, le logo mis à
   l'échelle dedans, le titre en « Titre 1 » sur deux lignes au plus, aligné en bas), ligne d'infos
   et une ligne d'accroche toujours réservées ; seul le contenu change, en fondu. Test :
   `R3NavigationTest` (position de « Lire » identique pour deux titres de longueurs différentes).
   **Deuxième passage (28/10, après l'essai de `d3a2d45`)** : le bandeau bougeait encore. Il était le
   premier élément de la liste de l'accueil, et chaque passage du focus à une rangée faisait défiler
   la liste jusqu'au pivot : titre, infos et boutons défilaient avec elle. Il est désormais **fixe**,
   au-dessus d'une zone de rangées de hauteur constante (une rangée d'affiches avec son titre, deux
   lignes de légende et la place du focus, aux tailles de texte réglées, `homeRowsHeight`) ; les
   rangées défilent dans cette zone, la rangée focalisée en haut (ses cartes sous son titre). La
   jaquette focalisée grandissait déjà au dessin seulement (`graphicsLayer`), et sa légende garde sa
   place, focalisée ou non : la hauteur d'une rangée ne change pas, et le bandeau ne dépend plus
   d'elle. Pour tenir au-dessus en « Normale » (608 dp de haut), le titre sans logo passe en
   « Titre 1 » dans 96 dp, et l'accroche revient à une ligne ; plus haut que la place (grand texte),
   le bandeau monte sous le haut de l'écran sans être écrasé. Sans bandeau (réglage), l'accueil reste
   une seule liste. Test : `R3NavigationTest` (position verticale du bandeau et de « Lire » identique
   le long d'une rangée, d'une rangée à l'autre, et focus revenu au bandeau).

7. **Icônes des bibliothèques** (deuxième passage, 28/10) : sur la Shield, trois bibliothèques
   avaient la même icône (les étoiles d'« Animes ») et une la montagne des documentaires. Une cause
   probable : les accents étaient retirés avant de chercher les mots-clés, si bien qu'un nom comme
   « Films animés » se lisait « animes ». Désormais « animé(e)s », avec l'accent, désigne les dessins
   animés ; « anime(s) », sans accent, l'animation japonaise. Le jeu passe de 11 à 27 icônes Material
   (Apache 2.0, chemins de `@material-design-icons/svg` 0.14.15, comme les autres) :
   - genres : action et aventure, horreur, science-fiction, romance, humour ;
   - usages : vidéos de famille, 4K, classiques, cinéma du monde, coups de cœur, émissions, cours et
     conférences, cuisine, fêtes, nature, clips musicaux.
   Les mots-clés sont en français et en anglais ; la première règle qui correspond l'emporte, genres
   et usages avant « Films » et « Séries ». Ces deux-là ne servent qu'à une bibliothèque de dossiers :
   une bibliothèque « Films » de type Mixte prend l'icône des films. Le type « Vidéos personnelles »
   de Jellyfin prend l'icône des vidéos de famille (un dossier auparavant). La ligature « œ » se lit
   « oe » (« Coups de cœur »). Le choix manuel (Réglages → Bibliothèques) propose les 27 icônes, chacune
   dessinée à côté de son nom, l'option « Automatique » avec celle que donne le nom ; la ligne de
   chaque bibliothèque montre son icône (`SettingItem.Choice.optionIcons` et `icon`). Une icône
   choisie à la main est gardée par son nom : les anciens choix restent valables. Test :
   `LibraryIconTest` ; captures 207 (rail) et 208 (sélecteur).

### 4.22 Essai réel 4 : la v0.9.0 au canapé (28/10/2026)

Quatre retours du mainteneur sur la v0.9.0 (Shield, écran 4K, taille « Très compacte »).

- **A. « Très compacte » par défaut** (`LayoutSize.Default`, `Appearance.interfaceSize`) : c'est
  celle que le mainteneur a choisie. La taille se garde par son nom (`interface_size`) : une valeur
  déjà enregistrée, « Compacte » comprise, ne change pas ; seul un appareil qui n'en a jamais choisi
  passe à 1 440 dp. Les captures de référence suivent la nouvelle valeur par défaut. Test :
  `AppPreferencesTest`.
- **B. Une rangée et demie sous le bandeau.** Le bandeau prenait ce que laissait une zone d'une
  rangée de haut : on ne devinait pas qu'il y en avait d'autres. Premier passage : bandeau réduit à une
  bande (titre, boutons et infos sur une ligne), les rangées sur tout le reste ; essayé sur la Shield
  (`0.9.0-essai.1`), c'était trop : deux rangées entières en « Très compacte », un bandeau qui
  « passe à peine ». **Règle retenue** (deuxième passage, 28/10) : la hauteur du bandeau est celle de
  l'écran moins **une rangée entière (titre compris) et la moitié de la suivante**, chacune à la
  hauteur de ses cartes (affiches ou vignettes, d'après les deux premières rangées ; deux rangées de
  vignettes tant qu'elles ne sont pas lues ; `homeRowsArea`). Le bandeau se met en page dans cette
  hauteur, en bas (`FeaturedBanner`) : en colonne comme en 0.9.0 (logo ou titre dans 96 dp, infos,
  une ligne de résumé, boutons dessous) ; ce qui ne tient pas disparaît dans l'ordre, le résumé
  d'abord, puis la colonne elle-même pour une seule bande (titre sur deux lignes de « Titre 2 », infos
  à droite des boutons), qui est aussi sa hauteur minimale. Le choix dépend de la hauteur seule, jamais
  du titre : rien ne bouge d'un titre à l'autre. Les hauteurs additionnent des lignes de texte
  mesurées comme `Text` les pose (`textLineHeight`), pas le `lineHeight` des styles. Avec deux rangées
  de vignettes, une rangée et demie dans les trois tailles ; avec deux rangées d'affiches, une et
  demie en « Très compacte » ; en « Compacte » et « Normale », une rangée et demie d'affiches ne
  laisserait pas la place des boutons : le bandeau garde sa bande et la deuxième rangée montre son
  titre et le haut de ses affiches. Test : `R4Home1080pTest` et `R4Home4kTest` (part visible de la
  deuxième rangée entre 40 et 60 %, dans chaque taille). Captures 200 (les trois tailles,
  réenregistrées), `avant-b-*` et `apres-b-*` dans `docs/screenshots/r4/`.
- **C. Le titre de la rangée sautillait** en passant d'une jaquette à l'autre. La liste des rangées
  suivait le rectangle de la carte focalisée (`BringIntoViewSpec`) ; ce rectangle compte
  l'agrandissement au focus (`graphicsLayer`), animé : la liste défilait de quelques points pendant
  l'animation, titre compris. Elle ne suit plus le focus d'elle-même (`ScrolledByIndex`) : l'accueil
  la fait défiler **par l'index** de la rangée qui reçoit le focus (`animateScrollToItem`), la rangée
  focalisée en haut de la zone. Se déplacer dans une rangée ne déplace rien verticalement. Les rangées
  arrivant en plusieurs temps (cache, puis serveur), la liste est remise sur la rangée focalisée, ou
  en haut, quand elles changent : une `LazyColumn` garde sinon sa première rangée par clé, et restait
  sur une rangée lue avant les autres. Test :
  `R4Home*Test` (position du titre identique le long d'une rangée, d'une rangée à l'autre et au
  retour).
- **F. Les rangées passaient sous le rail** (essai de `0.9.0-essai.2`, vu sur le Chromecast) : une
  rangée de l'accueil défilée vers la droite faisait passer ses affiches sous le rail, qui n'a pas de
  fond, ses icônes dessinées par-dessus. La liste de chaque rangée commence désormais au bord du rail
  (`HomeRowView`) : ses cartes y sont coupées, et sa marge et son pivot comptent depuis ce bord. Les
  autres rangées n'étaient pas touchées : la recherche pose ses rangées à droite du clavier, les fiches
  n'ont pas de rail, et la grille d'une bibliothèque défile de haut en bas. Test : `R4Home*Test`
  (aucune carte à gauche du bord du rail après cinq →) ; capture 210 (`apres-f-*`).
- **G. Un peu plus de bandeau, moins de vide entre les rangées** (même essai) : il restait un grand
  vide entre la première rangée et le titre de la suivante, la place des légendes (cachées sur une
  affiche sans le focus) suivie de 32 dp. L'écart entre deux rangées de l'accueil passe à 16 dp
  (`RowGap`, dans la liste et dans la règle de B) : la règle « une rangée et demie » rend ces 16 dp au
  bandeau, qui descend d'autant et laisse mieux voir l'image à la une. Captures 200 (1080p) et 211
  (4K), les trois tailles (`apres-g-*`).
- **E. Versions d'essai installables par-dessus une release** (demandé par l'orchestrateur pour
  essayer A à D sur la Shield) : l'APK de la CI d'une PR, signé de la clé de débogage et de code 1,
  obligeait à désinstaller la v0.9.0 (code 900), donc à se reconnecter. Nouveau schéma de
  `versionCode` et workflow d'essai : § 4.18. Test : `UpdateLogicTest` (codes, ordre, une version
  d'essai ne se voit proposer que la release suivante).
- **D. Plus de « Quitter Ciné Doury ? »** : la confirmation était pénible. « Retour » sur l'accueil
  remonte en haut de page, puis va au rail, puis ferme l'appli aussitôt (§ 4.9). Ailleurs, rien ne
  change : « Retour » remonte d'un niveau, et un écran de premier niveau ramène à l'accueil. La
  capture 44 est retirée. Test : `J2NavigationTest`, `J2FlowTest` (bibliothèque → accueil → rail →
  appli fermée), `J8FocusTest`.

### 4.23 Essai réel 5 : la v0.9.1 au canapé (29/10/2026)

Quatre retours du mainteneur sur la v0.9.1 (Shield en 4K et Chromecast, taille « Très compacte »)
et une réparation du banc de mesures, faits dans l'ordre E, A, B, C, D.

- **E. Banc de mesures rouge depuis la v0.9.0** (« No entry Films in the rail ») : le banc cherche
  une bibliothèque en lisant le nom de l'entrée focalisée du rail par l'accessibilité. Depuis le J8,
  l'icône d'une entrée n'a plus de description (pour que TalkBack ne lise pas le nom deux fois) et le
  nom n'est plus qu'un texte, transparent rail fermé (`graphicsLayer { alpha }`). Compose retire un
  nœud transparent de l'arbre d'accessibilité et ne l'y remet pas quand l'alpha, lu au dessin, remonte :
  le texte n'était exposé ni au banc ni à TalkBack (un défaut d'accessibilité, corrigé dans l'appli).
  Le nom est désormais la description de l'entrée elle-même (`NavigationRail`), texte et avatar
  n'exposant rien de plus. Le banc s'arrête aussi au bas du rail (« Réglages ») et dit le dernier nom
  lu. Test : `R5RailTest` (chaque entrée dit son nom, rail fermé et ouvert, une seule fois).

- **A. Le compte à rebours coupait la fin des épisodes.** Sans segment Outro,
  `PlaybackRules.creditsStart` suppose un générique de 30 s : la carte « Épisode suivant » apparaissait
  30 s avant la fin, et son compte à rebours (10 s) lançait l'épisode suivant **environ 20 s avant la
  fin réelle**, coupant la fin d'un épisode sans générique. **Règle** (`PlaybackRules.countdownLeft`,
  `countdownStartsNext`) :
  - **avec** un segment Outro : inchangé ; la carte dès le début du générique, le compte à rebours
    court pendant la lecture, puis l'épisode suivant démarre ;
  - **sans** segment Outro : la carte apparaît toujours 30 s avant la fin, d'abord sans compte à
    rebours ; celui-ci commence quand il reste exactement sa durée et suit ce qui reste de l'épisode
    (une pause l'arrête, un retour en arrière le remonte) ; à zéro, il ne lance rien : c'est la fin de
    l'épisode (`PlayerEvent.Ended`) qui lance le suivant. L'épisode suivant ne démarre donc **jamais
    avant la fin réelle**.
  - OK sur la carte lance toujours l'épisode suivant tout de suite ; « Retour » l'écarte.
  Tests : `PlaybackRulesTest` (avec et sans segment), `R5PlayerTest` (modèle de vue : rien 20 s avant
  la fin sans générique, la fin lance le suivant ; avec générique, le compte à rebours le lance ; OK).

- **B. Épisode précédent / suivant depuis les contrôles de lecture.** Pour un épisode, la rangée de
  commandes du lecteur a deux boutons ronds de part et d'autre de lecture/pause, « Épisode précédent »
  et « Épisode suivant » (icônes ⏮ ⏭ de Material dans `CineIcons`, `SkipPrevious` et `SkipNext` ;
  le libellé est la description lue par TalkBack). L'ordre est celui de la série, d'une saison à
  l'autre (les spéciaux seulement autour d'un spécial, comme la carte « Épisode suivant ») ; un bouton
  est **absent**, pas grisé, au premier ou au dernier épisode, et pour un film. Les touches média
  « précédent » et « suivant » de la télécommande font la même chose quand l'épisode a un voisin, et
  sautent d'un chapitre sinon (le comportement d'avant pour un film). L'épisode quitté est enregistré
  comme sur « Retour » (dernier rapport au serveur, cache de l'accueil et des fiches), et le compteur
  de « Vous êtes toujours là ? » repart de 1 (c'est une touche). Le lecteur connaît les deux voisins
  dès le chargement (`PlayerState.previous`, `next`). Tests : `R5PlayerTest` (voisins d'une saison à
  l'autre, aucun aux bouts, position de l'épisode quitté rapportée), `R5PlayerControlsTest` (boutons
  présents ou absents, libellés TalkBack, touches média) ; capture 221 (`avant-b-*`, `apres-b-*`).

- **C. Appui long sur une jaquette : menu d'options rapides** (`UI.md` § 2). Une seule logique,
  branchée partout :
  - **options** (`QuickOptions.of`, `:core:model`) : lecture (« Reprendre » si entamé, sinon « Lire »,
    puis « Lire depuis le début » si entamé) pour un film, un épisode ou une vidéo ; une série ou une
    saison ne se lance pas d'ici (sa fiche choisit l'épisode) ; « Ouvrir la fiche » (pas pour une
    saison, onglet de la fiche de sa série) ; « Aller à la série » et « Aller à la saison » pour un
    épisode (la fiche de la série, ouverte sur la saison : `Route.Details(item, season)`) ; « vu » pour
    un film, un épisode, une série ou une saison ; « Retirer de Continuer » pour une carte entamée de
    « Reprendre » ; favoris pour tout sauf un dossier ; « Sélectionner » en dernier dans une
    bibliothèque pour les comptes qui gèrent (l'appui long y commençait une sélection, J6). Jamais
    d'action de gestion ;
  - **« Retirer de Continuer »** : Jellyfin 12.3 n'a pas d'appel « masquer de la reprise »
    (`docs/api/`) ; `POST /UserItems/{itemId}/UserData` avec `PlaybackPositionTicks = 0` retire
    l'élément de `/UserItems/Resume` sans le marquer vu (`LibrarySource.clearProgress`, test
    d'intégration) ;
  - **changements** (`QuickActions`, `:core:data`) : « vu », « Retirer de Continuer » et favoris se
    voient aussitôt (la réponse attendue, puis celle du serveur ; un échec remet l'état d'avant et se
    journalise) : `UserDataUpdates` pour les modèles de vue (bibliothèque et recherche changent la
    carte là où elle est, `ItemPager.update` ; une fiche relit les épisodes d'une saison ou d'une série
    marquée), le cache de l'accueil pour ses rangées (favori, position ; la carte quitte
    « Reprendre » une fois retirée ou vue). Rien n'est relu en entier ;
  - **interface** : `QuickMenu` (`:core:designsystem`) est un panneau latéral à droite (il tient mieux
    qu'un panneau près de la carte en « Très compacte », où une carte fait 160 dp), focus piégé comme
    `ConfirmDialog`, « Retour » le ferme ; l'appui maintenu qui l'a ouvert (répétitions et
    relâchement) n'y choisit rien, seul un nouvel appui compte. `QuickMenuHost` et
    `Modifier.quickMenu` (`:core:ui`) l'ouvrent et rendent le focus à la carte d'où il vient (un
    `FocusRequester` posé sur le conteneur de la carte, qui le passe à la partie focalisable) ;
    `LocalQuickMenu` sert les cartes au fond des listes (recherche, fiches). L'appui long
    (`Modifier.onLongPress`) oubliait aussi qu'il était maintenu quand le relâchement partait ailleurs
    (dans le menu) : l'appui suivant était avalé ; un nouvel appui le remet à zéro.
  Tests : `QuickOptionsTest` (options selon le type et l'état), `R5QuickMenuTest` (accueil : appui bref
  ou long, contenu, focus piégé puis rendu à la carte, une option qui mène), `R5QuickMenuFlowTest`
  (appli réelle : « Marquer comme vu » d'une carte de bibliothèque arrive au serveur, la carte change
  sans relire la bibliothèque, le menu propose ensuite « Marquer comme non vu »), `J6NavigationTest`
  (« Sélectionner » commence la sélection) ; captures 222 et 223 (`avant-c-*`, `apres-c-*`).

- **D. Nouvel accueil : la mosaïque « Ce soir »** (`UI.md` § 8, « Accueil » ; choisie par le
  mainteneur parmi trois maquettes). **Écart à `UI.md` (bandeau à la une)** : dès qu'il y avait des
  lectures en cours, le bandeau répétait la première et la rangée « À suivre » n'avait qu'une carte ;
  la mosaïque montre cinq titres dans la même hauteur. Décisions :
  - **sources** (`HomeRows.fetchTonight`, une lecture de plus au rafraîchissement de l'accueil, quand
    la mosaïque est active) : lectures en cours, épisodes à suivre, derniers ajouts et titres anciens
    (les plus vieux ajouts, films et séries) des bibliothèques admises sur l'accueil, au plus 12 par
    source, quelles que soient les rangées choisies. Elles vont au cache de l'accueil sous des clés à
    part (`tonight:*`) : la mosaïque s'affiche aussitôt au lancement suivant, comme les rangées ;
  - **choix** (`Tonight.pick`, `:core:data`) : grande tuile = la lecture en cours la plus récente,
    sinon le premier « À suivre » (le serveur les donne par série regardée en dernier), sinon le
    dernier ajout ; puis les autres lectures, « À suivre », nouveaux épisodes (un « À suivre » ajouté
    depuis moins de 14 jours), ajouts, un titre à redécouvrir (jamais vu, ou vu il y a plus d'un an ;
    un autre chaque jour, le même toute la journée : la mosaïque ne change pas à chaque retour), sans
    doublon de titre ni de série. Les rangées « Reprendre » et « À suivre » perdent ce que la mosaïque
    montre (`HomeViewModel`) ;
  - **mise en page** : la mosaïque est le premier élément de la liste de l'accueil, de la hauteur que
    laissent une rangée entière et la moitié de la suivante (`homeRowsArea`, la règle de l'essai réel
    4), au moins ce qu'il faut à deux petites tuiles ; sa largeur est celle du contenu, les images
    sont recadrées à la tuile (Coil demande la taille de la tuile). La liste défile **par index** :
    la rangée focalisée en haut, la mosaïque hors de l'écran ; la marge du haut coupe la liste, pour
    qu'il ne reste aucune bande de mosaïque au-dessus de la rangée. → depuis la grande tuile va à la
    petite tuile du haut (`focusProperties`), les deux étant à égale distance ; ↑ depuis la première
    rangée ramène la mosaïque et sa grande tuile (la recherche 2D trouvait le rail d'abord, vu sur
    l'émulateur du banc) ;
  - **fluidité** : chaque tuile lit son propre focus (`MosaicTile`, `rememberCineFocus`) ; le fond
    d'ambiance lit le titre focalisé au dessin ; rien d'autre ne recompose au passage d'une tuile à
    l'autre ;
  - **réglage** : « Mosaïque « Ce soir » » (Accueil et navigation, profil, `tonight_mosaic`, activée par
    défaut) ; désactivée, l'accueil reprend le bandeau de la 0.9.1 et « Bandeau à la une » s'applique ;
  - **parcours automatiques** : le banc (`Remote.waitHome`, `backToStart`), les profils,
    `SignInJourneyTest` et `ci/couch-gestures.py` partent de la grande tuile (`tonight-0`), ou de
    « Lire » quand la mosaïque est coupée ; le geste 2 ouvre la fiche par le menu de la tuile.
  Tests : `TonightTest` (ordre, grande tuile, sans doublon, toujours pleine, à redécouvrir),
  `R5Home1080pTest` et `R5Home4kTest` (cinq tuiles, grande tuile focalisée, une rangée et demie dans
  les trois tailles, mosaïque défilée par index et revenue, titre immobile, OK qui lit ou ouvre),
  `R5TonightFlowTest` (appli réelle : mosaïque pleine par défaut, « Reprendre » parti des rangées,
  « Retour » vers le rail puis hors de l'appli), `AppPreferencesTest` ; les parcours du bandeau
  (`J2FlowTest`, `J8FocusTest`) tournent mosaïque coupée. Capture 224 (`avant-d-*`, `apres-d-*`).

## 5. Tests et CI

- GitHub Actions sur `ubuntu-latest` :
  - construction ;
  - analyse statique (ktlint ou Spotless, Detekt, Android Lint ; Animal Sniffer contre l'API 30
    d'Android sur les modules Kotlin purs, que Lint ne vérifie pas, § 4.15) ;
  - tests unitaires ;
  - **vérification des captures Roborazzi** ;
  - **tests d'intégration contre un vrai Jellyfin** ;
  - **deux émulateurs Android TV**, API 30 (`minSdk`, la Shield) et API 34 (le Chromecast) :
    installation et démarrage, lecture directe et transcodée, connexion à la télécommande (§ 4.15) ;
    puis l'APK optimisé, installé par-dessus, connexion et lecture (§ 4.16) ;
  - **profils et mesures** (émulateur API 34) : Baseline et Startup Profiles (`[record-profiles]`),
    Macrobenchmark de l'appli avant et après (§ 4.16, § 4.20) ; artefact `cine-doury-release-apk` ;
  - **contenu de l'APK optimisé** : bibliothèques 32 bits et taille (`ci/check-apk.sh`, § 4.20) ;
  - **20 gestes au canapé** sur l'émulateur API 34 (`ci/couch-gestures.sh`, § 4.20, indicatif).
  - **CI rapide sur une PR** (§ 4.20) : l'émulateur API 34, les gestes, les profils et les mesures
    ne tournent que sur `main`, sur `workflow_dispatch` ou avec `[full-ci]` dans le commit de tête
    (`[record-profiles]` pour les profils).
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
que le serveur de l'utilisateur, à l'exception des fournisseurs que Jellyfin interroge lui-même et
des releases de ce dépôt sur GitHub, lues pour proposer les mises à jour (§ 4.18).
