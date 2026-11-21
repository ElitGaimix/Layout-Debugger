# Building from source — developers

[Français](BUILD.fr.md) · [Install prebuilt files](INSTALLATION.md) · [Paper installation](../paper/README.md)

**Players and server administrators can install the release files directly. Compilation is only needed to work on the source.**

Use **JDK 25 or newer** and access to the Gradle/Maven repositories. The Gradle wrapper is included; dependency versions are in `gradle.properties`. Builds target Java 25; local checks have run under JDK 27.

## Build

From the repository root:

```sh
./gradlew build
./gradlew -p paper build
```

On Windows, replace `./gradlew` with `./gradlew.bat`.

| Artifact | Output |
| --- | --- |
| Fabric mod | `build/libs/layout-debugger-1.0.1.jar` |
| Fabric source JAR | `build/libs/layout-debugger-1.0.1-sources.jar` |
| Paper plugin | `paper/build/libs/layout-debugger-paper-0.1.1.jar` |
| Paper startup agent | `paper/agent/build/libs/layout-debugger-agent.jar` |

The Fabric build runs the model/storage checks. The Paper build verifies its startup hooks against the targeted native server JAR and builds the included agent project.

## Optional Minecraft integration tests

```sh
./gradlew -PgameTests runGameTest
./gradlew -PgameTests runClientGameTest
./gradlew -p paper integrationTestJar
```

Client tests launch Minecraft and need a graphical environment. The Paper integration test plugin is a separate test artifact: **never install it on a player world or include it in a release**.

Paper network tests need a disposable server on `127.0.0.1:25577`, with its EULA handled by its operator. `LAYOUT_DEBUGGER_PAPER_TEST_ADDRESS=127.0.0.1:25577` enables the remote client scenario. `LAYOUT_DEBUGGER_PAPER_ONLY=true` skips solo scenarios; `LAYOUT_DEBUGGER_PAPER_MULTIPLAYER=true` together with `tools/verify-paper-vanilla.py` adds a vanilla client. Never target a public server or a player’s world.

See the [verification report](../paper/VERIFICATION.md) and [manual test procedure](TEST-EN-JEU.md) for coverage and remaining limits.

## Prepare local distributions

After both builds succeed:

```sh
python tools/package-release.py
```

The script checks Java class versions, the ASM redistribution notice and the absence of test classes in runtime JARs. It prepares `out/`, distribution ZIPs, source archives and SHA-256 checksums. It does not compile or publish. Regenerate the archives after changing their contents.

`out/`, test worlds, caches and local settings stay outside Git. The `modrinth/` folder contains publication text and settings; its `files/` directory holds local upload files and is also ignored.
