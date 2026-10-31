# Journal des changements

Ce que chaque étape a changé pour qui regarde, du point de vue de la télécommande. Le détail
technique est dans [`docs/SPEC.md`](docs/SPEC.md) ; les notes de chaque version publiée, dans
[`docs/releases/`](docs/releases/).

## Non publié

Transcodage à la taille de l'écran (Chromecast sur une télé 1080p SDR, les deux applis) :

- **« Connexion au serveur perdue »** ne s'affiche plus au démarrage d'un film transcodé ni quand le
  serveur est lent : seulement quand des chargements échouent vraiment.
- **Un film 4K converti par le serveur** l'est à la taille de l'écran (1080p sur une télé 1080p, le côté
  long sur un téléphone), à un débit raisonnable en « Automatique » (20 Mb/s en 1080p) et en H.264
  « High » ; une qualité choisie est respectée ; un film lisible tel quel reste lu tel quel.
- **Écran sans HDR** : un film HDR est converti tout de suite, couleurs adaptées, sans essai raté.
- **Une lecture figée** finit, au bout de 30 secondes, sur un message avec « Réessayer » et le détail dans
  « Plus d'infos », au lieu d'attendre sans fin.

## 1.0.2 — 31/10/2026

Notes de version : [`docs/releases/v1.0.2.md`](docs/releases/v1.0.2.md).

M1 — l'appli **téléphone et tablette** (Android), première étape ; pas encore dans les releases :
le mainteneur l'installe depuis les artefacts de la CI ou du workflow « Release ».

- **Une appli à part** (« Ciné Doury » sur le téléphone, à côté de celle de la TV), mêmes couleurs,
  même jaune projecteur, même noir OLED, à la main : barre du bas Accueil, Recherche, Bibliothèques,
  Profil ; rotation, geste de retour, bord à bord.
- **Connexion** comme sur la TV, avec le clavier du téléphone : serveur trouvé sur le réseau ou
  adresse saisie, profils, Quick Connect ou mot de passe, code PIN. En passant du Wi-Fi au réseau
  mobile, l'appli bascule seule sur l'adresse publique, et revient à l'adresse de la maison au retour ;
  le profil dit laquelle est utilisée.
- **Connecter un appareil** (Profil) : le code à six chiffres de la TV, validé depuis le téléphone :
  la TV se connecte sans mot de passe.
- **Accueil** : la mosaïque « Ce soir » (grande tuile, quatre petites dessous), puis les rangées de la
  TV. **Bibliothèques** en grille, tri, filtres, A–Z ; **fiches** avec les boutons en haut ;
  **recherche** instantanée, au clavier ou à la voix ; **appui long** sur une jaquette : le menu
  d'options rapides de la TV.
- **Lecture** en plein écran paysage : lecture/pause, −10 s / +10 s, barre à faire glisser, pistes
  audio et sous-titres, épisode suivant ; reprise et position partagées avec la TV.
- **TV** : rien ne change à l'écran. Pour les versions suivantes, chaque appli ne prendra dans une
  release que son propre fichier.

M2 — le **lecteur nomade** du téléphone, pour regarder en voiture :

- **Marge de lecture** : la lecture démarre aussi vite qu'avant, puis le téléphone charge d'avance,
  en mémoire (environ 2 min) puis sur son stockage, jusqu'à la marge choisie (Profil → « Marge de
  lecture » : 5, 15 ou 30 min, 15 par défaut). Un tunnel, une campagne sans réseau : le film continue
  sur sa marge, sans message ; le chargement reprend seul au retour du réseau, même si l'adresse a
  changé (Wi-Fi de la maison, réseau mobile). Si la marge s'épuise : « Le réseau est coupé. La
  lecture reprendra dès son retour. », et le film repart seul. La marge se voit en gris clair sur la
  barre ; « Informations » dit « Marge : 12 min (dont 2 en mémoire) » et le débit mesuré.
