package fr.eligaimix.ltdr.paper;

import fr.eligaimix.ltdr.LayoutDebugger;
import fr.eligaimix.ltdr.bridge.HookBus;
import fr.eligaimix.ltdr.network.*;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.playback.*;
import fr.eligaimix.ltdr.preview.*;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

import java.util.*;

/**
 * Bukkit events cover user interactions/movement; only missing native hooks use the startup bridge.
 */
public final class PaperEngine implements HookBus.Handler, Listener {
    private final LayoutDebuggerPaper plugin;
    private final MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
    private LayoutService layouts;
    private LivePlayback live;

    public PaperEngine(LayoutDebuggerPaper plugin) {
        this.plugin = plugin;
    }

    public LayoutService layouts() {
        return layouts;
    }

    public void enable() throws Exception {
        if (HookBus.version() != 1 || HookBus.handler != null)
            throw new IllegalStateException("Pont de démarrage incompatible ou déjà utilisé.");
        for (String name :
                List.of(
                        "world.level.block.piston.PistonBaseBlock",
                        "server.MinecraftServer",
                        "server.level.ServerLevel",
                        "server.network.ServerCommonPacketListenerImpl",
                        "server.network.ServerGamePacketListenerImpl",
                        "world.level.Level",
                        "server.ServerTickRateManager")) {
            String type = "net.minecraft." + name;
            Class.forName(type, true, server.getClass().getClassLoader());
            if (!HookBus.transformed.contains(type.replace('.', '/')))
                throw new IllegalStateException("Hook absent : " + type + " ; " + HookBus.failure);
        }
        if (HookBus.failure != null) throw new IllegalStateException(HookBus.failure);
        layouts =
                new LayoutService(
                        server, plugin.getDataFolder().toPath().resolve("sequences.json"));
        layouts.definitions();
        LayoutDebugger.active = layouts;
        fr.eligaimix.ltdr.platform.EditorAccess.install(
                player -> plugin.authorized(player.getBukkitEntity()));
        live = new LivePlayback(layouts);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        HookBus.handler = this;
    }

    public int capabilities() {
        return BridgeProtocol.ALL;
    }

    public void request(ServerPlayer player, EditorRequest request) {
        if (!plugin.authorized(player.getBukkitEntity()))
            throw new IllegalStateException(
                    "Créatif et permission layoutdebugger.use nécessaires.");
        switch (request.operation()) {
            case "live_open", "live_close" -> {
                if (layouts.editor().isActive())
                    throw new IllegalStateException(
                            "Terminer la capture avant la lecture en direct.");
                live.start(player, request.value(), request.operation().equals("live_close"));
                refresh(player, "Lecture en direct : le monde continue à fonctionner.");
            }
            case "live_stop" -> {
                live.stop(player);
                refresh(player, "Lecture arrêtée ; signal du mod retiré.");
            }
            default -> {
                if (live.active() && !request.operation().equals("open"))
                    throw new IllegalStateException(
                            "Arrêter la lecture en direct avant de modifier la sélection ou de"
                                    + " capturer.");
                layouts.editor().request(player, request);
            }
        }
    }

    public void refresh(ServerPlayer player, String message) {
        layouts.editor().refresh(player, message);
    }

    public EditorState decorate(ServerPlayer player, EditorState state) {
        return live == null ? state : live.decorate(player, state);
    }

    public void join(Player player) {
        if (Bukkit.getOnlinePlayers().size() > 1) layouts.editor().useRealtime();
        layouts.editor().recoverInventory(((CraftPlayer) player).getHandle());
    }

    public void quit(Player player) {
        var nativePlayer = ((CraftPlayer) player).getHandle();
        fr.eligaimix.ltdr.i18n.ServerLanguage.forget(nativePlayer);
        try {
            if (layouts.editor().isOwner(nativePlayer)) layouts.editor().interruptForMultiplayer();
        } finally {
            live.disconnect(nativePlayer);
        }
    }

    public void disable() {
        if (layouts == null) return;
        boolean ownedPause = layouts.editor().controlsTicks();
        try {
            if (live != null) live.shutdown();
            layouts.shutdown();
        } finally {
            RedstonePistonController.releaseAll(server);
            SeekSprint.forget(server);
            HookBus.handler = null;
            LayoutDebugger.active = null;
            fr.eligaimix.ltdr.platform.EditorAccess.reset();
            if (ownedPause && layouts.editor().isActive()) {
                server.tickRateManager().stopSprinting();
                server.tickRateManager().stopStepping();
                server.tickRateManager().setFrozen(false);
            }
        }
    }

    @Override
    public boolean power(Object level, Object pos) {
        return level instanceof net.minecraft.world.level.SignalGetter signal
                && RedstonePistonController.hasVirtualPower(signal, (BlockPos) pos);
    }

