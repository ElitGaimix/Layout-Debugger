# Verification report — Minecraft 26.2 / Paper build 135

[Français](VERIFICATION.fr.md) · [Install release files](README.md) · [Manual checks](../docs/TEST-EN-JEU.md)

Target: **Paper 26.2 build 135, commit e4d6dfd**, with Java 25 bytecode. Local runtime checks used JDK 27. Fabric uses Loader 0.20.1 and Fabric API 0.161.0+26.2.

## MIT revision checks

For the licensing revision, the Fabric and Paper builds were rerun successfully under JDK 27:

- **142 model/storage checks passed.**
- **7 native Paper classes / 8 transformed methods verified**, including an inert bridge when no backend is installed.
- Runtime JARs were inspected for MIT license text, Fabric version/license metadata, the Paper plugin version and archive integrity.

The licensing revision does not change gameplay code. The full in-game/network scenarios below were recorded for the preceding implementation; they were not rerun solely for the license change.

## Previously recorded coverage

- Model/storage codec limits, malformed packets, UTF-8, action ordering and document versions, plus 10,000 deterministic randomized messages.
- Two Fabric server GameTests and native client scenarios for UI, zoom, key bindings, library, opening/closing, construction, warnings, hidden replay and inventory restoration.
- Eleven native Paper checks covering additive ON/OFF power, real redstone after OFF, transported piston identity, sticky retraction and capture restoration.
- Fabric client connection to a real Paper server: protocol handshake, animation creation/selection/renaming, persistence across restart, capture, pages and both timelines.
- Real ON/OFF tool clicks, repeat-to-remove behavior and client recognition of Paper’s items.
- Restoration of a non-empty inventory with a named item stack, components and selected slot; direct playback and stop outside the menu.
- A vanilla client joining during capture: shared-mode transition, visible page replay and editing, session completion and recapture with two players.
- Non-OP use through an explicit permission attachment, without administration access.
- French/English language switching and retained client preference.
- Preservation of pre-existing dropped items and removal of tracked replay drops.
- Paper startup without the agent: editor activation refused while the server continues starting.
- Permission loss, game-mode/dimension changes and disconnect cleanup; inventory recovery files retained when durable restoration fails.
- Runtime JAR inspection: no GameTest/test plugin classes, no Fabric runtime in the Paper plugin, relocated ASM and its redistribution notice.

## Ownership and exchange coverage

- Storage schema 4: separate owners with matching animation names, preserved UUIDs, unassigned legacy migration and non-overwritten initial backup.
- Export/import: both timelines, relative positions/warnings, new name/anchor/dimension, importer ownership, UTF-8/size/path validation and private import folders.
- Requested downloads with bounded fragments and immutable buffers; rejection of unsolicited transfers, out-of-order fragments and overwrites.
- Private catalogs and rejection of foreign-animation selection in solo and on Paper; library export to the client.
- Paper administrative inspection with explicit read permission; assignment rejected with read permission alone. Console listing, inspection and legacy assignment preserve both timelines.
- Repeated Paper client/vanilla/shared-capture scenarios with bridge protocol 3.

Imports are covered by model/filesystem checks. Manual layout placement and import behavior on a production server remain the operator’s responsibility.

## Test environment limits

Network scenarios used a disposable offline-mode server at `127.0.0.1:25577`, not two authenticated Mojang accounts or an external permissions manager. This does not validate public-server authentication or proxy configurations.

The previous test report is retained in [French](VERIFICATION.fr.md). These results support an **alpha limited to the targeted Paper build**, not certification of every redstone layout.

## Remaining validation before a stable release

- Large real layouts, capture/memory limits, long timelines, outside circuits and extended use.
- Your target server’s plugins, protections and piston modifications. Other Paper builds, forks and Folia are unsupported.
- Execution on Java 25 itself; `--release 25` checks APIs and bytecode, while local runtime tests used Java 27.
- Authenticated connections, proxies, real latency and reconnects in the intended deployment.
- Crashes, disk errors and manual recovery on a copy of the target world. A regional in-memory capture is not a world backup.
- A dedicated Fabric server: recorded multiplayer network checks target Paper, even though the engine is shared.
- A successful remote CI run; local build success does not establish GitHub Actions status.

Follow the [manual test procedure](../docs/TEST-EN-JEU.md) on a disposable copy of your server before using important layouts.
