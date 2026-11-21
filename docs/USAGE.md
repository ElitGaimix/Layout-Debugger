# Guide d’utilisation — Layout Debugger

Éditeur Fabric de séquences de pistons pour Minecraft **26.2**, en créatif, en solo ou avec capture partagée sur serveur.
Les actions sont appliquées sur le serveur logique et les déplacements restent ceux de Minecraft.

## Utilisation en jeu

1. Construire le layout, attendre la fin des mouvements, puis se placer près de son centre.
2. Appuyer sur **K** pour ouvrir **Animations**. Créer une animation ou cliquer
   sur une animation existante, puis **Ouvrir** pour accéder à sa timeline.
3. Choisir le rayon de capture (8 à 31 blocs), puis **Capturer**. La zone est centrée
   sur votre position à cet instant ; sa hauteur est limitée aux bornes du monde.
4. Le monde se met en pause. L'inventaire complet est sauvegardé, puis remplacé
   temporairement par les deux outils. **Échap** ferme l'écran pour revenir au montage,
   sans quitter l'édition.
5. Choisir une page avec **← / →** ou dans la timeline. Faire un clic droit sur la base
   d'un piston avec l'outil **ON** ou **OFF**. Son identifiant est créé
   automatiquement. À la page 0, le premier clic programme la page 1.
   Recliquer avec le même outil sur ce piston à la même page efface son action ;
   utiliser l’autre outil la remplace. Cela fonctionne aussi sur la tête du piston.
6. Rouvrir avec **K** : les lignes représentent les pistons, les colonnes les pages.
   Cliquer dans une cellule pour programmer le mode choisi, clic droit pour effacer.
   L'inspecteur d'ordre permet de permuter ou supprimer les actions d'un même tick.
7. **U** lance ou met en pause la lecture ; **Espace** fait de même dans l'interface.
   **R** revient au début de la dernière animation jouée et met en pause.
   Les raccourcis sont configurables dans **Param. → Touches** et dans les contrôles Minecraft.
8. **Terminer** remet le montage au départ avec vos modifications de construction, l'inventaire d'origine et l'état de pause
   antérieur. Les modifications de la séquence restent sauvegardées. Le bouton
   **Outils** permet de récupérer un outil temporaire manquant.

Pour lire une séquence sans commencer une capture d'édition : **K → sélectionner
la séquence → Ouvrir → Visionner**. Ce mode conserve votre inventaire et interdit les
modifications de la timeline. Les boutons **Lecture/Pause**, **0**, **< / >**,
**Aller** et **Fin** permettent de parcourir la séquence. **Fin** affiche l'état
après les dernières actions et leur stabilisation. **Terminer** quitte ce mode
et remet le montage au départ. Le mode lecture prépare automatiquement un état
initial régional pour permettre le retour au départ ; aucun outil n'est fourni.

L'inventaire sauvegardé comprend les objets et leurs composants, la barre rapide,
l'armure, la main secondaire, la case sélectionnée, la grille de fabrication et
l'objet porté par le curseur. Une sauvegarde de secours est écrite avant le
remplacement dans `<monde>/data/layout-debugger/inventory-backups/`. Elle est
retirée après restauration et vérification de la sauvegarde du joueur. Une
sauvegarde encore présente est récupérée automatiquement à la reconnexion.

Les couleurs sont accompagnées de libellés : vert ON, rouge OFF.
Le HUD montre la page courante et, pour un piston enregistré visé, sa position
courante, son état physique et le signal du mod. Le HUD distingue un piston en
mouvement d'un piston simplement étendu.

Les pistons ayant une action sur la page affichée sont également entourés d'un
volume translucide : vert pour ON et rouge pour OFF.
Le piston reste visible à travers la teinte ; le repère suit les pistons déplacés.

## Gestion des animations

Hors session, **K** ouvre le gestionnaire **Animations**. Pendant une capture ou
un visionnage, K rouvre directement la timeline ; son bouton **Animations** permet
également de consulter le gestionnaire.

