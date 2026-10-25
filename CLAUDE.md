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

- Le proxy refuse `api.github.com` et `codeload.github.com` : pas de `gh` (passer par les outils `mcp__github__*`), et aucune dépendance tirée d'une archive GitHub. **Le reste est à mesurer au J0** et à noter ici : accès à `dl.google.com`, `maven.google.com`, Maven Central, au SDK Android, à Docker. Docker n'est pas supposé disponible : les tests contre un vrai Jellyfin tournent alors en CI seulement.
- La PR brouillon ne s'ouvre pas toute seule : la créer avec `mcp__github__create_pull_request` (`draft: true`) après le premier push.

## Commandes

*(À remplir par le J0.)*

## Pièges

- Jellyfin 12 n'accepte que l'en-tête `Authorization: MediaBrowser …, Token="…"`. `X-Emby-Token` et `?api_key=` reçoivent un 401.
- Sur un serveur qui a des collections, `/Items` regroupe les films dans leurs collections par défaut : `CollapseBoxSetItems=false` pour les lister ou les compter.
- Un appel authentifié crée une session Jellyfin à notre `DeviceId` : la filtrer quand on liste les lectures en cours.
