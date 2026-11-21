# v1 — Minecraft 26.2 (MIT revision, alpha)

Licensing revision of the first public release: adopts MIT and updates the packaged license and metadata. Fabric artifact version 1.0.1; Paper 0.1.1. No gameplay changes. Previous CC0 grants remain valid.

Features from the first release:

- Opening and closing timelines for redstone layouts.
- In-game ON/OFF piston tools, highlights and explicit action ordering.
- Redstone-tick navigation, playback, pause and reset.
- Construction editing during a capture and piston path warnings.
- Animation library, renaming, creator ownership and JSON import/export.
- English/French UI, configurable shortcuts and UI zoom.
- Fabric client/server support and a companion backend for Paper 26.2 build 135.

## Requirements

Minecraft 26.2, Java 25 or newer, Fabric Loader 0.20.1 or newer and Fabric API for 26.2 (project version: 0.161.0+26.2).

Upload `layout-debugger-1.0.1.jar` for this Fabric version. The same JAR is used by clients and Fabric servers. Paper uses separate components from the [GitHub release](https://github.com/ElitGaimix/Layout-Debugger/releases/tag/v1.26.2-mit).

## Known limitations

Alpha release: back up your world. Preview restoration is regional and does not rewind entities or effects outside the capture. Shared capture uses visible replay while the world keeps running. OFF does not disable real redstone power. Other Paper builds and Folia are not supported.