    @Override
    public void serverTick(Object source, boolean end) {
        if (source != server) return;
        if (end) layouts.editor().endServerTick();
        else {
            if (layouts.editor().isActive()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (layouts.editor().isOwner(((CraftPlayer) player).getHandle())
                            && !plugin.authorized(player)) {
                        try {
                            layouts.editor().interruptForMultiplayer();
                        } catch (RuntimeException exception) {
                            plugin.getLogger()
                                    .log(
                                            java.util.logging.Level.SEVERE,
                                            "Fermeture après perte des droits ; récupération"
                                                    + " conservée",
                                            exception);
                        }
                        refresh(
                                ((CraftPlayer) player).getHandle(),
                                "Édition fermée : droits d'utilisation perdus.");
                        break;
                    }
                }
            }
            layouts.editor().prepareTick();
        }
    }

    @Override
    public void levelTick(Object source, boolean end) {
        var level = (ServerLevel) source;
        if (level.getServer() != server) return;
        if (end) layouts.editor().endLevelTick(level);
        else {
            layouts.editor().startLevelTick(level);
            layouts.tick(level);
            live.tick(level);
        }
    }

    @Override
    public boolean suppress(Object listener, Object packet, Object completion) {
        return completion == null
                && listener instanceof ServerGamePacketListenerImpl game
                && ReplayVisibility.suppress(game.player, (Packet<?>) packet);
    }

    @Override
    public void construction(Object listener, boolean end) {
        if (!server.isSameThread()) return;
        if (end) ConstructionEdits.end();
        else ConstructionEdits.begin(((ServerGamePacketListenerImpl) listener).player);
    }

    @Override
    public void changed(Object level, Object pos) {
        ConstructionEdits.changed((Level) level, (BlockPos) pos);
    }

    @Override
    public Object quiet(Object source) {
        var commands = (CommandSourceStack) source;
        return SeekSprint.isOwned(server) ? commands.withSuppressedOutput() : commands;
    }

    @Override
    public void sprintFinished(Object manager) {
        SeekSprint.finished(server);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() != null
                && layouts.editor()
                        .reservedForOther(
                                ((CraftPlayer) event.getPlayer()).getHandle(),
                                new BlockPos(
                                        event.getClickedBlock().getX(),
                                        event.getClickedBlock().getY(),
                                        event.getClickedBlock().getZ()))) {
            event.setCancelled(true);
            return;
        }
        var player = ((CraftPlayer) event.getPlayer()).getHandle();
        String mode = EditorTools.mode(player.getMainHandItem());
        if (mode != null) {
            event.setCancelled(true);
            if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    || event.getClickedBlock() == null
                    || !plugin.authorized(event.getPlayer())
                    || !plugin.peer(player)
                    || !layouts.editor().isOwner(player)) return;
            var block = event.getClickedBlock();
            var pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            if (event.getPlayer().isSneaking())
                layouts.editor()
                        .request(
                                player,
                                new EditorRequest(
                                        "ignore_warning",
                                        pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                                        0,
                                        0));
            else layouts.editor().useTool(player, pos, mode);
        } else if (layouts.editor().isOwner(player) && !layouts.editor().mayBuild(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (EditorTools.mode(
                        ((CraftPlayer) event.getPlayer())
                                .getHandle()
                                .getItemInHand(
                                        event.getHand() == EquipmentSlot.HAND
                                                ? net.minecraft.world.InteractionHand.MAIN_HAND
                                                : net.minecraft.world.InteractionHand.OFF_HAND))
                != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breaking(BlockBreakEvent event) {
        var player = ((CraftPlayer) event.getPlayer()).getHandle();
        if (layouts.editor()
                        .reservedForOther(
                                player,
                                new BlockPos(
                                        event.getBlock().getX(),
                                        event.getBlock().getY(),
                                        event.getBlock().getZ()))
                || EditorTools.mode(player.getMainHandItem()) != null
                || layouts.editor().isOwner(player) && !layouts.editor().mayBuild(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void placing(BlockPlaceEvent event) {
        var player = ((CraftPlayer) event.getPlayer()).getHandle();
        if (layouts.editor()
                        .reservedForOther(
                                player,
                                new BlockPos(
                                        event.getBlock().getX(),
                                        event.getBlock().getY(),
                                        event.getBlock().getZ()))
                || layouts.editor().isOwner(player) && !layouts.editor().mayBuild(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void itemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        layouts.editor()
                .recordSimulationItem(
                        ((org.bukkit.craftbukkit.entity.CraftItem) event.getEntity()).getHandle());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extending(BlockPistonExtendEvent event) {
        movement(event, event.getBlocks(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retracting(BlockPistonRetractEvent event) {
        movement(event, event.getBlocks(), true);
    }

    private void movement(
            BlockPistonEvent event, List<org.bukkit.block.Block> blocks, boolean retract) {
        var level = ((org.bukkit.craftbukkit.CraftWorld) event.getBlock().getWorld()).getHandle();
        var pushed = new ArrayList<BlockPos>();
        var broken = new ArrayList<BlockPos>();
        for (var block : blocks) {
            var pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            if (level.getBlockState(pos).getPistonPushReaction() == PushReaction.DESTROY)
                broken.add(pos);
            else pushed.add(pos);
        }
        var face = event.getDirection();
        var direction = Direction.byName(face.name().toLowerCase(java.util.Locale.ROOT));
        if (direction == null) throw new IllegalStateException("Direction de piston invalide.");
        if (!retract) {
            var b = event.getBlock();
            RedstonePistonController.recordSpace(
                    level, new BlockPos(b.getX(), b.getY(), b.getZ()).relative(direction));
        }
        RedstonePistonController.beforeMove(level, pushed, broken, direction);
    }
}
