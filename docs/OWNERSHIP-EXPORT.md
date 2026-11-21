# Animation ownership, export and import

[Français](OWNERSHIP-EXPORT.fr.md) · [Usage](USAGE.md)

Each animation belongs to its creator’s Minecraft UUID. Menus, suggestions, ordinary commands, captures and playback use that UUID’s catalog. Two players can use the same animation name. Changing your username does not change ownership. Administrative access does not turn your personal catalog into a global catalog.

This protects documents, not world blocks. It is not a land-claim system: players can still observe a layout or modify its blocks outside a capture. Only one reserved capture/viewing session can run at a time.

## Export

In **K → Animations**, select an animation, finish any active session, then export it. The `.ldanim.json` file is saved to `<game directory>/layout-debugger/exports/`; chat shows the path. A server-side copy is also stored under `exports/<UUID>/` in the data folder.

The client accepts only explicitly requested transfers and never overwrites an existing file. Exports are limited to 4 MiB and one export per player every 10 seconds.

`/ld export Door` writes only the server-side copy. `/ld whoami` prints your UUID. Share the selected `.ldanim.json` file, not `sequences.json`, which contains every creator’s documents.

**Exports contain animation data, not layout blocks or a schematic.** They include both timelines, action order, piston references and orientations, and warning preferences. Positions are relative to the first registered piston. The original creator UUID is removed; importing cannot grant access to someone else’s catalog.

Snapshots, chest NBT, inventories, entities and recovery backups are not exported. Transfer the actual layout separately when needed.

## Import

The server operator places the file under `imports/<recipient UUID>/` in the data folder, creating the directories if needed. Clients cannot upload arbitrary documents through the editor. In single-player, these folders are inside the world save.

Stand **in the block intended for the base of the first piston**, in the correct dimension, then run:

```text
/ld whoami
/ld import Door-a1b2c3d4.ldanim.json MyCopy
```

Use the real filename. The new name must be unused in your catalog and contain 1–48 ASCII letters, digits, `_` or `-`.

Import creates a new document owned by the importing player and offsets positions to the current anchor. It does not place blocks or rotate the layout; orientations are preserved. Playback checks the real pistons at those positions. The format targets Minecraft 26.2.

Data folders:

- **Fabric / single-player:** `<world>/data/layout-debugger/`.
- **Paper:** `plugins/LayoutDebugger/`.

## Administration

Paper permissions:

- `layoutdebugger.admin.view`: read-only inspection and export.
- `layoutdebugger.admin.assign`: assignment of legacy animations.

Both are separate from `layoutdebugger.use`; OP receives them by default. A permissions manager can grant or deny each separately.

Fabric uses `layout-debugger:admin/view` and `layout-debugger:admin/assign` through the Fabric permissions API, with administrator-level fallback. Inspection and assignment commands also work from the server console. Administrative export needs a player to select the destination UUID folder.

```text
/ld admin owners
/ld admin list <UUID> [page]
/ld admin show <UUID> Door [page]
/ld admin export <UUID> Door
/ld admin list legacy
/ld admin show legacy OldDoor
/ld admin assign OldDoor <creator-UUID>
```

Replace angle-bracket placeholders without typing the brackets. `owners`, `list` and `show` use pages of 20 rows. `owners [page]` lists known UUIDs and optionally `legacy`. `show` displays coordinates and actions from both timelines without changing the world. Administrative export writes into the administrator’s server-side folder; it does not download automatically.

Admin commands do not allow editing or running another creator’s animation in the world. Export and import a personal copy for that purpose.

## Migrating older data

Storage schema **4** can read versions 1–3. Older documents did not record their creator, so they remain preserved but hidden from personal catalogs until explicitly assigned. They are not automatically given to the first player who joins.

Before the first schema-4 write, the original file is copied to `sequences.json.before-ownership-v4.bak` without replacing an existing backup. Also make a full world/data backup before installation.

Run `/ld whoami`, inspect `/ld admin list legacy`, then assign each animation with `/ld admin assign`. Omit the leading `/` in the console. An existing name in the recipient’s catalog is rejected.

Do not downgrade over a schema-4 file; restore your backup instead. Switching online/offline mode or using a proxy that changes UUIDs requires an operator-managed migration. This version does not provide transfers between existing owners with different UUIDs.
