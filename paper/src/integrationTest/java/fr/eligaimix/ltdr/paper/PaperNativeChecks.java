package fr.eligaimix.ltdr.paper;

import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.preview.*;
import fr.eligaimix.ltdr.sequence.*;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.*;
import java.util.*;

/** Test-only plugin, excluded from release artifacts; runs in an expendable loopback world. */
public final class PaperNativeChecks extends JavaPlugin implements Listener {
    private static final BlockPos A = new BlockPos(128, 80, 0), B = A.east();
    private RedstonePistonController controller;
    private ServerLevel level;
    private WorldSnapshot snapshot;
    private int stage, checks, waits;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LayoutDebugger")) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler()
                .runTaskTimer(
                        this,
                        () -> {
                            try {
                                step();
                            } catch (Throwable error) {
                                fail(error);
                            }
                        },
                        40,
                        6);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        player.teleport(new org.bukkit.Location(player.getWorld(), 8.5, 84, 8.5));
        player.setAllowFlight(true);
        player.setFlying(true);
        if (player.getName().equals("Player0")) {
            player.setOp(false);
            player.addAttachment(this, "layoutdebugger.use", true);
            var nativePlayer = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
            try {
                var service = fr.eligaimix.ltdr.LayoutDebugger.active;
                UUID other = UUID.fromString("a0c09000-0000-4000-8000-000000000001");
                if (!service.personal(other).containsKey("Other_private"))
                    service.create(other, "Other_private", "minecraft:overworld");
                check(
                        !service.personal(player.getUniqueId()).containsKey("Other_private"),
                        "Other creator omitted from personal catalog");
                try {
                    service.owned(player.getUniqueId(), "Other_private");
                    throw new AssertionError("Other creator document accessible");
                } catch (IllegalArgumentException expected) {
                }
                check(
                        !player.hasPermission("layoutdebugger.admin.view"),
                        "Non-OP use permission does not grant admin view");
                var messages = new ArrayList<String>();
                try {
                    fr.eligaimix.ltdr.storage.AnimationCommands.admin(
                            service,
                            new String[] {"list", other.toString()},
                            false,
                            false,
                            player.getUniqueId(),
                            messages::add);
                    throw new AssertionError("Admin list allowed without permission");
                } catch (IllegalArgumentException expected) {
                }
                check(messages.isEmpty(), "Unauthorized inspection emits no documents");
                fr.eligaimix.ltdr.storage.AnimationCommands.admin(
                        service,
                        new String[] {"show", other.toString(), "Other_private"},
                        true,
                        false,
                        player.getUniqueId(),
                        messages::add);
                check(
                        messages.stream().anyMatch(m -> m.contains("Other_private")),
                        "Explicit admin view can inspect another creator");
                try {
                    fr.eligaimix.ltdr.storage.AnimationCommands.admin(
                            service,
                            new String[] {"assign", "legacy", player.getUniqueId().toString()},
                            true,
                            false,
                            player.getUniqueId(),
                            messages::add);
                    throw new AssertionError("Read permission granted assign");
                } catch (IllegalArgumentException expected) {
                }
                getLogger()
                        .info(
                                "Paper ownership checks passed: private catalog, denied lookup,"
                                    + " admin view and separate assign.");
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            var stack =
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.DIAMOND, 23);
            stack.set(
                    net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("Paper inventory backup fixture"));
            nativePlayer.getInventory().clearContent();
            nativePlayer.getInventory().setItem(7, stack);
            nativePlayer.getInventory().setSelectedSlot(7);
            nativePlayer.inventoryMenu.broadcastChanges();
            nativePlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(7));
            var item =
                    new net.minecraft.world.entity.item.ItemEntity(
                            nativePlayer.level(),
                            11.5,
                            82,
                            11.5,
                            new net.minecraft.world.item.ItemStack(
                                    net.minecraft.world.item.Items.SLIME_BALL, 4));
            item.setNoGravity(true);
            item.setNeverPickUp();
            nativePlayer.level().addFreshEntity(item);
        }
    }

    private void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks++;
    }

    private void step() throws Exception {
        switch (stage++) {
            case 0 -> {
                var world = Bukkit.getWorlds().getFirst();
                level = ((CraftWorld) world).getHandle();
                for (int x = 6; x <= 9; x++)
                    for (int z = -2; z <= 1; z++) world.setChunkForceLoaded(x, z, true);
            }
            case 1 -> {
                for (int x = 6; x <= 9; x++)
                    for (int z = -2; z <= 1; z++) {
                        var sample = new BlockPos(x << 4, 80, z << 4);
                        if (!level.isLoaded(sample) || !level.shouldTickBlocksAt(sample)) {
                            if (++waits > 100)
                                throw new AssertionError(
                                        "Forced test chunks did not become block ticking");
                            stage--;
                            return;
                        }
                    }
                var world = Bukkit.getWorlds().getFirst();
                level = ((CraftWorld) world).getHandle();
                for (int x = 6; x <= 9; x++)
                    for (int z = -2; z <= 1; z++) world.setChunkForceLoaded(x, z, true);
                for (var pos : BlockPos.betweenClosed(124, 78, -3, 141, 87, 3))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                level.setBlock(
                        A,
                        Blocks.STICKY_PISTON
                                .defaultBlockState()
                                .setValue(PistonBaseBlock.FACING, Direction.EAST),
                        3);
                level.setBlock(
                        B,
                        Blocks.PISTON
                                .defaultBlockState()
                                .setValue(PistonBaseBlock.FACING, Direction.UP),
                        3);
                controller =
                        new RedstonePistonController(
                                level,
                                List.of(
                                        new PistonTarget(
                                                "carrier",
                                                new BlockPosition(A.getX(), A.getY(), A.getZ()),
                                                "east",
                                                true),
                                        new PistonTarget(
                                                "carried",
                                                new BlockPosition(B.getX(), B.getY(), B.getZ()),
                                                "up",
                                                false)));
                snapshot = WorldSnapshot.capture(level, new EditBounds(124, 78, -3, 141, 87, 3));
                controller.setPowered("carrier", true);
            }
            case 2 -> {
                check(
                        level.getBlockState(A).getValue(PistonBaseBlock.EXTENDED),
                        "Virtual ON must use native extension");
                check(
                        controller.positions().get("carried").equals(B.east()),
                        "Carried piston keeps its identity");
                controller.setPowered("carried", true);
            }
            case 3 -> {
                check(
                        level.getBlockState(B.east()).getValue(PistonBaseBlock.EXTENDED),
                        "Carried piston can extend at its new position");
                check(controller.hasEffectivePower("carried"), "Native additive signal query");
                controller.setPowered("carried", false);
            }
            case 4 -> {
                check(
                        !level.getBlockState(B.east()).getValue(PistonBaseBlock.EXTENDED),
                        "OFF retracts natively");
                level.setBlock(B.east().below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            }
            case 5 -> {
                check(
                        level.getBlockState(B.east()).getValue(PistonBaseBlock.EXTENDED),
                        "Real redstone still extends piston after mod OFF");
                check(
                        !controller.isModPowered("carried")
                                && controller.hasEffectivePower("carried"),
                        "OFF only removes mod input");
                level.setBlock(B.east().below(), Blocks.AIR.defaultBlockState(), 3);
            }
            case 6 -> {
                check(
                        !level.getBlockState(B.east()).getValue(PistonBaseBlock.EXTENDED),
                        "Removing real power retracts");
                controller.setPowered("carrier", false);
            }
            case 7 -> {
                check(
                        controller.positions().get("carried").equals(B),
                        "Sticky retraction tracks carried identity");
                controller.release();
                snapshot.restore();
                check(
                        level.getBlockState(A).is(Blocks.STICKY_PISTON)
                                && !level.getBlockState(A).getValue(PistonBaseBlock.EXTENDED),
                        "Snapshot restores native baseline");
                check(level.getBlockState(B).is(Blocks.PISTON), "Snapshot restores carried piston");
                // Fixed client fixture; tests will capture/edit it through the actual network
                // interface.
                level.setBlock(
                        new BlockPos(8, 80, 8),
                        Blocks.STICKY_PISTON
                                .defaultBlockState()
                                .setValue(PistonBaseBlock.FACING, Direction.EAST),
                        3);
                level.setBlock(new BlockPos(9, 80, 8), Blocks.STONE.defaultBlockState(), 3);
                String result =
                        "PASS: "
                                + checks
                                + " Paper native checks (virtual power, real power, nested pistons,"
                                + " snapshot).";
                Files.writeString(Path.of("paper-integration-result.txt"), result);
                getLogger().info(result);
                Bukkit.getScheduler().cancelTasks(this);
            }
            default -> throw new AssertionError("Unexpected stage");
        }
    }

    private void fail(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "Paper native verification failed", error);
        try {
            if (controller != null) controller.release();
            Files.writeString(Path.of("paper-integration-result.txt"), "FAIL: " + error);
        } catch (Exception ignored) {
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