Le gestionnaire contient la liste des animations du monde, une recherche et un
défilement à la molette. Cliquer sur une ligne la sélectionne et remplit le champ
nom. Saisir un autre nom puis **Renommer** pour changer son nom ; saisir un nom
libre puis **Créer** pour ajouter une animation. **Ouvrir** accède à la timeline
sélectionnée. La timeline ne contient plus de champ de création ni de liste
superposée : cet espace est rendu aux pages de la timeline.

Les noms suivent la convention existante : 1 à 48 lettres ASCII, chiffres, `_` ou
`-`. Un renommage conserve les pistons, les deux timelines, les alertes et les
positions ignorées. Le document complet est sauvegardé avant la mise à jour des
sélections en mémoire. Un nom déjà utilisé est refusé, sans écraser l’autre
animation. La création, le changement d’animation et le renommage depuis le menu
sont verrouillés pendant une session : **Terminer** avant de les utiliser.
Les commandes historiques restent disponibles ; le renommage s’utilise aussi avec
`/ld rename <ancienNom> <nouveauNom>`.

## Paramètres et raccourcis de lecture

**K → Param.** ouvre le menu de réglages, disponible avant et pendant une session.

- **Interface** : curseur de zoom de 50 % à 200 %, par pas de 5 %. Glisser le
  curseur ou utiliser la molette dessus. Le texte, les boutons, les cellules,
  les zones de clic et les infobulles changent d’échelle ensemble. Le changement
  est appliqué au retour ; les autres menus Minecraft ne changent pas.
  Le zoom effectif est limité si nécessaire pour conserver au moins 320 × 240
  coordonnées d’interface et garder les commandes accessibles. Les onglets actifs
  sont soulignés en cyan. Le réglage des alertes agit sur la séquence capturée ;
  il reste désactivé hors capture, pendant un recalcul et en lecture seule.
- **Touches** : cliquer sur une affectation, puis appuyer sur une touche ou un
  bouton de souris. Échap retire l’affectation ; la flèche de chaque ligne remet
  sa valeur par défaut. Les conflits avec les autres commandes sont signalés en rouge.

Valeurs par défaut : **K** menu, **U** lecture/pause, **I** ouvrir, **J** fermer,
**R** reset en pause, **← / →** page précédente/suivante. I/J concernent le mode
**Visionner** ; les boutons ON/OFF de la capture y deviennent **Ouvrir/Fermer**.
Ces touches évitent P/O/F, déjà utilisées par Minecraft 26.2. Les commandes
restent disponibles dans les contrôles Minecraft. L’ancienne affectation R de
lecture/pause est migrée vers U une fois ; les autres affectations personnalisées
sont conservées.

I/J relancent la transition choisie depuis son départ, avec la physique vanilla.
R interrompt la lecture et revient à la page 0 de la **dernière animation jouée** :
au départ capturé pour Ouverture, au résultat de l’ouverture pour Fermeture.
Consulter l’autre timeline sans la jouer ne change pas cette référence. Les flèches
permettent ensuite d’avancer et reculer page par page. Une fermeture ne garantit pas
un état fermé si les consignes ne l’obtiennent pas : le mod ne force pas les blocs.

Les touches sont enregistrées dans les options vanilla ; le zoom est
conservé dans `config/layout-debugger-client.json` (`zoomPercent`). L’ancien
réglage de dimensions du menu est remplacé par un zoom initial de 100 %. Le panneau d’identification
vise aussi les têtes et les corps des pistons transportés. Il apparaît en haut à
droite, ou sous le panneau principal si la largeur manque.

## Construction commune et deux timelines

Le bouton **Ouvert. / Fermet.** choisit le programme. Les identifiants des pistons
et la construction sont communs ; les actions et leur ordre sont propres à chaque
timeline. C’est une seule animation avec deux transitions, ce qui réutilise le
lecteur et le contrôleur existants. Deux documents indépendants auraient dupliqué
les cibles et rendu leurs modifications plus difficiles à synchroniser.

La page 0 d’**Ouverture** est le départ capturé. La page 0 de **Fermeture** est
le résultat physique de l’ouverture, après les derniers signaux et six ticks de
marge de stabilisation. La puissance du mod et les positions déplacées restent
actives pour la fermeture. Il n’y a pas de capture manuelle Reset/Animated.
Cette marge n’attend pas arbitrairement tous les circuits : prévoir une dernière
consigne plus tard si le montage nécessite davantage de temps.

