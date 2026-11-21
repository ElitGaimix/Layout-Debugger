# Layout Debugger

**Program and debug redstone layouts, one redstone tick at a time.**

Layout Debugger is a creative-mode Minecraft mod for building and debugging piston layouts. Capture a layout, edit its opening and closing timelines, and inspect the result directly in your world using Minecraft’s physics.

[Français](README.fr.md) · [Installation guide](docs/INSTALLATION.md) · [Full manual (French)](docs/USAGE.md) · [Paper backend (French)](paper/README.md)

## Features

- Separate **opening** and **closing** timelines with explicit action ordering.
- **ON / OFF** tools to program pistons in the world, with colored highlights.
- Redstone-tick navigation, playback, pause and reset.
- Construction editing during a capture and piston path warnings.
- Animation library, renaming and JSON import/export with creator ownership.
- French and English UI, adjustable zoom and configurable shortcuts.
- Single-player, Fabric servers and a companion backend for a specific Paper build.

## Requirements

| Component | Target version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 or newer |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | Project uses 0.161.0+26.2 |
| Optional Paper backend | 26.2 build 135 only |

The Fabric mod is version **1.0.0**; the Paper plugin is **0.1.0-dev**. The current distribution is an **alpha**. See the [verification report (French)](paper/VERIFICATION.md) for previous checks and known limitations.

## Installation

Download the client or server files from [Releases](https://github.com/ElitGaimix/Layout-Debugger/releases). Start with the client installation below, then set up a server if needed.

### Client / single-player

Install Fabric Loader and Fabric API for Minecraft 26.2, then put `layout-debugger-1.0.0.jar` in your instance’s `mods/` folder. Enable world commands for default access in single-player.

### Fabric server

On a **Fabric server**, install the mod and Fabric API on the server as well. Players using the editor need the client mod.

### Paper server

On **Paper 26.2 build 135**, install the companion plugin and its startup Java agent following the [Paper guide](paper/README.md). The editor requires the Fabric client mod. Unmodded players can still join. Folia is unsupported.

## Quick start

1. In creative mode, stand near your layout and press **K**.
2. Create an animation, open it, choose a radius and start a capture.
3. Program pistons with the **ON / OFF** tools and timeline. Each page represents one redstone tick, or two game ticks.
4. Navigate with **← / →**, play or pause with **U**, and reset the last played animation with **R**.
5. Finish the session to restore your inventory and leave editing. Your animation remains saved.

Shortcuts can be changed in the settings. The interface supports English and French.

> Back up your world before testing. Preview restoration covers a region, not entities or effects outside it. With multiple players, replay is visible and the world keeps running. OFF removes only the mod’s power; real redstone can still power a piston.

## Building

With **JDK 25 or newer**, run from the project root:

```sh
./gradlew build
./gradlew -p paper build
```

On Windows, use `./gradlew.bat`. The Fabric build runs model and storage checks; the Paper build also verifies its native hooks. Output JARs are in `build/libs/`, `paper/build/libs/` and `paper/agent/build/libs/`.

After building, create local distribution archives with:

```sh
python tools/package-release.py
```

The `out/` distribution folder, test worlds, caches and personal settings are excluded from Git. See the [build guide (French)](docs/BUILD.md) for optional in-game tests.

## Reporting issues

Include Minecraft, mod and server versions, reproduction steps, and other installed mods or plugins. Remove personal information from log excerpts before attaching them.

## License

By **ElitGaimix**, licensed under [CC0-1.0](LICENSE). The Paper startup component includes ASM under its [third-party license](paper/agent/THIRD-PARTY-ASM-LICENSE.txt).
