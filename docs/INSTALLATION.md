# Installation — Layout Debugger for Minecraft 26.2

[Français](INSTALLATION.fr.md) · [Usage manual (French)](USAGE.md)

## 1. Client / single-player

Requirements: **Minecraft 26.2**, **Java 25 or newer**, **Fabric Loader 0.20.1 or newer**, and **Fabric API for 26.2** (the project uses 0.161.0+26.2).

1. Install Fabric Loader for Minecraft 26.2 in your launcher.
2. Download Fabric API for Minecraft 26.2 separately.
3. Download `layout-debugger-1.0.1.jar` from the release. Alternatively, extract the client ZIP and take the mod from `client/mods/`.
4. Put both the mod and Fabric API JARs in your Minecraft instance’s `mods/` folder.
5. Start the Fabric profile. In single-player, open a creative world with commands enabled. Back up your world before testing.
6. Press **K**, create an animation, open it and capture your layout. Program pistons using the **ON / OFF** tools. One timeline page is one redstone tick (two game ticks).

**U** plays/pauses, **R** resets the last played animation, and **← / →** navigate pages. Finish the session to restore your inventory. Change shortcuts and the English/French UI in settings.

To edit on a server, install this same client mod and connect to an equipped Fabric or Paper server. Installing the client alone does not enable the editor on an unequipped server.

## 2. Server

### Fabric server

1. Set up a Fabric server for **Minecraft 26.2**, with Java 25 or newer.
2. Install Fabric API for 26.2 and `layout-debugger-1.0.1.jar` in the server’s `mods/` folder. The server Fabric ZIP contains the mod under `serveur-fabric/mods/`.
3. Restart the server. Players using the editor must follow the client steps above.
4. Use creative mode and grant the `layout-debugger:use` permission through the Fabric permissions API, or the default gamemaster command level.

### Paper server

Supported target: **Paper 26.2 build 135 (commit e4d6dfd)**, Java 25 or newer. Other builds and forks are not validated. **Folia is unsupported.**

1. Extract the Paper server ZIP. Its `serveur-paper/` folder contains the plugin, startup agent and example launch scripts.
2. Put `layout-debugger-paper-0.1.1.jar` in your server’s `plugins/` folder.
3. Put `layout-debugger-agent.jar` in the server root, **outside** `plugins/`.
4. Download the official Paper server separately. For the example scripts, name its file `paper-26.2-135.jar`.
5. Add the agent argument before `-jar` in your startup command:

```sh
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

6. Follow Paper’s normal first-start and EULA steps, then perform a full restart. Do not use `/reload`. Without the matching agent and Paper build, the plugin refuses to enable the editor.
7. Players using the editor need the Fabric client installation above, creative mode and `layoutdebugger.use`. OP players receive this permission by default. With LuckPerms, for example:

```text
/lp user PlayerName permission set layoutdebugger.use true
```

Players without the mod can still join Paper and play normally; they do not have the editor UI or tools. Update the client mod, Paper plugin and startup agent together (bridge protocol 3).

## Data and limitations

Fabric saves animations in `<world>/data/layout-debugger/sequences.json`; Paper uses `plugins/LayoutDebugger/sequences.json`. Inventory recovery files are stored under `<world>/data/layout-debugger/inventory-backups/`. Back up these files with your worlds.

The current distribution is an **alpha**: Fabric mod 1.0.1 and Paper plugin 0.1.1. Java bytecode targets Java 25; previous in-game checks ran under Java 27. See the [verification report (French)](../paper/VERIFICATION.md).

A solo capture can pause the server and hide accelerated replay. Once another player joins, capture continues in shared mode: replay is visible and the world keeps running until the session ends. Only one editing session is reserved at a time.

Preview restoration covers a region, not entities, global time or effects outside it. Keep the layout isolated and leave room for block movement. Path warnings are advisory. OFF removes only the mod’s power, so actual redstone can still power a piston.

Old animations without an owner need explicit assignment. See [ownership, export and migration (French)](OWNERSHIP-EXPORT.md). JSON exports contain animation data, not layout blocks.

Minecraft, Paper, Fabric Loader and Fabric API are not bundled.
