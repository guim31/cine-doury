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
- Médias de test : `ci/jellyfin/generate-media.sh DOSSIER` (9 s). Amorçage : `ci/jellyfin/bootstrap.sh URL`.
- OpenAPI : `curl http://localhost:8096/api-docs/openapi.json | python3 -m json.tool --indent 2 > docs/api/jellyfin-openapi-12.3.json`. La CI vérifie qu'il correspond au serveur (descriptions exclues).

## Pièges

- Jellyfin 12 n'accepte que l'en-tête `Authorization: MediaBrowser …, Token="…"`. `X-Emby-Token` et `?api_key=` reçoivent un 401.
- Sur un serveur qui a des collections, `/Items` regroupe les films dans leurs collections par défaut : `CollapseBoxSetItems=false` pour les lister ou les compter.
- Un appel authentifié crée une session Jellyfin à notre `DeviceId` : la filtrer quand on liste les lectures en cours.
- **SDK Kotlin de Jellyfin et caractères non ASCII** : le SDK recopie `ClientInfo.name` tel quel dans `User-Agent`, et OkHttp refuse tout caractère non ASCII dans un en-tête (exception à chaque appel). Le client s'annonce donc `Cine Doury`. Dans `Authorization`, le SDK encode les valeurs comme un formulaire (`Client="Cine+Doury"`, `Device="Salon+%C3%A9tage"`) : un nom d'appareil accentué y passe sans risque.
- Le SDK journalise par kotlin-logging, qui exige `org.slf4j:slf4j-api` à l'exécution sans que le SDK l'apporte (`NoClassDefFoundError: org/slf4j/LoggerFactory` au premier appel) : `:source:jellyfin` le déclare en `runtimeOnly`.
- L'OpenAPI servie par Jellyfin n'est pas stable d'un démarrage à l'autre : la description d'une propriété `$ref` change (« Gets or sets the user. » ou « Class UserDto. »). La CI la compare donc sans les descriptions (`ci/jellyfin/check-openapi.py`).
- Un `FocusRequester` posé dans un élément de `LazyColumn` n'est rattaché qu'après la première mise en page : `requestFocus()` dans un `LaunchedEffect(Unit)` ne fait rien. Attendre une image (`withFrameNanos { }`) avant. Et le poser sur l'élément focalisable lui-même, pas sur son conteneur (paramètre `focusRequester` des boutons du design system).
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
- **KSP 2.3.12** marche avec le Kotlin intégré d'AGP 9.4 (Room 2.8.5) sans réglage particulier.
- **Robolectric n'a pas d'Android Keystore** : les tests remplacent `TokenCipher` (module Koin de test, ou faux chiffrement).
- **DataStore** : une seule instance par fichier et par processus (sinon `IllegalStateException`) ; dans un test qui « redémarre » l'appli, partager l'instance.
- Les tailles se pensent en **dp sur un écran de 960 × 540 dp** (1080p en xhdpi) : un formulaire fait 400 à 560 dp de large, pas 1 000.
- `Crossfade` veut une `FiniteAnimationSpec<Float>` : `CineMotion.fade<Float>(…)`. `Paint.asFrameworkPaint()` est déprécié : dessiner un `android.graphics.Paint` sur `nativeCanvas`.
- `Modifier.focusRequester` posé sur une carte du design system atterrit sur son conteneur : passer le paramètre `focusRequester` des cartes et boutons.
