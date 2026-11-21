# Installation — Layout Debugger 26.2

Cette distribution est une **version de développement / alpha**, pas une garantie
pour tous les layouts techniques. Les numéros des JAR sont conservés : mod 1.0.0,
plugin 0.1.0-dev. Le pont réseau est en version **3** : mettre à jour le mod client,
le plugin et l'agent ensemble, même si leurs noms de fichiers sont inchangés.

| Usage | Dossier à utiliser | Installation |
|---|---|---|
| Solo | `client/mods/` | Fabric + Fabric API + mod dans l'instance Minecraft |
| Joueur d'un serveur Paper équipé | `client/mods/` | Même installation client ; pas de plugin ni agent sur son PC |
| Serveur Paper vanilla+ | `serveur-paper/` | Plugin dans `plugins/`, agent à la racine et argument de lancement |
| Serveur Fabric | `serveur-fabric/mods/` | Mod et Fabric API sur le serveur ; client Fabric pour l'éditeur |

Java **25 minimum**, Minecraft **26.2**, Fabric Loader **0.20.1** et Fabric API
**0.161.0+26.2** correspondent aux versions utilisées par le projet.
Les essais automatisés tournent sous Java 27, avec compilation `--release 25`.
Installer séparément Fabric et Fabric API ; leurs JAR ne sont pas redistribués ici.

## Paper

Cible stricte : **Paper 26.2 build 135, commit e4d6dfd**. Ne pas choisir une autre
build ou un fork en supposant que les hooks natifs seront identiques. Folia non pris
en charge. Télécharger le serveur officiel séparément ; l'archive n'inclut pas
Minecraft, Paper, des mondes de test ni un accord EULA pour votre propre serveur.

Copier `serveur-paper/` dans un dossier serveur, ajouter le JAR Paper officiel sous
le nom `paper-26.2-135.jar`, puis utiliser `start-paper-example.bat`, ou :

```text
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

L'agent est **en dehors de plugins/**. Un redémarrage complet est obligatoire ;
pas de `/reload`. Sans le bon agent/build, le plugin refuse d'activer l'éditeur.
Le composant est nécessaire pour l'alimentation virtuelle des pistons et le rejeu
masqué : un plugin Bukkit seul ne donne pas ces points d'interception natifs.
Les données, commandes, permissions et événements passent par le plugin Paper.

Un client vanilla peut rejoindre sans Fabric : aucun bloc/objet de registre moddé
n'est ajouté sur Paper. Il peut jouer normalement, et les commandes documentaires
permises restent disponibles ; il ne dispose pas du menu ni des outils du mod.
Installer seulement le mod client sur un serveur vanilla/Paper non équipé ne
crée pas de backend et ne permet pas d'utiliser l'éditeur.

## Accès

Créatif et permission Paper **`layoutdebugger.use`**. OP la reçoit par défaut,
mais OP n'est pas requis. Exemple si LuckPerms est installé :

```text
/lp user TonPseudo permission set layoutdebugger.use true
/lp user Admin permission set layoutdebugger.admin.view true
/lp user Admin permission set layoutdebugger.admin.assign true
```

Sur Fabric : `layout-debugger:use`, API de permissions Fabric, niveau gamemaster en
repli. Les permissions d'administration sont décrites dans OWNERSHIP-EXPORT.md.
En solo, activer les commandes du monde pour l'accès par défaut.

## Utilisation rapide

1. Faire une copie du monde avant d'essayer un grand circuit.
2. Passer en créatif, **K → créer une animation → Ouvrir → Capturer**.
3. Les outils vert/rouge programment ON/OFF à la page actuelle. Répéter la même
   action sur le même piston à cette page l'efface. Une page vaut un tick redstone
   (deux ticks de jeu). OFF retire seulement l'alimentation du mod.
4. Modifier les blocs pendant la capture affecte la construction commune aux deux
   timelines. Ouverture/Fermeture ont des actions indépendantes.
5. Utiliser les touches reconfigurables et les paramètres FR/EN/échelle du menu.
   En lecture : I ouvre, J ferme, U lit/met en pause, R revient en pause au début
   de la dernière animation. Les flèches parcourent les pages.
6. Terminer rend l'inventaire sauvegardé. Visionner ouvre une session de lecture
   avec pages ; sur Paper, Ouvrir/Fermer hors capture peut aussi agir en direct.
7. Exporter depuis la bibliothèque ; voir OWNERSHIP-EXPORT.md pour importer et
   récupérer les animations antérieures à cette mise à jour.

## Limites de simulation

Seul au début d'une capture : pause physique globale, étapes précises et rejeu
accéléré masqué. Un deuxième joueur fait passer la capture en mode partagé sans
l'interrompre : les pages se rejouent à vitesse normale et le monde continue.
Le mode partagé reste actif jusqu'à la fin, même si l'autre joueur repart.
Pause arrête les consignes ; elle ne fige pas le monde partagé. Une seule session
est réservée à la fois. Les interactions des autres joueurs sont empêchées dans
la zone, mais les circuits extérieurs peuvent toujours l'influencer.

Les alertes jaunes sont consultatives, basées sur les trajets de pistons observés.
Elles ne certifient pas tous les ordres d'updates, ni toutes les conditions de lag.
En mode partagé, l'analyse automatique complète est réduite. Les déplacements
restent ceux du moteur Minecraft, pas un simple changement de `extended`.
Les entités/items préexistants restent autorisés et ne sont pas rembobinés.
Les drops propres au rejeu sont suivis séparément pour éviter leur duplication.
Restaurer un snapshot régional ne rembobine pas la simulation entière, les entités,
le temps global ou les systèmes situés hors de la zone.
