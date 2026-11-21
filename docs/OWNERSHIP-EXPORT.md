# Animations privées et échanges

Chaque document possède le UUID Minecraft de son créateur. Les menus, suggestions,
commandes ordinaires, captures et lectures utilisent uniquement le catalogue de ce
UUID. Deux joueurs peuvent utiliser le même nom. Les changements de pseudo ne
changent pas le propriétaire. Une permission d'administration ne transforme pas
le catalogue personnel en catalogue global.

Cette confidentialité concerne les documents. Les constructions restent dans le
monde partagé : ce système ne remplace pas un plugin de claims et n'empêche pas
un joueur d'observer une porte ou de modifier ses blocs hors d'une capture.
Une seule capture/lecture réservée reste disponible à la fois.

## Exporter

Dans **K → Animations**, sélectionner une animation, terminer sa capture/lecture
si nécessaire, puis **Exporter**. Le fichier `.ldanim.json` est téléchargé dans
`<dossier du jeu>/layout-debugger/exports/`. Le chat indique le chemin.
Une copie reste aussi dans le dossier de données serveur `exports/<UUID>/`.
Seul un transfert explicitement demandé est accepté par le client ; aucun fichier
existant n'est remplacé. Limite : 4 Mio et un export par joueur toutes les 10 secondes.

Sans interface : `/ld export MaPorte` écrit uniquement la copie côté serveur.
`/ld whoami` indique son UUID. Ne partager que le fichier `.ldanim.json` voulu,
pas `sequences.json`, qui contient les documents de tous les créateurs.

**Contenu : données uniquement**, pas une schematic et pas les blocs du monde.
Le fichier contient les deux timelines, l'ordre des actions, les références et
orientations des pistons et les préférences d'avertissement. Les coordonnées
sont relatives au premier piston enregistré. L'UUID du créateur d'origine est
retiré ; un import ne peut pas donner les droits d'un autre joueur.
Les snapshots, NBT de coffres, inventaires, entités et sauvegardes de récupération
ne sont pas exportés. Il faut transférer la construction séparément si nécessaire.

## Importer

Le gestionnaire du serveur dépose le fichier dans le dossier de données
`imports/<UUID du destinataire>/`. Cela évite de permettre à un client d'envoyer
arbitrairement des documents sur le serveur. Créer les dossiers si nécessaire.
En solo, ces dossiers sont dans la sauvegarde du monde.

Se placer **dans le bloc où doit être la base du premier piston**, dans la bonne
dimension, puis exécuter par exemple :

```text
/ld whoami
/ld import MaPorte-a1b2c3d4.ldanim.json MaCopie
```

Le nom doit être libre dans son propre catalogue (1 à 48 caractères : lettres,
chiffres, `_`, `-`). L'import crée un nouveau document attribué à l'exécutant,
avec les positions décalées vers l'ancre actuelle. Il ne place aucun bloc et
ne tourne pas la construction : les orientations sont conservées. La lecture
vérifie les vrais pistons à ces positions. Le format est ciblé Minecraft 26.2.

Dossiers de données :

- Fabric/solo : `<monde>/data/layout-debugger/`.
- Paper : `plugins/LayoutDebugger/`.

## Administration

Paper : `layoutdebugger.admin.view` permet de consulter/exporter, en lecture seule.
`layoutdebugger.admin.assign` permet seulement d'attribuer les anciennes animations.
Les deux permissions sont distinctes de `layoutdebugger.use` ; OP les reçoit par
défaut, mais un gestionnaire peut les accorder/refuser individuellement.
Fabric : nœuds `layout-debugger:admin/view` et `layout-debugger:admin/assign`
via l'API de permissions Fabric, niveau administrateur comme valeur de repli.
Les commandes d'inspection et d'attribution sont aussi accessibles à la console.
L'export administrateur demande un joueur pour déterminer son dossier UUID.

```text
/ld admin owners
/ld admin list <UUID> [page]
/ld admin show <UUID> MaPorte [page]
/ld admin export <UUID> MaPorte
/ld admin list legacy
/ld admin show legacy AnciennePorte
/ld admin assign AnciennePorte <UUID-du-createur>
```

Remplacer les éléments entre chevrons, sans taper les chevrons. `owners`, `list`
et `show` sont paginés (20 lignes). `owners [page]` liste les UUID connus et
éventuellement `legacy`. `show` affiche les coordonnées et les actions des deux
timelines sans agir dans le monde. `admin export` écrit côté serveur, dans le
dossier de l'administrateur ; il ne télécharge rien automatiquement.
Aucune commande d'administration n'autorise à éditer ou lancer le document d'un
autre créateur dans le monde. Pour cela, exporter puis importer une copie personnelle.

## Mise à jour des anciennes données

Le schéma de stockage passe à **4**. Les versions 1 à 3 restent lisibles.
Comme elles ne mémorisaient pas le créateur, les attribuer automatiquement au
premier joueur connecté volerait potentiellement les animations des autres.
Elles restent donc conservées mais invisibles dans les catalogues personnels
jusqu'à une attribution explicite avec `admin assign`.

Avant la première écriture au nouveau format, le fichier initial est copié en
`sequences.json.before-ownership-v4.bak`, sans écraser une sauvegarde existante.
Faire aussi une sauvegarde complète du monde et des données avant installation.
Pour son ancien serveur de test : récupérer son UUID avec `/ld whoami`, lister
`/ld admin list legacy`, puis attribuer chaque ancienne animation. Depuis la
console, omettre `/` en début de commande. Ne pas réinstaller une ancienne
version sur le fichier v4 ; restaurer sa sauvegarde pour revenir en arrière.
Un passage online/offline ou un proxy changeant les UUID nécessite une migration
administrée ; cette version ne propose pas de transfert entre deux propriétaires
ayant déjà un UUID.