Pendant la capture, mettre en pause puis utiliser les blocs de l’inventaire créatif
pour construire ou casser. Les outils ON/OFF ne cassent pas les blocs. Chaque
modification révise la construction commune et recalcule la page choisie. Les
blocs ajoutés restent dans le monde après **Terminer**. Les changements s’appliquent
aux coordonnées choisies dans toutes les pages : placer un bloc à un emplacement
occupé par un bloc mobile ailleurs dans l’animation peut donc changer son comportement.
Pour modifier l’assemblage initial sans ambiguïté, utiliser la page 0 d’Ouverture.
Les placements, destructions et leurs modifications synchrones de voisinage sont
suivis côté serveur. Leurs nouveaux ticks et événements de blocs sont conservés,
sans recopier les files issues d’une autre page simulée. Les commandes externes
`/setblock`, les modifications d’autres mods et les changements d’inventaire d’un
coffre ne constituent pas des éditions de construction enregistrées.

Un piston supprimé reste dans les timelines et apparaît en jaune **Manquant**.
Réparer sa position/type/orientation le rétablit. Pour le remplacer ailleurs,
faire un clic droit sur son nom dans la grille, puis cliquer avec ON ou OFF sur
la nouvelle base. Cette réassociation conserve ses actions des deux timelines.
La lecture d’une action visant une identité manquante est interrompue explicitement.

## Alertes sur les trajets

**Param. → Interface → Alertes de trajet** active/désactive la vérification. Le mod rejoue les deux timelines
avec Minecraft pour relever les cases effectivement traversées par les têtes
et les blocs déplacés. Un bloc ajouté sur un trajet connu produit un **cadre jaune
translucide** et un message dans le chat ; le placement reste autorisé. Cela
signale aussi un conflit possible avec une page autre que celle affichée.
**Maj + clic droit avec ON ou OFF sur le bloc** ignore cette position ; refaire
le geste réactive ses alertes. Le réglage et les positions ignorées sont sauvegardés.
Les cadres eux-mêmes concernent les placements observés pendant la session.

Une case traversée n’est pas forcément interdite : le bloc peut être poussable
ou volontairement participer au mécanisme. Inversement, l’absence d’alerte ne
prouve pas la validité : un piston déjà bloqué ne fournit pas la trajectoire
qu’il aurait suivie sans obstacle. La référence est recalculée après les
modifications ; elle ne cherche pas toutes les constructions alternatives.

Cette vérification respecte les ticks, l’ordre des consignes et le résolveur vanilla.
Elle ne prétend pas simuler tous les « lags » : une baisse des TPS allonge le
temps réel des ticks, sans changer à elle seule leur ordre logique. Les entrées
externes, chunks déchargés, autres mods et l’aléatoire restent des conditions
particulières non validées exhaustivement. Tester les montages complexes en jeu.

## Pages, signaux et déplacements

- **Page 0** : départ de la timeline choisie, avant ses actions.
- **Page N** : résultat après N ticks redstone, donc N × 2 ticks de jeu.
- Les actions de la page N commencent au tick de jeu `2 × (N - 1)`. La simulation
  exécute ensuite les deux ticks vanilla correspondant à la page.
- **ON** ajoute le signal du mod jusqu'à une consigne OFF.
- **OFF** retire seulement le signal du mod. Un bloc de redstone, un levier ou une
  autre source réelle peut maintenir le piston alimenté.
- Pour un signal court, programmer ON puis OFF à la page suivante (2 ticks de jeu).
- L'ordre des actions au même tick est explicite. Deux changements dans un même tick
  n'ont aucun tick de simulation intermédiaire. Les règles vanilla restent applicables.

Une alimentation ne garantit pas une extension : obstacle, limite de poussée, mouvement
encore en cours, etc. Une impulsion écoulée pendant qu'un piston est transporté n'est
pas rejouée artificiellement à son arrivée.

