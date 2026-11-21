# Using Layout Debugger

[Français](USAGE.fr.md) · [Installation](INSTALLATION.md) · [Paper server](../paper/README.md)

Layout Debugger lets you program and inspect redstone layouts in creative mode using Minecraft’s server-side physics. Install the release files first; you do not need to compile anything to use the editor.

## Create and capture a layout

1. Build your layout, let all pistons finish moving, then stand near its center.
2. Press **K** to open the animation library. Create an animation or select one, then open its timeline.
3. Choose a capture radius of **8–31 blocks** and capture. The region is centered on your current position and limited vertically by the world bounds. Leave room for all block movement and isolate the layout from outside circuits.
4. In a solo session, the world pauses. Your inventory is saved and temporarily replaced with the ON/OFF tools. **Escape** closes the screen without ending editing.
5. Choose a page and right-click a piston base with **ON** or **OFF**. A piston ID is created automatically. At page 0, the first click programs page 1. The tools also recognize piston heads.
6. Repeating the same action on the same piston/page removes it; the other tool replaces it. Reopen the timeline with **K** to edit cells and the order of actions at a tick.
7. Play or pause with **U**. Use **R** to return to the start of the last played animation and pause. Finish the session to restore your inventory and leave editing. Animation edits remain saved.

Finishing restores the starting layout **including construction changes made during editing**, plus the previous pause state. A tools button can restore a missing temporary tool.

## Library and names

The library lists your animations with search and scrolling. Select an animation and open it, create a new name, or enter a different name and rename the selection. Names contain **1–48 ASCII letters, digits, `_` or `-`**. Existing destination names are rejected.

Renaming preserves piston identities, both timelines, warnings and ignored positions. Creation, switching animation and renaming are locked during a session; finish first. `/ld rename OldName NewName` is also available.

Animations belong to their creator’s UUID. For sharing and old unassigned documents, see [ownership, export and migration](OWNERSHIP-EXPORT.md).

## Timeline pages and power

- **Page 0** is the starting state of the selected timeline, before its actions.
- **Page N** shows the result after N redstone ticks: **N × 2 game ticks**.
- Actions on page N begin at game tick `2 × (N - 1)`, followed by two vanilla simulation ticks.
- **ON** adds the mod’s power until OFF. **OFF** removes only that power; real redstone can keep the piston powered.
- For a short signal, place ON and OFF on consecutive pages.
- Action order within a tick is explicit. There is no simulation tick inserted between actions at the same tick.

Power does not guarantee extension: obstacles, the push limit and ongoing movement still apply. An impulse that ends while a piston is being transported is not replayed artificially on arrival.

The mod adds power to the piston’s power check. It does not place redstone blocks or power neighboring wires. Movement, block events, moving blocks and collision resolution remain Minecraft’s behavior.

Piston identities follow pushed/pulled chains and remain stable when tracked pistons occupy each other’s former positions. All pistons inside the capture are tracked before their first tool selection. Saved coordinates describe the initial layout.

## Opening and closing

An animation contains two independent action timelines sharing its layout and piston identities.

**Opening** starts from the captured layout. **Closing** starts from the physical result of opening, including six extra game ticks for settling. The mod’s current power and moved piston positions carry into closing. A slow circuit may need a later final action; this settling margin does not wait indefinitely for every circuit.

Selecting a timeline does not change which animation **R** resets. Reset refers to the last animation actually played: the captured start for opening, or the opening result for closing. A closing timeline does not force a closed state unless its programmed actions achieve it.

## Edit the construction

Pause during capture, close the screen and use creative inventory blocks to build or break inside the region. ON/OFF tools do not break blocks. Construction changes update the shared layout and recalculate the selected page; added blocks remain after finishing.

Changes apply at their coordinates across both timelines. A block placed where another block passes on a different page can change behavior. Edit page 0 of opening when you want to change the initial assembly clearly.

Player placements/breaks and synchronous neighbor changes are tracked server-side. External `/setblock` commands, edits from other mods and chest inventory changes are not recorded construction edits.

A deleted piston remains in the timeline with a missing warning. Restore its type, position and orientation, or right-click its row name and select a replacement piston with ON/OFF. Reassociation preserves both timelines’ actions. Playback stops explicitly if an action targets a missing identity.

## Piston path warnings and highlights

Enable path warnings in the interface settings. The mod replays both timelines to observe positions traversed by piston heads and moved blocks. Adding a block on a known path produces a yellow outline and chat warning, but placement stays allowed.

**Shift + right-click with ON/OFF** toggles ignoring warnings for that block position. The setting and ignored positions are saved. Full automatic analysis is reduced in shared mode.

