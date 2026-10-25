# CLAUDE.md — Ciné Doury

Lecteur vidéo maison pour Android TV, au-dessus d'un serveur Jellyfin non modifié. Il remplace Plex.
Mainteneur : Guilhem (`guim31`).

`docs/SPEC.md` fait foi sur l'architecture, les contrats et les jalons ; `docs/FONCTIONNALITES.md`
sur ce que fait l'appli ; `docs/UI.md` sur l'interface. Ce fichier dit comment travailler ici et
rassemble ce qui ne se lit pas dans le code : pièges déjà payés, choix non évidents. **Le compléter
dès qu'un piège est découvert.**

## Avant de commencer

- Lire `docs/SPEC.md` en entier, puis les deux annexes. Le jalon demandé est décrit au § 6.
- Les versions des bibliothèques sont **postérieures à tes connaissances** : Android Gradle Plugin, Kotlin, Compose, Compose for TV, Media3, Coil, Room, Jellyfin 12.3. Ne pas écrire leur API de mémoire : lire les sources, les types ou la documentation en ligne, et pour Jellyfin l'OpenAPI versionnée dans `docs/api/`. Une API qui a changé se note dans « Pièges ».

## Règles

- Un jalon = une branche `feat/j<N>-<sujet>` = une PR vers `main`. Ne jamais pousser sur `main`.
- **Ne jamais fusionner sa propre PR** et **ne jamais lancer d'autre session** : l'orchestrateur fusionne une PR à la fois et enchaîne les jalons.
- Avant chaque push : `./gradlew check` vert en local. Après chaque push, lire la CI de la PR et corriger jusqu'au vert. En session cloud, les outils du serveur MCP GitHub ne passent pas par le proxy :
  - `mcp__github__actions_list` pour les exécutions ;
  - `mcp__github__get_job_logs` pour les journaux ;
  - `mcp__github__pull_request_read` pour l'état de la PR.
- Code, identifiants, commentaires et commits en anglais ; commits conventionnels (`feat:`, `fix:`, `chore:`…).
- Interface en français (par défaut) et en anglais, **au vouvoiement**, sans aucune chaîne en dur. Vocabulaire et typographie : `docs/UI.md` § 5 et § 10.
- **Dépôt public** : aucun secret, aucune adresse IP, aucun nom d'hôte, aucun détail d'infrastructure privée. Les exemples utilisent `localhost`, `192.0.2.x` et `example.com`. Aucun média autre que ceux générés par la CI ou les films ouverts de la fondation Blender.
- **Aucun type du client Jellyfin hors de `:source:jellyfin`.** Les écrans ne connaissent que `:core:source`.
- **Aucune valeur visuelle en dur** dans les écrans (couleur, taille, durée) : tout vient de `:core:designsystem`.
- Nouvelle dépendance : maintenue, licence compatible MIT (pas de GPL ni d'AGPL dans l'APK), poids justifié.
- Une décision qui s'écarte du cahier des charges s'écrit dans `docs/SPEC.md`, avec sa raison.
- Fin de jalon :
  - mettre à jour `docs/SPEC.md` § 0 et § 4 ;
  - mettre à jour ce fichier (commandes, pièges) ;
  - joindre à la PR les captures de `docs/screenshots/j<N>/`.

## Environnement des sessions cloud

