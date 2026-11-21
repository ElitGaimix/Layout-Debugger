package fr.eligaimix.ltdr.gametest;

import fr.eligaimix.ltdr.client.editor.*;
import fr.eligaimix.ltdr.network.BridgeProtocol;
import fr.eligaimix.ltdr.playback.EditorTools;

import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import java.nio.file.*;
import java.util.*;

/** Opt-in test against a disposable local Paper server; no production server address accepted. */
public final class PaperBridgeClientGameTest implements FabricClientGameTest {
    private static final BlockPos PISTON = new BlockPos(8, 80, 8);

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getenv("LAYOUT_DEBUGGER_PAPER_TEST_ADDRESS");
        if (address == null || address.isBlank()) return;
        if (!address.matches("127\\.0\\.0\\.1:[0-9]{1,5}"))
            throw new IllegalArgumentException(
                    "Paper integration tests require an explicit loopback address");
        try {
            context.runOnClient(client ->
                    ConnectScreen.startConnecting(
                            new TitleScreen(),
                            client,
                            ServerAddress.parseString(address),
                            new ServerData(
                                    "Disposable Paper tests", address, ServerData.Type.OTHER),
                            false,
                            null));
            context.waitFor(client -> client.level != null && client.player != null, 1200);
            context.waitFor(
                    client ->
                            EditorClient.paperBackend()
                                    && EditorClient.supports(BridgeProtocol.EDIT),
                    600);
            context.waitFor(
                    client -> client.level.getBlockState(PISTON).is(Blocks.STICKY_PISTON), 600);
            context.waitFor(
                    client -> client.level.hasChunk(-1, -1) && client.level.hasChunk(2, 2), 600);
            context.waitTicks(20);
            var inventory =
                    context.computeOnClient(client -> {
                        var items = new ArrayList<ItemStack>();
                        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++)
                            items.add(client.player.getInventory().getItem(i).copy());
                        return items;
                    });
            int selected =
                    context.computeOnClient(
                            client -> client.player.getInventory().getSelectedSlot());
            context.runOnClient(client -> {
                if (inventory.stream().allMatch(ItemStack::isEmpty))
                    throw new AssertionError("Expected a nonempty tagged inventory fixture");
            });
            context.runOnClient(client -> EditorClient.open());
            context.waitForScreen(AnimationLibraryScreen.class);
            TimelineClientGameTest.click(context, "Param.");
            context.waitForScreen(EditorSettingsScreen.class);
            TimelineClientGameTest.click(context, "Langue : Français");
            context.runOnClient(client -> {
                if (!EditorPreferences.language().equals("en_us"))
                    throw new AssertionError("Language preference did not switch to English");
                if (!ClientText.tr("ui.language").equals("Language: English"))
                    throw new AssertionError("English UI catalog not active");
            });
            TimelineClientGameTest.click(context, "Back");
            context.waitForScreen(AnimationLibraryScreen.class);
            TimelineClientGameTest.click(context, "Settings");
            context.waitForScreen(EditorSettingsScreen.class);
            TimelineClientGameTest.click(context, "Language: English");
            TimelineClientGameTest.click(context, "Retour");
            context.waitForScreen(AnimationLibraryScreen.class);
            context.runOnClient(client -> {
                if (EditorClient.state().sequences().contains("Other_private"))
                    throw new AssertionError("Remote catalog leaks another owner");
            });
            request(context, "select", "Other_private", 0);
            context.waitFor(client -> EditorClient.state().message().startsWith("Erreur"), 400);
            context.runOnClient(client -> {
                if (EditorClient.state().sequence().equals("Other_private"))
                    throw new AssertionError("Network selection crossed creator boundary");
            });
            String persisted = System.getenv("LAYOUT_DEBUGGER_PAPER_EXPECTED_ANIMATION");
            if (persisted != null && !persisted.isBlank()) {
                context.runOnClient(client -> {
                    if (!EditorClient.state().sequences().contains(persisted))
                        throw new AssertionError("Saved animation missing after Paper restart");
                });
                request(context, "select", persisted, 0);
                context.waitFor(client -> EditorClient.state().sequence().equals(persisted), 400);
                context.runOnClient(client -> {
                    if (EditorClient.state().actions().isEmpty())
                        throw new AssertionError("Persisted opening actions lost after restart");
                });
            }
            String name = "Paper_" + Long.toUnsignedString(System.nanoTime(), 36);
            request(context, "create", name, 0);
            context.waitFor(client -> EditorClient.state().sequence().equals(name));
            request(context, "rename", name + ":" + name + "_ok", 0);
            context.waitFor(client -> EditorClient.state().sequence().equals(name + "_ok"));
            context.runOnClient(client -> client.gui.setScreen(new TimelineScreen()));
            request(context, "begin", "", 8);
            context.waitFor(
                    client -> EditorClient.state().editing() && !EditorClient.state().busy(), 1200);
            context.runOnClient(client -> {
                boolean on = false, off = false;
                for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                    String mode = EditorTools.mode(client.player.getInventory().getItem(i));
                    on |= "on".equals(mode);
                    off |= "off".equals(mode);
                }
                if (!on || !off)
                    throw new AssertionError(
                            "Tagged Paper tools were not recognized by the client");
            });
            physicalTool(context, "on");
            waitPage(context, 1);
            physicalTool(context, "on");
            context.waitFor(
                    client ->
                            EditorClient.state().actions().isEmpty()
                                    && !EditorClient.state().busy(),
                    600);
            physicalTool(context, "on");
            context.waitFor(
                    client ->
                            EditorClient.state().actions().size() == 1
                                    && !EditorClient.state().busy(),
                    600);
            request(context, "seek", "", 4);
            waitPage(context, 4);
            context.waitTicks(2);
            var hudOutput =
                    context.computeOnClient(client ->
                            client.gameDirectory
                                    .toPath()
                                    .toAbsolutePath()
                                    .resolve("../../ui-qa")
                                    .normalize());
            context.takeScreenshot(
                    net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of(
                                    "paper-hud-merged")
                            .disableCounterPrefix()
                            .withDestinationDir(hudOutput));
            context.waitFor(
                    client ->
                            client.level.getBlockState(PISTON).is(Blocks.STICKY_PISTON)
                                    && client.level
                                            .getBlockState(PISTON)
                                            .getValue(PistonBaseBlock.EXTENDED),
                    400);
            request(context, "timeline", "closing", 0);
            waitPage(context, 0);
            physicalTool(context, "off");
            waitPage(context, 1);
            request(context, "seek", "", 4);
            waitPage(context, 4);
            context.waitFor(client -> settled(client, false), 400);
            request(context, "end", "", 0);
            context.waitFor(client -> !EditorClient.state().editing(), 400);
            context.runOnClient(client -> {
                if (client.player.getInventory().getSelectedSlot() != selected)
                    throw new AssertionError("Selected slot not restored on Paper");
                for (int i = 0; i < inventory.size(); i++)
                    if (!ItemStack.matches(
                            inventory.get(i), client.player.getInventory().getItem(i)))
                        throw new AssertionError(
                                "Remote Paper inventory restoration failed at slot " + i);
            });
            request(context, "live_open", name + "_ok", 0);
            context.waitFor(
                    client -> EditorClient.state().playing() && !EditorClient.state().editing(),
                    400);
            context.waitFor(client -> settled(client, true), 400);
            context.waitTicks(6);
            request(context, "live_close", name + "_ok", 0);
            context.waitFor(client -> settled(client, false), 400);
            context.runOnClient(client -> client.gui.setScreen(null));
            context.getInput().pressKey(EditorClient.PLAY);
            context.waitFor(client -> !EditorClient.state().playing(), 400);
            context.runOnClient(client -> client.gui.setScreen(new TimelineScreen()));
            if (Boolean.parseBoolean(System.getenv("LAYOUT_DEBUGGER_PAPER_MULTIPLAYER"))) {
                context.waitTicks(6);
                request(context, "begin", "", 8);
                context.waitFor(
                        client -> EditorClient.state().editing() && !EditorClient.state().busy(),
                        1200);
                marker(context, "paper-client-ready-for-vanilla.txt");
                context.waitFor(
                        client -> EditorClient.state().editing() && EditorClient.state().realtime(),
                        1200);
                context.waitFor(client -> !client.level.tickRateManager().isFrozen(), 400);
                request(context, "seek", "", 4);
                waitPage(context, 4);
                request(context, "seek", "", 0);
                waitPage(context, 0);
                request(
                        context,
                        "worldtool",
                        "@" + EditorClient.state().pistons().getFirst().id() + ":on",
                        0);
                context.waitFor(client -> !EditorClient.state().busy(), 600);
                request(context, "end", "", 0);
                context.waitFor(client -> !EditorClient.state().editing(), 400);
                context.runOnClient(client -> {
                    for (int i = 0; i < inventory.size(); i++)
                        if (!ItemStack.matches(
                                inventory.get(i), client.player.getInventory().getItem(i)))
                            throw new AssertionError("Shared capture inventory restore failed");
                });
                request(context, "begin", "", 8);
                context.waitFor(
                        client ->
                                EditorClient.state().editing()
                                        && EditorClient.state().realtime()
                                        && !EditorClient.state().busy(),
                        600);
                request(context, "end", "", 0);
                context.waitFor(client -> !EditorClient.state().editing(), 400);
                marker(context, "paper-client-vanilla-finished.txt");
                context.waitFor(client -> EditorClient.supports(BridgeProtocol.EDIT), 1200);
                request(context, "begin", "", 8);
                context.waitFor(
                        client -> EditorClient.state().editing() && !EditorClient.state().busy(),
                        1200);
                request(context, "end", "", 0);
                context.waitFor(client -> !EditorClient.state().editing(), 400);
            }
            context.runOnClient(client -> client.gui.setScreen(new AnimationLibraryScreen(null)));
            TimelineClientGameTest.click(context, "Exporter");
            context.waitFor(
                    client -> {
                        Path folder =
                                client.gameDirectory.toPath().resolve("layout-debugger/exports");
                        if (!Files.isDirectory(folder)) return false;
                        try (var files = Files.list(folder)) {
                            return files.anyMatch(
                                    p -> p.getFileName().toString().startsWith(name + "_ok-"));
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    },
                    400);
            Path output =
                    context.computeOnClient(client ->
                            client.gameDirectory
                                    .toPath()
                                    .toAbsolutePath()
                                    .resolve("../../paper-client-verification.txt")
                                    .normalize());
            try {
                Files.writeString(
                        output,
                        "PASS: Paper handshake, library create/rename, capture, tagged tools,"
                            + " opening/closing native movement, seek, inventory restoration, live"
                            + " playback, creator isolation and requested JSON download.");
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        } finally {
            context.runOnClient(client -> {
                client.disconnectWithProgressScreen(false);
                client.gui.setScreen(new TitleScreen());
            });
            context.waitFor(client -> client.level == null);
        }
    }

    private static boolean settled(net.minecraft.client.Minecraft client, boolean extended) {
        var state = client.level.getBlockState(PISTON);
        return state.is(Blocks.STICKY_PISTON)
                && state.getValue(PistonBaseBlock.EXTENDED) == extended;
    }

    private static void physicalTool(ClientGameTestContext context, String mode) {
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            client.player.setXRot(90);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            int slot = -1;
            for (int i = 0; i < 9; i++)
                if (mode.equals(EditorTools.mode(client.player.getInventory().getItem(i)))) {
                    slot = i;
                    break;
                }
            if (slot < 0) throw new AssertionError("Missing physical Paper tool " + mode);
            client.player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItemOn(
                    client.player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                            new net.minecraft.world.phys.Vec3(8.5, 81, 8.5),
                            net.minecraft.core.Direction.UP,
                            PISTON,
                            false));
        });
        context.waitTicks(2);
    }

    private static void marker(ClientGameTestContext context, String name) {
        Path file =
                context.computeOnClient(client ->
                        client.gameDirectory
                                .toPath()
                                .toAbsolutePath()
                                .resolve("../../" + name)
                                .normalize());
        try {
            Files.writeString(file, "ready");
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static void request(
            ClientGameTestContext context, String operation, String value, int page) {
        context.runOnClient(client -> EditorClient.send(operation, value, page, 0));
        context.waitTick();
    }

    private static void waitPage(ClientGameTestContext context, int page) {
        context.waitFor(
                client ->
                        EditorClient.state().page() == page
                                && !EditorClient.state().busy()
                                && !EditorClient.state().seeking(),
                1200);
    }
}