Warnings are advisory: a traversed position can intentionally contain a pushable block, and an already blocked piston cannot reveal the path it would have taken without the obstacle. No warning is not proof that a layout is valid under every update order, external input, mod or lag scenario.

ON/OFF highlights identify pistons with an action on the displayed page. The HUD shows the page and the targeted registered piston’s current position, physical state and mod signal. Highlights follow moved pistons.

## Viewing and navigation

To inspect without editing, choose an animation, open it and start viewing. Your inventory stays unchanged and timeline edits are disabled. Playback/pause, page 0, previous/next, jump and end controls navigate the sequence. End shows the result after final actions and settling. Finish exits viewing and restores the layout start.

Moving forward simulates only missing ticks. Moving backward, resetting or editing a past action reconstructs the starting state and replays Minecraft’s physics. In precise solo mode, accelerated reconstruction is hidden from the client; the result appears after the calculation. Normal playback keeps its normal speed. Long sequences can still take time to calculate.

## Shortcuts and settings

| Default | Action |
| --- | --- |
| **K** | Open library / current editor |
| **U** | Play / pause |
| **Space**, in the timeline | Play / pause |
| **I** | Opening in viewing mode / direct playback where supported |
| **J** | Closing in viewing mode / direct playback where supported |
| **R** | Reset the last played animation within a session, then pause |
| **← / →** | Previous / next page |

Change keyboard/mouse assignments in settings; conflicts are highlighted. Escape removes an assignment, and the reset arrow restores its default. Vanilla controls store key bindings. Interface settings include English/French and **50–200% zoom**, limited where necessary to keep controls usable. Client preferences are stored in `config/layout-debugger-client.json`.

## Multiplayer

Only one reserved editing/viewing session runs at a time. Starting alone allows precise global pause and accelerated page replay. When another player joins, capture continues in shared mode: visible real-time replay and no world-physics pause. Shared mode lasts until the session ends, even if the other player leaves. Finish and recapture while alone to return to precise mode.

The region is reserved for its owner during the session. Outside circuits can still affect it. On Paper, unmodded players can join normally, but only equipped Fabric clients have the editor.

## Data and recovery

- **Fabric:** `<world>/data/layout-debugger/sequences.json`.
- **Paper:** `plugins/LayoutDebugger/sequences.json`.
- **Inventory recovery:** `<world>/data/layout-debugger/inventory-backups/`.

Inventory snapshots include items/components, hotbar, armor, offhand, selected slot, crafting grid and cursor stack. A recovery copy is written before inventory replacement and removed after restoration and durable player-save verification. A remaining copy is recovered on reconnect.

Storage schema 4 keeps creator UUID, both timelines and warning preferences. Older documents need assignment as described in [migration](OWNERSHIP-EXPORT.md). Invalid data is retained and editing is disabled until it is repaired and reopened.

A regional capture contains blocks, air, block-entity NBT, scheduled block/fluid ticks and pending block events. It is held in memory during the session. Normal shutdown restores the layout; a crash does not guarantee restoration. Back up your world.

## Limits

Restoration is regional, **not a full world rewind**. Entities, existing items, vehicles, global time, random generators and outside effects are not rewound. Only drops identified as replay-generated are removed during restoration. Random-ticking blocks are flagged. Keep the region and surrounding chunks loaded and simulated while inspecting it.

Capturing refuses pistons already in motion. Navigation can restore in-progress movements after capture. Other mods/plugins, unloaded chunks and external input can affect results. Paper retains its own fixes and behavior; test technical layouts on your actual target server.

## Commands

Ordinary commands use your own animation catalog. Creative mode and the configured permission/default command level are required. During editing, modify the active sequence through its timeline. `/ld stop` also ends an active editing session.

```text
/ld edit
/ld create <sequence>
/ld rename <sequence> <newName>
/ld list
/ld show <sequence>
/ld delete <sequence>
/ld piston add <sequence> <id> <x> <y> <z>
/ld piston remove <sequence> <id>
/ld action add <sequence> <gameTick> <order> <id> on
/ld action add <sequence> <gameTick> <order> <id> off
/ld action remove <sequence> <gameTick> <order>
/ld run <sequence>
/ld status
/ld stop
```

These legacy action commands use **game ticks**, while the interface uses redstone ticks. They operate on opening; use the interface timeline selector for closing. Direct command playback starts from the current world without a capture and does not restore it afterward. Paper also provides `/ld run <sequence> opening` and `/ld run <sequence> closing`.

For export/import and administration commands, see [ownership and exchange](OWNERSHIP-EXPORT.md). For source builds and integration tests, see the [developer build guide](BUILD.md). Licensed under [MIT](../LICENSE).
