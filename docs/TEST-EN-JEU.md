# Manual in-game checks

[Français](TEST-EN-JEU.fr.md) · [Verification report](../paper/VERIFICATION.md)

Use a backed-up world, preferably a disposable copy. These checks do not certify every redstone layout. On Paper, update the client mod, plugin and agent together and fully restart.

## Creation and editing

1. Enter creative mode with `layoutdebugger.use` on Paper, or the Fabric access described in [installation](INSTALLATION.md). Also test a non-OP account granted only the use permission when a permissions manager is available.
2. Build a sticky piston facing a stone block and create `TestDoor`. Press **K**, select it, open it and capture the layout.
3. At page 0, use ON on the piston. Repeat the click to remove the action, then add it again. At page 2, use OFF. Test playback and reset.
4. Check the closing timeline, construction changes during capture, piston HUD, shortcuts, language and UI zoom.
5. Finish and check inventory restoration. Open viewing mode and check playback/page navigation. On Paper, `/ld run TestDoor opening` and `/ld stop` should start/stop direct playback from the live layout.

## Ownership

1. Join with a second authorized account: `/ld list` must not reveal the first player’s `TestDoor`.
2. `/ld create TestDoor` must create a separate document for the second player.
3. On Paper, selecting or running an animation name that exists only in another player’s catalog must be rejected.
4. An administrator with read access can inspect `/ld admin owners` and `/ld admin show <UUID> TestDoor`. Read access alone must not allow assignment. Deny permissions explicitly for this test if the account is OP, since OP receives them by default.

## Export and import

1. Finish the session, select an animation in the library and export it. Check `<game directory>/layout-debugger/exports/` and the chat message.
2. Inspect both timelines in the JSON. No layout blocks or inventories should be included. `/ld export TestDoor` should write only the server copy.
3. Run `/ld whoami`, put the file under the server data folder’s `imports/<UUID>/`, stand at the intended first piston and run `/ld import <actual-filename.ldanim.json> ImportedDoor`.
4. The copy must appear in your catalog without placing blocks. Build the blocks/orientations separately, then capture or play it. An existing destination name must be rejected.

## Legacy animations

After backing up, run `/ld admin list legacy`, `/ld whoami` and `/ld admin assign OldName <UUID>`. Check the recipient’s catalog and both timelines. `sequences.json.before-ownership-v4.bak` must preserve the original data. A name conflict must be rejected.

## Shared capture and limits

On Paper, have a vanilla player join during capture. The session must stay open, enter shared mode and let the world run. Page jumps become visible replay; pause must no longer freeze world physics. Finish, disconnect the other player and recapture to return to precise solo mode.

Test your actual large layouts and other plugins’ protections on a copy before deploying to a public server.
