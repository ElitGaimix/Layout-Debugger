# Vérification du portage Paper — 21 novembre 2026

Version de développement ciblant **Minecraft 26.2 / Paper build 135, e4d6dfd**.
Compilation pour Java 25, essais locaux sous le JDK 27 installé. Fabric conserve
Loader 0.20.1 et Fabric API 0.161.0+26.2. Aucune publication externe réalisée.

## Vérifications réalisées

- Compilation du mod, du plugin et du composant de démarrage.
- **142 contrôles du modèle et du stockage**, dont le protocole borné, les
  messages malformés, l’UTF-8, l’ordre des actions et les versions des documents.
  Le codec est aussi exercé avec 10 000 messages aléatoires déterministes.
- **Deux GameTests serveur Fabric**, puis les scénarios client natifs existants :
  interface, zoom, réglage des touches, bibliothèque, ouverture/fermeture,
  construction, avertissements, rejeu masqué et restitution d’inventaire.
- **Sept classes / huit méthodes Paper transformées** : vérification du flot
  de pile sur les classes du JAR natif réel, et pont inactif sans backend installé.
- **11 vérifications natives Paper** : alimentation virtuelle ON/OFF, alimentation
  réelle après OFF, piston transporté par un autre puis activé à sa nouvelle
  position, suivi lors de la rétraction collante, restauration de la capture.
- Connexion d’un client Fabric au vrai serveur Paper, négociation du protocole,
  création, sélection et renommage d’animations, actions retrouvées après
  redémarrage, capture, pages et lecture des deux timelines.
- Clics réels avec les objets ON/OFF de Paper, reconnaissance côté client,
  répétition du clic pour effacer une action, puis reprogrammation.
- Restitution d’un inventaire non vide contenant une pile nommée, avec contrôle
  du contenu, des composants et de la case sélectionnée. Lecture en direct et
  arrêt par la touche de lecture hors du menu.
- Connexion d’un **client Minecraft officiel sans Fabric ni mod** pendant une capture :
  la session reste ouverte en mode partagé, sans pause du serveur. Sauts de page,
  édition d’actions, fin de capture et nouvelle capture sont vérifiés à deux.
- Le joueur de test est **non OP**, avec une permission `layoutdebugger.use` accordée
  par une attachment Bukkit. Il utilise l’éditeur, les outils et les pages.
- Bascule français → anglais → français par les boutons des paramètres, reconstruction
  des écrans parents et contrôles du catalogue, y compris les messages imbriqués.
- Capture avec un item déjà présent, préservation lors des resets et suppression des
  seuls drops du rejeu. Les tests solo natifs couvrent aussi cette distinction.
- Démarrage Paper sans `-javaagent` : activation de Layout Debugger refusée avec
  un diagnostic, tandis que le serveur poursuit son démarrage.
- Revue des permissions, du propriétaire des sessions et des chemins de sortie.
  Une perte des droits, un changement de mode/dimension ou une déconnexion ferme
  la session exclusive ; les fichiers de récupération ne sont pas supprimés si
  leur restitution durable échoue.
- Inspection des JAR : absence de classes GameTest et du plugin de test,
  absence de runtime Fabric dans le plugin, ASM de l’agent relocalisé et notice
  BSD incluse. L’archive de distribution inclut les sommes SHA-256 et aucun JAR
  Minecraft, Paper ou Fabric API.

Les essais réseau utilisent uniquement un monde jetable sur `127.0.0.1:25577`,
en mode hors ligne pour des identités de test. Cela ne valide pas une configuration
d’authentification ou de proxy destinée à un serveur public.

## Vérification supplémentaire : propriétaires et exports

- Schéma 4 : deux propriétaires portant le même nom d'animation, UUID préservé
  par les transformations, migration des anciens documents non attribués,
  sauvegarde initiale non écrasée.
- Export/import : deux timelines conservées, positions et alertes relatives,
  nouveau nom/ancre/dimension et propriétaire dérivé de l'importateur.
  Validation UTF-8, taille maximale, chemins et dossier privé d'import.
