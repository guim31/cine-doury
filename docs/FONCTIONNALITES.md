# Ciné Doury — fonctionnalités

Cahier des fonctionnalités de Ciné Doury, lecteur vidéo maison qui remplace Plex. Rédigé le
24/10/2026, décisions du mainteneur reportées le 25/10/2026. Les règles d'interface sont dans
`UI.md`, l'architecture, les contrats et les jalons dans `SPEC.md`.

## 0. Cadre

- **Moteur** : Jellyfin 12.3, sur le serveur de la maison, **non modifié et épinglé**. L'appli n'utilise que son
  API publique. Personne n'a à ouvrir l'interface web de Jellyfin : tout geste utile est dans l'appli.
- **Couche « source » abstraite** : l'appli ne parle pas à Jellyfin directement depuis ses écrans, mais
  à une interface interne (bibliothèques, éléments, lecture, gestion). Jellyfin en est la première
  implémentation. Cette interface rend possibles un second serveur Jellyfin, IPTV, et un jour un
  serveur écrit maison, sans réécrire l'interface.
- **Aucune mise à jour imposée** : l'appli annonce une nouvelle version et montre le journal des
  changements, mais ne s'installe jamais seule. On peut revenir à la version précédente.
- **Zéro télémétrie**, aucun contenu « découverte » ni streaming tiers mêlé à la bibliothèque
  (un défaut reproché à Plex).
- **Langues** : français d'abord, anglais ensuite.

### Écrans et priorités

| Priorité | Cible | Appareils réels |
|---|---|---|
| **P0** | Android TV / Google TV | NVIDIA Shield TV (appareil de référence), Chromecast avec Google TV |
| **P1** | Android téléphone et tablette | Xiaomi 14, Galaxy Tab A9+ |
| **P2** | Navigateur (lecture + gestion) | PC, et le reste en dépannage |
| **P3** | Source pour une appli Android Auto maison | voiture garée seulement |

Les priorités dans la suite : **[P0]** = nécessaire pour remplacer Plex sur la TV (version 1.0),
**[P1]** = juste après, **[P2]** = plus tard. Une fonctionnalité sans plateforme précisée vaut pour
toutes les cibles où elle a du sens.

---

## 1. Connexion, serveurs et profils

- [P0] Découverte automatique du serveur sur le LAN (UDP 7359), ou saisie de l'adresse.
- [P0] **Quick Connect** : la TV affiche un code à 6 chiffres, validé depuis le téléphone. Aucun mot de passe tapé à la télécommande.
- [P0] Connexion par identifiant et mot de passe (mot de passe masqué, affichable).
- [P0] **Écran de choix du profil** au démarrage, avec avatars, ou ouverture directe sur le dernier profil (réglage).
- [P0] Code PIN à 4 chiffres par profil (facultatif), saisi au pavé de la télécommande.
- [P1] **Profils enfants** : bibliothèques autorisées, classification maximale (par ex. « Tous publics, -10 »), pas d'accès aux réglages ni à la gestion, horaires autorisés.
- [P1] Plusieurs serveurs (maison, et plus tard une autre instance), chacun avec ses comptes. Changer de serveur depuis le profil.
- [P1] Basculement automatique entre adresse locale et adresse publique (`https://…`) selon le réseau, avec indicateur discret.
- [P2] Connexion par lien magique ou QR code affiché sur la TV, pour un invité.

## 2. Accueil

- [P0] **Bandeau « à la une »** en tête : fond plein écran du titre sélectionné, logo du titre (pas du texte quand le logo existe), une ligne d'accroche, boutons « Lire » / « Reprendre » et « Fiche ».
- [P0] Rangées :
  - « Reprendre » (films et épisodes commencés) ;
  - « À suivre » (prochain épisode des séries en cours), avec une **fenêtre réglable**, 2 semaines par défaut comme Plex (`OnDeckWindow = 2`) ;
  - « Ajouts récents » par bibliothèque ;
  - « Récemment sortis ».
