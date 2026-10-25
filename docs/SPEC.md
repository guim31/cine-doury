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
| J6 — Gestion (P0, plus éléments à problème et actions en série) | terminé, en revue (§ 4.14 ; captures dans `docs/screenshots/j6/`, numéros 130 à 147) | [#8](https://github.com/guim31/cine-doury/pull/8) |

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

`:core:ui` n'était pas prévu au § 3 : les messages d'erreur servent à tous les écrans, mais
`:core:designsystem` ne doit pas connaître `:core:source`. Le design system reste sans dépendance
au domaine.

Les plugins de convention vivent dans `build-logic/` (build inclus) ; les versions dans
`gradle/libs.versions.toml`. Paquets Kotlin : `fr.guim.cinedoury.<module>`.

**Écart au § 3** : `:core:data`, `:core:playback`, `:feature:*` et `:benchmark` ne sont pas créés
vides au J0 ; chacun naît avec le jalon qui le remplit (J1 pour `:core:data` et `:feature:auth`, J4
pour `:core:playback`, J8 pour `:benchmark`). Raison : un module vide ne teste rien et ralentit la
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
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`) | 3.6.3 |
| Stockage | Room (avec KSP, qui fonctionne avec le Kotlin intégré d'AGP 9), DataStore Preferences | 2.8.5 (KSP 2.3.12), 1.3.0 |
| Injection | Koin (`koin-android`, `koin-androidx-compose`), plutôt que Hilt : pas de traitement d'annotations | 4.2.2 |
| JSON (découverte UDP) | kotlinx.serialization JSON (déjà apportée par le SDK Jellyfin) | 1.12.0 |
| Lecture (J4) | Media3 : `media3-exoplayer`, `-exoplayer-hls`, `-ui` (`PlayerView`, sous-titres) | 1.11.1 |
| Tests sur l'émulateur (J4) | `androidx.test:runner`, `androidx.test.ext:junit` | 1.7.0, 1.3.0 |
| À venir (retenus, non encore ajoutés) | Macrobenchmark (J8) | 1.5.0 |

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
  LGPL. La mention de licence sera affichée dans « À propos » (J7). *À confirmer par le mainteneur.*
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
- **Versions** : semver, étiquettes `vX.Y.Z`. L'APK de release est **signé hors de la CI** par le mainteneur ; la clé n'entre jamais dans le dépôt. La CI produit un APK de débogage.

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
- « Retour » sur l'accueil : en haut de page (focus sur « Lire »), puis le rail, puis « Quitter
  Ciné Doury ? » (focus sur « Annuler »).

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
un seul fond d'ambiance (BlurHash 32 × 18). Mesures sur la Shield au J8 (Macrobenchmark).

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
  films). À mesurer sur la Shield au J8.
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
l'épisode suivant démarre si « Épisode suivant automatique » est actif. « Retour » écarte la carte.

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
(recherche), suivant et précédent (chapitres), stop ; Info : écran d'informations. L'interface
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
serveur, format, apparence des sous-titres.

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
- Le mode des sous-titres (désactivés, forcés, toujours, intelligent) suit la pré-sélection du J3
  et les préférences du profil sur le serveur ; son réglage dans l'appli arrive avec les pages de
  réglages du J7, comme la langue préférée.
- La qualité choisie pendant la lecture vaut pour la séance ; le réglage par défaut est au J7.
- Le passage d'un Dolby Vision profil 5 sur un appareil qui ne le décode pas passe par le
  transcodage du serveur (tone mapping) ; seul le profil 8 se replie sur HEVC dans l'appareil.
- Rien n'a encore été mesuré sur la Shield (60 images/s, cadence, passthrough réel) : c'est le J8.
- Sous-titres incrustés par le serveur : ni taille ni position ne changent (voir plus haut).

**Retouches du J3** :
- **Pastilles** : « par défaut », « forcés », « SDH », « externe » suivent le libellé d'une piste
  en pastilles (`TrackPill`) ; le libellé passe à la ligne, jamais tronqué (`ListRow(wrapTitle)`).
- **Groupes insécables** : espaces insécables dans « se termine à 22 h 07 », « 1 h 49 », « 42 min »,
  « S01 · E02 », « 10:47 PM » (`UI.md` § 5).
- **Logo ou affiche** : un film, une série ou une collection qui a un logo n'affiche plus son
  affiche ; l'affiche de la carte vole vers le logo en s'y fondant (`sharedBounds`, des deux côtés).

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
- Rien n'est mesuré sur la Shield (temps de la recherche locale sur 11 500 titres, J8).
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
