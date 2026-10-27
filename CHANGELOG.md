# Journal des changements

Ce que chaque étape a changé pour qui regarde, du point de vue de la télécommande. Le détail
technique est dans [`docs/SPEC.md`](docs/SPEC.md) ; les notes de chaque version publiée, dans
[`docs/releases/`](docs/releases/).

## 1.0.0 — à paraître

La première version complète pour la télévision (Android TV et Google TV), qui remplace Plex au-dessus
d'un serveur Jellyfin 12.3. Elle réunit tout ce qui suit, de J0 à J8.

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
