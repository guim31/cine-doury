# Les 20 gestes au canapé

`UI.md` § 12 : avant chaque version, un essai en vrai, avec la télécommande, chronométré. Ces 20
gestes vont de l'accueil à la lecture d'un épisode en cours, en passant par les sous-titres et la
correction d'une affiche. `ci/couch-gestures.sh` les joue sur un appareil et imprime leurs temps ;
on peut aussi les faire à la main, chronomètre en main.

## Les gestes

« Appuis » : touches de la télécommande attendues sur l'appareil de référence, quand la bibliothèque
s'y prête (la rangée « Reprendre » en premier, par exemple). « Cible » : le temps entre le **dernier
appui** et l'écran prêt, ce que ressent la main ; « Total » compte en plus le temps des appuis.

| # | Geste | Départ | Appuis | Cible (réponse) | Fin |
|---|---|---|---|---|---|
| 1 | Démarrage à froid jusqu'à l'accueil | appli fermée | 0 (1 sur « Qui regarde ? ») | 2 s en tout (`FONCTIONNALITES` § 13) | l'accueil dessiné |
| 2 | Accueil → fiche du premier titre à reprendre | « Lire » focalisé | 2 (↓, OK) | 0,3 s | la fiche dessinée |
| 3 | Fiche → lecture (« Reprendre ») | la fiche, « Reprendre » focalisé | 1 | 2 s (`UI.md` § 11) | l'image bouge |
| 4 | Afficher l'interface du lecteur | lecture, interface cachée | 1 (OK) | 0,2 s | l'interface |
| 5 | Pause, puis reprise | interface affichée | 2 (OK, OK) | 0,5 s | l'image bouge de nouveau |
| 6 | Avancer de 30 s | lecture, interface cachée | 1 (→) | 1,5 s | l'image bouge à la nouvelle place |
| 7 | Changer de sous-titres (puis les remettre) | interface affichée | ≈ 6 | 0,5 s | la piste choisie |
| 8 | Quitter le lecteur | lecture | 1 à 2 (Retour) | 0,5 s | la fiche |
| 9 | Fiche → accueil | la fiche | 1 (Retour) | 0,3 s | l'accueil |
| 10 | Lire le titre à la une | « Lire » focalisé | 1 (OK) | 2 s | l'image bouge |
| 11 | Ouvrir la première bibliothèque | l'accueil | 3 (←, ↓, OK) | 0,5 s | la grille |
| 12 | Descendre de cinq rangées dans la grille | la grille | 5 (↓) | 0,3 s par rangée | la carte focalisée |
| 13 | Ouvrir une fiche depuis la grille | une carte | 1 (OK) | 0,3 s (`FONCTIONNALITES` § 13) | la fiche |
| 14 | Marquer comme vu, puis non vu | la fiche | ≈ 6 | 0,3 s (§ 4.10 : moins de 100 ms à l'écran) | le bouton change |
| 15 | Corriger une affiche : ouvrir la galerie (sans rien changer) | la fiche, administrateur | ≈ 8 | 1 s | la galerie d'affiches |
| 16 | Retour à la grille, sur la carte quittée | la fiche | 1 (Retour) | 0,3 s | la même carte focalisée |
| 17 | Rechercher : ouvrir, taper une lettre, voir les résultats | l'accueil | ≈ 4 | 1 s | des résultats |
| 18 | Réglages → page « Sous-titres » | l'accueil | ≈ 8 | 0,5 s | la page |
| 19 | Changer de profil (« Qui regarde ? », même profil) | l'accueil | ≈ 4 | 1 s | l'accueil du profil |
| 20 | « À suivre » : de l'accueil à la lecture de l'épisode | « Lire » focalisé | 4 (↓…, OK, OK) | 2 s | l'image bouge |

Le script ne change rien sur le serveur : ce qu'un geste modifie (vu, sous-titres), il le remet ; la
galerie d'affiches est ouverte puis quittée sans choix. Un geste impossible sur la bibliothèque ou le
profil (rien à reprendre, pas d'« À suivre », profil non administrateur, profil avec code) est
**sauté** et dit pourquoi ; les suivants continuent.

## Lancer le script

Sur un ordinateur avec `adb` et `python3`, la TV en débogage réseau, Ciné Doury **optimisé**
installé et un profil connecté (sans code de profil, ou le geste 19 est sauté) :

```sh
adb connect <adresse de la TV>:5555
adb devices                                  # le numéro de série : <adresse>:5555
ci/couch-gestures.sh <adresse>:5555 --out gestes-shield.md
ci/couch-gestures.sh <adresse>:5555 --only 11,12,13,16   # quelques gestes (ils peuvent dépendre des précédents)
```

Le script active pour la séance le journal de mesure de l'appli
(`adb shell setprop log.tag.CineDoury DEBUG`, remis à la fin) : chaque touche reçue, chaque écran
dessiné, l'image qui bouge. Il attend ces lignes ou, à défaut, ce que montre l'écran
(`uiautomator dump`) ; aucun `sleep` aveugle. Il remet à zéro `dumpsys gfxinfo` au début et en
imprime le résumé à la fin.

Lecture du tableau :
- **Total** : du premier appui à la fin, le temps d'`adb shell input` compris (0,1 à 0,3 s par touche
  sur une TV : un appui à la main est plus rapide) ;
- **Réponse** : du dernier appui reçu par l'appli à la fin : c'est elle qu'on compare à la cible ;
- **≈** : la fin a été vue par `uiautomator dump`, à une seconde près ;
- un geste en échec ramène l'appli à l'accueil et le script continue ; il sort en erreur s'il y en a.

## Résultats

| Date | Appareil | Version | Tableau |
|---|---|---|---|
| 27/10/2026 | émulateur Android TV API 34 de la CI (rendu logiciel, indicatif) | J8 | résumé du job « Emulator » (API 34), artefact `emulator-api34` |
| à faire | NVIDIA Shield TV | 1.0.0 | |
| à faire | Chromecast avec Google TV | 1.0.0 | |
