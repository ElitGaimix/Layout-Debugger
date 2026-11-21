# Layout Debugger

**Programmez et inspectez vos layouts redstone, tick redstone par tick redstone.**

Layout Debugger est un mod Minecraft pour construire des animations de pistons en créatif. Capturez un layout, programmez ses séquences d’ouverture et de fermeture, puis parcourez leur résultat directement dans le monde avec la physique de Minecraft.

[English](README.md) · [Installation](docs/INSTALLATION.fr.md) · [Guide complet](docs/USAGE.fr.md) · [Serveur Paper](paper/README.fr.md)

## Fonctionnalités

- Deux timelines par animation : **ouverture** et **fermeture**, avec ordre des actions configurable.
- Outils **ON / OFF** pour programmer les pistons en jeu et repères colorés pour les identifier.
- Navigation par tick redstone, lecture, pause et retour au début.
- Modification de la construction pendant l’édition et alertes sur les trajets des pistons.
- Bibliothèque d’animations, renommage, export et import JSON avec attribution au créateur.
- Interface en français et en anglais, zoom et raccourcis personnalisables.
- Solo et serveurs Fabric ; plugin compagnon pour une version précise de Paper.

## Compatibilité

| Composant | Version ciblée |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 minimum |
| Fabric Loader | 0.20.1 minimum |
| Fabric API | 0.161.0+26.2 utilisée par le projet |
| Paper, en option | 26.2 build 135 uniquement |

Le mod Fabric porte le numéro **1.0.1** et le plugin Paper **0.1.1**. La distribution actuelle reste une **alpha** ; les limites et essais précédents sont décrits dans le [rapport de vérification](paper/VERIFICATION.md).

## Installation

**Solo ou client :** installez Fabric Loader et Fabric API pour Minecraft 26.2, puis placez `layout-debugger-1.0.1.jar` dans `mods/`.

**Serveur Fabric :** installez également le mod et Fabric API sur le serveur. Les joueurs qui utilisent l’éditeur ont besoin du mod côté client.

**Serveur Paper :** installez le plugin compagnon et son agent Java de démarrage sur **Paper 26.2 build 135**. L’éditeur demande un client Fabric équipé du mod ; les joueurs sans mod peuvent rejoindre le serveur. Suivez le [guide Paper](paper/README.fr.md) pour les fichiers et l’argument `-javaagent` nécessaires. Folia n’est pas pris en charge.

Consultez le [guide d’installation](docs/INSTALLATION.fr.md) pour les permissions et la configuration. En solo, activez les commandes du monde pour l’accès par défaut.

## Premiers pas

1. En créatif, placez-vous près du montage et appuyez sur **K**.
2. Créez une animation, cliquez sur **Ouvrir**, choisissez le rayon puis **Capturer**.
3. Utilisez les outils **ON / OFF** sur les pistons et la timeline pour programmer les actions. Une page représente un tick redstone, soit deux ticks de jeu.
4. Parcourez les pages avec **← / →** et lancez la lecture avec **U**. **R** revient au début de la dernière animation jouée.
5. Cliquez sur **Terminer** pour quitter l’édition et récupérer votre inventaire. La séquence reste sauvegardée.

Les touches se modifient dans les paramètres. Le [guide complet](docs/USAGE.fr.md) détaille le visionnage, les deux timelines, les commandes et les outils.

> Faites une sauvegarde du monde avant les essais. La prévisualisation restaure une région du montage ; elle ne rembobine pas les entités ni les effets extérieurs. À plusieurs joueurs, le rejeu est visible et le monde continue de tourner. OFF retire uniquement le signal du mod : la redstone réelle reste active.

## Documentation

- [Utilisation et commandes](docs/USAGE.fr.md)
- [Installation et permissions](docs/INSTALLATION.fr.md)
- [Propriétaires, export, import et migration](docs/OWNERSHIP-EXPORT.fr.md)
- [Compilation et préparation des distributions](docs/BUILD.fr.md)
- [Scénarios de test en jeu](docs/TEST-EN-JEU.fr.md)
- [Architecture et installation Paper](paper/README.fr.md)

## Compilation

Avec un **JDK 25 minimum** configuré, depuis la racine du projet :

```sh
./gradlew build
./gradlew -p paper build
```

Sous Windows, utilisez `./gradlew.bat`. Le build Fabric inclut les vérifications du modèle et du stockage ; le build Paper vérifie aussi ses hooks natifs. Les JAR se trouvent dans `build/libs/`, `paper/build/libs/` et `paper/agent/build/libs/`.

Pour préparer les archives locales après compilation :

```sh
python tools/package-release.py
```

Le dossier `out/` contient les fichiers de distribution et reste exclu de Git. Les mondes de test, caches et réglages personnels sont également ignorés.

## Signaler un problème

Dans une issue, indiquez les versions de Minecraft, du mod et du serveur, les étapes pour reproduire le problème et les autres mods ou plugins installés. Joignez les extraits de logs utiles après avoir retiré les informations personnelles.

## Licence

Projet d’**ElitGaimix**, distribué sous [MIT](LICENSE). Le composant de démarrage Paper inclut ASM, avec sa [notice de licence](paper/agent/THIRD-PARTY-ASM-LICENSE.txt).

Voir [l’historique des licences](LICENSING.md) pour la première release CC0 et les notices de tiers.
