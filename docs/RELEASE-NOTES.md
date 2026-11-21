# Layout Debugger v1 — Minecraft 26.2 (MIT revision)

Program and debug redstone layouts with opening and closing timelines, ON/OFF tools and redstone-tick navigation. Includes a Fabric client/server mod and a companion Paper backend. English and French UI, configurable shortcuts, animation ownership and JSON import/export are included.

## Licensing revision

This revision adopts MIT for the current distribution and updates the packaged license and Fabric metadata. Artifact versions are now Fabric 1.0.1 and Paper 0.1.1. Gameplay is unchanged. The original v1.26.2 remains available under its original CC0 terms; previously granted rights are not revoked. See [licensing history](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/LICENSING.md).

## 1. Client / single-player installation

1. Use **Minecraft 26.2** and **Java 25 or newer**.
2. Install **Fabric Loader 0.20.1 or newer** and **Fabric API for 26.2** (project version: 0.161.0+26.2).
3. Download **`layout-debugger-1.0.1.jar`** below and place it next to Fabric API in your instance’s `mods/` folder. The **client ZIP** is an alternative containing the same mod plus documentation.
4. Start your Fabric profile. Open a creative world with commands enabled, or join an equipped server with the required permission.
5. Press **K**, create an animation, open it and capture your layout. Use ON/OFF tools to add piston actions. One page is one redstone tick, or two game ticks. **U** plays/pauses, **R** resets, and **← / →** navigate pages. Finish the session to restore your inventory.

Install only the client mod on your PC; Paper plugins and the startup agent belong on the server. A client mod alone cannot enable the editor on an unequipped server.

## 2. Server installation

### Fabric server

1. Set up a **Minecraft 26.2 Fabric server** with Java 25 or newer.
2. Download **`layout-debugger-v1.26.2-mit-server-fabric.zip`**. Place the JAR from `serveur-fabric/mods/` in the server’s `mods/` folder, along with Fabric API for 26.2.
3. Restart. Editors need the client setup above, creative mode and `layout-debugger:use` (default: gamemaster command access).

### Paper server

**Supported target: Paper 26.2 build 135, commit e4d6dfd. Java 25 or newer. Folia is unsupported.**

1. Download **`layout-debugger-v1.26.2-mit-server-paper.zip`** and extract it.
2. Put `serveur-paper/plugins/layout-debugger-paper-0.1.1.jar` in the server’s `plugins/` folder.
3. Put `serveur-paper/layout-debugger-agent.jar` in the server root, **outside** `plugins/`.
4. Download Paper separately. Name its JAR `paper-26.2-135.jar` to use the included launch examples, or adjust the filename in this command:

```sh
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

5. Follow the normal Paper first-start and EULA steps, then fully restart. Do not use `/reload`.
6. Players using the editor need the Fabric client mod, creative mode and **`layoutdebugger.use`**. OP receives this permission by default. With LuckPerms: `/lp user PlayerName permission set layoutdebugger.use true`.

Without the matching startup agent and Paper build, the plugin refuses to enable the editor. Unmodded players can still join Paper and play normally. Update the client, plugin and agent together (bridge protocol 3).

## Downloads and limitations

- **Client:** `layout-debugger-1.0.1.jar`, or `layout-debugger-v1.26.2-mit-client.zip` with documentation.
- **Fabric server:** `layout-debugger-v1.26.2-mit-server-fabric.zip`.
- **Paper server:** `layout-debugger-v1.26.2-mit-server-paper.zip`, containing both required components.
- **Sources:** `layout-debugger-v1.26.2-mit-sources.zip`, or GitHub’s source archives.
- **Integrity:** `SHA256SUMS.txt` lists SHA-256 hashes of the attached distribution files.

This first release retains the project’s **alpha** status. Internal artifact versions remain Fabric **1.0.1** and Paper **0.1.1**. Back up your world before use. Preview restoration is regional; entities and effects outside the capture are not rewound. Multiplayer capture uses visible replay while the world keeps running. Path warnings are advisory, and real redstone can still power pistons after OFF.

Minecraft, Paper, Fabric Loader and Fabric API are not bundled. The project is licensed under MIT; the Paper startup component includes ASM with its redistribution notice.

[Full installation guide](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/docs/INSTALLATION.md) · [Usage manual](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/docs/USAGE.md) · [Verification report](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/paper/VERIFICATION.md)
