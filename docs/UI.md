# Ciné Doury — règles d'interface

Ce que doit être l'interface et comment la juger. Rédigé le 24/10/2026 ; **fait foi** pour toutes les
sessions de code. La référence de qualité est Plex à son meilleur (2019-2024) : un salon de cinéma,
pas un tableau de bord. Les fonctionnalités sont dans `FONCTIONNALITES.md`.

## 1. Principes

1. **L'image d'abord.** Affiches, fonds et logos font l'interface ; le texte et les commandes s'effacent devant eux. Pas de cadres, pas de séparateurs inutiles, pas de chrome.
2. **Rien ne fait attendre.** Chaque écran s'affiche d'abord depuis le cache, puis se met à jour sans saut. Pas de roue qui tourne au premier plan : des squelettes à la forme exacte du contenu. Une action répond visuellement en moins de 100 ms, même si le serveur met plus longtemps.
3. **Le focus ne se perd jamais.** On sait toujours où l'on est, et « Retour » ramène exactement d'où l'on vient : même rangée, même affiche, même position de défilement.
4. **Deux appuis au plus** pour toute action fréquente depuis l'écran où elle a du sens : lire, reprendre, changer de sous-titres, marquer vu, supprimer, corriger une affiche.
5. **Une chose par écran.** Un écran a une action principale évidente (le bouton qui a le focus en arrivant), et le reste est secondaire.
6. **Calme.** Une seule couleur d'accent, utilisée avec parcimonie : focus, progression, bouton principal. Aucune animation décorative qui retarde une saisie.
7. **Pas de surprise.** Aucun contenu extérieur à la bibliothèque, aucune pub, aucune fenêtre « nouveautés » imposée. Une mise à jour change l'interface seulement si l'utilisateur l'a voulu.

## 2. Écran TV (10 pieds) — règles de base

