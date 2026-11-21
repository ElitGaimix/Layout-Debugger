# Dossier de préparation Modrinth

Les textes destinés au public sont en anglais et utilisent le vocabulaire redstone. Ce dossier ne publie rien automatiquement.

## Champs à remplir

1. Nom : **Layout Debugger**.
2. Summary : copier `SUMMARY.txt`.
3. Description : copier `DESCRIPTION.md`.
4. Icône : utiliser `assets/icon.png` (icône existante du mod).
5. Liens, environnements et réglages : consulter `PROJECT-SETTINGS.json`. C’est une fiche de saisie, pas un fichier à envoyer à l’API.
6. Licence : **MIT**. Le texte complet est dans `licensing/LICENSE`, avec une explication dans `licensing/LICENSING.md`.

## Version Fabric à publier

- Nom : **Layout Debugger v1 — Minecraft 26.2 (MIT revision)**.
- Version Modrinth proposée : **1.0.1+mc26.2** (le JAR reste en version interne 1.0.1).
- Canal : **Alpha**.
- Minecraft : **26.2**, si cette version est proposée dans le formulaire.
- Loader : **Fabric**.
- Dépendance obligatoire : **Fabric API**.
- Fichier principal : **`files/layout-debugger-1.0.1.jar`**.
- Changelog : copier `CHANGELOG.md`.
- Le même JAR sert au client et au serveur Fabric : une seule version suffit.

Ne pas téléverser les ZIP client/serveur GitHub comme fichiers supplémentaires de cette version Fabric. Modrinth réserve les fichiers supplémentaires à des usages particuliers et demande des versions séparées pour les loaders différents. Le texte public renvoie vers GitHub pour Paper, son agent et ses scripts de lancement.

## Licence et vérification

`files/SHA256SUMS.txt` permet de vérifier le JAR. Il correspond à la nouvelle révision MIT publiée sur GitHub ; sa licence embarquée et son descripteur Fabric sont vérifiés. Les archives et sources privées restent dans `out/`, hors Git.

La licence MIT choisie oblige à conserver le copyright et la licence dans les redistributions de code ; elle n’impose pas un crédit visible dans le jeu. L’ancienne release CC0 reste sous ses conditions d’origine : les droits déjà accordés ne sont pas retirés. Lire `licensing/LICENSING.md`.

## Images de galerie

L’icône existante est incluse. Aucune capture de gameplay n’est inventée. Ajouter de vraies captures de la timeline et d’un layout en jeu dans la galerie Modrinth si souhaité.

## Références

- [Règles des fichiers supplémentaires Modrinth](https://support.modrinth.com/en/articles/8793363-additional-files)
- [Release GitHub](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit)
