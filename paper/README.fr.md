# Installer Layout Debugger sur Paper

[English](README.md) · [Installation client](../docs/INSTALLATION.fr.md)

**Utilisez les fichiers déjà compilés de la release : aucune compilation n’est nécessaire.** Cible : **Paper 26.2 build 135, commit e4d6dfd**, Java 25 minimum. Folia n’est pas pris en charge.

## 1. Client

Installez Fabric Loader 0.20.1 minimum pour Minecraft 26.2 et Fabric API pour cette version. Placez `layout-debugger-1.0.1.jar`, téléchargé depuis la release, avec Fabric API dans `mods/`. Lancez le profil Fabric. Les joueurs qui utilisent l’éditeur ont besoin de cette installation ; les joueurs vanilla peuvent rejoindre Paper sans éditeur.

## 2. Serveur Paper

Téléchargez **`layout-debugger-v1.26.2-mit-server-paper.zip`** dans la [release GitHub](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit), puis extrayez-le.

1. Copiez `serveur-paper/plugins/layout-debugger-paper-0.1.1.jar` dans `plugins/` sur votre serveur.
2. Copiez `serveur-paper/layout-debugger-agent.jar` à la racine du serveur, hors de `plugins/`.
3. Téléchargez Paper séparément. Avec le JAR nommé `paper-26.2-135.jar`, lancez :

```sh
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

Adaptez le nom après `-jar` si nécessaire. Des scripts `.bat` et `.sh` sont fournis comme exemples. Suivez les étapes normales du premier démarrage et de l’EULA Paper, puis redémarrez complètement ; pas de `/reload`. Sans le bon agent et la bonne build Paper, l’éditeur ne s’active pas.

## 3. Permissions et utilisation

Créatif et permission `layoutdebugger.use`. OP la reçoit par défaut ; avec LuckPerms :

```text
/lp user TonPseudo permission set layoutdebugger.use true
```

Appuyez sur **K**, créez une animation, ouvrez-la puis capturez votre layout. Programmez ON/OFF dans Ouverture et Fermeture. Une page vaut un tick redstone. **U** lit/met en pause, **R** remet au départ, **← / →** parcourent les pages. Terminer rend votre inventaire. Les touches et la langue sont configurables.

Mettez à jour le client, le plugin et l’agent ensemble. Sauvegardez le monde et `plugins/LayoutDebugger/sequences.json`. Les sauvegardes d’inventaire sont sous `<monde>/data/layout-debugger/inventory-backups/`.

Cette alpha restaure une région et non le monde entier. À plusieurs, le rejeu est visible et la physique continue. OFF retire seulement le signal du mod ; la redstone réelle reste active. Testez vos layouts et les autres plugins sur une copie du serveur.

[Guide d’utilisation français](../docs/USAGE.fr.md) · [Compilation, pour les développeurs uniquement](../docs/BUILD.fr.md) · [Licence MIT](../LICENSE)
