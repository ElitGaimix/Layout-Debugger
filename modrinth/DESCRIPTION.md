# Layout Debugger

**Program and debug your redstone layouts, one redstone tick at a time.**

Build opening and closing sequences for piston doors and other redstone layouts directly in your creative world. Capture a layout, program its pistons with ON/OFF tools, then inspect each page using Minecraft’s physics.

## Features

- **Opening and closing timelines** sharing the same layout, with independent actions and explicit action ordering.
- **In-game ON/OFF tools** and colored piston highlights.
- **Tick-by-tick navigation**, playback, pause and reset. One page equals one redstone tick, or two game ticks.
- **Construction editing** during a capture and warnings about observed piston paths.
- **Animation library**, renaming, creator ownership and JSON import/export. Exports contain animation data, not the layout’s blocks.
- **English and French interface**, adjustable UI zoom and configurable shortcuts.
- **Single-player and Fabric servers**, plus a companion backend for a specific Paper build.

## 1. Install on your client

1. Use **Minecraft 26.2** and **Java 25 or newer**.
2. Install **Fabric Loader 0.20.1 or newer** and **Fabric API for Minecraft 26.2**. The project uses Fabric API 0.161.0+26.2.
3. Put `layout-debugger-1.0.1.jar` and Fabric API in your instance’s `mods/` folder.
4. Start your Fabric profile. For single-player, open a creative world with commands enabled.
5. Press **K**, create an animation, open it and capture your layout.

Use the ON/OFF tools to program pistons. **U** plays or pauses, **R** resets the last played animation, and **← / →** navigate pages. Finish the session to restore your inventory. Shortcuts and interface language can be changed in settings.

For multiplayer editing, install this same client mod and connect to an equipped server. The client mod alone cannot enable the editor on an unequipped server.

## 2. Install on a server

### Fabric

Install Fabric for **Minecraft 26.2**, then put this same mod JAR and Fabric API for 26.2 in the server’s `mods/` folder. Restart the server. Players using the editor need the client setup above, creative mode and the `layout-debugger:use` permission, which defaults to gamemaster command access.

### Paper companion backend

The Paper backend targets **Paper 26.2 build 135, commit e4d6dfd**, with Java 25 or newer. **Folia is unsupported.**

Download the **Paper server package** from the [GitHub release](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit).

1. Put `layout-debugger-paper-0.1.1.jar` in the server’s `plugins/` folder.
2. Put `layout-debugger-agent.jar` in the server root, **outside** `plugins/`.
3. Download Paper separately. With its JAR named `paper-26.2-135.jar`, start it with:

```sh
java -javaagent:layout-debugger-agent.jar -Xms1G -Xmx2G -jar paper-26.2-135.jar --nogui
```

Follow Paper’s normal first-start and EULA steps, then fully restart. Do not use `/reload`. Without the matching agent and Paper build, the editor refuses to activate.

Players using the editor need the Fabric client mod, creative mode and **`layoutdebugger.use`**. OP receives the permission by default. With LuckPerms:

```text
/lp user PlayerName permission set layoutdebugger.use true
```

Players without the mod can still join Paper and play normally, without the editor UI or tools. Update the client mod, Paper plugin and agent together.

## Before using it

This first distribution is an **alpha**. Back up your world before testing.

Preview restoration covers the captured region, not entities, global time or effects outside it. Leave room for block movement and isolate the layout from outside circuits. Path warnings are advisory. OFF removes only the mod’s power, so actual redstone can still power the piston.

When another player joins a solo capture, it continues in shared mode: replay becomes visible and the world keeps running. One editing session is reserved at a time.

Minecraft, Paper, Fabric Loader and Fabric API are not bundled.

## Documentation and support

- [English installation guide](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/docs/INSTALLATION.md)
- [Detailed usage manual](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/docs/USAGE.md)
- [Ownership, export and migration](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/docs/OWNERSHIP-EXPORT.md)
- [Verification report and limitations](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/paper/VERIFICATION.md)
- [Source code](https://github.com/ElitGaimix/Layout-Debugger)
- [Report an issue](https://github.com/ElitGaimix/Layout-Debugger/issues)

By **ElitGaimix**.

## License

The current distribution is licensed under [MIT](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/LICENSE). Preserve the copyright and license notice when redistributing code. The original v1.26.2 was published under CC0; those earlier grants remain valid. See [licensing history](https://github.com/ElitGaimix/Layout-Debugger/blob/v1.26.2-mit/LICENSING.md).
