# Journal des changements

Ce que chaque étape a changé pour qui regarde, du point de vue de la télécommande. Le détail
technique est dans [`docs/SPEC.md`](docs/SPEC.md) ; les notes de chaque version publiée, dans
[`docs/releases/`](docs/releases/).

## Non publié

Essai réel 5 (29/10/2026, NVIDIA Shield TV en 4K et Chromecast avec Google TV, v0.9.1).

- **TalkBack** : chaque entrée du menu de gauche dit de nouveau son nom, menu ouvert ou fermé.

## 0.9.1 — 29/10/2026

Essai réel 4 (28 et 29/10/2026, NVIDIA Shield TV en 4K et Chromecast avec Google TV, v0.9.0).
Notes de version : [`docs/releases/v0.9.1.md`](docs/releases/v0.9.1.md).


- **Taille de l'interface** : « Très compacte » par défaut ; une taille déjà choisie est gardée.
- **Accueil** : sous le bandeau, une rangée entière et la moitié de la suivante ; le bandeau garde
  titre ou logo, infos, résumé et boutons quand ils tiennent, le résumé partant d'abord ; le titre d'une rangée ne bouge plus quand on passe
  d'une jaquette à l'autre.
- **Rangées** : moins d'espace entre elles, et une rangée qui défile ne passe plus sous le menu de
  gauche.
- **Quitter** : « Retour » depuis le menu de l'accueil ferme l'appli sans demander de confirmation.
- **Versions d'essai** : elles s'installent par-dessus la version publiée, sans désinstaller ni se
  reconnecter ; la version publiée suivante s'installe ensuite par-dessus.

## 1.0.0 — à paraître

La première version complète pour la télévision (Android TV et Google TV), qui remplace Plex au-dessus
d'un serveur Jellyfin 12.3. Elle réunit tout ce qui suit, de J0 à J8.

## 0.9.0 — à paraître

Une version d'étape avant la 1.0.0, qui attend que le Chromecast avec Google TV soit aussi fluide que
la Shield. Elle réunit le J8 (ci-dessous) et l'essai réel 3. Notes de version :
[`docs/releases/v0.9.0.md`](docs/releases/v0.9.0.md).

## Essai réel 3 (27 et 28/10/2026, NVIDIA Shield TV en 4K)

- **Taille de l'interface** (Apparence) : Très compacte, Compacte (par défaut) ou Normale ; toute la
  mise en page change d'échelle. Une bibliothèque montre 7, 6 ou 5 affiches par ligne.
- **Bandeau de l'accueil fixe** : il ne défile plus avec les rangées, et son titre, ses infos et ses
  boutons ne bougent plus d'un film à l'autre.
- **Menu de gauche** : → depuis le menu ouvert ramène le focus là où il était, et referme le menu.
- **Lecteur** : boutons, icônes, « Passer l'intro » et barre de progression plus petits.
- **Barre des sous-titres** : ← → passent de la taille à la position et au fond, ↑ ↓ changent la valeur.
- **Barre d'une bibliothèque** : « Gestion » n'est plus une pastille vide.
- **Icônes des bibliothèques** : 27 au lieu de 11, choisies d'après le nom (français et anglais),
  chacune dessinée dans le choix manuel des réglages ; « Films animés » n'a plus l'icône des animes.

## J8 — Finitions 1.0 (27/10/2026)

- **Plus fluide en 4K** : le halo du focus n'est plus recalculé à chaque image (il est préparé une
  fois par taille de carte), le fond d'ambiance se dessine en une couche de moins. Rien ne change à
  l'œil.
- **Les petits appareils** (Chromecast avec Google TV) gardent moins d'images en mémoire et n'en
  chargent pas d'avance quand Android manque de place.
- **Lisibilité** : le texte discret (`texte-3`) est un peu plus clair et le fond des boutons de
  suppression un peu plus sombre, pour un contraste suffisant partout (niveau AA).
- **Grand texte** : en « Très grande » taille, les titres d'écran ne coupent plus les mots, la barre
  d'une bibliothèque passe sur deux lignes au lieu de sortir de l'écran (« Affichage » était déjà
  rogné en taille normale), les touches du clavier gardent leur taille, les noms de profils et les
  explications des réglages ne sont plus coupés.
- **Focus jamais rogné** : le contour du bouton « Utiliser un mot de passe » (Quick Connect) et des
  lignes en bord de panneau n'est plus coupé.
- **Focus** : « Retour » sur un panneau d'une bibliothèque (Trier, Filtres, Affichage) rend le focus
  à son bouton, et non plus au menu de gauche.
- **TalkBack** : les entrées du menu ne sont plus lues deux fois, les cartes disent leur titre, les
  interrupteurs et les choix des réglages disent leur état, les titres d'écran sont des titres.
