# Ciné Doury

Lecteur vidéo maison pour **Android TV / Google TV**, au-dessus d'un serveur
[Jellyfin](https://jellyfin.org) **non modifié**. Il remplace Plex chez son auteur : une interface
soignée et rapide, toute la gestion de la bibliothèque dans l'appli, et aucune mise à jour imposée.

État : **version 1.0** pour la télévision (Android TV, Google TV) ; ce qu'elle a changé, étape par
étape : [`CHANGELOG.md`](CHANGELOG.md). Voir aussi [`docs/SPEC.md`](docs/SPEC.md) (architecture et
jalons), [`docs/FONCTIONNALITES.md`](docs/FONCTIONNALITES.md) et [`docs/UI.md`](docs/UI.md).

Serveur requis : Jellyfin 12.3 ou plus récent.

## APK de débogage et clé de signature

La CI produit à chaque exécution un APK de débogage (artefact `cine-doury-debug-apk`). Il est signé
par une **clé de débogage fixe, versionnée dans le dépôt** (`app/tv/debug.keystore`, mot de passe
`android`, alias `androiddebugkey`) : chaque nouvel APK s'installe par-dessus le précédent, sans
désinstaller l'appli ni perdre ses réglages (`adb install -r`). **Cette clé n'est pas un secret** :
elle ne signe que des APK de débogage, que n'importe qui peut reconstruire depuis ce dépôt public.

L'APK de **release** est signé par une **clé de release**, distincte, gardée dans les secrets du
dépôt et jamais dans le dépôt lui-même.

## Installer

Depuis la [dernière release](https://github.com/guim31/cine-doury/releases/latest), jamais depuis un
artefact de la CI (un APK de débogage, qui ne recevrait pas les mises à jour).

1. Sur la télévision, autorisez l'installation d'applications venues d'ailleurs :
   - **Google TV** (Chromecast) : Paramètres → Applications → Sécurité et restrictions → Sources
     inconnues, puis activez l'appli qui ouvrira l'APK (un gestionnaire de fichiers, ou Ciné Doury
     lui-même pour ses mises à jour) ;
   - **NVIDIA Shield** : Paramètres → Préférences de l'appareil → Sécurité et restrictions → Sources
     inconnues.
2. Téléchargez `cine-doury-X.Y.Z.apk` depuis la [dernière release](https://github.com/guim31/cine-doury/releases/latest)
   et vérifiez-le : `sha256sum -c cine-doury-X.Y.Z.apk.sha256`.
3. Installez-le : `adb install cine-doury-X.Y.Z.apk` depuis un ordinateur (débogage réseau activé
   dans les options pour les développeurs de la TV), ou ouvrez-le avec un gestionnaire de fichiers.
4. Ouvrez Ciné Doury : il cherche le serveur sur le réseau (sinon, saisissez son adresse), puis
   connectez-vous par Quick Connect ou par mot de passe.

### Vous aviez une version de débogage

Les APK de la CI (essais, jalons) sont signés par la clé de débogage, la release par la clé de
release : Android refuse d'installer l'une par-dessus l'autre (« Application non installée », ou
« Désinstallez d'abord cette version » dans l'écran de mise à jour). Une fois, et une seule :

```sh
adb uninstall fr.guim.cinedoury      # ou Paramètres → Applications → Ciné Doury → Désinstaller
adb install cine-doury-1.0.0.apk
```

Les profils se reconnectent (Quick Connect ou mot de passe) ; les préférences gardées sur le serveur
(langues, ordre des bibliothèques, vu et en cours) reviennent seules, celles de la télévision
(thème, taille du texte, accueil personnalisé) sont à refaire. Ensuite, chaque version s'installe
par-dessus la précédente.

## Mettre à jour

Ciné Doury regarde les releases du dépôt au démarrage (une fois par jour au plus) et dans Réglages →
Appareil → Mise à jour de l'appli. Une nouvelle version est **proposée, jamais imposée** :
« Installer », « Plus tard » ou « Ignorer cette version ». L'appli télécharge l'APK, vérifie son
SHA-256 et sa signature, puis Android demande de confirmer.

- **Une version de développement** (APK de la CI, signé par la clé de débogage) ne se voit rien
  proposer : voir « Vous aviez une version de débogage » ci-dessus.
- **Revenir en arrière** : Android n'installe jamais une version plus ancienne par-dessus une plus
  récente. Un retour arrière est donc publié comme une **nouvelle version** qui reprend le code
  d'avant (par exemple la 1.2.1 avec le code de la 1.1.0) : l'appli la propose comme n'importe quelle
  mise à jour, sans rien perdre. Voir `docs/SPEC.md` § 4.18.

## Publier une version (mainteneur)

Poser l'étiquette `vX.Y.Z` sur `main` et la pousser : le workflow « Release » construit la version
`release`, la signe avec la clé des secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) et publie la release avec l'APK, son SHA-256 et le journal
des changements (`docs/releases/vX.Y.Z.md` s'il existe, sinon les commits depuis la version
précédente). Pour un retour arrière : « Run workflow », `version` = la nouvelle version, `source` =
l'étiquette dont on reprend le code.

## Mesurer (mainteneur)

[`docs/MESURES.md`](docs/MESURES.md) : images perdues et coût du GPU sur la télévision
(`dumpsys gfxinfo`) ; [`docs/GESTES.md`](docs/GESTES.md) : les 20 gestes au canapé, chronométrés par
`ci/couch-gestures.sh`.

Ciné Doury n'est affilié ni à Jellyfin, ni à Plex.

Licence MIT.