Les pistons conservent leur identité pendant les poussées et les tractions. Le suivi
transfère les positions d'un déplacement complet en une opération, ce qui couvre les
chaînes où plusieurs pistons occupent successivement la position des autres. Tous les
pistons présents dans la capture sont suivis, même avant leur première sélection avec
un outil. Les positions sauvegardées restent celles du montage initial.

Le signal virtuel s'ajoute au test d'alimentation du piston ; il ne place aucun bloc
et ne distribue pas un signal aux fils voisins comme le ferait un bloc de redstone.
La mécanique de résolution, les événements de blocs, les blocs mobiles et les
collisions sont exécutés par Minecraft. La propriété `extended` n'est jamais utilisée
pour simuler un mouvement.

## Navigation et périmètre de la prévisualisation

L'édition a lieu **dans le monde d'origine**, à l’intérieur de la zone capturée.
Avancer vers une page suivante continue depuis l’état courant : seuls les ticks
manquants sont calculés. Un retour en arrière, un reset ou une modification d’une
action passée reconstruit le départ puis rejoue la physique en accéléré.
La mise à jour de la construction ou des actions peut aussi recalculer les trajets
de référence lorsque les alertes sont activées. Ce calcul est **masqué côté client** :
le montage affiché reste sur sa dernière image pendant le rejeu serveur. Les mises
à jour intermédiaires des blocs, les événements de pistons et leurs sons/particules
ne sont pas envoyés au joueur. À la fin, un bundle vanilla remplace ensemble les
chunks concernés, y compris les entités de blocs et la progression exacte des
pistons mobiles. Les cases d’air et les blocs disparus sont également synchronisés.
L’affichage passe donc directement au résultat, sans montrer une ouverture ou une
fermeture accélérée avant la page demandée. Cela s’applique aux sauts de page,
changements de timeline, corrections d’actions, reconstructions après placement
et vérifications de trajet. Aucun écran de chargement n’est ajouté.

Le calcul utilise toujours la physique du serveur dans le monde d’origine :
ce n’est pas une simulation dans un faux monde ni une modification arbitraire de
`extended`. Une longue séquence peut prendre du temps à calculer ; masquer le rejeu
ne supprime pas ce coût. Les meshes et l’éclairage peuvent encore demander un
court rafraîchissement graphique, mais les états mécaniques intermédiaires ne
sont pas rejoués côté client.

La lecture normale conserve sa vitesse habituelle. La pause attend une frontière
de page complète. Le montage est gelé entre deux pages et les interactions de
construction sont autorisées en pause dans la zone capturée.

La capture contient les blocs, l'air, le NBT complet des entités de blocs (inventaires,
etc.), les ticks de blocs et de fluides ainsi que les événements de blocs en attente.
Le rejeu retire les anciens blocs mobiles et les événements parasites avant de remettre
les files capturées avec leurs délais relatifs. Il ne s'agit donc pas d'une simple
restauration de propriétés de blocs.

Ce mécanisme est une **prévisualisation régionale de montage redstone**, pas un
rembobinage complet du monde :

- Isoler le montage des circuits extérieurs à la zone. Les effets sortant de cette
  zone ne sont pas restaurés. Prévoir une marge pour les déplacements.
- La zone et une marge de chunks doivent être chargées et simulées pendant le calcul.
  Le retrait normal de l'éditeur peut charger ponctuellement les chunks capturés pour
  restaurer le montage, sans ticket permanent.
- Les items, mobs et véhicules sont autorisés et conservés ; leurs états ne sont pas
  rembobinés. Seuls les drops identifiés comme issus du rejeu sont retirés à la
  restauration, sans effacer les objets qui étaient déjà présents.
- Le temps global, les générateurs aléatoires et l'activité extérieure continuent
  d'exister. Les blocs à ticks aléatoires sont signalés. Les circuits dépendant de
  ces éléments ne bénéficient pas d'une garantie de reproduction identique.
- Une capture refuse les pistons déjà en mouvement. Une fois capturée, la navigation
  peut restaurer le montage alors qu'un mouvement est en cours.
- L'état initial est gardé en mémoire pendant la session. Un arrêt normal restaure
  le montage ; un crash ne garantit pas cette restauration. Les séquences restent
  sauvegardées, mais aucune reprise automatique de prévisualisation n'est effectuée.

