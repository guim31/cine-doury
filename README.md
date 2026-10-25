# Ciné Doury

Lecteur vidéo maison pour **Android TV / Google TV**, au-dessus d'un serveur
[Jellyfin](https://jellyfin.org) **non modifié**. Il remplace Plex chez son auteur : une interface
soignée et rapide, toute la gestion de la bibliothèque dans l'appli, et aucune mise à jour imposée.

État : en construction. Voir [`docs/SPEC.md`](docs/SPEC.md) (architecture et jalons),
[`docs/FONCTIONNALITES.md`](docs/FONCTIONNALITES.md) et [`docs/UI.md`](docs/UI.md).

Serveur requis : Jellyfin 12.3 ou plus récent.

## APK de débogage et clé de signature

La CI produit à chaque exécution un APK de débogage (artefact `cine-doury-debug-apk`). Il est signé
par une **clé de débogage fixe, versionnée dans le dépôt** (`app/tv/debug.keystore`, mot de passe
`android`, alias `androiddebugkey`) : chaque nouvel APK s'installe par-dessus le précédent, sans
désinstaller l'appli ni perdre ses réglages (`adb install -r`). **Cette clé n'est pas un secret** :
elle ne signe que des APK de débogage, que n'importe qui peut reconstruire depuis ce dépôt public.

L'APK de **release** est signé par le mainteneur avec une clé gardée hors du dépôt.

Ciné Doury n'est affilié ni à Jellyfin, ni à Plex.

Licence MIT.
