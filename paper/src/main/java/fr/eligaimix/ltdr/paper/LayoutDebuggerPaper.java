package fr.eligaimix.ltdr.paper;

import com.google.gson.Gson;

import fr.eligaimix.ltdr.network.*;

import net.minecraft.server.level.ServerPlayer;

import org.bukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

/** Optional-client plugin. The startup component supplies the narrowly scoped native hooks. */
public final class LayoutDebuggerPaper extends JavaPlugin
        implements Listener, PluginMessageListener {
    public static LayoutDebuggerPaper instance;
    public static final String HELLO = "layout-debugger:bridge_hello",
            REQUEST = "layout-debugger:editor_request",
            STATE = "layout-debugger:editor_state",
            OPEN = "layout-debugger:editor_open",
            EXPORT = "layout-debugger:animation_export";
    private static final Gson JSON = new Gson();
    private final Set<UUID> peers = new HashSet<>();
    private final Map<UUID, RateWindow> rates = new HashMap<>();
    private PaperEngine engine;

    private static final class RateWindow {
        long start = System.nanoTime();
        int count;
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            if (!Bukkit.getMinecraftVersion().equals("26.2")
                    || !Bukkit.getVersion().contains("e4d6dfd"))
                throw new IllegalStateException("Build ciblée : Paper 26.2 build 135 (e4d6dfd).");
            Class.forName("fr.eligaimix.ltdr.bridge.HookBus", false, null);
            engine = new PaperEngine(this);
            engine.enable();
        } catch (Exception | LinkageError exception) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Backend indisponible. Installer le composant -javaagent compatible ;"
                                    + " aucun éditeur activé.",
                            exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        var messenger = getServer().getMessenger();
        for (String channel : List.of(HELLO, STATE, OPEN, EXPORT))
            messenger.registerOutgoingPluginChannel(this, channel);
        for (String channel : List.of(HELLO, REQUEST))
            messenger.registerIncomingPluginChannel(this, channel, this);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("ld"))
                .setExecutor((sender, command, label, args) -> {
                    if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                        try {
                            fr.eligaimix.ltdr.storage.AnimationCommands.admin(
                                    engine.layouts(),
                                    Arrays.copyOfRange(args, 1, args.length),
                                    sender.hasPermission("layoutdebugger.admin.view"),
                                    sender.hasPermission("layoutdebugger.admin.assign"),
                                    sender instanceof Player p ? p.getUniqueId() : null,
                                    sender::sendMessage);
                        } catch (Exception e) {
                            sender.sendMessage("Layout Debugger: " + e.getMessage());
                        }
                        return true;
                    }
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Commande réservée aux joueurs.");
                        return true;
                    }
                    if (!authorized(player)) {
                        player.sendMessage(
                                fr.eligaimix.ltdr.i18n.ServerLanguage.text(
                                        ((CraftPlayer) player).getHandle(),
                                        "Créatif et permission layoutdebugger.use nécessaires."));
                        return true;
                    }
                    var nativePlayer = ((CraftPlayer) player).getHandle();
                    if (args.length == 1 && args[0].equals("whoami")) {
                        player.sendMessage(player.getUniqueId().toString());
                        return true;
                    }

                    if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                        player.sendMessage(
                                fr.eligaimix.ltdr.i18n.ServerLanguage.text(
                                        ((CraftPlayer) player).getHandle(),
                                        "Animations : "
                                                + String.join(
                                                        ", ",
                                                        engine.layouts()
                                                                .personal(player.getUniqueId())
                                                                .keySet())));
                        return true;
                    }
                    if (args.length > 0 && (args[0].equals("export") || args[0].equals("import"))) {
                        var pos = nativePlayer.blockPosition();
                        try {
                            fr.eligaimix.ltdr.storage.AnimationCommands.personal(
                                    engine.layouts(),
                                    player.getUniqueId(),
                                    nativePlayer.level().dimension().identifier().toString(),
                                    new fr.eligaimix.ltdr.sequence.BlockPosition(
                                            pos.getX(), pos.getY(), pos.getZ()),
                                    args,
                                    player::sendMessage);
                        } catch (Exception e) {
                            player.sendMessage("Layout Debugger: " + e.getMessage());
                        }
                        return true;
                    }
                    if (!peers.contains(player.getUniqueId())) {
                        player.sendMessage(
                                fr.eligaimix.ltdr.i18n.ServerLanguage.text(
                                        ((CraftPlayer) player).getHandle(),
                                        "Installer le mod client Layout Debugger compatible pour"
                                            + " utiliser l’interface. /ld list reste disponible."));
                        return true;
                    }
                    try {
                        if (args.length == 0)
                            engine.request(nativePlayer, new EditorRequest("open", "", 0, 0));
                        else if (args.length == 1 && args[0].equalsIgnoreCase("stop"))
                            engine.request(nativePlayer, new EditorRequest("live_stop", "", 0, 0));
                        else if (args.length >= 2
                                && args.length <= 3
                                && args[0].equalsIgnoreCase("run")) {
                            String timeline = args.length == 3 ? args[2] : "opening";
                            if (!List.of("opening", "closing").contains(timeline))
                                throw new IllegalArgumentException(
                                        "Timeline : opening ou closing.");
                            engine.request(
                                    nativePlayer,
                                    new EditorRequest(
                                            timeline.equals("closing") ? "live_close" : "live_open",
                                            args[1],
                                            0,
                                            0));
                        } else if (args.length == 2
                                && List.of("create", "select")
                                        .contains(args[0].toLowerCase(Locale.ROOT)))
                            engine.request(
                                    nativePlayer,
                                    new EditorRequest(
                                            args[0].toLowerCase(Locale.ROOT), args[1], 0, 0));
                        else if (args.length == 3 && args[0].equalsIgnoreCase("rename"))
                            engine.request(
                                    nativePlayer,
                                    new EditorRequest("rename", args[1] + ":" + args[2], 0, 0));
                        else
                            player.sendMessage(
                                    fr.eligaimix.ltdr.i18n.ServerLanguage.text(
                                            ((CraftPlayer) player).getHandle(),
                                            "/ld | /ld list | /ld create <nom> | /ld select <nom> |"
                                                + " /ld rename <ancien> <nouveau> | /ld run <nom>"
                                                + " [opening|closing] | /ld stop"));
                    } catch (RuntimeException exception) {
                        player.sendMessage(
                                fr.eligaimix.ltdr.i18n.ServerLanguage.text(
                                        ((CraftPlayer) player).getHandle(),
                                        "Layout Debugger : " + exception.getMessage()));
                    }
                    return true;
                });
        for (Player player : Bukkit.getOnlinePlayers()) {
            engine.join(player);
            offer(player);
        }
        getLogger()
                .info(
                        "Backend Paper actif. capture précise seul, capture partagée et lecture"
                                + " en direct à plusieurs. Clients vanilla acceptés.");
    }

    @Override
    public void onDisable() {
        if (engine != null)
            try {
                engine.disable();
            } catch (RuntimeException exception) {
                getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "Récupération à vérifier avant redémarrage",
                                exception);
            }
        getServer().getScheduler().cancelTasks(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        peers.clear();
        rates.clear();
        instance = null;
    }

    public boolean authorized(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission("layoutdebugger.use");
    }

    public boolean peer(ServerPlayer player) {
        return peers.contains(player.getUUID());
    }

    private boolean allow(Player player) {
        var w = rates.computeIfAbsent(player.getUniqueId(), ignored -> new RateWindow());
        long now = System.nanoTime();
        if (now - w.start >= 1_000_000_000L) {
            w.start = now;
            w.count = 0;
        }
        return ++w.count <= 32;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void joined(PlayerJoinEvent event) {
        engine.join(event.getPlayer());
        getServer().getScheduler().runTaskLater(this, this::offerAll, 5);
    }

    @EventHandler
    public void registered(PlayerRegisterChannelEvent event) {
        if (event.getChannel().equals(HELLO)) offer(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        engine.quit(event.getPlayer());
        peers.remove(event.getPlayer().getUniqueId());
        rates.remove(event.getPlayer().getUniqueId());
        getServer().getScheduler().runTask(this, this::offerAll);
    }

    private void offerAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            offer(player);
            if (peers.contains(player.getUniqueId()) && authorized(player))
                engine.refresh(
                        ((CraftPlayer) player).getHandle(),
                        "Fonctions actualisées : "
                                + (Bukkit.getOnlinePlayers().size() == 1
                                        ? "mode précis disponible pour une nouvelle capture."
                                        : "capture partagée disponible ; rejeu visible à vitesse"
                                                + " normale."));
        }
    }

    private void offer(Player player) {
        if (player.getListeningPluginChannels().contains(HELLO))
            player.sendPluginMessage(
                    this,
                    HELLO,
                    BridgeProtocol.hello(
                            new BridgeProtocol.Hello(
                                    BridgeProtocol.VERSION, engine.capabilities(), "paper")));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!Bukkit.isPrimaryThread() || !allow(player)) return;
        try {
            if (channel.equals(HELLO)) {
                var hello = BridgeProtocol.readHello(bytes);
                if (hello.version() == BridgeProtocol.VERSION
                        && hello.backend().equals("fabric-client")) peers.add(player.getUniqueId());
                else peers.remove(player.getUniqueId());
                return;
            }
            if (!channel.equals(REQUEST) || !peers.contains(player.getUniqueId())) return;
            var request = BridgeProtocol.readRequest(bytes);
            if (request.operation().equals("language")) {
                fr.eligaimix.ltdr.i18n.ServerLanguage.set(
                        ((CraftPlayer) player).getHandle(), request.value());
                if (engine.layouts().editor().isOwner(((CraftPlayer) player).getHandle()))
                    fr.eligaimix.ltdr.playback.EditorTools.refreshNames(
                            ((CraftPlayer) player).getHandle());
                return;
            }
            if (!authorized(player))
                throw new IllegalStateException(
                        "Créatif et permission layoutdebugger.use nécessaires.");
            engine.request(((CraftPlayer) player).getHandle(), request);
        } catch (RuntimeException exception) {
            if (peers.contains(player.getUniqueId()))
                send(
                        ((CraftPlayer) player).getHandle(),
                        new EditorState(
                                "",
                                0,
                                0,
                                false,
                                false,
                                "Erreur : " + exception.getMessage(),
                                List.of(),
                                List.of(),
                                List.of(),
                                16,
                                "on",
                                false));
        }
    }

    public void export(ServerPlayer player, String ticket, String filename, byte[] bytes) {
        if (peer(player))
            for (var chunk : ExportChunk.split(ticket, filename, bytes))
                player.getBukkitEntity()
                        .sendPluginMessage(this, EXPORT, BridgeProtocol.exportPayload(chunk));
    }

    public void open(ServerPlayer player) {
        if (peer(player)) player.getBukkitEntity().sendPluginMessage(this, OPEN, new byte[0]);
    }

    public void send(ServerPlayer player, EditorState state) {
        if (!peer(player) || !player.getBukkitEntity().isOnline()) return;
        if (engine != null) state = engine.decorate(player, state);
        byte[] bytes;
        try {
            bytes = BridgeProtocol.state(JSON.toJson(state));
        } catch (IllegalArgumentException exception) {
            bytes =
                    BridgeProtocol.state(
                            JSON.toJson(
                                    new EditorState(
                                            "",
                                            0,
                                            0,
                                            false,
                                            false,
                                            "Erreur : vue trop volumineuse.",
                                            List.of(),
                                            List.of(),
                                            List.of(),
                                            16,
                                            "on",
                                            false)));
        }
        player.getBukkitEntity().sendPluginMessage(this, STATE, bytes);
    }
}