- **Qualité selon le réseau** : Automatique en Wi-Fi, 4 Mb/s sur le réseau mobile (réglables dans le
  Profil, avec ce qu'une heure consomme). Si la marge fond, la qualité baisse d'un cran (« Qualité
  réduite : réseau faible »), puis remonte prudemment ; jamais si vous avez choisi une qualité.
- **Gestes** : deux appuis à gauche ou à droite pour −10 / +10 s (cumulables : « −30 s »), glisser
  verticalement pour la luminosité (à gauche) ou le volume (à droite), pincer pour remplir l'écran,
  glisser sur l'image pour chercher dans le film.
- **Cadenas** : l'écran ignore les doigts des enfants jusqu'à un appui long sur le cadenas.
- **Adresse à distance** (Profil) : « Adresse locale » et « Adresse à distance », proposée aussi à la
  première connexion faite à la maison ; hors de portée, le Profil dit « Serveur injoignable » (et
  pourquoi) au lieu de « Connecté à la maison ».
- **Films remuxés** (téléphone et TV) : un film H.264 que le serveur copie sans le réencoder (son DTS
  converti, par exemple) se lit de nouveau ; et une erreur de lecture du flux n'est plus présentée comme
  une connexion perdue.
- **Image dans l'image** en quittant l'appli pendant le film (lecture/pause, ±10 s) ; pause en
  arrière-plan, écran éteint, écouteurs débranchés ou appel entrant ; portrait possible (bouton).

Fausse erreur de lecture (les deux applis) :

- Un film ou un épisode **transcodé dont le son est copié tel quel** (un AVI en MPEG-4 et MP3, par
  exemple) n'affiche plus « La lecture s'est arrêtée » au lancement, alors que la vidéo se lançait
  derrière le message.
- Quand une erreur s'affiche vraiment, la lecture s'arrête : plus de son ni d'image derrière le
  message. « Réessayer » reprend où elle s'était arrêtée.

## 1.0.1 — 30/10/2026

Notes de version : [`docs/releases/v1.0.1.md`](docs/releases/v1.0.1.md).

- **Nouveau logo** : un pot de pop-corn, sur l'icône de l'appli et sur la bannière de l'écran
  d'accueil d'Android TV (« Ciné Doury » à côté du pot).
- **Mosaïque « Ce soir »** : une tuile du haut focalisée n'est plus rognée ; son contour et son halo
  restent entiers, dans toutes les tailles de l'interface.
- **Menu de gauche** : il ne s'ouvre plus tout seul au lancement, ni quand on choisit une
  bibliothèque ; il s'ouvre et se ferme une fois par geste.

## 1.0.0 — 29/10/2026

La première version complète pour la télévision (Android TV et Google TV), qui remplace Plex au-dessus
d'un serveur Jellyfin 12.3 : elle réunit tout ce journal, de J0 à l'essai réel 5.
Notes de version : [`docs/releases/v1.0.0.md`](docs/releases/v1.0.0.md).

Essai réel 5 (29/10/2026, NVIDIA Shield TV en 4K et Chromecast avec Google TV, v0.9.1).

- **Épisode suivant** : sans générique repéré par le serveur, l'épisode suivant ne démarre plus
  avant la fin de l'épisode en cours ; le compte à rebours se termine à la fin.
- **Lecteur** : pour un épisode, « Épisode précédent » et « Épisode suivant » de part et d'autre de
  lecture/pause, d'une saison à l'autre ; les touches précédent / suivant de la télécommande font de
  même.
- **Appui long sur une jaquette** (accueil, bibliothèques, recherche, fiches) : un menu d'options
  rapides : reprendre ou lire, lire depuis le début, ouvrir la fiche, aller à la série ou à la saison,
  marquer comme vu ou non vu, retirer de « Continuer », ajouter aux favoris ou les retirer ; dans une
  bibliothèque, « Sélectionner » pour les comptes qui gèrent.
- **Accueil** : en haut, la mosaïque « Ce soir » remplace le bandeau : une grande tuile (la lecture
  en cours la plus récente) et quatre petites (autres lectures, épisodes à suivre, nouveaux épisodes,
  derniers ajouts, un titre à redécouvrir). Dessous, une seule rangée « Continuer » : les lectures en
  cours puis les épisodes à suivre que la mosaïque ne montre pas ; dans « Personnaliser l'accueil »,
  elle prend la place de « Reprendre » et « À suivre », et garde ce qui était réglé pour elles.
  Réglages → Accueil et navigation → « Mosaïque « Ce soir » » pour revenir au bandeau, avec
  « Reprendre » et « À suivre » comme avant.
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