- Le proxy refuse `api.github.com` et `codeload.github.com` : pas de `gh` (passer par les outils `mcp__github__*`), et aucune dépendance tirée d'une archive GitHub. Les fichiers de release GitHub (`github.com/<org>/<dépôt>/releases/download/…`) passent.
- La PR brouillon ne s'ouvre pas toute seule : la créer avec `mcp__github__create_pull_request` (`draft: true`) après le premier push.
- Mesuré au J0 (25/10/2026) :
  - **`dl.google.com` et `maven.google.com`** : accessibles. Le SDK Android s'installe : cmdline-tools depuis `dl.google.com/android/repository/`, puis `sdkmanager` (les paquets s'écrivent désormais `platforms/android-37.0`, `build-tools/36.0.0`). Voir « Commandes ».
  - **Portail des plugins Gradle** (`plugins.gradle.org`) : accessible.
  - **Distributions Gradle : bloquées.** `services.gradle.org` redirige vers `downloads.gradle.org`, que le proxy refuse (403). Le wrapper ne peut donc pas télécharger Gradle 9.8.2, et le seul Gradle de l'image est le 8.14.3, trop ancien pour AGP 9.4 (≥ 9.6). **Conséquence : `./gradlew` ne tourne pas en session cloud ; la CI construit et teste.** Ne pas contourner (miroir, autre hôte).
  - **Maven Central** (`repo.maven.apache.org`, `repo1.maven.org`) : accessible mais **limité en débit**. Après quelques centaines de requêtes, il répond 404 (avec `Retry-After`) sur des artefacts qui existent ; ça se débloque par à-coups. `curl` sur un fichier précis passe en général.
  - **JDK** : OpenJDK 21 préinstallé.
  - **Docker** : client installé, démon arrêté ; `sudo dockerd` le démarre et l'image Jellyfin se tire depuis Docker Hub. **Le Jellyfin de test tourne donc en local** (`ci/jellyfin/run-local.sh`, avec `sudo`).
  - **ffmpeg 6.1** et **jq** : présents. ffmpeg 6.1 n'a pas d'encodeur PGS (d'où `ci/jellyfin/make_pgs.py`).
  - **KVM : absent** (`/dev/kvm` n'existe pas) : pas d'émulateur en session ; la CI en lance un (API 34 TV).
  - Pour vérifier le code Kotlin pur sans Gradle 9, on peut monter un projet jetable avec le Gradle 8.14.3 local (hors du dépôt, dans le *scratchpad*), qui compile les sources de `:core:model`, `:core:source` et `:source:jellyfin` et lance leurs tests, y compris d'intégration contre le Jellyfin Docker. Astuce : dans son `settings.gradle.kts`, `project(":x").projectDir = file("<dépôt>/x")` et `buildFileName = "none.gradle.kts"` pour ignorer les scripts du dépôt, et décrire les dépendances dans le `build.gradle.kts` jetable. Ça reste soumis à la limite de Maven Central (429 : relancer en boucle, ça passe en quelques minutes). Les modules Android ne se compilent qu'en CI.
- **Compiler les modules Android sans AGP** (J4) : un projet jetable Kotlin JVM (Gradle 8.14.3, `kotlin("jvm")` et `kotlin("plugin.compose")` 2.4.21) compile tous les modules, appli et tests compris, contre `android.jar` (`sdkmanager "platforms;android-37.0"`). Les bibliothèques se résolvent dans leur variante Android par une configuration aux attributs `KotlinPlatformType.androidJvm` et `TargetJvmEnvironment.ANDROID` ; on extrait `classes.jar` et `libs/*.jar` de chaque AAR et on les met en `compileOnly`. Les `R` se génèrent depuis `res/` (un `object R { object string { const val … } }` par module, au paquet du `namespace`). Ça ne lance pas Robolectric ni Lint, mais ça évite un aller-retour de CI par erreur de compilation.
- **ktlint et Detekt sans Gradle** : les jars `ktlint-cli-1.8.0-all.jar` et `detekt-cli-1.23.8-all.jar` (Maven Central) tournent en local ; voir « Commandes ». Les lancer avant chaque push évite un aller-retour de CI.
- **Lire un échec de CI** : chaque tâche Gradle de la CI finit par une étape « Failure summary » (`ci/failure-summary.sh`) qui regroupe erreurs de compilation, tests en échec et constats d'analyse. Elle est suivie d'environ 250 lignes de nettoyage (caches de setup-gradle) : `mcp__github__get_job_logs` avec `tail_lines` ≈ 300 la montre.
- Un commit poussé par la CI (`[record-screenshots]`) fait que la branche distante avance : `git pull --no-rebase` avant de pousser la suite (pas d'`amend` ni de réécriture une fois poussé).
- Les captures de référence ne peuvent pas s'enregistrer en session (pas de Gradle). Pour les (ré)enregistrer, pousser un commit dont le message contient **`[record-screenshots]`** : le job « Roborazzi screenshots » les enregistre, les copie dans `docs/screenshots/j<N>/` et les commite sur la branche. Un push fait par la CI ne relance pas la CI : récupérer ce commit (`git pull`) et pousser la suite normalement.
- Les artefacts de la CI (APK, captures, rapports) ne se téléchargent pas depuis la session (`api.github.com`). Lire les journaux avec `mcp__github__get_job_logs`.

## Commandes

```sh
./gradlew check                          # Spotless, Detekt, Android Lint, tests unitaires (dont Robolectric)
./gradlew spotlessApply                  # formatage (ktlint, style ktlint_official)
./gradlew :app:tv:assembleDebug          # APK de débogage : app/tv/build/outputs/apk/debug/
./gradlew :app:tv:verifyRoborazziDebug   # captures comparées à app/tv/src/test/screenshots/
./gradlew :app:tv:recordRoborazziDebug   # réenregistre les captures de référence
ci/jellyfin/run-local.sh up              # Jellyfin 12.3 de test sur http://localhost:8096 (Docker)
JELLYFIN_URL=http://localhost:8096 ./gradlew :source:jellyfin:integrationTest
ci/jellyfin/run-local.sh down
```

- Identifiants du Jellyfin de test (serveur jetable, pas des secrets) : `admin` / `cinedoury-test`, et un profil enfant `enfant` sans mot de passe. Bibliothèques « Films » et « Séries », collection « Le Phare : la saga ».
- En session cloud : `sudo dockerd &` avant `run-local.sh`. SDK Android :
  ```sh
  curl -o clt.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
  # décompresser dans $ANDROID_HOME/cmdline-tools/latest, puis :
  yes | sdkmanager --licenses && sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
  ```
- ktlint seul, sans Gradle : `java -jar ktlint-cli-1.8.0-all.jar -F "**/*.kt" "**/*.kts" "!**/build/**"` (jar sur Maven Central).
- Detekt seul : `java -jar detekt-cli-1.23.8-all.jar --config config/detekt/detekt.yml --build-upon-default-config --input core,source,feature,app --excludes "**/build/**"`.
- Médias de test : `ci/jellyfin/generate-media.sh DOSSIER` (20 s, 30 Mo). Amorçage : `ci/jellyfin/bootstrap.sh URL` (rejouable : il ne refait que ce qui manque).
- Images du serveur de test (J3) : `ci/jellyfin/images.sh URL JETON` (appelé par `bootstrap.sh`) pose affiches, fonds, logos et photos générés. Sur un serveur déjà amorcé, il ne refait que ce qui manque (un élément qui a un logo est laissé).
- Clé de débogage : `app/tv/debug.keystore` est versionnée exprès (`.gitignore` l'exclut de `*.keystore`) ; ne pas la régénérer, sinon les APK ne s'installent plus par-dessus.
- Jeu de test du J2 : 45 films dans « Films » (A–Z, 11 genres, 8 décennies, 4 studios, 7 personnes, 2 collections), 3 séries, « Spectacles » exclue de l'accueil de `admin`, un historique (vus, en cours, favoris) posé par `bootstrap.sh`. Les tests d'intégration en dépendent : changer `generate-media.sh` oblige à revoir `JellyfinBrowseIntegrationTest`.
- Banc JVM de l'index (J3) : le projet jetable peut compiler `core/data/.../db`, `index/LibraryIndex.kt`, `index/IndexedQuery.kt` et `library/LibraryIcons.kt` (`java.srcDir` + `include`), puis lancer l'index réel contre le Jellyfin Docker avec `Room.inMemoryDatabaseBuilder { CineDatabase_Impl() }.setDriver(BundledSQLiteDriver())` (`androidx.sqlite:sqlite-bundled`) : c'est ainsi que les temps du § 4.10 ont été mesurés.
- Schéma Room : exporté dans `core/data/schemas/` par KSP à chaque compilation ; la CI échoue s'il diffère de ce qui est versionné. Sans Gradle 9 en session, le projet jetable (voir plus haut) peut compiler le paquet `core/data/.../db` sur la JVM (Room KMP, `room-runtime` + `room-compiler` en KSP, `sqlite-bundled`) et produire le JSON.
- Lecture réelle sur l'émulateur (J4) : `ci/emulator-playback.sh http://10.0.2.2:8096` (job « Emulator » de la CI, Jellyfin en service) lance `PlaybackOnDeviceTest` (`app/tv/src/androidTest`) : un film H.264 lu tel quel, un HEVC HDR10 transcodé.
- Segments (J4) : `bootstrap.sh` installe Chapter Segments Provider depuis le dépôt de plugins du serveur et lance la tâche des segments ; sans Internet (session cloud), il le dit et continue sans segments. `JELLYFIN_SEGMENTS=true` (posé par `bootstrap.sh` en CI) active la vérification des segments dans `JellyfinPlaybackIntegrationTest`.
- Recherche (J5) : `JELLYFIN_URL=http://localhost:8096 ./gradlew :source:jellyfin:integrationTest --tests '*Search*'` vérifie titres accentués, fautes, personnes, collections, épisodes. Le client Seerr se teste contre une fausse API (MockWebServer, `SeerrClientTest`) : aucun Seerr réel, ni en CI ni en session.
- Utilisateur masqué du serveur de test (essai réel 1) : `parent` / `cinedoury-parent`, absent de `/Users/Public`, avec image et politique complète. `JellyfinHiddenUserIntegrationTest` en dépend.
- Journal de l'appli sur un appareil : `adb logcat -s CineDoury` (chaque échec attrapé, avec sa pile ; `FailureLog`).
- Connexion réelle à la télécommande sur l'émulateur (essai réel 1) : `ci/emulator-signin.sh http://10.0.2.2:8096`, après `ci/emulator-playback.sh` dans le job « Emulator », lance `KeystoreTokenCipherTest` (vrai Keystore) et `SignInJourneyTest` (du premier lancement à l'accueil, puis « Lire »). Pas d'émulateur en session cloud (pas de KVM).
- OpenAPI : `curl http://localhost:8096/api-docs/openapi.json | python3 -m json.tool --indent 2 > docs/api/jellyfin-openapi-12.3.json`. La CI vérifie qu'il correspond au serveur (descriptions exclues).

## Performances (J2)

Ce qui compte pour garder 60 images/s au défilement (à mesurer sur la Shield au J8) :
- des **clés stables** et un `contentType` dans chaque `LazyRow`/`LazyColumn`/`LazyVerticalGrid` ;
- des états **immuables** (`@Immutable`, `data class`) recréés seulement quand ils changent : le
  cache Room passe par `distinctUntilChanged()`, et l'avatar du rail est calculé une fois ;
- aucune lambda neuve par composition vers une carte : `rememberArt(image, sources)` mémorise le
  contenu d'image d'une carte ;
- l'animation du focus est lue au dessin (`graphicsLayer`, `drawWithCache`), jamais en composition ;
- un `Crossfade` indexé par une clé stable, pas par une valeur qui porte des lambdas ;
- les pages de la grille arrivent dans un `PagedItems` immuable (une `Map` de pages, pas une liste
  recopiée) ; les cases non lues sont des squelettes de même taille : aucune remise en page ;
- les premières images de la rangée suivante sont préchargées (`PrefetchImages`).

## Pièges

- Jellyfin 12 n'accepte que l'en-tête `Authorization: MediaBrowser …, Token="…"`. `X-Emby-Token` et `?api_key=` reçoivent un 401.
- Sur un serveur qui a des collections, `/Items` regroupe les films dans leurs collections par défaut : `CollapseBoxSetItems=false` pour les lister ou les compter.
- Un appel authentifié crée une session Jellyfin à notre `DeviceId` : la filtrer quand on liste les lectures en cours.
- **SDK Kotlin de Jellyfin et caractères non ASCII** : le SDK recopie `ClientInfo.name` tel quel dans `User-Agent`, et OkHttp refuse tout caractère non ASCII dans un en-tête (exception à chaque appel). Le client s'annonce donc `Cine Doury`. Dans `Authorization`, le SDK encode les valeurs comme un formulaire (`Client="Cine+Doury"`, `Device="Salon+%C3%A9tage"`) : un nom d'appareil accentué y passe sans risque.
- Le SDK journalise par kotlin-logging, qui exige `org.slf4j:slf4j-api` à l'exécution sans que le SDK l'apporte (`NoClassDefFoundError: org/slf4j/LoggerFactory` au premier appel) : `:source:jellyfin` le déclare en `runtimeOnly`.
- L'OpenAPI servie par Jellyfin n'est pas stable d'un démarrage à l'autre : la description d'une propriété `$ref` change (« Gets or sets the user. » ou « Class UserDto. »). La CI la compare donc sans les descriptions (`ci/jellyfin/check-openapi.py`).
- Un `FocusRequester` posé dans un élément de `LazyColumn` n'est rattaché qu'après la première mise en page : `requestFocus()` dans un `LaunchedEffect(Unit)` ne fait rien. Attendre une image (`withFrameNanos { }`) avant. Un élément ajouté plus tard (profils arrivés du serveur) peut demander davantage : `InitialFocus` réessaie à chaque image tant que `requestFocus()` renvoie `false` (essai réel 1). Mais déplacer le focus d'un élément focalisé d'une `LazyRow` (« Autre compte ») vers une tuile insérée avant lui échoue (la tuile est composée et focalisable, la demande n'aboutit pas, cause non trouvée) : l'écran des profils ne donne aucun focus tant qu'il attend le serveur sans profil connu, et le modèle de vue publie profils et fin du chargement ensemble. Et le poser sur l'élément focalisable lui-même, pas sur son conteneur (paramètre `focusRequester` des boutons du design system).
- `rememberLazyListState` prend `initialFirstVisibleItemIndex` (pas `firstVisibleItemIndex`).
- Le SDK 1.9 a déplacé les appels : authentification dans `authenticationApi` (pas `userApi`), éléments et détail dans `libraryApi.getItems` / `libraryApi.getItem` (`/Items`, `/Items/{id}`), bibliothèques dans `userViewApi.getUserViews`. Ses exceptions : `InvalidStatusException(status)`, `TimeoutException` (hôte injoignable, connexion refusée, délai), `SecureConnectionException`, `InvalidContentException`.
- Sans fournisseur de métadonnées en ligne (`EnableInternetProviders: false`, réglage du serveur de test), Jellyfin garde le nom du dossier comme titre (« Le Phare (2001) ») : les médias de test portent donc un `.nfo` (titre, année, synopsis, genres).
- Jellyfin regroupe deux fichiers d'un même dossier nommés `Titre (Année) - 1080p.mkv` et `Titre (Année) - 720p.mkv` en **un seul élément à deux sources média**.
- `GET /Items?parentId=<bibliothèque>` renvoie les enfants directs (films, ou séries) ; le `ChildCount` d'une série est son nombre de saisons.
- **AGP 9** : Kotlin est intégré (ne pas appliquer `org.jetbrains.kotlin.android`, et `kapt` est incompatible) ; `CommonExtension` n'est plus générique et ne porte ni `defaultConfig` ni `buildFeatures` (passer par `ApplicationExtension` / `LibraryExtension`) ; `testOptions.unitTests.all {}` n'existe plus : configurer les `Test` avec `tasks.withType<Test>()`. Paquets du SDK : `platforms/android-37.0`.
- Les suites `JvmTestSuite` autres que `test` utilisent JUnit Jupiter par défaut : `useJUnit(...)` pour JUnit 4 / `kotlin-test-junit`.
- **Robolectric et le focus.** Robolectric démarre en mode tactile, où Compose refuse le focus aux éléments `clickable` : `setTvContent` (`app/tv/src/test/.../TvTest.kt`) appuie d'abord sur une touche pour passer en mode clavier, comme une télé sans écran tactile, et les tests de navigation passent. **Mais sous `@GraphicsMode(NATIVE)`, nécessaire aux captures, aucun élément n'obtient le focus**, ni par `requestFocus()`, ni après `dispatchWindowFocusChanged(true)`, ni après `Instrumentation.setInTouchMode(false)` (essayé au J0, cause non trouvée). Les captures dessinent donc l'élément focalisé avec `CineFocus.preview()` (paramètre `previewFocus` du catalogue), par le même code que le vrai focus ; la navigation réelle est testée sans rendu natif. Robolectric est aussi en anglais par défaut : les qualificateurs commencent par `fr-rFR`.
- Robolectric et Roborazzi : `@GraphicsMode(NATIVE)` et `robolectric.pixelCopyRenderMode=hardware` (réglé dans `app/tv/build.gradle.kts`) pour que les ombres apparaissent ; qualificateurs `fr-rFR-w960dp-h540dp-land-television-xhdpi-notouch` pour une capture 1920 × 1080.
- `Modifier.focusable` (et donc `clickable`) émet ses `FocusInteraction` dans une coroutine, après le changement de focus : un `collectIsFocusedAsState()` a une image de retard, et une capture prise aussitôt ne montre pas le focus. Le design system lit l'état synchrone (`onFocusChanged`, classe `CineFocus`).
- Dans un test Compose, un `testTag` posé sur un composant du design system atterrit sur son conteneur, pas sur le nœud focalisable : chercher `hasClickAction() and hasAnyAncestor(hasTestTag(…))`. Le nom d'un `ProfileTile` est **sous** l'avatar cliquable, hors de lui : cliquer la tuile par son étiquette, pas par son texte.
- **Jellyfin 12 cache les nouveaux utilisateurs** (`Policy.IsHidden = true`) : `/Users/Public` est vide tant qu'on ne les rend pas publics (`ci/jellyfin/bootstrap.sh` le fait). `/Users/Public` ne répond qu'aux requêtes du réseau local.
- Jellyfin 12.3 renvoie `HasPassword: true` pour **tous** les utilisateurs, même sans mot de passe : ne pas s'en servir pour sauter l'écran d'identifiants.
- `POST /UserImage` attend l'image **encodée en Base64** dans le corps (`Content-Type: image/png`).
- Au démarrage, Jellyfin répond à `/System/Info/Public` avant d'être prêt (`Version` absente, puis 503 sur l'assistant) : attendre une version (fait dans `bootstrap.sh`).
- Quick Connect : un administrateur peut valider un code pour un autre utilisateur (`POST /QuickConnect/Authorize?code=…&userId=…`), ce qui simule le téléphone dans les tests. Une demande inconnue ou expirée (10 min) répond 404 « Unknown secret » ; Quick Connect désactivé répond 401 à `Initiate`.
- **HTTP en clair** : Android refuse `http://` par défaut ; un serveur de maison est en `http://…:8096`, d'où `android:usesCleartextTraffic="true"`.
- **SDK Jellyfin sur Android** : Gradle donne à l'appli la variante **Android** du SDK, dont `createJellyfin { }` exige un `Context` (sinon `IllegalArgumentException` au démarrage, invisible dans les tests JVM et Robolectric qui ne créent pas le connecteur). `JellyfinConnector` construit donc ses clients avec `OkHttpFactory` (`jellyfin-api-okhttp`), identique partout. Le test de l'émulateur l'a révélé : regarder son journal (`logcat -b crash`).
- **KSP 2.3.12** marche avec le Kotlin intégré d'AGP 9.4 (Room 2.8.5) sans réglage particulier.
- **Robolectric n'a pas d'Android Keystore** : les tests remplacent `TokenCipher` (module Koin de test, ou faux chiffrement).
- **DataStore** : une seule instance par fichier et par processus (sinon `IllegalStateException`) ; dans un test qui « redémarre » l'appli, partager l'instance.
- Les tailles se pensent en **dp sur un écran de 960 × 540 dp** (1080p en xhdpi) : un formulaire fait 400 à 560 dp de large, pas 1 000.
- `Crossfade` veut une `FiniteAnimationSpec<Float>` : `CineMotion.fade<Float>(…)`. `Paint.asFrameworkPaint()` est déprécié : dessiner un `android.graphics.Paint` sur `nativeCanvas`.
- `Modifier.focusRequester` posé sur une carte du design system atterrit sur son conteneur : passer le paramètre `focusRequester` des cartes et boutons.
- **Fournisseurs en ligne de Jellyfin 12** : `EnableInternetProviders: false` ne suffit pas. Sans `TypeOptions`, le serveur interroge quand même TMDb et OMDb (en CI, où il a Internet : étiquettes « kite », « christmas »… sur les films dont le titre existe). `bootstrap.sh` pose des `TypeOptions` aux listes de fournisseurs vides.
- **Durée d'un média** : Jellyfin ignore le `<runtime>` d'un `.nfo` (et `lockdata`, `LockedFields`) et garde la durée lue par ffprobe. Les films de test sont donc de vrais fichiers longs : une couleur fixe à une image toutes les 10 s pèse quelques Ko même en 4K (`generate-media.sh`, `clip`).
- `/UserItems/Resume` renvoie aussi les saisons et séries entamées : `mediaTypes=Video` pour ne garder que films et épisodes.
- `/UserViews` cache les bibliothèques de `MyMediaExcludes` et suit l'ordre de `OrderedViews` ; `includeHidden=true` les rend toutes, dans le même ordre. Il ajoute une vue « Collections » (`boxsets`) dès qu'une collection existe.
- `SortName` de Jellyfin : minuscules, sans accents ni ponctuation, nombres sur 10 chiffres (« 0000000002 soleils ») ; `nameLessThan` compare sur lui sans tenir compte de la casse. Le nombre de titres avant une lettre (`count` avec `titleBelow`) est l'index du saut A–Z ; les titres à chiffre précèdent « A ».
- `/Items` : `genres`, `tags`, `officialRatings`… acceptent plusieurs valeurs par clé répétée (ce que fait le SDK), pas par virgule ; les valeurs d'un même filtre s'additionnent (OU), les filtres se combinent (ET). `minWidth`/`maxWidth` filtrent la résolution, `audioLanguages`/`subtitleLanguages` les langues (codes ISO 639-2). Rien pour le HDR ni le format audio.
- Regrouper les films par collection : `recursive=true&includeItemTypes=Movie&collapseBoxSetItems=true` (sans `recursive`, `collapseBoxSetItems` est sans effet sous une bibliothèque).
- Le SDK sérialise les `DateTime` (`LocalDateTime`) dans le fuseau de l'appareil : convertir un `Instant` par `atZone(ZoneId.systemDefault())`.
- `BringIntoViewSpec` (pivot du défilement) est expérimental sur Android et `scrollAnimationSpec` y est déprécié : ne surcharger que `calculateScrollDistance` (`FocusPivot`). `Modifier.focusRestorer(fallback)` prend désormais un `FocusRequester`, plus une lambda.
- Les clés de `Crossfade` : sa cible est comparée par égalité ; une valeur qui porte des lambdas (recréées à chaque composition) relance le fondu à chaque image. Fondre sur une clé stable (identifiant) et retrouver la valeur à côté (`FeaturedBanner`).
- **Jellyfin et les adresses longues** : au-delà d'environ 8 Ko de ligne de requête, le serveur répond 414. `ids=` tient donc au plus une centaine d'identifiants (`MAX_IDS`) ; l'index local lit ses pages par paquets.
- **Collections d'un film** : `GET /Items/{id}/Collections` (SDK : `libraryApi.getItemCollections`) les donne en un appel ; `collapseBoxSetItems` avec `ids=` ne regroupe pas, et les collections ne sont pas des ancêtres (`/Ancestors`).
- **`minDateLastSaved`** filtre sur la date d'enregistrement en base, que le scan initial met à l'heure du scan : sur un serveur tout juste amorcé, une mise à jour « par différence » relit tout ce qui a moins d'une heure (marge de l'index).
- Jellyfin reconnaît une bande-annonce locale au suffixe `-trailer` du fichier, à côté du film ; une bande-annonce distante vient du `<trailer>` d'un `.nfo` (`plugin://plugin.video.youtube/?…videoid=ID` devient `https://www.youtube.com/watch?v=ID`).
- **Atmos et DTS** : Jellyfin 12.3 les dit par `AudioSpatialFormat` (`DolbyAtmos`, `DTSX`) et par le `Profile` du codec (« Dolby TrueHD + Dolby Atmos », « DTS-HD MA ») ; rien d'autre dans `/Items` pour filtrer dessus (d'où l'index local).
- **Captures avec images** : Coil n'affiche une image dans Robolectric qu'en mode inspection : `LocalInspectionMode provides true` et `LocalAsyncImagePreviewHandler` (API `AsyncImagePreviewHandler { _, request -> AsyncImagePainter.State.Success(…) }`, la forme à lambda unique est ambiguë avec sa variante dépréciée). `SampleArt` (tests de l'appli) dessine ainsi les images générées.
- **Test de migration** : créer une version depuis son JSON demande aussi les `indices` de chaque table (le schéma 2 en a) ; sinon Room refuse la migration (« Migration didn't properly handle »).
- **Éléments partagés** : `SharedTransitionLayout` et `Modifier.sharedElement` sont stables dans Compose 1.12 (`sharedElement(rememberSharedContentState(clé), animatedVisibilityScope, boundsTransform)`) ; une clé ne doit apparaître qu'une fois par écran (une affiche dans deux rangées : la première seulement).
- **Pluriels** : `pluralStringResource` ; le français a besoin de `one`, `many` et `other`.
- **Android Lint et Compose** : `Locale.getDefault()` dans une fonction `@Composable` est une erreur (`NonObservableLocale`) : lire `LocalConfiguration.current.locales[0]`. Construire un `FocusRequester()` en composition hors `remember` aussi (`RememberInComposition`), même dans un `getOrPut` : passer par une fonction non composable.
- **PlaybackInfo et profils H.264** : x264 (`testsrc`) écrit « constrained baseline », que Jellyfin compare tel quel (sans casse) : le profil de l'appareil le liste, sinon un fichier lisible est transcodé.
- **HLS transcodé** : la playlist de Jellyfin couvre tout le film depuis 0 ; reprendre à une position, c'est se placer dans le flux (le `StartTimeTicks` de l'URL ne suffit pas). Le motif `TranscodeReasons=` de `TranscodingUrl` dit pourquoi.
- **Tests d'intégration et `LastPlayedDate`** : un test qui lit un film change sa date de dernière lecture, donc l'ordre « Vus récemment » de `JellyfinBrowseIntegrationTest` ; `restoreLastPlayed` la remet (`POST /UserItems/{id}/UserData`). Sur un serveur local déjà pollué, la remettre à la main.
- **Plugins Jellyfin** : `repo.jellyfin.org` n'est pas joignable depuis le conteneur Jellyfin en session ; Chapter Segments Provider ne s'installe qu'en CI. Sans lui, `/MediaSegments/{id}` est vide.
- **Media3 1.11** : bloquer le passthrough d'un format passe par un `AudioOutputProvider` (`ForwardingAudioOutputProvider`, `FormatSupport.UNSUPPORTED`) dans `DefaultRenderersFactory.buildAudioSink` ; un Dolby Vision profil 8 se replie seul sur HEVC, pas les profils 5 et 7 (filtrer les décodeurs par `MediaCodecSelector`). `@OptIn` : `androidx.annotation.OptIn(UnstableApi::class)`.
- **Extension FFmpeg de Media3** : la seule à jour (`org.jellyfin.media3:media3-ffmpeg-decoder`) est en GPL-3.0 : refusée (SPEC § 4.2).
- **Noms `internal` d'un module** : deux `private val CardShape` dans deux fichiers d'un même paquet entrent en conflit dès que l'un est `internal` ; préfixer par le composant (`PlayerShape`).
- `FlowRow` sans `@OptIn` : la surcharge non expérimentale ne prend que `horizontalArrangement`, `verticalArrangement` et `maxItemsInEachRow`.
- Transition affiche → logo : `sharedBounds` (pas `sharedElement`, les deux contenus diffèrent) avec `ResizeMode.scaleToBounds(ContentScale.Fit)`.
- Lint : `toUri()` (core-ktx) n'est pas une dépendance de `:core:playback` ; `Uri.parse` suffit.
- `IntArray` n'a pas de `mapNotNull` (`toList()` d'abord). Une lambda finale va au **dernier** paramètre : avec `onChange` suivi de `modifier: Modifier = Modifier`, `Screen(style) { … }` la passe à `modifier` ; nommer `onChange =`.
- **Sous-titres à notre taille** : `PlayerView` dessine ses sous-titres lui-même dans son `SubtitleView` (`exo_subtitles`, sur toute sa surface) ; pour transformer les répliques (taille des ASS, images PGS), le cacher et poser un `SubtitleView` à nous par-dessus, nourri par `Player.Listener.onCues` (`SubtitleLayer`). `setApplyEmbeddedFontSizes(true)` garde les tailles ASS, que `SubtitleCues` multiplie.
- **Recherche de Jellyfin 12.3** : `/Items?searchTerm=` et `/Persons?searchTerm=` ne trouvent pas « ines » pour « Inès Garnier » (pas de pliage des accents sur les personnes) ; `/Search/Hints` si (accents et casse ignorés, début de n'importe quel mot), mais ses résultats n'ont pas toutes les images : prendre ses identifiants puis `/Items?ids=`. Aucune des deux ne tolère une faute : c'est l'index des titres qui le fait (SPEC § 4.12). `/Persons` et `/Items?ids=` ne rendent pas les photos des personnes posées par `images.sh` (`ImageTags` vide) : carte générée.
- **Seerr** : la requête de `/api/v1/search` doit être entièrement encodée (espaces en `%20`, apostrophes) ; OkHttp `addQueryParameter` laisse passer `'` : passer par `addEncodedQueryParameter` et `URLEncoder` (`+` → `%20`). Les affiches passent par `/imageproxy/tmdb/…` du serveur Seerr, jamais par TMDb.
- **Reconnaissance vocale** : depuis Android 11, `SpeechRecognizer.isRecognitionAvailable` et `resolveActivity(RECOGNIZE_SPEECH)` rendent faux sans `<queries>` dans le manifeste (`:feature:search`). Robolectric n'a pas de reconnaisseur : la touche micro n'y apparaît pas, les captures passent par `PreviewVoice`.
- **Schéma Room sans Gradle 9** (J5) : dans le projet jetable, un module `:data` avec `com.google.devtools.ksp` 2.3.12, `room-compiler` en `ksp`, `room-runtime` 2.8.5 et `sqlite-bundled` 2.6.2, sources limitées à `db/**` et aux fichiers purs ; `room.schemaLocation` pointé sur `core/data/schemas` du dépôt écrit le nouveau JSON (et laisse les anciens identiques). Créer le dossier du projet `:data` du projet jetable, sinon KSP échoue après avoir écrit le schéma. Un test Room sur la JVM : `Room.inMemoryDatabaseBuilder { CineDatabase_Impl() }.setDriver(BundledSQLiteDriver())`.
- **Focus d'une boîte de dialogue** : une boîte posée par-dessus l'écran (même composition) ne retient pas le focus ; la recherche 2D des flèches peut choisir une carte derrière le voile, plus proche que le bouton voisin. `ConfirmDialog` est donc un `focusGroup()` à `focusProperties { onExit = { cancelFocusChange() } }`.
- Appui long à la télécommande : Compose reçoit des `KeyDown` répétés (`nativeKeyEvent.repeatCount` ≥ 1 après environ 0,5 s, puis une vingtaine par seconde) et un seul `KeyUp` ; distinguer appui bref et maintenu au `KeyUp`.
- **Jamais de réseau sur le fil principal, même par le SDK** (essai réel 1) : le client OkHttp du SDK Jellyfin lit le corps de la réponse (`response.body.bytes()`) dans la coroutine appelante, après `enqueue`. Appelé depuis un `viewModelScope` (fil principal), Android lève `NetworkOnMainThreadException` dès que le corps n'est pas arrivé avec les en-têtes ; Jellyfin (Kestrel) répond en `Transfer-Encoding: chunked`, donc souvent (toujours pour `/Users/AuthenticateByName`, quelques Ko). Ni les tests JVM ni Robolectric ne le voient. `TracingApiClient` fait passer chaque requête par `Dispatchers.IO` ; `RealServerAnswersTest` simule la règle d'Android avec des sockets qui refusent de lire sur un fil donné (comparer le `Thread`, pas son nom : le mode débogage des coroutines y ajoute `@coroutine#N`).
- **Le SDK ne range pas tout dans ses exceptions** : une `RuntimeException` hors de `IOException` et de `SerializationException` (`NetworkOnMainThreadException`, `IllegalArgumentException` d'un UUID illisible) sort telle quelle de l'appel ; `jellyfinCall` la range en `SourceException.Unexpected`, la journalise et lui joint la requête.
- Le SDK ne déclare OkHttp qu'à l'exécution (`runtime`) : pour construire un `OkHttpClient` (tests), `:source:jellyfin` le déclare en `implementation`, à la même version.
- **Champs de saisie et télécommande** : Compose ne laisse sortir la croix d'un champ que si l'appareil se déclare `SOURCE_DPAD`, non virtuel (`interceptDPadAndMoveFocus`) ; une télécommande Bluetooth (Chromecast) ou une touche envoyée par `Instrumentation` ne l'est pas, et le champ garde haut et bas pour son curseur. Le champ ouvre aussi le clavier dès l'arrivée du focus (sauf `KeyboardOptions(showKeyboardOnFocus = false)`, que seule la version `TextFieldState` respecte), et le clavier de la TV prend alors la croix. Ouvert à l'appui sur OK, le clavier reçoit le relâchement et tape la touche sous son focus (un « a »). D'où `CineTextField` à deux niveaux (cadre focalisable, saisie après le relâchement de OK). `KeyboardActions(onAny = …)` remplace l'action par défaut : « Suivant » ne déplace plus rien si on ne le fait pas soi-même.
- `SourceException.status` serait masqué par `SourceException.Server(val status)` : la propriété commune s'appelle `httpStatus`.
- Un `verticalScroll` dans `MessagePanel` casserait les écrans qui le posent dans une `LazyColumn` (hauteur infinie) : c'est `FormScreen` qui défile ; ailleurs, la liste parente ramène le détail focalisé à l'écran.
- **Tests instrumentés et mode tactile** : une activité lancée par l'instrumentation (émulateur de la CI) démarre en mode tactile, où Compose refuse le focus aux éléments cliquables : le focus initial d'un écran n'est pas posé. `Instrumentation.setInTouchMode(false)` avant le lancement ne suffit pas toujours ; `SignInJourneyTest` envoie une touche puis, au besoin, pose le premier focus par l'action sémantique `requestFocus()`, et joint l'arbre sémantique (`printToString`) à chaque attente qui échoue. L'émulateur de la CI est **en anglais** : un test instrumenté lit ses libellés dans les ressources (`targetContext.getString(R.string.…)`), jamais en dur.