- [P0] Les bibliothèques **exclues de l'accueil** dans les préférences Jellyfin du profil (`MyMediaExcludes`, `LatestItemsExcludes`) n'y apparaissent pas, comme les bibliothèques masquées de Plex. Elles restent accessibles par la navigation.
- [P0] **Accueil personnalisable** depuis l'appli : ajouter, retirer et réordonner les rangées ; choisir les bibliothèques de chaque rangée ; affiche portrait ou vignette paysage.
- [P1] Rangées intelligentes :
  - « Parce que vous avez regardé X » ;
  - « Une saga à finir » (collection entamée) ;
  - « Ce soir, moins de 1 h 45 » (filtre de durée) ;
  - « Au hasard dans vos favoris » ;
  - « Sorti il y a 30 ans cette semaine » ;
  - genres mis en avant ;
  - « Jamais vus, ajoutés il y a longtemps ».
- [P1] Rangée « Bientôt » : prochains épisodes attendus, lus dans le calendrier de Sonarr.
- [P1] Retirer un élément de « Reprendre » ou de « À suivre » sans le marquer vu.
- [P2] Accueil différent selon le moment (le soir, les films ; le week-end matin, les dessins animés pour le profil enfant).

## 3. Navigation dans les bibliothèques

- [P0] Menu latéral (rail) : Accueil, Recherche, une entrée par bibliothèque (ordre et visibilité réglables), Réglages, profil.
- [P0] Vues **grille d'affiches** (taille réglable en 3 crans), **liste détaillée** et **vignettes paysage**.
- [P0] **Tri** : titre, titre de tri, date d'ajout, date de sortie, note, durée, dernière lecture, nombre de lectures, aléatoire. Sens croissant ou décroissant.
- [P0] **Filtres combinables**, avec un résumé lisible (« Comédies · années 80 · non vus ») et un bouton pour tout effacer :
  - vu, non vu, en cours ;
  - genre ; année ou décennie ; note minimale ;
  - résolution (SD, 720p, 1080p, 4K) ; HDR10, HDR10+, Dolby Vision ; codec ;
  - audio (stéréo, 5.1, 7.1, Atmos, DTS) ; langue audio (VF, VO, multi) ; sous-titres disponibles ;
  - classification ; pays ; studio ; acteur, réalisateur ; collection ; étiquette ; favori.