- Le rapport de diagnostic dit la mémoire de l'appareil et ses architectures.
- Mesures : ouverture d'une fiche et des réglages, captures 4K et grand texte, 20 gestes au canapé
  chronométrés ([`docs/GESTES.md`](docs/GESTES.md)).
- Profils de démarrage et de navigation régénérés (fiche, réglages, mise à jour compris).

## J7 — Réglages et mise à jour (27/10/2026)

- **Réglages** rangés par thème (Lecture, Image, Son, Sous-titres, Intro et générique, Accueil et
  navigation, Bibliothèques, Apparence, Profil, Réseau, Appareil, Avancé), chaque ligne avec sa valeur
  par défaut, et ce qui suit le profil séparé de ce qui vaut pour la télévision.
- Les langues audio et de sous-titres et l'ordre des bibliothèques sont gardés sur le serveur : ils
  vous suivent sur tous vos appareils.
- **Mise à jour proposée** depuis les releases du dépôt, jamais imposée : « Installer », « Plus tard »,
  « Ignorer cette version » ; l'APK est vérifié (somme et signature) avant d'être installé.
- Taille du texte, thème noir OLED, animations réduites ; nom de l'appareil dans Jellyfin.
- Journaux de l'appli et **rapport de diagnostic** envoyé au serveur, sans aucun secret.

## Fluidité (26/10/2026)

- Une **version optimisée** (et non plus de débogage) : démarrage deux fois plus rapide, défilement
  sans saccade sur la Shield.
- Sur une télévision 4K, l'appli montre exactement l'écran du 1080p, deux fois plus net.
- Le menu de gauche ne rogne plus le contour de l'entrée focalisée.

## Essai réel 2 (26/10/2026, NVIDIA Shield TV)

- L'appli ne plante plus au premier affichage sur Android 11.
- Le clavier de la télévision se ferme après la saisie et ne vole plus la touche OK.

## J6 — Gestion (25/10/2026)

- Menu « ⋯ » d'un titre : actualiser, **corriger l'identification** (résultats avec affiches et
  année), **changer l'affiche, le fond ou le logo** (galerie), supprimer avec confirmation (chemin et
  taille, focus sur « Annuler »).
- Une bibliothèque : analyser, éléments à problème (non identifiés, sans affiche, fichiers disparus),
  sélection de plusieurs titres pour les marquer vus ou les actualiser ensemble.

## J5 — Recherche (25/10/2026)

- Recherche instantanée, tolérante aux accents et aux fautes, au clavier à l'écran ou **à la voix**.
- Résultats groupés : films, séries, épisodes, personnes, collections ; recherches récentes et genres.
- **Seerr** (facultatif) : demander un film ou une série absents de la bibliothèque.

## Essai réel 1 (25/10/2026, Chromecast avec Google TV)

- Connexion et Quick Connect fiables à la télécommande, saisie de l'adresse sans lettres perdues.
- Un focus toujours posé sur « Qui regarde ? », « Plus d'infos » sur chaque erreur.

## J4 — Lecteur (25/10/2026)

- Lecture directe quand l'appareil sait lire le fichier, sinon le serveur adapte ; la raison est
  affichée dans les informations.
- HDR10, HDR10+, Dolby Vision, son vers l'ampli (passthrough), cadence de l'écran adaptée au film.
- Pistes audio et sous-titres (SRT, ASS, PGS), **taille des sous-titres** réglable en lecture.
- Reprise là où vous en étiez, aperçus en avançant, « Passer l'intro », épisode suivant, « Toujours
  là ? ».

## J3 — Fiches (25/10/2026)

- Fiches de film, de série (saisons, épisodes, **bouton intelligent** « Reprendre S02E05 »),
  d'épisode, de personne et de collection, avec l'heure de fin.
- Badges techniques (4K, Dolby Vision, Atmos…), choix de la version et des pistes avant la lecture.
- Filtres HDR, format audio et codec dans les bibliothèques.

## J2 — Accueil et navigation (25/10/2026)

- **Accueil** : bandeau à la une, « Reprendre », « À suivre », « Ajouts récents » par bibliothèque,
  « Récemment sortis » ; personnalisable ; s'affiche tout de suite depuis le cache.
- **Bibliothèques** : grille, liste, vignettes, tri, filtres, saut A–Z, collections, genres.
- Le focus et la position sont gardés : « Retour » ramène exactement à la carte quittée.

## J1 — Connexion et profils (25/10/2026)

- Serveurs trouvés sur le réseau, ou adresse saisie ; **Quick Connect** (un code à valider sur le
  téléphone) ou mot de passe.
- « Qui regarde ? » avec avatars, code PIN par profil, adresse locale puis distante.
- Erreurs en deux phrases : ce qui s'est passé, ce que vous pouvez faire.

## J0 — Fondations (25/10/2026)

- Le socle : l'appli Android TV, son design (jaune projecteur, focus visible, fond sombre ou noir
  OLED) et le lien avec Jellyfin 12.3.
