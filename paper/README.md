# Layout Debugger — Paper server installation

[Français](README.fr.md) · [Client installation](../docs/INSTALLATION.md) · [Downloads](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit)

**Install the prebuilt files from the release. You do not need to compile the project.**

The companion backend requires **Paper 26.2 build 135 (commit e4d6dfd)** and **Java 25 or newer**. Other Paper builds and forks are not validated. **Folia is unsupported.** Players using the editor also need the Fabric client mod.

## 1. Install the client

On the PC used to edit layouts:

1. Install Fabric Loader **0.20.1 or newer** for **Minecraft 26.2**.
2. Install Fabric API for 26.2 (project version: **0.161.0+26.2**).
3. Download **`layout-debugger-1.0.1.jar`** from the release and put it next to Fabric API in your Minecraft instance’s `mods/` folder.
4. Start your Fabric profile. Do not put the Paper plugin or startup agent on your client.

## 2. Download the Paper server package

From the [MIT revision release](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit), download:

**`layout-debugger-v1.26.2-mit-server-paper.zip`**

Extract it. Its `serveur-paper/` folder contains:

| File | Destination |
| --- | --- |
| `plugins/layout-debugger-paper-0.1.1.jar` | Your server’s `plugins/` folder |
| `layout-debugger-agent.jar` | Your server root, outside `plugins/` |
| `start-paper-example.bat` / `.sh` | Optional example launch scripts |

Download Paper separately. The package does not contain Minecraft, the Paper server, worlds or an accepted EULA.

## 3. Start the server with the agent

With the official Paper JAR named `paper-26.2-135.jar`, run from your server folder:

```sh
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

If your Paper JAR has another filename, adjust the name after `-jar`. Keep `-javaagent:layout-debugger-agent.jar` before `-jar`.

Follow Paper’s normal first-start and EULA steps, then fully restart. Do not use `/reload`. The editor refuses to activate without the matching startup agent and Paper build.

## 4. Give access to editors

Editors need **creative mode** and the **`layoutdebugger.use`** permission. OP receives it by default; OP is not required if the permission is granted explicitly. With LuckPerms, for example:

```text
/lp user PlayerName permission set layoutdebugger.use true
```

Players without the mod can still join and play normally. They do not receive the editor UI or tools.

## 5. Use the editor

Press **K**, create an animation, open it and capture your layout. Program ON/OFF actions in the opening and closing timelines. One page represents one redstone tick, or two game ticks.

**U** plays/pauses, **R** resets the last played animation within a session, and **← / →** navigate pages. Finish the session to leave editing and restore your inventory. Shortcuts and language can be changed in settings.

Outside a capture, `/ld run Door opening` and `/ld run Door closing` play an animation directly in the world; `/ld stop` removes the mod’s signals. Direct playback does not capture or rewind the layout.

## Data and limitations

Animations are stored in **`plugins/LayoutDebugger/sequences.json`**. Inventory recovery files are stored under `<world>/data/layout-debugger/inventory-backups/`. Back up the world and these files before updating. Update the client mod, Paper plugin and agent together (bridge protocol 3).

This is an **alpha**. Solo capture can pause the server and hide accelerated replay. When another player joins, capture continues in shared mode: replay is visible and the world keeps running. Finish and recapture while alone to return to precise solo mode.

Preview restoration is regional; it does not rewind entities, global time or outside circuits. Paper retains its own physics behavior and fixes. The plugin respects cancelled piston events; other plugins can still affect your layouts. OFF removes only the mod’s signal, leaving real redstone power active.

## More documentation

- [Full usage guide](../docs/USAGE.md)
- [Ownership, export and migration](../docs/OWNERSHIP-EXPORT.md)
- [Verification report and known limitations](VERIFICATION.md)
- [Manual in-game checks](../docs/TEST-EN-JEU.md)
- [Building from source — developers only](../docs/BUILD.md)

Licensed under [MIT](../LICENSE). See [licensing history](../LICENSING.md) for earlier CC0 grants and third-party notices.