Une seule édition est autorisée à la fois. Si le joueur est seul au début, l’éditeur
peut mettre globalement le serveur en pause et accélérer les sauts de page. Dès qu’un
autre joueur rejoint, la capture reste ouverte en mode partagé : rejeu visible à
vitesse normale, sans pause physique du monde. La zone est réservée à son propriétaire.

Le français et l’anglais se choisissent dans **Paramètres → Interface → Langue**.
Sur Paper, `layoutdebugger.use` remplace l’obligation OP ; sur Fabric, le nœud de
permission est `layout-debugger:use` avec le niveau commandes comme valeur par défaut.

## Données et architecture

- `sequence/` : document immuable partagé par les commandes et l'interface ; lecteur
  de ticks et opérations de timeline indépendants de Minecraft. Les anciens fronts
  d’impulsion restent lisibles pour compatibilité ; les outils utilisent ON et OFF.
- `piston/` : alimentation additive, suivi des positions et déplacements en chaîne.
- `preview/` : capture, reconstruction accélérée et synchronisation de l'état régional.
- `playback/EditorService` : autorité serveur pour les requêtes, outils et pages ;
  reconstruction entre deux ticks, calcul des pages via sprint vanilla et lecture
  normale via freeze/step. `InventorySnapshot` conserve et restaure l'inventaire.
- `network/` : intentions client et vues serveur avec tailles de paquets limitées.
- `src/client/` : écran natif, raccourcis et HUD ; aucune mutation du monde côté client.
- `mixin/` : test d'alimentation des pistons, suivi de leurs déplacements et accès
  ciblé aux files nécessaires à la reconstruction. Aucune réécriture du moteur physique.

Les séquences sont sauvegardées dans `<monde>/data/layout-debugger/sequences.json`.
Le format **4** conserve le UUID du créateur, les deux timelines, le réglage des alertes et les positions
ignorées. Les documents antérieurs restent non attribués jusqu’à `admin assign`. Les formats 1 et 2 migrent vers Ouverture, avec Fermeture vide et alertes
activées. Les écritures remplacent le fichier atomiquement si possible ; un fichier invalide
est conservé et les éditions sont désactivées jusqu'à correction et réouverture.
Les identifiants acceptent 1 à 48 lettres ASCII, chiffres, `_` ou `-`.

## Commandes de débogage

```text
/ld edit
/ld create <sequence>
/ld rename <sequence> <nouveauNom>
/ld list
/ld show <sequence>
/ld delete <sequence>
/ld piston add <sequence> <id> <x> <y> <z>
/ld piston remove <sequence> <id>
/ld action add <sequence> <tickJeu> <ordre> <id> on
/ld action add <sequence> <tickJeu> <ordre> <id> off
/ld action remove <sequence> <tickJeu> <ordre>
/ld run <sequence>
/ld status
/ld stop
```

Les commandes utilisent toujours des **ticks de jeu**, pour préserver les anciennes
séquences. L'interface affiche des ticks redstone. Les commandes demandent un joueur
créatif avec les droits de commandes de niveau 2. Pendant l'édition, modifier la
séquence active depuis la timeline. `/ld stop` termine aussi une édition active.
Les commandes historiques modifient et lisent **Ouverture** ; utiliser le sélecteur
de timeline de l’interface pour Fermeture. Une lecture par commande ne possède pas de capture : elle part de la construction
actuelle et ne la restaure pas à sa fin.

## Versions et compilation

Versions du projet : Minecraft **26.2**, Java **25**, Fabric Loader
**0.20.1**, Fabric API **0.161.0+26.2**, Loom **1.19-SNAPSHOT**, Gradle **9.8.2**.
Les packages sont `fr.eligaimix.ltdr`.

Avec un JDK 25 configuré :

```powershell
.\gradlew.bat build
.\gradlew.bat sequenceTest
.\gradlew.bat -PgameTests runGameTest
.\gradlew.bat -PgameTests runClientGameTest
.\gradlew.bat runClient
```