- [P0] **Saut alphabétique** latéral (A–Z, #) et retour en haut.
- [P0] Onglets par bibliothèque : Tout, Collections, Genres, et pour une bibliothèque de séries « À suivre ».
- [P1] **Filtres enregistrés** (« collections intelligentes ») : un filtre devient une entrée du menu ou une rangée d'accueil.
- [P1] Vue **dossiers** : parcourir l'arborescence réelle du NAS, utile pour les dossiers rangés à la main (Animes JPN, Spectacles).
- [P1] Mémoire de la position : revenir dans une bibliothèque ramène au même élément, avec le même tri et les mêmes filtres.
- [P2] Mode « mur » : grille plein écran sans texte, défilement continu.

## 4. Fiches

### Film
- [P0] Fond plein écran, logo du titre, année, durée, **heure de fin calculée** (« se termine à 22 h 47 »), classification, genres, synopsis complet (dépliable).
- [P0] **Badges techniques** sobres : 4K, HDR10 / Dolby Vision, Atmos / DTS-HD / 5.1, VF / VO / VOST, taille du fichier en option.
- [P0] Boutons : Lire / Reprendre (avec le temps restant), Depuis le début, Bande-annonce, Marquer vu, Favori, menu « ⋯ ».
- [P0] **Choix de la piste audio et des sous-titres avant la lecture**, et choix de la version si plusieurs fichiers existent (par ex. 4K HDR et 1080p).
- [P0] Distribution avec photos (rôle sous le nom), réalisation, scénario.
- [P0] Saga : rangée de la collection TMDb (« Retour vers le futur — la trilogie »), dans l'ordre de sortie.
- [P1] Films similaires présents dans la bibliothèque.
- [P1] Notes TMDb et IMDb quand elles existent (source réglable, Plex est sur TMDb).
- [P1] Chapitres avec vignettes ; bonus et extras.
- [P1] « Où regarder dans la maison » : pistes et sous-titres disponibles résumés en une ligne (« VF 5.1, VO 5.1, ST fr forcés »).

### Série
- [P0] Fond, logo, années de diffusion, statut (en cours / terminée), nombre de saisons, synopsis.
- [P0] Bouton principal intelligent : « Lire S02E05 » (prochain non vu) ou « Reprendre S02E05 — 12 min restantes ».
- [P0] Saisons en onglets ; épisodes en liste avec vignette, numéro, titre, durée, résumé, progression, date de diffusion.
- [P0] Marquer vu ou non vu un épisode, une saison, une série.
- [P0] **Aplatir les séries à saison unique** (pas d'écran « Saison 1 » inutile), comme le réglage Plex `flattenSeasons` des bibliothèques Animes JPN et Reportages.
- [P1] Ordre des épisodes : diffusion, DVD, absolu (animes).
- [P1] Afficher les épisodes manquants (grisés), en option.
- [P1] Titre original ou titre traduit, au choix par bibliothèque : Plex affiche les titres originaux dans « Séries TV » (`originalTitles`).
- [P2] « Rattrapage » : résumé de l'épisode précédent avant de reprendre une série après plusieurs semaines.

### Personne
- [P1] Photo, biographie, **filmographie limitée à la bibliothèque**, puis « autres œuvres » en option.

## 5. Lecture (le cœur de l'appli)

### Démarrage
- [P0] **Lecture directe en priorité**, puis flux direct (remux), puis transcodage, choisis automatiquement d'après les capacités réelles de l'appareil : codecs, HDR, audio.
- [P0] Objectif : lecture lancée **en moins de 2 s** en lecture directe sur le réseau local. Une des plaintes contre Plex est de 10 à 30 s.
- [P0] Reprise à la dernière position, avec le choix « Reprendre à 42:10 » ou « Depuis le début ».
- [P0] Un élément est « vu » à 90 % (seuil réglable), ou dès le début du générique de fin quand il est connu.

### Image
- [P0] HDR10, HDR10+ et Dolby Vision transmis tels quels quand la chaîne lecteur → ampli → TV les accepte. Sinon, conversion côté serveur (tone mapping hable, comme Plex).
- [P0] **Fréquence d'images adaptée** (24p pour un film) et résolution d'origine, en option par appareil.
- [P0] Format : adapter, remplir, étirer, zoom ; forcer 4:3 ou 2,39:1.
- [P1] Désentrelacement pour les vieux DVD (dessins animés, Silly Symphony).

### Son
- [P0] **Passthrough** AC3, E-AC3, DTS, DTS-HD, TrueHD et Atmos vers l'ampli audio-vidéo, réglable codec par codec et par appareil (le Chromecast n'est pas forcément derrière un ampli).
- [P0] Piste audio préférée par langue : **VF par défaut** (préférence du profil), règles par bibliothèque (par ex. Animes JPN : VO japonaise et sous-titres français).
- [P1] **Mode nuit** : compression dynamique, dialogues renforcés, normalisation du volume.
- [P1] Décalage audio réglable (± 500 ms), mémorisé par appareil.

### Sous-titres
- [P0] SRT, ASS/SSA (avec styles), PGS et VobSub (Blu-ray et DVD).
- [P0] Mode : désactivés, forcés seulement (réglage actuel), toujours, ou « intelligent » (si l'audio n'est pas dans ma langue).
- [P0] Apparence : taille, couleur, contour, fond, position verticale. Aperçu en direct sur l'image en pause.
- [P0] **Taille réglable en cours de film, à sa guise** *(ajout du 25/10/2026)* : de 60 % à 200 %, depuis la lecture en deux appuis au plus (↑ ou ↓ maintenus quand l'interface est cachée, ou la barre « Taille, position et fond » des panneaux). Effet immédiat sur l'image, sans pause ; une phrase d'exemple s'affiche s'il n'y a pas de sous-titre à l'écran. Position verticale et fond (aucun, ombré, bandeau) dans la même barre.
  - Taille relative à la hauteur de l'image, jamais en pixels : même rendu en 1080p et en 4K.
  - Mémorisée par profil et par appareil ; le réglage fait pendant un film devient la valeur par défaut, et se retrouve dans l'apparence des sous-titres.
  - Vaut pour SRT, ASS (en gardant les proportions des styles), PGS et VobSub (images agrandies). Des sous-titres incrustés par le serveur ne peuvent pas changer : l'écran le dit.
- [P1] Décalage de synchronisation (± 0,1 s), mémorisé pour le fichier.
- [P1] Recherche et téléchargement de sous-titres (OpenSubtitles) depuis le lecteur.
- [P2] Double sous-titre (deux langues), pour apprendre une langue.

### Pendant la lecture
- [P0] **Passer l'intro / passer le générique** (segments Jellyfin), avec bouton ou saut automatique (réglage).
- [P0] **Épisode suivant** : carte « Prochain épisode dans 10 s » pendant le générique, lecture enchaînée réglable.
- [P0] « Vous êtes toujours là ? » après N épisodes d'affilée (3 par défaut, réglable).
- [P0] Recherche dans la vidéo avec **vignettes d'aperçu** (trickplay), sauts de 10 ou 30 s (réglables), saut de chapitre.
- [P0] Changer d'audio, de sous-titres et de qualité sans quitter la lecture.
- [P0] Touches dédiées de la télécommande et HDMI-CEC : lecture, pause, avance, recul.
- [P1] **Écran d'informations technique** : moteur de lecture, mode (direct / remux / transcodage) **et sa raison**, codecs, débit, mémoire tampon, images perdues. C'est ce qui permet de comprendre une saccade.
- [P1] Vitesse de lecture (0,5× à 2×), pour les documentaires et YouTube.
- [P1] Minuterie de mise en veille : 15/30/60 min, « fin de l'épisode ».
- [P1] Pause longue : écran de veille avec le fond du film et l'heure.
- [P1] Second moteur de lecture **mpv**, utilisé automatiquement si ExoPlayer échoue, ou forcé par fichier. Il sert aux sous-titres ASS complexes et aux formats exotiques.
- [P2] SyncPlay : regarder à plusieurs, synchronisés, depuis deux appareils.

### Files et aléatoire
- [P1] Lecture aléatoire d'une série, d'une collection ou d'un filtre (« 3 Silly Symphonies au hasard »).
- [P1] File d'attente modifiable (ajouter après celui-ci, à la fin).

## 6. Recherche

- [P0] Recherche globale **instantanée** dès la première lettre, tolérante aux fautes et aux accents (« amelie » trouve « Amélie »).
- [P0] Clavier à l'écran optimisé pour la télécommande, plus la **recherche vocale** d'Android TV.
- [P0] Résultats groupés : films, séries, épisodes, personnes, collections.
- [P1] Recherches récentes et suggestions.
- [P1] **« Pas dans la bibliothèque ? »** : résultats Seerr en bas de liste, et bouton « Demander » (Seerr, s'il est configuré).

## 7. Plusieurs appareils

- [P1] **Le téléphone pilote la TV** : depuis l'appli mobile, « Lire sur Salon (Shield) » ou « Lire sur Chromecast ». Cela passe par les sessions Jellyfin (commandes à distance), donc sans Google Cast.
- [P1] Télécommande complète dans l'appli mobile : lecture, pause, recherche, pistes, volume de l'ampli (option, pour un ampli pilotable par le réseau).
- [P1] Reprise d'un appareil à l'autre : position synchronisée, « Reprendre sur ce téléphone ».
- [P2] Gladys : l'intégration Jellyfin existante voit l'appli comme lecteur (déclencheurs `playback_*`, scènes « film lancé → lumières tamisées »).

## 8. Hors ligne (téléphone et tablette)

- [P1] Télécharger un film, un épisode, une saison, « les 3 prochains épisodes non vus ».
- [P1] Qualité au choix : fichier d'origine, ou transcodé par le serveur (1080p 8 Mb/s, 720p 4 Mb/s…).
- [P1] Wi-Fi seulement, emplacement de stockage, quota, suppression automatique une fois vu.
- [P1] Synchronisation des positions et des « vu » au retour du réseau.
- [P2] Passerelle avec une appli Android Auto maison : un téléchargement fait par l'une est visible dans l'autre.

## 9. Gestion depuis l'appli (profil administrateur)

Objectif : **ne jamais avoir à ouvrir l'interface web de Jellyfin**. Toute action se trouve dans le menu
« ⋯ » de l'élément concerné, à **deux appuis au plus** (une plainte réelle contre Plex : il faut deux
menus différents pour supprimer).

### Sur un élément
- [P0] Actualiser les métadonnées (« compléter ce qui manque » ou « tout remplacer »).
- [P0] **Corriger l'identification** : recherche TMDb, ou AniDB pour les animes, par titre et année ; résultats avec affiches ; application en un clic.
- [P0] **Changer l'affiche, le fond ou le logo** : galerie des images proposées (TMDb, Fanart) avec langue et résolution, ou envoi d'une image depuis le téléphone.
- [P0] **Supprimer** le film, l'épisode, la saison ou la série, **fichiers compris**, avec une confirmation explicite qui indique le chemin et la taille.
- [P1] Modifier : titre, titre de tri, titre original, année, synopsis, genres, étiquettes, classification, studio. **Verrouiller un champ** pour qu'une actualisation ne l'écrase pas.
- [P1] Séparer ou regrouper des versions (deux fichiers d'un même film).
- [P1] Ajouter à une collection, ou en créer une ; ordonner une collection.
- [P1] Voir le fichier : chemin, taille, pistes détaillées, date d'ajout.

### Sur une bibliothèque
- [P0] Rescanner (rapide) ou tout actualiser ; progression visible.
- [P1] Créer ou supprimer une bibliothèque ; ajouter ou retirer des dossiers ; langue et pays des métadonnées ; aperçus (trickplay) et images de chapitres activés ou non.
- [P1] **Éléments à problème** : non identifiés, sans affiche, doublons, fichiers disparus. Plex, lui, garde des centaines de fiches fantômes sans rien signaler.
- [P1] Actions en série sur une sélection : marquer vu, ajouter à une collection, actualiser.

### Serveur
- [P1] **Utilisateurs** : créer, PIN, bibliothèques autorisées, classification maximale, droit de supprimer, droit de télécharger.
- [P1] Lectures en cours (qui, quoi, direct ou transcodé, charge du NAS), arrêter une lecture, envoyer un message à l'écran.
- [P1] Tâches planifiées : état, dernière exécution, lancer maintenant.
- [P1] Journal d'activité lisible (« Léa a regardé… », « 3 films ajoutés »).
- [P2] Santé : version du serveur, espace disque des bibliothèques, transcodage matériel actif ou non.

## 10. Statistiques (remplace Tautulli)

- [P1] Historique par profil : quoi, quand, sur quel appareil, combien de temps.
- [P1] Totaux : heures regardées par semaine et par mois, séries les plus suivies, appareils les plus utilisés.
- [P2] « Votre année » : bilan annuel par profil.

Ces données viennent du plugin **Playback Reporting**, déjà installé.

## 11. Bibliothèques particulières

- [P0] **YouTube** (archive de chaînes) : épisodes datés, vignettes locales, vitesse de lecture, et aucun écran « série » superflu.
- [P1] **Animes JPN** : métadonnées AniDB, ordre absolu, règle audio VO et sous-titres.
- [P1] **MMA** : spoilers masqués (pas de résumé ni de vignette avant d'avoir vu). Ce réglage par bibliothèque est utile aussi pour le sport.
- **Musique** : hors périmètre (décision du 25/10/2026 : un autre logiciel s'en charge).
- [P2] **TV en direct / IPTV Xtream** : guide des programmes, chaînes favorites.

## 12. Réglages

Les réglages sont nombreux, mais **rangés** : une page par thème, chaque option avec une ligne
d'explication et sa valeur par défaut. On distingue les réglages **du profil**, qui suivent la personne,
et ceux **de l'appareil**, qui dépendent de la TV ou de l'ampli.

| Thème | Exemples d'options |
|---|---|
| Lecture | qualité locale et distante, débit maximal, lecture directe forcée ou non, moteur (ExoPlayer, mpv, auto), sauts avant/arrière, seuil « vu », épisode suivant auto, délai du compte à rebours, « toujours là ? » après N |
| Image | fréquence d'images adaptée, résolution adaptée, HDR et DV autorisés, format par défaut |
| Son | passthrough codec par codec, sortie stéréo forcée, mode nuit, décalage audio, langue préférée, règles par bibliothèque |
| Sous-titres | mode, langue, apparence, taille, fond, position, prise en compte des sous-titres forcés |
| Intro et générique | bouton, saut automatique, rien ; par bibliothèque |
| Accueil | rangées, ordre, bibliothèques incluses, bandeau à la une oui/non, fenêtre « À suivre » |
| Navigation | ordre et visibilité des bibliothèques, vue par défaut, taille des affiches, titres sous les affiches oui/non, indicateur « non vu » |
| Apparence | thème sombre ou noir OLED, couleur d'accent, fond animé ou fixe, animations réduites, taille du texte |
| Profils | PIN, profil au démarrage, restrictions enfant, avatar |
| Réseau | adresse locale et distante, basculement auto, limite de débit sur données mobiles |
| Téléchargements | qualité, Wi-Fi seulement, emplacement, suppression auto |
| Appareil | nom de l'appareil dans Jellyfin, écran de veille, démarrage avec la TV, mise à jour de l'appli (vérifier, installer, revenir en arrière) |
| Avancé | journaux de l'appli, rapport de diagnostic exportable, vider le cache |

## 13. Exigences transverses

- **Vitesse** :
  - démarrage à froid < 2 s sur le Chromecast avec Google TV (l'appareil le plus faible) ;
  - défilement à 60 images/s ;
  - ouverture d'une fiche < 300 ms grâce au cache ;
  - aucune attente bloquante à l'écran : squelettes plutôt que roues qui tournent.
- **Cache local** des métadonnées et des images : l'accueil s'affiche immédiatement, puis se met à jour.
- **Robustesse** : serveur injoignable → message clair et nouvel essai ; jamais d'écran noir muet.
- **Accessibilité** : contrastes AA, focus toujours visible, taille du texte réglable.
- **Qualité** : tests unitaires sur la couche source et la logique de lecture, captures d'écran de référence (tests de rendu), mesures de performance sur l'appareil réel.
- **Distribution TV** : APK installé sur la Shield et le Chromecast (pas de Play Store), puis mise à jour proposée par l'appli elle-même depuis les releases du dépôt.

## 14. Pour plus tard

- Serveur maison en remplacement de Jellyfin, grâce à la couche source, si un jour Jellyfin à son tour impose ses choix.
- Appli iOS / Apple TV : aucun appareil Apple dans la maison à ce jour.
- Liste de souhaits partagée avec Seerr, demandes des enfants à valider.
