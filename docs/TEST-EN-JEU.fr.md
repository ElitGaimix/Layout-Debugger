# Procédure de test en jeu

Utiliser un monde sauvegardé, de préférence une copie. Ce guide complète le rapport
des contrôles déjà réalisés ; il ne prétend pas certifier toutes les constructions.
Pour Paper, installer les trois nouveaux JAR ensemble et redémarrer complètement.

## Création et édition

1. Créatif, avec `layoutdebugger.use` sur Paper (ou accès Fabric décrit dans INSTALLATION).
   Un administrateur peut tester OP, mais vérifier aussi un compte non OP avec le
   seul droit d'utilisation si un gestionnaire de permissions est installé.
2. Construire un piston collant face à un bloc de pierre, puis `/ld create PorteTest`.
   K ouvre la bibliothèque ; sélectionner PorteTest, Ouvrir, Capturer.
3. Page 0 : outil vert sur le piston. Cliquer à nouveau doit retirer l'action.
   Recliquer pour la remettre. Page 2 : outil rouge. Tester Lecture puis Reset.
4. Tester la timeline Fermeture, la construction pendant la capture, la visibilité
   du piston dans le HUD, les touches et les réglages de langue/échelle.
5. Terminer : l'inventaire initial doit être rendu. Une lecture et un saut de page
   doivent rester possibles dans Visionner. Sur Paper, `/ld run PorteTest opening`,
   puis `/ld stop`, lance/arrête une lecture en direct depuis le monde réel.

## Propriétaires

1. Rejoindre avec un second compte autorisé : `/ld list` ne doit pas montrer PorteTest.
2. `/ld create PorteTest` doit créer son propre document, indépendant du premier.
3. Sur Paper, `/ld select AutreNomPrive` et `/ld run AutreNomPrive opening` doivent
   refuser un nom qui n'existe que chez l'autre joueur.
4. Seul un administrateur avec le droit de lecture peut inspecter via
   `/ld admin owners`, puis `/ld admin show UUID PorteTest`.
   Donner seulement `admin.view` ne doit pas permettre `admin assign`.
   Refuser explicitement ces droits sur un compte OP si nécessaire pour ce test,
   car OP les reçoit par défaut.

## Export et import

1. Finir la session, K → sélectionner → Exporter. Vérifier le fichier dans
   `<instance Minecraft>/layout-debugger/exports/` et le message du chat.
2. Vérifier les deux timelines dans le JSON. Aucune construction ni inventaire
   ne doit y figurer. `/ld export PorteTest` produit seulement la copie serveur.
3. `/ld whoami`, puis déposer le fichier dans `imports/UUID/` du dossier de données
   serveur. Se placer à la position destinée au premier piston et exécuter :
   `/ld import PorteTest-XXXXXXXX.ldanim.json PorteImportee` (utiliser le vrai nom).
4. La copie doit apparaître dans son catalogue ; aucun bloc ne doit être placé.
   Reproduire séparément les blocs et orientations, puis capturer/lire la copie.
   Tester un autre nom pour éviter de remplacer un document existant.

## Anciennes animations

Après sauvegarde, `/ld admin list legacy`, `/ld whoami`, puis
`/ld admin assign AncienNom UUID`. Vérifier le catalogue du joueur et ses deux
timelines. Le fichier `sequences.json.before-ownership-v4.bak` doit conserver
l'ancien document. Un nom déjà présent chez le destinataire doit être refusé.

## Partage et limites

En capture, faire rejoindre un joueur vanilla : la session doit rester ouverte,
passer en mode partagé et laisser le monde tourner. Les sauts de page deviennent
visibles et la pause ne bloque plus la physique. Terminer, faire partir l'autre
joueur et recapturer pour revenir au mode précis. Tester les grands layouts et
les protections des autres plugins avant de déployer sur un serveur public.