Les vérifications pures sont incluses dans `build`. Le serveur GameTest est activé
explicitement par `-PgameTests` et vérifie le comportement réel de Minecraft sans
interface graphique. Son mod de test n'est pas inclus dans le JAR de distribution.
Le JAR du mod est `build/libs/layout-debugger-1.0.0.jar` ; le JAR `-sources` contient
les sources. Les résultats réellement obtenus sont consignés dans `build/verification.txt`.

Les tests client ouvrent aussi un vrai monde solo pour vérifier le parcours réseau
de l'éditeur, les pages, les déplacements et la restauration finale. Ils génèrent
des captures de la timeline et de l'inspecteur en 320 × 240 et 854 × 480 dans
`build/ui-qa`. Ils demandent un environnement graphique fonctionnel.

## Test manuel de l'interface

Créer un monde superplat créatif avec commandes activées. Dans une zone vide :

```text
/tp @s 103 83 103
/setblock 100 80 100 minecraft:sticky_piston[facing=east]
/setblock 101 80 100 minecraft:piston[facing=up]
/setblock 102 80 100 minecraft:piston[facing=up]
/setblock 101 81 100 minecraft:stone
/setblock 102 81 100 minecraft:stone
```

Appuyer sur K, créer `demo`, cliquer Ouvrir, puis choisir le rayon 16 et Capturer. Fermer l'écran avec Échap.
À la page 0, cliquer sur le piston collant avec ON : une action de page 1 est créée
et le montage est rejoué. Vérifier que les deux autres pistons ont été poussés.
Aller à la page 3 puis cliquer avec ON sur l'un de ces pistons à sa **nouvelle position**.
Rouvrir la timeline : sa ligne doit suivre le même piston, avec ses coordonnées actuelles.

Vérifier aussi : retour à la page 0, navigation avant/arrière, impulsion d'une page,
OFF avec une alimentation réelle encore présente, ordre des actions, suppression
des actions ON/OFF, raccourcis reconfigurés et sortie normale avec
restauration du montage. Tester des résolutions d'écran et échelles d'interface différentes.
La compilation et les tests serveur ne remplacent pas cette vérification visuelle.

Placer aussi des objets renommés dans la barre rapide, l'inventaire, l'armure et la
main secondaire : après **Capturer → Terminer**, tous doivent retrouver leur place.
Vérifier ensuite **Visionner → Fin → 0 → Lecture/Pause** sans passer par Capturer :
l'inventaire doit rester inchangé et les cellules de timeline ne doivent pas pouvoir
modifier les actions. Contrôler les repères translucides ON/OFF sur les bonnes pages.


Pour tester les nouveautés sur un montage simple dans une autre zone :

```text
/tp @s 203 83 203
/setblock 200 80 200 minecraft:sticky_piston[facing=east]
/setblock 201 80 200 minecraft:stone
```

1. **K → créer `double_demo` → Ouvrir → Capturer**, puis ON page 1 sur le piston.
2. Aller à la page 0, ajouter un bloc décoratif hors du trajet avec un objet
   créatif. Il doit rester présent après navigation et après Terminer.
3. Dans **Fermet.**, vérifier que la page 0 montre le piston étendu. Programmer
   OFF page 1, puis aller page 3 : le piston doit s’être rétracté. Revenir dans
   Ouvert. : son ON est toujours présent, sans le OFF de Fermeture.
4. Dans Ouvert. page 0, poser de l’obsidienne en `202 80 200` : cadre jaune et
   message annonçant le trajet. **Maj + clic droit avec ON sur l’obsidienne**
   masque le cadre ; refaire le geste le réactive. Tester aussi le réglage Alertes de trajet dans Param. → Interface.
5. Retirer l’obstacle ; supprimer puis replacer le piston dans la même orientation.
   Sa ligne doit passer par Manquant puis retrouver ses actions. Pour tester son
   remplacement ailleurs, clic droit sur le nom de ligne, puis outil sur le nouveau piston.
6. **Terminer**, puis **Visionner** : vérifier les deux timelines et l’inventaire.

Les tests automatisés ne couvrent pas toutes les grandes portes, la quasi-connectivité
combinée aux autres mods ni les circuits extérieurs : valider ces montages dans
Minecraft sur une copie du monde avant de les utiliser sur une construction importante.

