# Compilation et préparation d'une publication

Utiliser un JDK 25 minimum (JDK 27 utilisé pour ces vérifications) et l'accès aux
dépôts Maven/Gradle. Le wrapper est fourni, versions dans gradle.properties.
Depuis la racine du projet sous Windows :

```powershell
./gradlew.bat build
./gradlew.bat -PgameTests build runClientGameTest
./gradlew.bat -p paper build integrationTestJar
python tools/package-release.py
```

Les tests client ouvrent Minecraft et requièrent un environnement graphique.
Le build Paper vérifie ses hooks contre le JAR natif ciblé. `integrationTestJar`
produit un plugin de tests séparé, réservé à un monde jetable : **ne jamais le
mettre dans une distribution ou sur un monde de joueur**. Il n'est pas nécessaire
pour utiliser le produit. Le portage Paper dépend d'un composite Gradle agent.

Les scripts locaux de contournement des chemins/caches `.gradle/` ne sont pas
nécessaires pour un build normal et ne font pas partie des sources publiques.
Pour les essais Paper décrits dans VERIFICATION.md, il faut un serveur local
jetable configuré sur 127.0.0.1:25577, le plugin de tests, l'agent et l'EULA acceptée
par son opérateur. Le test client distant est activé par
`LAYOUT_DEBUGGER_PAPER_TEST_ADDRESS=127.0.0.1:25577` et
`LAYOUT_DEBUGGER_PAPER_ONLY=true`. `LAYOUT_DEBUGGER_PAPER_MULTIPLAYER=true` avec
le helper `tools/verify-paper-vanilla.py` vérifie aussi la connexion vanilla.
Ne jamais pointer les tests sur le serveur public ou le monde d'un joueur.

`package-release.py` contrôle les classes Java, la notice ASM, l'absence des
classes de tests dans les JAR et prépare out, ses ZIP, sources et SHA-256.
Il ne compile pas et ne publie pas : lancer les builds avant. Recréer les archives
après chaque changement. Consulter VERIFICATION.md et tester les layouts et autres
plugins du serveur cible avant de présenter cette alpha comme une version stable.