- Téléchargement : ticket demandé, fragments bornés, buffers immuables,
  refus des transferts non sollicités, des fragments désordonnés et des écrasements.
- Dans Minecraft solo et sur Paper : catalogue privé, sélection réseau d'une
  animation étrangère refusée, export local depuis le bouton de la bibliothèque.
- Sur le vrai serveur Paper : joueur non OP autorisé à utiliser le système mais
  sans accès admin ; inspection avec droit explicite et attribution refusée avec
  le seul droit de lecture. Commandes `admin owners`, `list legacy`, `show` et
  `assign` exécutées depuis la console ; les deux timelines subsistent après attribution.
- Reprise complète du parcours Paper, y compris le second client vanilla,
  capture partagée, pages, lecture et inventaire, avec le protocole 3.

Les imports sont couverts par les contrôles du modèle et du système de fichiers.
Le placement manuel de la construction et le parcours d'import sur un serveur
public restent à valider par l'opérateur. Les tests réseau locaux n'utilisent
pas deux comptes Mojang authentifiés ni un gestionnaire de permissions externe.

## Ce qu’il reste à tester avant une version stable

- De grands layouts réels : limites de capture, mémoire, longues timelines,
  comportement des circuits autour de la zone et confort d’utilisation prolongé.
- Le serveur de destination avec ses autres plugins, protections et éventuelles
  modifications du comportement des pistons. Folia et les forks ne sont pas
  pris en charge par cette build.
- Exécution sous Java 25 lui-même ; la compilation `--release 25` vérifie les API
  et le format des classes, mais les essais précédents ont été réalisés sous Java 27.
- Connexions authentifiées, proxies, latence réseau réelle et reconnexions dans
  la configuration de déploiement choisie. Les vérifications locales ne prouvent
  pas un comportement identique pour tous les scénarios de lag ou d’updates.
- Arrêts brutaux, erreurs de disque et restauration manuelle sur une copie du
  monde du serveur visé. Le chemin de sauvegarde de l’inventaire est partagé avec
  Fabric et vérifie la sauvegarde du joueur avant suppression de sa copie ; une
  capture régionale en mémoire n’est pas une sauvegarde complète du monde.
- Serveur Fabric dédié : le moteur commun autorise le joueur seul et surveille
  la perte d’exclusivité, mais les essais réseau réalisés ici concernent Paper.
- Exécution du workflow GitHub Actions : il a été étendu à Paper, mais aucune CI
  distante n’a été déclenchée depuis cet espace de travail.

## Procédure manuelle conseillée

1. Sur une copie du serveur, installer le plugin et démarrer avec l’agent comme
   indiqué dans `README-PAPER.md` dans l’archive, ou `paper/README.md` dans le dépôt.
2. Avec un client Fabric créatif et opérateur, saisir `/ld create TestPorte`,
   puis `/ld select TestPorte`, et ouvrir **K → Ouvrir → Capturer**.
3. Programmer ON dans l’ouverture et OFF dans la fermeture, consulter les pages,
   puis cliquer Terminer et vérifier inventaire et montage.
4. Exécuter `/ld run TestPorte opening`, attendre la fin du mouvement, puis
   `/ld run TestPorte closing`. `/ld stop` retire les signaux du mod.
5. Recommencer une capture et connecter un joueur vanilla : la capture doit rester
   ouverte, afficher le mode partagé et libérer la pause introduite par l’éditeur.
   Naviguer dans les pages : le rejeu est visible et le monde continue de tourner.
6. Terminer puis recapturer à deux. Vérifier les outils et la restitution d’inventaire.
   Une fois seul, terminer puis recapturer pour retrouver le mode précis.
7. Tester Paramètres → Interface → Langue en français et anglais, puis redémarrer
   le client pour vérifier la préférence. Donner la permission à un joueur non OP.

Ces résultats permettent de distribuer une **version de test limitée à cette
build Paper** ; ils ne constituent pas une certification de toutes les machines
redstone ni une promesse de rembobinage intégral de Minecraft.