Pour vérifier le reset, programmer Ouverture ON page 1 et Fermeture OFF page 1,
puis utiliser **Visionner** : **I** ouvre, **R** remet au départ en pause ; **J**
ferme, **R** remet au début de la fermeture, donc ouvert dans ce montage.
Vérifier les flèches après chaque reset. Tester Param. → Touches (souris incluse),
le retour avec les nouvelles touches, la molette du curseur et les zooms 80/100/150 %. Tester les
identifiants en visant la base puis la tête pendant une page de mouvement.

Pour vérifier le calcul masqué, programmer un ON puis un OFF assez tard pour que
le bloc soit tiré par le piston collant. Aller à la dernière page stable, désactiver
puis réactiver les alertes : le recalcul ne doit montrer aucun aller-retour. Ajouter
ensuite un bloc décoratif en capture et modifier une action : vérifier que seule la
page finale apparaît. Dans Visionner, I/J doivent montrer l’animation normale après
la préparation silencieuse du départ. Contrôler aussi les coffres et autres entités
de blocs après navigation, ainsi que la synchronisation client/serveur sur les grands
layouts. Les montages avec entités actives et effets hors zone gardent les limites
régionales documentées plus haut.

Pour tester le gestionnaire, terminer la session puis ouvrir Animations. Sélectionner
`double_demo`, saisir `double_renommee` et cliquer Renommer : l’ancien nom doit
quitter la liste et les deux timelines doivent rester intactes dans Ouvrir. Tester
un nom déjà utilisé (refus), la recherche et le défilement avec plusieurs animations.
Fermer puis rouvrir le monde pour vérifier le nouveau nom sauvegardé.

## Serveurs Fabric et Paper

Le JAR principal reste un mod Fabric pour Minecraft 26.2, Java 25, Loader 0.20.1 et
Fabric API 0.161.0+26.2. Le moteur partagé autorise désormais la capture sur un
serveur dédié lorsqu’un seul joueur est connecté ; elle passe en mode partagé
si un autre joueur rejoint. La capture et le visionnage précis pilotent les ticks du serveur entier.
Le parcours intégré solo est couvert par les tests Minecraft existants ; le
serveur Fabric dédié et les assemblages de mods restent à tester séparément.

Le portage [Paper 26.2 est documenté ici](../paper/README.md). Il utilise un plugin
compagnon **et un petit composant `-javaagent` au démarrage**, pour reprendre les
hooks ciblés sans demander Fabric aux clients vanilla. Le client Fabric conserve
l’interface. Quand plusieurs joueurs sont présents, la capture reste disponible en temps réel ;
les sauts de page sont des rejeux visibles, sans gel ni accélération du serveur.
Les documents et le moteur sont partagés avec Fabric. Le plugin cible strictement
Paper build 135 ; il ne garantit pas le fonctionnement sur toutes les builds ou
les forks. Les essais locaux Fabric, Paper et client vanilla sont décrits dans le
[rapport de vérification](../paper/VERIFICATION.md), avec les limites de cette version de test.

## Repères Paper

`ModInitializer` est le point d'entrée, puis `SERVER_STARTED` fournit un monde prêt.
Brigadier et `CommandRegistrationCallback` remplacent l'enregistrement de commandes
par `plugin.yml`. Les événements Fabric pilotent l'exécution serveur ; le client
possède son écran et envoie des intentions, que le serveur valide. Les Mixins restent
limités aux méthodes et files vanilla sans événement public adapté. Les noms de
classes et signatures sont ceux des sources Minecraft 26.2 ciblées par le projet.

## Licence

[CC0-1.0](../LICENSE).


Les anciens signaux Impulsion sont convertis en deux actions ON/OFF au chargement.
L’ancien identifiant d’objet reste enregistré pour préserver les inventaires
sauvegardés, mais n’est plus fourni ni utilisable comme outil.

## Catalogues privés et export

Les animations sont désormais attribuées au UUID de leur créateur (stockage v4,
pont Paper v3). Les anciennes animations restent conservées et nécessitent une
attribution explicite. Voir [le guide des propriétaires, exports et migrations](OWNERSHIP-EXPORT.md).
