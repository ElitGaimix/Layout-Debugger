package fr.eligaimix.ltdr.gametest;

import fr.eligaimix.ltdr.client.editor.AnimationLibraryScreen;
import fr.eligaimix.ltdr.client.editor.EditorClient;
import fr.eligaimix.ltdr.client.editor.PistonPicking;
import fr.eligaimix.ltdr.client.editor.TimelineScreen;
import fr.eligaimix.ltdr.playback.EditorTools;
import fr.eligaimix.ltdr.playback.InventorySnapshot;
import fr.eligaimix.ltdr.preview.ConstructionEdits;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the actual editor service, network and integrated-server tick stepping. */
public final class EditorWorkflowClientGameTest implements FabricClientGameTest {
    private static final BlockPos PISTON = new BlockPos(8, 80, 8);
    private static final BlockPos STONE = new BlockPos(9, 80, 8);
    private static final BlockPos PUSHED = new BlockPos(10, 80, 8);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.parseBoolean(System.getenv("LAYOUT_DEBUGGER_PAPER_ONLY"))) return;
        java.util.concurrent.atomic.AtomicBoolean
                watchReplay = new java.util.concurrent.atomic.AtomicBoolean(),
                replayLeaked = new java.util.concurrent.atomic.AtomicBoolean();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    if (!watchReplay.get() || client.level == null) return;
                    var piston = client.level.getBlockState(PISTON);
                    if (!piston.is(Blocks.STICKY_PISTON)
                            || piston.getValue(PistonBaseBlock.EXTENDED)
                            || !client.level.getBlockState(STONE).is(Blocks.STONE)
                            || !client.level.getBlockState(PUSHED).isAir()) replayLeaked.set(true);
                });
        AtomicReference<InventoryFixture> originalInventory = new AtomicReference<>();
        AtomicReference<ItemStack> originalMutableStack = new AtomicReference<>();
        try (TestSingleplayerContext world =
                context.worldBuilder()
                        .adjustSettings(settings -> {
                            settings.setName("Layout Debugger editor workflow");
                            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                            settings.setAllowCommands(true);
                        })
                        .create()) {
            world.getServer().runCommand("tp @a 8 84 8");
            world.getServer()
                    .runOnServer(server -> {
                        var player = world.getConnection().getServerPlayer();
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                    });
            world.getServer().runCommand("setblock 8 80 8 minecraft:sticky_piston[facing=east]");
            world.getServer().runCommand("setblock 9 80 8 minecraft:stone");
            world.getServer()
                    .waitFor(
                            server ->
                                    server.overworld()
                                                    .shouldTickBlocksAt(new BlockPos(-16, 80, -16))
                                            && server.overworld()
                                                    .shouldTickBlocksAt(new BlockPos(32, 80, 32)),
                            1200);
            world.getConnection().waitForChunksDownload();
            world.getServer()
                    .runOnServer(server -> {
                        ServerPlayer player = world.getConnection().getServerPlayer();
                        seedInventory(player);
                        originalInventory.set(InventoryFixture.capture(player));
                        originalMutableStack.set(player.getInventory().getItem(7));
                    });

            request(context, "open", "", 0);
            context.waitForScreen(AnimationLibraryScreen.class);
            libraryName(context, "Editor_flow");
            TimelineClientGameTest.click(context, "Créer");
            context.waitFor(client -> EditorClient.state().sequence().equals("Editor_flow"));
            TimelineClientGameTest.click(context, "Ouvrir");
            context.waitForScreen(TimelineScreen.class);
            request(context, "begin", "", 8);
            context.waitFor(client -> EditorClient.state().editing());
            world.getServer()
                    .runOnServer(server -> {
                        if (!server.tickRateManager().isFrozen())
                            throw new AssertionError("Editor did not freeze game simulation");
                        assertTemporaryTools(world.getConnection().getServerPlayer());
                        // Original objects must not be the backup: ItemStack count and components
                        // are mutable.
                        originalMutableStack.get().setCount(1);
                        originalMutableStack
                                .get()
                                .set(
                                        DataComponents.CUSTOM_NAME,
                                        Component.literal("Changed after capture"));
                    });

            // The same server-authoritative path is used by a physical tool's right-click callback.
            request(context, "worldtool", "8,80,8:on", 0);
            waitPage(context, 1);
            // Repeating either physical tool erases its event, including a click on a moving head.
            request(context, "worldtool", "9,80,8:on", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().actions().isEmpty()
                                    && !EditorClient.state().busy(),
                    400);
            waitPage(context, 1);
            request(context, "worldtool", "8,80,8:off", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().actions().size() == 1
                                    && !EditorClient.state().busy(),
                    400);
            request(context, "worldtool", "8,80,8:off", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().actions().isEmpty()
                                    && !EditorClient.state().busy(),
                    400);
            request(context, "worldtool", "8,80,8:on", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().actions().size() == 1
                                    && !EditorClient.state().busy(),
                    400);
            waitPage(context, 1);
            assertSeekMovingProgress(context, world);
            takeHighlightScreenshot(context, world, "on", 0);
            String id =
                    context.computeOnClient(
                            client -> EditorClient.state().pistons().getFirst().id());
            request(context, "seek", "", 3);
            waitPage(context, 3);
            assertExtended(world, true);
            context.runOnClient(client -> {
                var selected =
                        PistonPicking.pick(
                                client.level,
                                EditorClient.state().pistons(),
                                new net.minecraft.world.phys.Vec3(9.5, 83, 8.5),
                                new net.minecraft.world.phys.Vec3(9.5, 79, 8.5),
                                new net.minecraft.world.phys.BlockHitResult(
                                        new net.minecraft.world.phys.Vec3(9.5, 81, 8.5),
                                        net.minecraft.core.Direction.UP,
                                        STONE,
                                        false),
                                1.0F);
                if (selected == null || !selected.id().equals(id))
                    throw new AssertionError(
                            "A visible piston head did not identify its base in the HUD");
            });

            // A forward page jump must continue the current world, not restore the baseline.
            world.getServer()
                    .runOnServer(server ->
                            world.getConnection()
                                    .getServerLevel()
                                    .setBlock(
                                            new BlockPos(12, 80, 12),
                                            Blocks.DIAMOND_BLOCK.defaultBlockState(),
                                            2));
            request(context, "seek", "", 4);
            waitPage(context, 4);
            world.getServer()
                    .runOnServer(server -> {
                        if (!world.getConnection()
                                .getServerLevel()
                                .getBlockState(new BlockPos(12, 80, 12))
                                .is(Blocks.DIAMOND_BLOCK))
                            throw new AssertionError(
                                    "Forward seek unnecessarily restored the initial world");
                    });
            request(context, "edit", id + ":off", 4);
            waitPage(context, 4);
            takeHighlightScreenshot(context, world, "off", 1);
            request(context, "seek", "", 6);
            waitPage(context, 6);
            assertExtended(world, false);
            // Rechecking all trajectories must keep this closed frame on the client throughout.
            request(context, "warnings", "", 0);
            context.waitFor(client -> !EditorClient.state().warningsEnabled());
            context.waitTicks(2);
            long suppressedBefore =
                    world.getServer()
                            .computeOnServer(server ->
                                    fr.eligaimix.ltdr.preview.ReplayVisibility.suppressedPackets());
            watchReplay.set(true);
            request(context, "warnings", "", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().warningsEnabled() && !EditorClient.state().busy(),
                    400);
            context.waitTicks(3);
            watchReplay.set(false);
            if (replayLeaked.get())
                throw new AssertionError(
                        "Intermediate replay blocks/animation leaked to the client");
            world.getServer()
                    .runOnServer(server -> {
                        if (fr.eligaimix.ltdr.preview.ReplayVisibility.suppressedPackets()
                                <= suppressedBefore)
                            throw new AssertionError(
                                    "Internal replay did not suppress intermediate packets");
                        if (fr.eligaimix.ltdr.preview.ReplayVisibility.active(
                                world.getConnection().getServerPlayer()))
                            throw new AssertionError(
                                    "Replay visibility gate leaked after final frame");
                    });

            // Seeking backward rebuilds the captured baseline; seeking forward runs vanilla again.
            request(context, "seek", "", 0);
            waitPage(context, 0);
            assertExtended(world, false);
            request(context, "seek", "", 3);
            waitPage(context, 3);
            assertExtended(world, true);

            request(context, "edit", id + ":on", 1);
            waitPage(context, 1);
            request(context, "edit", id + ":off", 2);
            waitPage(context, 2);
            request(context, "seek", "", 3);
            waitPage(context, 3);
            world.getServer()
                    .runOnServer(server -> {
                        var piston = world.getConnection().getServerLevel().getBlockState(PISTON);
                        if (!piston.is(Blocks.STICKY_PISTON)
                                || piston.getValue(PistonBaseBlock.EXTENDED))
                            throw new AssertionError("Pulse did not retract the piston");
                        // A one-redstone-tick sticky-piston pulse may spit its block. Preserve that
                        // vanilla behavior.
                    });
            context.runOnClient(client -> {
                if (EditorClient.state().pistons().getFirst().modPowered())
                    throw new AssertionError("Pulse remained powered");
            });

            // Two programs share the construction; closing page zero is the physical result of
            // opening.
            request(context, "edit", id + ":clear", 2);
            waitPage(context, 2);
            request(context, "edit", id + ":clear", 4);
            waitPage(context, 4);
            switchTimeline(context, "closing");
            context.runOnClient(client -> {
                if (!EditorClient.state().timeline().equals("closing")
                        || !EditorClient.state().actions().isEmpty())
                    throw new AssertionError("Closing did not start as an independent program");
            });
            assertExtended(world, true);
            request(context, "edit", id + ":off", 1);
            waitPage(context, 1);
            request(context, "seek", "", 3);
            waitPage(context, 3);
            assertExtended(world, false);
            switchTimeline(context, "opening");
            context.runOnClient(client -> {
                if (EditorClient.state().actions().size() != 1
                        || !EditorClient.state().actions().getFirst().powered())
                    throw new AssertionError("Closing overwrote opening actions");
            });

            // Actual vanilla-resolver trajectories produce advisory warnings for construction
            // edits.
            construction(context, world, PUSHED, Blocks.OBSIDIAN.defaultBlockState());
            context.waitFor(
                    client ->
                            !EditorClient.state().warnings().isEmpty()
                                    && !EditorClient.state().busy(),
                    400);
            takeHighlightScreenshot(context, world, "warning", 0);
            request(context, "ignore_warning", "10,80,8", 0);
            context.waitFor(client -> EditorClient.state().warnings().isEmpty());
            request(context, "ignore_warning", "10,80,8", 0);
            context.waitFor(client -> EditorClient.state().warnings().size() == 1);
            request(context, "warnings", "", 0);
            context.waitFor(client ->
                    !EditorClient.state().warningsEnabled()
                            && EditorClient.state().warnings().isEmpty());
            construction(context, world, PUSHED, Blocks.AIR.defaultBlockState());
            request(context, "warnings", "", 0);
            context.waitFor(
                    client ->
                            EditorClient.state().warningsEnabled() && !EditorClient.state().busy(),
                    400);
            waitPage(context, 0);
            // Missing identities remain in both programs and recover when the construction is
            // repaired.
            construction(context, world, PISTON, Blocks.AIR.defaultBlockState());
            context.waitFor(client -> EditorClient.state().orphanPistons().contains(id));
            construction(
                    context,
                    world,
                    PISTON,
                    Blocks.STICKY_PISTON
                            .defaultBlockState()
                            .setValue(PistonBaseBlock.FACING, net.minecraft.core.Direction.EAST));
            context.waitFor(
                    client ->
                            EditorClient.state().orphanPistons().isEmpty()
                                    && !EditorClient.state().busy(),
                    400);
            // A shared edit persists through both programs, backward seeks and capture completion.
            construction(
                    context, world, new BlockPos(12, 79, 12), Blocks.STONE.defaultBlockState());
            world.getServer()
                    .runOnServer(server -> {
                        ServerPlayer player = world.getConnection().getServerPlayer();
                        player.getInventory().setItem(2, new ItemStack(Items.GOLD_BLOCK));
                        player.getInventory().setSelectedSlot(2);
                        player.connection.handleUseItemOn(
                                new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                                        net.minecraft.world.InteractionHand.MAIN_HAND,
                                        new net.minecraft.world.phys.BlockHitResult(
                                                new net.minecraft.world.phys.Vec3(12.5, 80, 12.5),
                                                net.minecraft.core.Direction.UP,
                                                new BlockPos(12, 79, 12),
                                                false),
                                        0));
                        if (!server.overworld()
                                .getBlockState(new BlockPos(12, 80, 12))
                                .is(Blocks.GOLD_BLOCK))
                            throw new AssertionError(
                                    "Real player placement was blocked during capture");
                        player.getInventory().setSelectedSlot(0);
                        player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    });
            context.waitTicks(4);
            waitPage(context, 0);
            switchTimeline(context, "closing");
            world.getServer()
                    .runOnServer(server -> {
                        if (!server.overworld()
                                .getBlockState(new BlockPos(12, 80, 12))
                                .is(Blocks.GOLD_BLOCK))
                            throw new AssertionError(
                                    "Global construction edit was lost across timelines");
                    });
            switchTimeline(context, "opening");
            // Newly queued vanilla neighbor events must survive a construction revision.
            construction(
                    context,
                    world,
                    new BlockPos(7, 80, 8),
                    Blocks.REDSTONE_BLOCK.defaultBlockState());
            switchTimeline(context, "closing");
            request(context, "seek", "", 3);
            waitPage(context, 3);
            assertExtended(world, true);
            context.runOnClient(client -> {
                if (EditorClient.state().pistons().getFirst().modPowered())
                    throw new AssertionError("Closing did not release virtual power");
            });
            switchTimeline(context, "opening");
            construction(context, world, new BlockPos(7, 80, 8), Blocks.AIR.defaultBlockState());
            // Keep opening held ON so read-only shortcut tests can distinguish both start states.

            // A real key press requests and receives the dedicated screen-open packet.
            context.setScreen(() -> null);
            context.getInput().pressKey(EditorClient.OPEN);
            context.waitForScreen(TimelineScreen.class);
            request(context, "end", "", 0);
            context.waitFor(client -> !EditorClient.state().editing());
            assertExtended(world, false);
            world.getServer()
                    .runOnServer(server -> {
                        if (server.tickRateManager().isFrozen())
                            throw new AssertionError(
                                    "Editor did not restore previous unfrozen state");
                        originalInventory
                                .get()
                                .assertMatches(world.getConnection().getServerPlayer());
                        if (!server.overworld()
                                .getBlockState(new BlockPos(12, 80, 12))
                                .is(Blocks.GOLD_BLOCK))
                            throw new AssertionError("Ending capture discarded construction edits");
                    });

            // Rename the completed animation through the dedicated library, without losing either
            // program.
            TimelineClientGameTest.click(context, "Animations");
            context.waitForScreen(AnimationLibraryScreen.class);
            libraryName(context, "Editor_final");
            TimelineClientGameTest.click(context, "Renommer");
            context.waitFor(client -> EditorClient.state().sequence().equals("Editor_final"));
            world.getServer()
                    .runOnServer(server -> {
                        var service = fr.eligaimix.ltdr.LayoutDebugger.service(server);
                        if (service.personal(
                                        server.getPlayerList().getPlayers().getFirst().getUUID())
                                .containsKey("Editor_flow"))
                            throw new AssertionError("Rename kept its old document key");
                        var definition =
                                service.owned(
                                        server.getPlayerList().getPlayers().getFirst().getUUID(),
                                        "Editor_final");
                        if (definition.pistons().size() != 1
                                || definition.actions().size() != 1
                                || definition.closingActions().size() != 1)
                            throw new AssertionError("Rename lost the animation programs");
                        try {
                            var persisted =
                                    new fr.eligaimix.ltdr.storage.SequenceStore(
                                                    server.getWorldPath(LevelResource.ROOT)
                                                            .resolve(
                                                                    "data/layout-debugger/sequences.json"))
                                            .load();
                            if (persisted.containsKey(
                                            fr.eligaimix.ltdr.playback.LayoutService.key(
                                                    definition.owner(), "Editor_flow"))
                                    || !definition.equals(persisted.get(definition.storageKey())))
                                throw new AssertionError(
                                        "Rename did not persist the full document under its new"
                                            + " key");
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    });
            request(context, "create", "Taken", 0);
            context.waitFor(client -> EditorClient.state().sequence().equals("Taken"));
            context.runOnClient(client -> {
                var screen = (AnimationLibraryScreen) client.gui.screen();
                screen.children().stream()
                        .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast)
                        .filter(field ->
                                field.getMessage().getString().equals("Rechercher une animation"))
                        .findFirst()
                        .orElseThrow()
                        .setValue("Editor");
                screen.mouseClicked(
                        new net.minecraft.client.input.MouseButtonEvent(
                                30 * screen.uiScale(),
                                70 * screen.uiScale(),
                                new net.minecraft.client.input.MouseButtonInfo(0, 0)),
                        false);
            });
            context.waitFor(client -> EditorClient.state().sequence().equals("Editor_final"));
            context.runOnClient(client ->
                    client.gui.screen().children().stream()
                            .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                            .map(net.minecraft.client.gui.components.EditBox.class::cast)
                            .filter(field ->
                                    field.getMessage()
                                            .getString()
                                            .equals("Rechercher une animation"))
                            .findFirst()
                            .orElseThrow()
                            .setValue(""));
            var libraryOutput =
                    context.computeOnClient(client ->
                            client.gameDirectory
                                    .toPath()
                                    .toAbsolutePath()
                                    .resolve("../../ui-qa")
                                    .normalize());
            context.takeScreenshot(
                    TestScreenshotOptions.of("animations-populated")
                            .disableCounterPrefix()
                            .withDestinationDir(libraryOutput));
            request(context, "rename", "Editor_final:Taken", 0);
            context.waitFor(client -> EditorClient.state().message().startsWith("Erreur"));
            world.getServer()
                    .runOnServer(server -> {
                        var service = fr.eligaimix.ltdr.LayoutDebugger.service(server);
                        if (service.owned(
                                                        server.getPlayerList()
                                                                .getPlayers()
                                                                .getFirst()
                                                                .getUUID(),
                                                        "Editor_final")
                                                .actions()
                                                .size()
                                        != 1
                                || !service.owned(
                                                server.getPlayerList()
                                                        .getPlayers()
                                                        .getFirst()
                                                        .getUUID(),
                                                "Taken")
                                        .actions()
                                        .isEmpty())
                            throw new AssertionError(
                                    "Rejected rename overwrote an existing animation");
                    });
            TimelineClientGameTest.click(context, "Exporter");
            context.waitFor(
                    client -> {
                        var folder =
                                client.gameDirectory.toPath().resolve("layout-debugger/exports");
                        if (!java.nio.file.Files.isDirectory(folder)) return false;
                        try (var files = java.nio.file.Files.list(folder)) {
                            return files.anyMatch(
                                    p -> p.getFileName().toString().startsWith("Editor_final-"));
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    },
                    400);
            world.getServer()
                    .runOnServer(server -> {
                        var service = fr.eligaimix.ltdr.LayoutDebugger.service(server);
                        var owner = server.getPlayerList().getPlayers().getFirst().getUUID();
                        try {
                            service.create(
                                    java.util.UUID.randomUUID(),
                                    "Foreign_private",
                                    "minecraft:overworld");
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                        if (service.personal(owner).containsKey("Foreign_private"))
                            throw new AssertionError("Foreign creator leaked");
                    });
            request(context, "select", "Foreign_private", 0);
            context.waitFor(client -> EditorClient.state().message().startsWith("Erreur"));
            context.runOnClient(client -> {
                if (!EditorClient.state().sequence().equals("Editor_final")
                        || EditorClient.state().sequences().contains("Foreign_private"))
                    throw new AssertionError("Private network access failed");
            });
            TimelineClientGameTest.click(context, "Ouvrir");
            context.waitForScreen(TimelineScreen.class);

            // A view session starts without the capture button or a temporary inventory.
            request(context, "view", "", 8);
            context.waitFor(
                    client -> EditorClient.state().editing() && EditorClient.state().readOnly());
            world.getServer()
                    .runOnServer(server ->
                            originalInventory
                                    .get()
                                    .assertMatches(world.getConnection().getServerPlayer()));
            request(context, "rename", "Editor_final:During_view", 0);
            context.waitFor(client -> EditorClient.state().message().startsWith("Erreur"));
            context.runOnClient(client -> {
                if (!EditorClient.state().sequence().equals("Editor_final")
                        || EditorClient.state().sequences().contains("During_view"))
                    throw new AssertionError("An active view session was renamed");
            });
            int actionCount =
                    context.computeOnClient(client -> EditorClient.state().actions().size());
            request(context, "edit", id + ":on", 1);
            context.waitFor(client -> EditorClient.state().message().startsWith("Erreur"));
            context.runOnClient(client -> {
                if (EditorClient.state().actions().size() != actionCount)
                    throw new AssertionError("Read-only playback modified sequence data");
            });
            request(context, "last", "", 0);
            int lastPage = context.computeOnClient(client -> EditorClient.state().endPage());
            waitPage(context, lastPage);
            request(context, "seek", "", 0);
            waitPage(context, 0);
            assertExtended(world, false);
            request(context, "play", "", 0);
            context.waitFor(client -> EditorClient.state().playing());
            request(context, "pause", "", 0);
            context.waitFor(
                    client -> !EditorClient.state().playing() && !EditorClient.state().busy());
            context.setScreen(() -> null);
            context.getInput().pressKey(EditorClient.OPENING);
            waitPage(context, lastPage);
            assertExtended(world, true);
            context.getInput().pressKey(EditorClient.RESET);
            waitPage(context, 0);
            assertExtended(world, false);
            context.getInput().pressKey(EditorClient.CLOSING);
            context.waitFor(client -> EditorClient.state().timeline().equals("closing"));
            int closingEnd = context.computeOnClient(client -> EditorClient.state().endPage());
            waitPage(context, closingEnd);
            assertExtended(world, false);
            context.getInput().pressKey(EditorClient.RESET);
            waitPage(context, 0);
            assertExtended(world, true);
            switchTimeline(context, "opening");
            context.getInput().pressKey(EditorClient.RESET);
            context.waitFor(client -> EditorClient.state().timeline().equals("closing"));
            waitPage(context, 0);
            assertExtended(world, true);
            context.getInput().pressKey(EditorClient.NEXT);
            waitPage(context, 1);
            context.getInput().pressKey(EditorClient.PREVIOUS);
            waitPage(context, 0);
            assertExtended(world, true);
            request(context, "end", "", 0);
            context.waitFor(client -> !EditorClient.state().editing());
            world.getServer()
                    .runOnServer(server -> {
                        ServerPlayer player = world.getConnection().getServerPlayer();
                        originalInventory.get().assertMatches(player);
                        verifyDiskRecovery(player, originalInventory.get());
                    });
        }
    }

    private static void libraryName(ClientGameTestContext context, String value) {
        context.runOnClient(client ->
                client.gui.screen().children().stream()
                        .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                        .map(net.minecraft.client.gui.components.EditBox.class::cast)
                        .filter(field ->
                                field.getMessage().getString().equals("Nom de l'animation"))
                        .findFirst()
                        .orElseThrow()
                        .setValue(value));
    }

    private static void switchTimeline(ClientGameTestContext context, String timeline) {
        request(context, "timeline", timeline, 0);
        context.waitFor(
                client ->
                        EditorClient.state().timeline().equals(timeline)
                                && !EditorClient.state().busy(),
                400);
        waitPage(context, 0);
    }

    private static void construction(
            ClientGameTestContext context,
            TestSingleplayerContext world,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        world.getServer()
                .runOnServer(server -> {
                    ConstructionEdits.begin(world.getConnection().getServerPlayer());
                    try {
                        world.getConnection().getServerLevel().setBlock(pos, state, 3);
                    } finally {
                        ConstructionEdits.end();
                    }
                });
        context.waitTicks(4);
        waitPage(context, 0);
    }

    private static void request(
            ClientGameTestContext context, String operation, String value, int page) {
        context.runOnClient(client -> EditorClient.send(operation, value, page, 0));
    }

    private static void waitPage(ClientGameTestContext context, int page) {
        context.waitFor(
                client ->
                        EditorClient.state().editing()
                                && EditorClient.state().page() == page
                                && !EditorClient.state().busy()
                                && !EditorClient.state().seeking()
                                && !EditorClient.state().playing(),
                400);
    }

    private static void assertSeekMovingProgress(
            ClientGameTestContext context, TestSingleplayerContext world) {
        Map<BlockPos, Float> progress =
                world.getServer()
                        .computeOnServer(server -> {
                            Map<BlockPos, Float> values = new LinkedHashMap<>();
                            for (BlockPos pos : List.of(PISTON, STONE, PUSHED)) {
                                if (world.getConnection().getServerLevel().getBlockEntity(pos)
                                        instanceof PistonMovingBlockEntity moving)
                                    values.put(pos, moving.getProgress(1));
                            }
                            return values;
                        });
        context.runOnClient(client -> {
            for (var entry : progress.entrySet()) {
                if (!(client.level.getBlockEntity(entry.getKey())
                                instanceof PistonMovingBlockEntity moving)
                        || Math.abs(moving.getProgress(1) - entry.getValue()) > 0.001F) {
                    throw new AssertionError(
                            "Seek left a client piston animation behind the selected physical page"
                                + " at "
                                    + entry.getKey()
                                    + "; expected progress="
                                    + entry.getValue()
                                    + "; client BE="
                                    + client.level.getBlockEntity(entry.getKey()));
                }
            }
        });
    }

    private static void takeHighlightScreenshot(
            ClientGameTestContext context,
            TestSingleplayerContext world,
            String mode,
            int heldSlot) {
        context.setScreen(() -> null);
        world.getServer()
                .runOnServer(server -> {
                    ServerPlayer player = world.getConnection().getServerPlayer();
                    player.getInventory().setSelectedSlot(heldSlot);
                    player.connection.send(new ClientboundSetHeldSlotPacket(heldSlot));
                });
        world.getServer().runCommand("tp @a 11 83 11 135 45");
        context.waitTicks(3);
        var directory =
                context.computeOnClient(client ->
                        client.gameDirectory
                                .toPath()
                                .toAbsolutePath()
                                .resolve("../../ui-qa")
                                .normalize());
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        context.takeScreenshot(
                TestScreenshotOptions.of("piston-highlight-" + mode)
                        .disableCounterPrefix()
                        .withDestinationDir(directory));
    }

    private static void seedInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        ItemStack sword = named(new ItemStack(Items.DIAMOND_SWORD), "Épée avant capture");
        sword.set(DataComponents.DAMAGE, 17);
        player.getInventory().setItem(0, sword);
        ItemStack pistons = named(new ItemStack(Items.PISTON, 23), "Mes pistons personnels");
        CompoundTag custom = new CompoundTag();
        custom.putString("layout_note", "à conserver");
        pistons.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        player.getInventory().setItem(7, pistons);
        player.getInventory().setItem(17, new ItemStack(Items.REDSTONE, 64));
        player.getInventory().setItem(36, new ItemStack(Items.DIAMOND_BOOTS));
        player.getInventory().setItem(37, new ItemStack(Items.DIAMOND_LEGGINGS));
        player.getInventory().setItem(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
        player.getInventory()
                .setItem(39, named(new ItemStack(Items.DIAMOND_HELMET), "Casque avant capture"));
        player.getInventory().setItem(40, named(new ItemStack(Items.SHIELD), "Main secondaire"));
        player.getInventory().setItem(41, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        player.getInventory().setItem(42, new ItemStack(Items.SADDLE));
        player.getInventory().setSelectedSlot(7);
        var grid = player.inventoryMenu.getCraftSlots();
        grid.clearContent();
        grid.setItem(0, new ItemStack(Items.SLIME_BALL, 5));
        grid.setItem(3, new ItemStack(Items.STONE, 3));
        player.inventoryMenu.setCarried(
                named(new ItemStack(Items.PAPER, 6), "Objet sur le curseur"));
        player.connection.send(new ClientboundSetHeldSlotPacket(7));
        player.inventoryMenu.broadcastFullState();
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void assertTemporaryTools(ServerPlayer player) {
        if (!player.getInventory().getItem(0).is(EditorTools.ON)
                || !player.getInventory().getItem(1).is(EditorTools.OFF)
                || !player.getInventory().getItem(2).isEmpty()
                || player.getInventory().getSelectedSlot() != 0) {
            throw new AssertionError("Capture did not install a temporary three-tool hotbar");
        }
        for (int slot = 2; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty())
                throw new AssertionError(
                        "Original inventory slot leaked into the tool palette: " + slot);
        }
        for (int slot = 0; slot < player.inventoryMenu.getCraftSlots().getContainerSize(); slot++) {
            if (!player.inventoryMenu.getCraftSlots().getItem(slot).isEmpty())
                throw new AssertionError("Crafting inputs were not backed up privately");
        }
        if (!player.inventoryMenu.getCarried().isEmpty())
            throw new AssertionError("Cursor item was not backed up privately");
    }

    private static void verifyDiskRecovery(ServerPlayer player, InventoryFixture original) {
        var directory =
                player.level()
                        .getServer()
                        .getWorldPath(LevelResource.ROOT)
                        .resolve("data/layout-debugger/inventory-recovery-test");
        InventorySnapshot snapshot = InventorySnapshot.captureSaved(player, directory);
        snapshot.installTools(player);
        if (!InventorySnapshot.recoverSaved(player, directory))
            throw new AssertionError("Saved inventory backup was not recovered from disk");
        original.assertMatches(player);
        if (Files.exists(directory.resolve(player.getUUID() + ".json")))
            throw new AssertionError(
                    "Verified original player save did not retire its recovery backup");
    }

    private record InventoryFixture(
            List<ItemStack> slots, List<ItemStack> crafting, ItemStack cursor, int selected) {
        static InventoryFixture capture(ServerPlayer player) {
            var items = new ArrayList<ItemStack>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                items.add(player.getInventory().getItem(slot).copy());
            var craft = new ArrayList<ItemStack>();
            var grid = player.inventoryMenu.getCraftSlots();
            for (int slot = 0; slot < grid.getContainerSize(); slot++)
                craft.add(grid.getItem(slot).copy());
            return new InventoryFixture(
                    items,
                    craft,
                    player.inventoryMenu.getCarried().copy(),
                    player.getInventory().getSelectedSlot());
        }

        void assertMatches(ServerPlayer player) {
            for (int slot = 0; slot < slots.size(); slot++)
                if (!ItemStack.matches(slots.get(slot), player.getInventory().getItem(slot))) {
                    throw new AssertionError(
                            "Inventory count/components were not restored exactly in slot " + slot);
                }
            for (int slot = 0; slot < crafting.size(); slot++)
                if (!ItemStack.matches(
                        crafting.get(slot), player.inventoryMenu.getCraftSlots().getItem(slot))) {
                    throw new AssertionError("Crafting input was not restored in slot " + slot);
                }
            if (!ItemStack.matches(cursor, player.inventoryMenu.getCarried())
                    || selected != player.getInventory().getSelectedSlot()) {
                throw new AssertionError("Cursor item or selected hotbar slot was not restored");
            }
        }
    }

    private static void assertExtended(TestSingleplayerContext world, boolean extended) {
        world.getServer()
                .runOnServer(server -> {
                    var level = world.getConnection().getServerLevel();
                    var piston = level.getBlockState(PISTON);
                    if (!piston.is(Blocks.STICKY_PISTON)
                            || piston.getValue(PistonBaseBlock.EXTENDED) != extended) {
                        throw new AssertionError("Unexpected physical piston state: " + piston);
                    }
                    if (!level.getBlockState(extended ? PUSHED : STONE).is(Blocks.STONE)) {
                        throw new AssertionError(
                                "Vanilla piston did not move the stone to its expected position");
                    }
                    if (!level.getBlockState(extended ? STONE : PUSHED).isAir()) {
                        // The extended piston head occupies the old stone position.
                        if (!extended || !level.getBlockState(STONE).is(Blocks.PISTON_HEAD))
                            throw new AssertionError("Unexpected block left behind by replay");
                    }
                });
    }
}