- **Zone sûre** : marges de 48 dp à gauche et à droite, 27 dp en haut et en bas (5 % de l'écran). Seuls les fonds plein écran débordent.
- **Grille de 8 dp** pour tous les espacements (8, 16, 24, 32, 48, 64).
- **Navigation à la croix directionnelle** :
  - chaque élément focalisable a un voisin évident dans les quatre directions ;
  - aucun piège à focus ;
  - aucun élément accessible seulement au toucher ;
  - un appui long sur OK ouvre le menu contextuel de l'élément.
- **Mémoire du focus** par rangée et par écran. Une rangée qu'on retrouve redonne le focus au dernier élément choisi, pas au premier.
- **Retour** : remonte d'un niveau. Sur l'accueil, un premier appui ramène en haut de page, un second ouvre le menu latéral, et rien ne quitte l'appli sans confirmation.
- **Rail latéral** : fermé, il ne montre que les icônes (72 dp de large) ; avec le focus, il s'ouvre en recouvrant le contenu avec les libellés (240 dp). Plex vient de rétablir ce menu à gauche sous la pression de ses utilisateurs : c'est le bon modèle.
  - Chaque bibliothèque a **son icône** *(retouche du 25/10/2026)* : films, séries, animation, animes, documentaires, spectacles, sport, vidéos en ligne, famille et enfants, collections, dossier. Elle est choisie d'après le type et des mots du nom (« Animes JPN », « Dessins animés », « Documentaires », « MMA », « YouTube »), et se change dans les réglages (« Bibliothèques »).

## 3. Le focus — ce qui doit être parfait

- L'élément focalisé :
  - **grandit** de 1,08 (affiches) ou 1,05 (boutons, vignettes) ;
  - prend un **contour de 3 dp** dans la couleur « focus » (blanc à 90 %), écarté de 2 dp du bord ;
  - s'entoure d'un **halo** : lueur claire et diffuse (jeton `halo de focus`, blanc à 40 %, flou de 14 dp) qui part du contour, dans les deux thèmes. L'ombre ne se voit pas sur un noir OLED ; le halo, si : le focus se repère d'un coup d'œil à 3 m, même sur un fond noir pur *(retouche du 25/10/2026)* ;
  - reçoit une **ombre portée** douce.
- Le focus ne repose **jamais sur la couleur seule** : le contour et l'échelle suffisent à le voir sans percevoir les couleurs.
- Animation : 150 ms en arrivant, 120 ms en partant, courbe de décélération standard. Un défilement rapide (touche maintenue) ne joue pas les animations intermédiaires.
- Sur une affiche focalisée, le **fond de l'écran** passe au fond du titre, en fondu de 300 ms et après 250 ms d'arrêt. On évite ainsi un clignotement en défilant.
- **Fond d'ambiance** *(retouche du 25/10/2026)* : derrière les écrans de navigation (accueil, bibliothèques, fiches), un fond plein écran **très flou**, tiré de l'élément focalisé (son fond, à défaut son affiche), suit le focus selon la même règle (fondu de 300 ms après 250 ms d'arrêt).
  - Il est tiré du **BlurHash** que Jellyfin fournit pour chaque image, décodé en 32 × 18 puis étiré en plein écran avec un filtrage bilinéaire : aucun appel réseau, aucun calcul notable, et pas de `RenderEffect` (API 31, alors que la Shield est en Android 11).
  - Une carte générée (sans image) donne son dégradé.
  - Il est **assombri** par une couche `fond` à 60 % (jeton `assombrissement d'ambiance`), puis recouvert des voiles de lisibilité du § 4. À 60 %, le `texte` garde 4,6:1 au-dessus de l'image la plus claire possible (blanc pur) ; le `texte-2` et les petits textes se posent sur les voiles.
  - Composant `AmbientBackdrop` du design system. Une option « fond fixe » (sans ambiance) est prévue dans les réglages (J7).
- Les rangées défilent pour **garder le focus à une position fixe** : deuxième colonne pour les affiches, le plus à gauche possible pour les vignettes. C'est le contenu qui bouge, pas le focus qui court.

## 4. Couleurs

Thème sombre seulement, sur la TV comme ailleurs. Variante **noir OLED** en réglage (pour les TV OLED).

| Jeton | Valeur | Usage |
|---|---|---|
| `fond` | `#0B0C0F` | fond d'application (noir OLED : `#000000`) |
| `surface-1` | `#15171C` | cartes, panneaux |
| `surface-2` | `#1E2128` | menus, boîtes de dialogue |
| `surface-3` | `#2A2E37` | survol, éléments désactivés |
| `texte` | `#F2F3F5` | texte principal |
| `texte-2` | `#A9AEB8` | texte secondaire |
| `texte-3` | `#6E7480` | métadonnées discrètes |
| `focus` | `#FFFFFF` à 90 % | contour de focus |
| `halo de focus` | `#FFFFFF` à 40 %, flou de 14 dp | lueur autour de l'élément focalisé (§ 3) |
| `accent` | `#F2C14E` (« jaune projecteur », § 4.1) | bouton principal, progression, sélection |
| `danger` | `#E5484D` | suppression |
| `succès` | `#46A758` | confirmations |

- **Voiles de lisibilité** sur les fonds :
  - dégradé de gauche à droite (fond à 92 % → 0 % sur 60 % de la largeur) ;
  - dégradé du bas vers le haut (fond à 100 % → 0 % sur 45 % de la hauteur).
  - Le texte posé sur une image doit atteindre **4,5:1** de contraste dans le pire cas, et on le vérifie sur des captures réelles.
- **Assombrissement d'ambiance** : couche `fond` à 60 % sur le fond d'ambiance (§ 3), sous les voiles.
- **Teinte d'ambiance** facultative : une couleur extraite du fond (Palette d'Android) colore légèrement `surface-1` sur la fiche, à 6 % au plus.

### 4.1 Couleur d'accent

**Jaune projecteur `#F2C14E`**, choisi le 25/10/2026. C'est délibérément autre chose que l'ambre de
Plex (`#E5A00D`).
- Texte sur l'accent (bouton principal) : `#14120B`.
- Variante pressée : `#D9A93A`.
- Rail de progression : `blanc à 25 %`.

## 5. Typographie

- Police : **Inter**, sous licence libre et embarquée (pas de police système variable d'un appareil à l'autre). Chiffres tabulaires pour les durées et les heures.
- Échelle TV, en sp, pour un écran de 1080p vu à 3 m :

| Rôle | Taille | Graisse | Usage |
|---|---|---|---|
| Affiche | 48 | 700 | titre sans logo sur une fiche |
| Titre 1 | 34 | 600 | titres d'écran |
| Titre 2 | 26 | 600 | titres de rangée |
| Corps | 20 | 400 | synopsis, listes |
| Légende | 16 | 500 | métadonnées, badges |
| Minimum absolu | 14 | 500 | rien de plus petit sur une TV |

- Sur mobile, même échelle divisée par 1,4 environ (Titre 2 à 18, corps à 15).
- **Logo plutôt que texte** : quand le titre a un logo (clearlogo de Fanart ou de TMDb), on l'affiche à sa place, en 360 × 140 dp au plus.
- **Règles du français** : espace insécable avant « : ; ! ? » et dans « 22 h 47 », guillemets « », majuscule seulement en début de libellé (« Ajouts récents », pas « Ajouts Récents »), durées en « 1 h 52 », dates en « 24 oct. 2026 ».

## 6. Images et cartes

- **Affiche** 2:3, coins de 8 dp. Tailles TV : 150 × 225 dp (normal), 120 × 180 (petit), 190 × 285 (grand).
- **Vignette** 16:9, coins de 8 dp, pour « Reprendre », les épisodes et les chapitres : **176 × 99 dp** : une rangée de 1080p en montre quatre entières et le début de la cinquième *(retouche du 25/10/2026 ; 300 × 169 auparavant, trois seulement)*. Le texte reste à la taille Légende (16 sp) sous la vignette ; le titre d'une carte générée y est en Légende aussi.
- **Progression** : barre de 4 dp au bas de la carte, dans la couleur d'accent, sur un rail `blanc à 25 %`.
- **Non vu** : petit triangle dans le coin haut droit, dans la couleur d'accent (comme le `watchedIndicator` de Plex). Pour une série, le nombre d'épisodes non vus dans une pastille.
- **Titres sous les affiches** : désactivés par défaut sur la TV (l'affiche suffit), le titre et l'année s'affichent sous l'élément focalisé seulement. Activables en réglage.
- **Jamais deux fois le titre** *(retouche du 25/10/2026)* : une carte générée (sans image) porte déjà le titre ; sous elle, on n'affiche que la ligne secondaire (année, épisode, « Reprendre à … »). Une carte avec une vraie image garde dessous le titre et la ligne secondaire (au focus pour une affiche, toujours pour une vignette).
- **Chargement d'image** :
  - couleur dominante, ou flou de basse définition (BlurHash, fourni par Jellyfin), puis fondu de 200 ms vers l'image nette (composant `RemoteImage`, Coil 3 ; le BlurHash reste affiché si l'image ne vient pas) ;
  - préchargement des rangées voisines ;
  - images demandées **à la taille affichée**, jamais en pleine définition pour une vignette.
- **Image absente** : carte générée avec le titre en typographie soignée sur un dégradé tiré du nom. Jamais d'icône « image cassée ».

## 7. Mouvement

| Situation | Durée | Courbe |
|---|---|---|
| Focus entrant / sortant | 150 / 120 ms | décélération / accélération standard |
| Changement de fond | 300 ms (après 250 ms d'arrêt) | fondu enchaîné |
| Ouverture d'une fiche | 250 ms | élément partagé : l'affiche vole vers sa place |
| Panneau, menu | 200 ms | glissement + fondu |
| Interface du lecteur (OSD) | 200 ms | fondu |

- **Animations réduites** en réglage, et automatiquement si le système le demande : fondus seulement.
- Aucune animation ne bloque la saisie : une touche pendant une transition l'achève instantanément et s'applique.

## 8. Écrans clés

### Accueil
- Fond d'ambiance du § 3 derrière tout l'écran ; il en va de même pour les bibliothèques et les fiches.
- Bandeau à la une sur 60 % de la hauteur. Le fond est le **film focalisé**, pas un carrousel qui défile seul. On peut le remplacer en réglage par un fond animé lent (Ken Burns à 3 % de zoom en 20 s), désactivé par défaut.
- **Raccord** *(retouche du 25/10/2026)* : le fond du bandeau se fond dans le fond d'ambiance par la transparence, à gauche et en bas, sur une courbe douce : aucune arête ne marque l'endroit où il s'arrête, dans les deux thèmes.
- Rangées en dessous ; titre de rangée en Titre 2, à 24 dp au-dessus des cartes.

### Fiche
- Fond plein écran, voiles des § 4. Colonne gauche de 45 % : logo, ligne de métadonnées (« 2014 · 2 h 49 · 12 ans · se termine à 23 h 36 »), badges techniques en Légende sur `surface-2`, synopsis sur 3 lignes, dépliable.
- **Focus initial sur « Lire » ou « Reprendre »**. Les boutons secondaires sont ronds, en icône seule, et leur libellé apparaît au focus.
- En dessous : distribution, saga, similaires ; pour une série, les saisons et les épisodes.
- Précisions du J3 :
  - l'**affiche** (190 × 285) est à gauche de la colonne de texte : c'est elle qui vole depuis la carte ouverte ; un épisode montre sa vignette (320 × 180), une personne sa photo ;
  - les **boutons** sont sous l'affiche et le texte, sur toute la largeur : « Reprendre — 1 h 09 restantes » et six boutons ronds ne tiennent pas dans la colonne de texte ;
  - la ligne de métadonnées finit par l'heure de fin (« se termine à 22 h 07 ») ; les genres, puis les badges, viennent dessous ; le synopsis tient sur 3 lignes et se déplie par OK (il ne prend le focus que s'il est plus long) ;
  - un titre entamé dit ce qui reste : « Reprendre — 1 h 09 restantes » ; une série dit l'épisode : « Reprendre S01E02 — 24 min restantes », « Lire S01E02 » ;
  - les saisons sont des onglets, avec « Marquer la saison comme vue » ; une série à saison unique n'en a pas : ses épisodes suivent, sous « Épisodes » ;
  - « Audio et sous-titres » ouvre, à droite, un seul panneau : version, audio, sous-titres, chaque piste décrite en clair, et « Lire avec ces choix » en tête.

### Lecteur
- **Rien à l'écran pendant la lecture.** Une touche fait apparaître l'interface, qui disparaît après 4 s sans action.
- En haut à gauche : titre, ou série et « S02 · E05 — titre ». En haut à droite : l'heure et l'heure de fin.
- En bas : barre de progression pleine largeur, temps écoulé et temps restant, rangée de commandes (lecture, chapitres, audio et sous-titres, qualité, réglages).
- **Recherche dans la vidéo** : la vignette d'aperçu (trickplay) apparaît au-dessus de la barre, à 320 dp de large ; l'image en fond reste figée. L'accélération est progressive si on maintient la touche.
- **Passer l'intro** : bouton en bas à droite, dans la couleur d'accent, focalisé d'office et sans masquer la vidéo, qui disparaît seul à la fin du segment.
- **Épisode suivant** : carte en bas à droite pendant le générique (vignette, titre, compte à rebours circulaire). La vidéo réduite reste visible derrière.
- **Audio et sous-titres** : un seul panneau à deux colonnes (audio | sous-titres), en panneau latéral droit, sans quitter l'image. Chaque piste est décrite en clair : « Français — 5.1 — E-AC3 — par défaut ».

### Gestion
- Même langage visuel que le reste : pas de formulaire d'administration gris. Une correction d'affiche est une **galerie d'affiches**, une correction d'identification une **liste de résultats avec affiches et année**.
- **Action destructrice** :
  - boîte de dialogue `surface-2` qui dit précisément ce qui sera supprimé (titre, chemin, taille) ;
  - bouton « Supprimer » en `danger` ;
  - **focus initial sur « Annuler »**.

## 9. Mobile et tablette

- Même identité, adaptée au toucher :
  - barre de navigation en bas (Accueil, Recherche, Bibliothèques, Téléchargements, Profil) ;
  - cibles de 48 dp au minimum ;
  - gestes du lecteur : double appui à gauche ou à droite pour ±10 s, glissement vertical pour la luminosité et le volume, pincer pour remplir l'écran.
- Tablette (Galaxy Tab A9+) : mise en page à deux volets quand la largeur dépasse 840 dp (liste et fiche côte à côte).
- Télécommande d'une TV : écran dédié avec pavé tactile directionnel, barre de lecture, pistes.

## 10. Écrire dans l'interface

- **Vocabulaire constant** :
  - « Reprendre » (jamais « Continuer » ni « Poursuivre ») ;
  - « À suivre » ;
  - « Ajouts récents » ;
  - « Marquer comme vu » ;
  - « Bibliothèque » (pas « médiathèque ») ;
  - « Sous-titres ».
- Messages d'erreur en deux phrases : ce qui s'est passé, ce qu'on peut faire (« Le serveur ne répond pas. Vérifiez que le serveur est allumé, puis réessayez. »). Jamais de code d'erreur seul ; le détail technique va dans « Plus d'infos ».
- Pas de point d'exclamation, pas de familiarité, vouvoiement.

## 11. Anti-modèles (ce qu'on reproche à Plex et à Jellyfin)

- Menus imbriqués et réglages éparpillés en plusieurs endroits.
- Contenus de streaming et « découverte » mêlés à la bibliothèque personnelle.
- Écran d'accueil qui se recompose sous les yeux pendant le chargement, et focus qui saute.
- Plus de 2 s pour lancer une vidéo en lecture directe, ou défilement saccadé des affiches.
- Interface d'administration à l'allure de formulaire technique, avec des termes internes (« bibliothèque virtuelle », « fournisseur de métadonnées »).
- Changements d'interface imposés par une mise à jour.

## 12. Comment on juge le résultat

- Chaque jalon livre des **captures de référence** (tests de rendu, par ex. Roborazzi) pour chaque écran, en 1080p et en 4K, en thème sombre et noir OLED. le mainteneur les valide **avant fusion** : une capture refusée bloque le jalon.
- **Mesures sur l'appareil réel** (Macrobenchmark et Baseline Profiles) : démarrage à froid, images perdues au défilement de l'accueil, temps d'ouverture d'une fiche. Elles se font sur la **Shield**, l'appareil de référence, avec les seuils de `FONCTIONNALITES.md` § 13. Le Chromecast avec Google TV, plus faible, est essayé à la main : prévoir de la marge.
- **Essai en vrai au canapé**, avec la télécommande, avant chaque version : une liste de 20 gestes chronométrés (de l'accueil à la lecture d'un épisode en cours, changer de sous-titres, corriger une affiche…).
- Jeu de composants unique (`design-system`) : les écrans ne définissent ni couleur, ni taille, ni durée en dur. Tout passe par les jetons ci-dessus.
