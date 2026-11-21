# Layout Debugger sur Paper — version de développement

Cible stricte : **Paper 26.2 build 135, commit e4d6dfd**, Java 25 minimum.
Versions Fabric du projet : Minecraft 26.2, Loader
0.20.1 et Fabric API 0.161.0+26.2. Les essais précédents ont été réalisés sous Java 27 ;
la compilation utilise `--release 25`.

## Installation

Depuis la racine du dépôt :

```powershell
./gradlew.bat -p paper build
```

Installer les deux fichiers produits :

Dans l’archive de test, les dossiers `client/mods/` et `server/plugins/` indiquent
directement leur destination ; l’agent est déjà placé à la racine de `server/`.

- `paper/build/libs/layout-debugger-paper-0.1.0-dev.jar` dans `plugins/`.
- `paper/agent/build/libs/layout-debugger-agent.jar` à côté du JAR Paper,
  **en dehors de `plugins/`**.

Ajouter l’argument avant `-jar` dans la commande de lancement :

```text
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

Le composant fonctionne uniquement au démarrage ; il ne s’attache pas à une JVM
existante. Sans lui, avec une autre build Paper ou des hooks incompatibles, le
plugin refuse d’activer l’éditeur et explique le problème dans le journal.
Un redémarrage complet est nécessaire pour installer ou mettre à jour ces fichiers.

Les utilisateurs de l’éditeur installent le mod et Fabric API côté client.
Les autres joueurs peuvent rejoindre avec un **client vanilla**. Aucun bloc ou
objet n’est ajouté aux registres du serveur Paper. Les outils ON/OFF sont des colorants vert et rouge vanilla portant des composants privés. Le protocole est
optionnel, borné en taille et négocie version et fonctions. Les requêtes sont
validées côté serveur ; la présence du mod ne donne aucun droit supplémentaire.

## Règle du monde partagé

L’utilisation demande le **créatif et `layoutdebugger.use`**. Il n’y a aucun
contrôle OP supplémentaire : un gestionnaire de permissions peut accorder ce nœud
à un joueur non OP. Les OP le reçoivent par défaut. Par exemple, avec LuckPerms :
`/lp user TonPseudo permission set layoutdebugger.use true`.

- **Un seul joueur au début de la capture** : mode précis, pause physique, pages
  exactes et rejeu accéléré masqué, comme en solo.
- **Plusieurs joueurs** : capture et visionnage restent disponibles. Le rejeu vers
  une page se déroule visiblement, à vitesse normale. La page indique les consignes
  appliquées ; les mouvements, entités et circuits continuent ensuite de tourner.
  Pause suspend les consignes, pas la physique du monde. Reset reconstruit le départ,
  sans rembobiner le serveur. Les événements sont toujours programmés en ticks.
- **Un deuxième joueur rejoint** : la capture reste ouverte et passe en mode partagé ;
  le gel et le sprint appartenant à l’éditeur sont arrêtés. Le retour du joueur seul
  ne refait pas geler une capture partagée : terminer et recapturer pour le mode précis.
- Une seule session à la fois. Les autres joueurs peuvent jouer hors de la zone ;
  leurs interactions, placements et destructions y sont refusés pendant la capture.
  Les circuits extérieurs restent capables d’influencer la zone.
- En mode partagé, les alertes utilisent les trajets déjà observés. Elles ne relancent
  pas systématiquement les deux animations pour analyser un simple placement.
- Hors capture, Ouvrir/Fermer garde la lecture en direct depuis l’état réel du monde ;
  Arrêter retire uniquement les signaux du mod.

Les items et les autres entités ne bloquent plus la capture. Ils sont conservés et
ne sont pas rembobinés. Seuls les drops identifiés comme produits par le rejeu sont
retirés à la restauration ; ils ne peuvent pas être ramassés ni fusionner avec les
items déjà présents. Les outils utilisent les mêmes colorants vert et rouge qu’en solo.

**Paramètres → Interface → Langue** bascule entre français et anglais, sans changer
la langue globale de Minecraft. La préférence est enregistrée côté client. Le HUD
réunit les informations de page et du piston visé dans le même panneau.

Seul connecté, le gel et le rejeu utilisent les ticks **du serveur entier**, y
compris les autres dimensions. La capture reste une reconstruction mécanique
régionale : elle ne rembobine pas entièrement les entités, l’aléatoire ou le monde.
Isoler le layout des circuits extérieurs reste nécessaire. Les avertissements
jaunes signalent des trajets observés ; ils ne certifient pas une machine pour
tous les ordres de mises à jour ou scénarios de lag.

Les déplacements utilisent le moteur **Paper ciblé** et ses propres corrections.
Le plugin ne supprime pas toutes les différences entre Paper et vanilla, notamment
pour les machines dépendant de bugs. Il respecte les événements de pistons annulés
par les autres plugins. Un plugin qui modifie encore un événement au niveau
MONITOR peut invalider le suivi ; cette pratique sort du contrat usuel des événements.

## Utilisation et commandes

1. Se connecter avec le client Fabric en créatif avec la permission `layoutdebugger.use`.
2. Appuyer sur **K**, créer une animation et cliquer **Ouvrir**.
3. Près du montage, choisir un rayon qui contient tout le circuit et cliquer
   **Capturer**. Programmer ON/OFF et les deux timelines comme en solo.
   Une page représente un tick redstone, soit deux ticks de jeu.
4. Cliquer **Terminer** pour retrouver la construction initiale modifiée et son
   inventaire ; les timelines restent enregistrées.
5. Hors capture, **Ouvrir/Fermer** lance la lecture en direct. **Arrêter** retire
   le signal du mod. OFF laisse agir la redstone réelle. À plusieurs, la navigation se fait par rejeu visible ; la physique ne se met pas en pause.

Les raccourcis I/J déclenchent l’ouverture/fermeture en direct avec une animation
sélectionnée et le menu masqué. R reste réservé au reset dans une session précise ;
il ne prétend pas rembobiner le monde en multijoueur.

```text
/ld
/ld list
/ld create MaPorte
/ld select MaPorte
/ld rename MaPorte GrandePorte
/ld run GrandePorte opening
/ld run GrandePorte closing
/ld stop
```

Un client vanilla peut rejoindre et consulter `/ld list` avec les droits requis.
L’éditeur demande le client compatible. Les commandes documentaires autorisées
(list, whoami, export, import et administration selon les droits) restent accessibles
sans mod ; l’import ne place aucun bloc.

Les documents sont enregistrés dans `plugins/LayoutDebugger/sequences.json`.
Un fichier illisible est conservé et empêche l’activation du moteur. Les sauvegardes
d’inventaire sont dans le monde, sous `data/layout-debugger/inventory-backups` ;
elles sont conservées si la restitution durable échoue. La capture des blocs en
mémoire ne remplace pas une sauvegarde du monde après un arrêt brutal.

## Architecture

Les documents, le stockage, le contrôleur, la capture et le lecteur sont compilés
à partir des **mêmes sources** que Fabric. Les adaptateurs Paper remplacent l’entrée,
le transport et les outils. Les accès natifs ont une voie Fabric par accessors et
une voie Paper par réflexion, pour la version ciblée.

Les événements Bukkit couvrent les interactions et les déplacements acceptés.
Le composant de démarrage fournit les hooks manquants : puissance additive,
frontières des ticks, masquage des paquets du rejeu, portée des modifications de
construction et fin du sprint. Il ne se contente pas de changer `extended`.
Son ASM est relocalisé pour éviter de remplacer celui du serveur. Les hooks sont
inactifs avant l’activation du plugin et après sa désactivation.

## Vérifications et limites de publication

Le build Paper vérifie le flot de pile des huit méthodes transformées dans les
sept classes du JAR natif réel. Les contrôles du modèle partagé couvrent le codec,
ses limites et les messages invalides. Le plugin `integrationTestJar` est un
**artefact de test séparé**, exclu des fichiers à distribuer.

Les essais locaux réalisés et les points restant à vérifier sont consignés dans
[VERIFICATION.md](VERIFICATION.md). Les tests se font dans un monde jetable, avec
un serveur limité à `127.0.0.1:25577` ; ils ne configurent aucun serveur de production.

Le test client opt-in utilise `LAYOUT_DEBUGGER_PAPER_TEST_ADDRESS=127.0.0.1:25577`.
`LAYOUT_DEBUGGER_PAPER_ONLY=true` saute les scénarios solo déjà vérifiés.
`LAYOUT_DEBUGGER_PAPER_MULTIPLAYER=true`, avec `tools/verify-paper-vanilla.py`, ajoute
un client officiel sans Fabric et teste le passage en capture partagée, les pages
à deux et la restitution de l’inventaire. Ce script n’utilise aucun compte ni
jeton du launcher ; le mode hors ligne ne sert que sur le serveur de boucle locale.
Ne jamais installer le plugin de test sur un monde de jeu.

## Catalogues privés et export

Les animations sont désormais attribuées au UUID de leur créateur (stockage v4,
pont Paper v3). Les anciennes animations restent conservées et nécessitent une
attribution explicite. Voir [le guide des propriétaires, exports et migrations](../docs/OWNERSHIP-EXPORT.md).
