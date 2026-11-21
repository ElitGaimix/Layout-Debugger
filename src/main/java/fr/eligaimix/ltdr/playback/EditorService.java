package fr.eligaimix.ltdr.playback;

import fr.eligaimix.ltdr.LayoutDebugger;
import fr.eligaimix.ltdr.network.EditorNetworking;
import fr.eligaimix.ltdr.network.EditorRequest;
import fr.eligaimix.ltdr.network.EditorState;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.preview.ConstructionEdits;
import fr.eligaimix.ltdr.preview.EditBounds;
import fr.eligaimix.ltdr.preview.ReplayVisibility;
import fr.eligaimix.ltdr.preview.SeekPresentation;
import fr.eligaimix.ltdr.preview.SeekSprint;
import fr.eligaimix.ltdr.preview.WorldSnapshot;
import fr.eligaimix.ltdr.sequence.BlockPosition;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import fr.eligaimix.ltdr.sequence.SequenceAction;
import fr.eligaimix.ltdr.sequence.SequenceDefinition;
import fr.eligaimix.ltdr.sequence.TimelineProgram;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns a single in-world editing session. All mutations happen between level ticks. */
public final class EditorService {
    private final MinecraftServer server;
    private final LayoutService layouts;
    private final ArrayDeque<Pending> requests = new ArrayDeque<>();
    private final Map<UUID, String> selections = new LinkedHashMap<>();
    private final Map<String, EditBounds> lastBounds = new LinkedHashMap<>();
    private Session session;
    private boolean writing;

    public boolean mayBuild(ServerPlayer player) {
        return isOwner(player)
                && !session.readOnly
                && !session.seeking
                && !session.playing
                && !session.restoreRequested;
    }

    public WorldSnapshot.PendingInputs constructionInputs() {
        return session.snapshot.pendingInputs();
    }

    public void constructionChanged(
            ServerPlayer player,
            Map<BlockPos, WorldSnapshot.Cell> changes,
            WorldSnapshot.PendingInputs inputs) {
        if (!mayBuild(player) || changes.isEmpty()) return;
        changes.forEach((pos, cell) -> {
            if (session.snapshot.bounds().includes(pos)) session.changes.put(pos.immutable(), cell);
        });
        session.inputChanges = session.inputChanges.plus(inputs);
    }

    EditorService(MinecraftServer server, LayoutService layouts) {
        this.server = server;
        this.layouts = layouts;
    }

    public boolean reservedForOther(ServerPlayer player, BlockPos pos) {
        return session != null
                && session.level == player.level()
                && !isOwner(player)
                && session.snapshot.bounds().includes(pos);
    }

    public void recordSimulationItem(net.minecraft.world.entity.Entity entity) {
        if (session != null && session.stepScheduled && entity.level() == session.level)
            session.snapshot.recordSimulationItem(entity);
    }

    public void useRealtime() {
        if (session == null || session.realtime) return;
        var current = session;
        SeekSprint.stop(server);
        SeekSprint.forget(server);
        server.tickRateManager().stopStepping();
        server.tickRateManager().setFrozen(current.wasFrozen);
        ReplayVisibility.discard(current.player);
        SeekPresentation.send(current.player, current.snapshot.bounds());
        current.realtime = true;
        if (current.analysisStage >= 0) {
            current.closing = current.returnClosing;
            current.analysisStage = -1;
            current.dirty = true;
            seek(current.returnPage);
        }
        current.message = "Mode partagé : rejeu à vitesse normale ; le monde continue d'évoluer.";
        sendOwner();
    }

    private void stopReplay() {
        if (session == null || !session.realtime) SeekSprint.stop(server);
    }

    public boolean isActive() {
        return session != null;
    }

    public boolean controlsTicks() {
        return session != null && !session.realtime;
    }

    public boolean isWriting() {
        return writing;
    }

    void renamed(String oldName, String newName) {
        selections.replaceAll((player, selected) -> selected.equals(oldName) ? newName : selected);
        EditBounds bounds = lastBounds.remove(oldName);
        lastBounds.remove(newName);
        if (bounds != null) lastBounds.put(newName, bounds);
    }

    public boolean owns(String name) {
        return session != null && session.name.equals(name);
    }

    public boolean isOwner(ServerPlayer player) {
        return session != null && session.owner.equals(player.getUUID());
    }

    public void request(ServerPlayer player, EditorRequest request) {
        if (requests.size() >= 64) return;
        requests.addLast(new Pending(player.getUUID(), request));
    }

    /** Fabric START_SERVER_TICK: tick-rate manager has not evaluated this tick yet. */
    public void prepareTick() {
        if (session != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(session.owner);
            if (owner == null
                    || !owner.isCreative()
                    || owner.level() != session.level
                    || !fr.eligaimix.ltdr.platform.EditorAccess.allowed(owner)) {
                try {
                    interruptForMultiplayer();
                } catch (RuntimeException exception) {
                    LayoutDebugger.LOGGER.error(
                            "Fermeture de capture : sauvegarde de récupération conservée",
                            exception);
                }
                if (owner != null) {
                    try {
                        send(
                                owner,
                                "Édition fermée : accès exclusif ou droits perdus. Vérifier le"
                                        + " journal si une restauration a échoué.");
                    } catch (RuntimeException exception) {
                        LayoutDebugger.LOGGER.warn(
                                "Actualisation après fermeture impossible", exception);
                    }
                }
            }
        }
        ConstructionEdits.clear();
        if (session != null && !session.changes.isEmpty()) {
            try {
                applyConstruction();
            } catch (Exception exception) {
                abort(
                        session.player,
                        new IllegalStateException(
                                "Modification de construction impossible : "
                                        + exception.getMessage(),
                                exception),
                        false);
            }
        }
        while (!requests.isEmpty()) {
            Pending pending = requests.removeFirst();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (player == null) continue;
            try {
                if (pending.request().operation().equals("language")) {
                    fr.eligaimix.ltdr.i18n.ServerLanguage.set(player, pending.request().value());
                    if (isOwner(player) && !session.readOnly) EditorTools.refreshNames(player);
                    continue;
                }
                authorized(player);
                perform(player, pending.request());
            } catch (Exception exception) {
                fail(player, exception);
            }
        }
        Session current = session;
        if (current == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(current.owner);
        if (player == null || !player.isCreative() || player.level() != current.level) {
            if (current.faulted) return;
            try {
                end();
            } catch (RuntimeException exception) {
                LayoutDebugger.LOGGER.error("Fermeture de l'éditeur", exception);
            }
            return;
        }
        if (current.faulted) return;
        if (server.getPlayerList().getPlayerCount() > 1) useRealtime();
        try {
            if (current.restoreRequested) {
                stopReplay();
                current.controller.release();
                current.snapshot.restore();
                current.controller =
                        new RedstonePistonController(
                                current.level, List.copyOf(current.tracked.values()));
                attachTrace();
                current.prelude = current.closing && current.orphans.isEmpty();
                current.elapsed = 0;
                current.restoreRequested = false;
                current.dirty = false;
                if (!current.prelude && current.targetTicks == 0) current.settleTicks = 2;
            }
            if (current.seeking) {
                if (current.settleTicks > 0) {
                    current.stepScheduled = false;
                    return;
                }
                current.snapshot.validateArea();
                if (current.elapsed < goalTicks()) {
                    if (!current.realtime
                            && (!SeekSprint.isOwned(server)
                                    || !server.tickRateManager().isSprinting()))
                        SeekSprint.start(server, goalTicks() - current.elapsed);
                    current.stepScheduled = true;
                } else {
                    stopReplay();
                    current.settleTicks = 2;
                    current.stepScheduled = false;
                }
            } else {
                if (!current.realtime && !server.tickRateManager().isFrozen())
                    server.tickRateManager().setFrozen(true);
                if (!current.realtime) server.tickRateManager().stopStepping();
                if (current.elapsed < current.targetTicks || current.playing) {
                    current.snapshot.validateArea();
                    current.stepScheduled =
                            current.realtime || server.tickRateManager().stepGameIfPaused(1);
                } else current.stepScheduled = false;
            }
        } catch (RuntimeException exception) {
            abort(player, exception, false);
        }
    }

    /** Publish only after a subsequent frozen tick flushed the final block deltas. */
    public void endServerTick() {
        Session current = session;
        if (current == null || current.faulted || current.settleTicks <= 0) return;
        if (--current.settleTicks != 0) return;
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(current.owner);
            current.seeking = false;
            if (finishAnalysis()) return;
            if (current.playAfterSeek) {
                current.playAfterSeek = false;
                current.playing = true;
            }
            if (player != null && player.level() == current.level)
                if (!current.realtime) SeekPresentation.send(player, current.snapshot.bounds());
            current.message =
                    "Page "
                            + current.elapsed / 2
                            + " · "
                            + (current.playing
                                    ? "lecture en cours"
                                    : current.realtime
                                            ? "mode partagé · monde actif"
                                            : current.readOnly
                                                    ? "mode lecture en pause"
                                                    : "édition en pause");
            sendOwner();
        } catch (RuntimeException exception) {
            abort(server.getPlayerList().getPlayer(current.owner), exception, false);
        }
    }

    public void startLevelTick(ServerLevel level) {
        Session current = session;
        if (current == null
                || current.level != level
                || !current.stepScheduled
                || !level.tickRateManager().runsNormally()) return;
        try {
            current.controller.validate();
            for (SequenceAction action : simulationDefinition().actions()) {
                if (action.tick() != current.elapsed) continue;
                if (current.orphans.contains(action.pistonId()))
                    throw new IllegalStateException(
                            "Réassocier le piston manquant : " + action.pistonId());
                if (action.pulseEdge()
                        && action.powered()
                        && current.controller.hasEffectivePower(action.pistonId())) {
                    throw new IllegalStateException(
                            "Impulsion refusée : "
                                    + action.pistonId()
                                    + " est déjà alimenté, y compris par la redstone réelle.");
                }
                current.controller.setPowered(action.pistonId(), action.powered());
            }
        } catch (RuntimeException exception) {
            abort(server.getPlayerList().getPlayer(current.owner), exception, true);
        }
    }

    public void endLevelTick(ServerLevel level) {
        Session current = session;
        if (current != null
                && current.level == level
                && current.realtime
                && !current.stepScheduled
                && !current.faulted
                && current.changes.isEmpty()
                && (level.getGameTime() & 1) == 0) {
            try {
                current.controller.validate();
                sendOwner();
            } catch (RuntimeException exception) {
                abort(current.player, exception, false);
            }
        }
        if (current == null
                || current.level != level
                || !current.stepScheduled
                || !level.tickRateManager().runsNormally()) return;
        current.stepScheduled = false;
        current.elapsed++;
        try {
            for (BlockPos pos : current.controller.positions().values()) {
                if (!current.snapshot.bounds().includes(pos))
                    throw new IllegalStateException(
                            "Un piston a quitté la zone capturée. Revenir au départ et agrandir la"
                                    + " zone.");
            }
            if (current.prelude && current.elapsed >= goalTicks()) {
                stopReplay();
                current.prelude = false;
                current.elapsed = 0;
                if (current.targetTicks == 0) current.settleTicks = 2;
                return;
            }
            if (current.prelude) return;
            if (current.seeking) {
                if (current.elapsed >= current.targetTicks) {
                    stopReplay();
                    current.settleTicks = 2;
                }
                return;
            }
            if (current.playing && current.elapsed >= endTick(activeDefinition()))
                current.playing = false;
            if ((current.elapsed & 1) == 0) {
                current.message =
                        current.elapsed < current.targetTicks
                                ? "Recalcul de la page " + current.targetTicks / 2 + "…"
                                : "Page "
                                        + current.elapsed / 2
                                        + (current.playing
                                                ? " · lecture en cours"
                                                : current.realtime
                                                        ? " · monde actif"
                                                        : " · monde en pause");
                sendOwner();
            }
        } catch (RuntimeException exception) {
            abort(server.getPlayerList().getPlayer(current.owner), exception, true);
        }
    }

    private void perform(ServerPlayer player, EditorRequest request) throws IOException {
        String op = request.operation();
        switch (op) {
            case "open" -> {
                send(
                        player,
                        isOwner(player)
                                ? session.message
                                : "Choisir ou créer une animation dans le gestionnaire.");
                EditorNetworking.open(player);
            }
            case "export" -> {
                if (!java.util.UUID.fromString(request.value()).toString().equals(request.value()))
                    throw new IllegalArgumentException("Ticket invalide.");
                var d = layouts.definition(selected(player));
                if (!player.getUUID().equals(d.owner()))
                    throw new IllegalStateException("Animation privée.");
                var file =
                        fr.eligaimix.ltdr.storage.AnimationExchange.write(
                                layouts.directory(), player.getUUID(), d);
                EditorNetworking.export(player, request.value(), file.filename(), file.bytes());
                send(player, "Export: " + file.filename());
            }
            case "create" -> {
                if (session != null)
                    throw new IllegalStateException(
                            "Terminer l'édition avant de créer une autre séquence.");
                layouts.create(
                        player.getUUID(),
                        request.value().trim(),
                        player.level().dimension().identifier().toString());
                selections.put(
                        player.getUUID(),
                        LayoutService.key(player.getUUID(), request.value().trim()));
                send(player, "Séquence créée. Capturer le montage pour commencer.");
            }
            case "rename" -> {
                if (session != null)
                    throw new IllegalStateException(
                            "Terminer la capture ou le visionnage avant de renommer une"
                                    + " animation.");
                String[] names = request.value().split(":", -1);
                if (names.length != 2) throw new IllegalArgumentException("Nom invalide.");
                layouts.rename(LayoutService.key(player.getUUID(), names[0]), names[1].trim());
                send(player, "Animation renommée : " + names[1].trim());
            }
            case "select" -> {
                if (session != null)
                    throw new IllegalStateException(
                            "Terminer l'édition avant de changer de séquence.");
                layouts.owned(player.getUUID(), request.value());
                selections.put(
                        player.getUUID(), LayoutService.key(player.getUUID(), request.value()));
                send(player, "Séquence sélectionnée.");
            }
            case "timeline" -> {
                requireReady(player);
                if (!List.of("opening", "closing").contains(request.value()))
                    throw new IllegalArgumentException("Timeline inconnue.");
                session.closing = request.value().equals("closing");
                session.dirty = true;
                recalculate(0);
                sendOwner();
            }
            case "reset" -> {
                requireOwner(player);
                session.playing = false;
                session.playAfterSeek = false;
                if (session.analysisStage >= 0) {
                    session.returnPage = 0;
                } else {
                    if (session.lastPlayedClosing != null
                            && session.closing != session.lastPlayedClosing) {
                        session.closing = session.lastPlayedClosing;
                        session.dirty = true;
                    }
                    seek(0);
                }
                sendOwner();
            }
            case "animate" -> {
                requireOwner(player);
                if (!session.readOnly)
                    throw new IllegalStateException(
                            "Utiliser Visionner pour les raccourcis ouvrir/fermer.");
                if (!List.of("opening", "closing").contains(request.value()))
                    throw new IllegalArgumentException("Timeline inconnue.");
                session.closing = request.value().equals("closing");
                session.lastPlayedClosing = session.closing;
                session.dirty = true;
                seek(0);
                session.playAfterSeek = true;
                sendOwner();
            }
            case "warnings" -> {
                requireEditable(player);
                var doc = layouts.definition(session.name);
                saveDocument(doc.withWarnings(!doc.warningsEnabled(), doc.ignoredWarnings()));
                if (!doc.warningsEnabled()) recalculate(session.elapsed / 2);
                sendOwner();
            }
            case "ignore_warning" -> {
                requireEditable(player);
                String[] xyz = request.value().split(",");
                if (xyz.length != 3) throw new IllegalArgumentException("Position invalide.");
                BlockPosition pos =
                        new BlockPosition(
                                Integer.parseInt(xyz[0]),
                                Integer.parseInt(xyz[1]),
                                Integer.parseInt(xyz[2]));
                if (!session.snapshot.bounds().includes(new BlockPos(pos.x(), pos.y(), pos.z())))
                    throw new IllegalArgumentException("Position hors zone.");
                var doc = layouts.definition(session.name);
                var ignored = new ArrayList<>(doc.ignoredWarnings());
                boolean muted = !ignored.remove(pos);
                if (muted) ignored.add(pos);
                session.message =
                        (muted ? "Alertes ignorées" : "Alertes réactivées")
                                + " : "
                                + request.value();
                saveDocument(doc.withWarnings(doc.warningsEnabled(), ignored));
                sendOwner();
            }
            case "bind" -> {
                requireReady(player);
                if (session.readOnly) throw new IllegalStateException("Mode lecture.");
                layouts.definition(session.name).target(request.value());
                session.bindId = request.value();
                session.message =
                        "Cliquer avec ON ou OFF sur le piston à associer à " + request.value();
                sendOwner();
            }
            case "begin" -> begin(player, request.page(), false);
            case "view" -> begin(player, request.page(), true);
            case "end" -> {
                requireOwner(player);
                end();
                send(player, "État initial restauré ; édition terminée.");
            }
            case "seek" -> {
                requireOwner(player);
                if (session.analysisStage >= 0) session.returnPage = request.page();
                else seek(request.page());
                sendOwner();
            }
            case "last" -> {
                requireOwner(player);
                seek(endTick(activeDefinition()) / 2);
                sendOwner();
            }
            case "cancel_seek" -> {
                requireOwner(player);
                session.analysisStage = -1;
                session.prelude = false;
                seek(0);
                sendOwner();
            }
            case "play" -> {
                requireOwner(player);
                if (session.seeking || session.faulted)
                    throw new IllegalStateException("Attendre la fin du chargement de la page.");
                session.lastPlayedClosing = session.closing;
                if (session.elapsed >= endTick(activeDefinition())) seek(0);
                if (session.seeking) session.playAfterSeek = true;
                else session.playing = true;
                sendOwner();
            }
            case "pause" -> {
                requireOwner(player);
                if (session.seeking)
                    throw new IllegalStateException("Attendre la fin du chargement.");
                session.playing = false;
                session.targetTicks = (session.elapsed + 1) / 2 * 2;
                sendOwner();
            }
            case "tools" -> {
                requireEditable(player);
                EditorTools.give(player);
                send(
                        player,
                        "Outils reçus : clic droit sur un piston pour programmer la page"
                                + " courante.");
            }
            case "mode" -> {
                requireEditable(player);
                checkMode(request.value());
                session.mode = request.value();
                sendOwner();
            }
            case "worldtool" -> worldTool(player, request.value());
            case "edit" -> {
                requireEditable(player);
                String[] parts = request.value().split(":", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Action invalide.");
                edit(player, parts[0], request.page(), parts[1]);
            }
            case "remove" -> {
                requireEditable(player);
                save(TimelineProgram.remove(activeDefinition(), request.page(), request.order()));
                recalculate(session.elapsed / 2);
                sendOwner();
            }
            case "swap" -> {
                requireEditable(player);
                String[] orders = request.value().split(":", -1);
                if (orders.length != 2) throw new IllegalArgumentException("Ordres invalides.");
                save(
                        TimelineProgram.swap(
                                activeDefinition(),
                                request.page(),
                                Integer.parseInt(orders[0]),
                                Integer.parseInt(orders[1])));
                recalculate(session.elapsed / 2);
                sendOwner();
            }
            default -> throw new IllegalArgumentException("Action d'éditeur inconnue.");
        }
    }

    private void begin(ServerPlayer player, int radius, boolean readOnly) {

        if (session != null) throw new IllegalStateException("Une édition est déjà en cours.");
        if (layouts.hasPlayback())
            throw new IllegalStateException("Arrêter la lecture avec /ld stop avant d'éditer.");
        if (radius < 8 || radius > 31)
            throw new IllegalArgumentException("Le rayon doit être compris entre 8 et 31 blocs.");
        String name = selected(player);
        SequenceDefinition definition = layouts.definition(name);
        if (readOnly && definition.actions().isEmpty() && definition.closingActions().isEmpty())
            throw new IllegalArgumentException("Cette séquence ne contient aucune action à lire.");
        if (java.util.stream.Stream.concat(
                        definition.actions().stream(), definition.closingActions().stream())
                .anyMatch(action -> action.tick() >= TimelineProgram.MAX_PAGE * 2)) {
            throw new IllegalArgumentException(
                    "Cette séquence dépasse les "
                            + TimelineProgram.MAX_PAGE
                            + " pages de l'éditeur. Elle reste lisible par /ld run.");
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!definition.dimension().equals(level.dimension().identifier().toString()))
            throw new IllegalArgumentException("Rejoindre la dimension " + definition.dimension());
        BlockPos center = player.blockPosition();
        if (readOnly && !definition.pistons().isEmpty()) {
            int minX =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().x())
                            .min()
                            .orElseThrow();
            int maxX =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().x())
                            .max()
                            .orElseThrow();
            int minY =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().y())
                            .min()
                            .orElseThrow();
            int maxY =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().y())
                            .max()
                            .orElseThrow();
            int minZ =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().z())
                            .min()
                            .orElseThrow();
            int maxZ =
                    definition.pistons().stream()
                            .mapToInt(t -> t.position().z())
                            .max()
                            .orElseThrow();
            center =
                    new BlockPos(
                            (int) (((long) minX + maxX) / 2),
                            (int) (((long) minY + maxY) / 2),
                            (int) (((long) minZ + maxZ) / 2));
        }
        EditBounds requested =
                readOnly && lastBounds.containsKey(name)
                        ? lastBounds.get(name)
                        : EditBounds.around(center, radius);
        EditBounds bounds =
                new EditBounds(
                        requested.minX(),
                        Math.max(level.getMinY(), requested.minY()),
                        requested.minZ(),
                        requested.maxX(),
                        Math.min(level.getMaxY(), requested.maxY()),
                        requested.maxZ());
        for (PistonTarget target : definition.pistons()) {
            if (!bounds.includes(position(target)))
                throw new IllegalArgumentException(
                        "La zone ne contient pas le piston "
                                + target.id()
                                + ". Recentrer ou agrandir la capture.");
        }
        boolean frozen = server.tickRateManager().isFrozen();
        boolean realtime = server.getPlayerList().getPlayerCount() > 1;
        InventorySnapshot inventory = readOnly ? null : InventorySnapshot.capture(player);
        if (!realtime) {
            server.tickRateManager().stopSprinting();
            server.tickRateManager().stopStepping();
            server.tickRateManager().setFrozen(true);
        }
        RedstonePistonController createdController = null;
        try {
            WorldSnapshot snapshot = WorldSnapshot.capture(level, bounds);

            Map<String, PistonTarget> tracked = new LinkedHashMap<>();
            definition.pistons().stream()
                    .filter(target -> matchesTarget(target, level.getBlockState(position(target))))
                    .forEach(target -> tracked.put(target.id(), target));
            var initialPositions = new java.util.HashSet<BlockPos>();
            tracked.values().forEach(target -> initialPositions.add(position(target)));
            var reservedIds =
                    definition.pistons().stream()
                            .map(PistonTarget::id)
                            .collect(java.util.stream.Collectors.toSet());
            int hidden = 1;
            for (BlockPos pos : bounds.positions()) {
                var state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof PistonBaseBlock)
                        || initialPositions.contains(pos)) continue;
                while (tracked.containsKey("unbound" + hidden)
                        || reservedIds.contains("unbound" + hidden)) hidden++;
                String id = "unbound" + hidden++;
                tracked.put(
                        id,
                        new PistonTarget(
                                id,
                                new BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
                                state.getValue(PistonBaseBlock.FACING).getName(),
                                state.is(Blocks.STICKY_PISTON)));
            }
            RedstonePistonController controller =
                    new RedstonePistonController(level, List.copyOf(tracked.values()));
            createdController = controller;
            if (inventory != null)
                inventory = InventorySnapshot.captureSaved(player, inventoryDirectory());
            session =
                    new Session(
                            player,
                            name,
                            level,
                            snapshot,
                            controller,
                            tracked,
                            radius,
                            frozen,
                            readOnly,
                            inventory);
            session.realtime = realtime;
            lastBounds.put(name, bounds);
            definition.pistons().stream()
                    .filter(t -> !tracked.containsKey(t.id()))
                    .forEach(t -> session.orphans.add(t.id()));
            attachTrace();
            session.message =
                    readOnly
                            ? "Mode lecture. Choisir une page ou démarrer la lecture."
                            : "État initial capturé. Page 0 ; choisir un outil puis cliquer sur un"
                                    + " piston.";
            if (inventory != null) inventory.installTools(player);
            for (String warning : snapshot.warnings())
                player.sendSystemMessage(
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                        player, "[Layout Debugger] " + warning)
                                .withStyle(ChatFormatting.YELLOW));
            if (!readOnly && definition.warningsEnabled()) recalculate(0);
            sendOwner();
        } catch (RuntimeException exception) {
            if (createdController != null) createdController.release();
            if (session != null && session.owner.equals(player.getUUID())) session = null;
            if (inventory != null)
                try {
                    inventory.restore(player);
                } catch (RuntimeException restoreError) {
                    exception.addSuppressed(restoreError);
                }
            if (!realtime) server.tickRateManager().setFrozen(frozen);
            throw exception;
        }
    }

    /**
     * Called by Fabric item interaction; deferred so reconstruction never runs inside a world
     * callback.
     */
    public void useTool(ServerPlayer player, BlockPos pos, String mode) {
        if (requests.size() >= 64) return;
        requests.addLast(
                new Pending(
                        player.getUUID(),
                        new EditorRequest(
                                "worldtool",
                                pos.getX() + "," + pos.getY() + "," + pos.getZ() + ":" + mode,
                                0,
                                0)));
    }

    private void worldTool(ServerPlayer player, String encoded) throws IOException {
        requireEditable(player);
        String[] fields = encoded.split(":", -1);
        String[] coords = fields[0].split(",", -1);
        if (fields.length != 2 || (!fields[0].startsWith("@") && coords.length != 3))
            throw new IllegalArgumentException("Cible d'outil invalide.");
        String directId = fields[0].startsWith("@") ? fields[0].substring(1) : null;
        BlockPos pos =
                directId == null
                        ? new BlockPos(
                                Integer.parseInt(coords[0]),
                                Integer.parseInt(coords[1]),
                                Integer.parseInt(coords[2]))
                        : session.controller.positions().get(directId);
        if (pos == null) throw new IllegalArgumentException("Piston inconnu.");
        String mode = fields[1];
        checkMode(mode);
        if (player.blockPosition().distSqr(pos) > 100)
            throw new IllegalArgumentException("Piston trop éloigné.");
        String id = directId == null ? session.controller.targetIdForHit(pos) : directId;
        if (id == null)
            throw new IllegalArgumentException(
                    "Viser la base d'un piston présent lors de la capture initiale.");
        SequenceDefinition definition = activeDefinition();
        if (!session.bindId.isEmpty()) {
            String wanted = session.bindId;
            PistonTarget candidate = session.tracked.get(id);
            if (candidate == null) throw new IllegalArgumentException("Piston absent.");
            var doc = layouts.definition(session.name);
            PistonTarget replacement =
                    new PistonTarget(
                            wanted, candidate.position(), candidate.facing(), candidate.sticky());
            var targets =
                    doc.pistons().stream()
                            .map(t -> t.id().equals(wanted) ? replacement : t)
                            .toList();
            saveDocument(doc.withPistons(targets));
            session.bindId = "";
            rebuildTracking();
            session.dirty = true;
            recalculate(0);
            sendOwner();
            return;
        }
        String currentId = id;
        if (definition.pistons().stream().noneMatch(target -> target.id().equals(currentId))) {
            PistonTarget original = session.tracked.get(id);
            int index = 1;
            while (session.tracked.containsKey("p" + index)
                    || session.orphans.contains("p" + index)) index++;
            PistonTarget selected =
                    new PistonTarget(
                            "p" + index, original.position(), original.facing(), original.sticky());
            SequenceDefinition updated = definition.withPiston(selected);
            save(updated);
            session.controller.renameTarget(id, selected);
            session.tracked.remove(id);
            session.tracked.put(selected.id(), selected);
            id = selected.id();
        }
        session.mode = mode;
        int page = Math.max(1, session.elapsed / 2);
        edit(player, id, page, TimelineProgram.toolMode(activeDefinition(), id, page, mode));
    }

    private void edit(ServerPlayer player, String id, int page, String mode) throws IOException {
        if (!mode.equals("clear")) checkMode(mode);
        int tick = TimelineProgram.tickForPage(page);
        save(TimelineProgram.edit(activeDefinition(), id, page, mode));
        recalculate(page);
        session.message = id + " : " + mode + " · page " + page + " (t=" + tick + ")";
        send(player, session.message);
    }

    private void save(SequenceDefinition definition) throws IOException {
        var doc = layouts.definition(session.name).withPistons(definition.pistons());
        saveDocument(
                session.closing
                        ? doc.withClosingActions(definition.actions())
                        : doc.withActions(definition.actions()));
        session.dirty = true;
    }

    private void saveDocument(SequenceDefinition definition) throws IOException {
        writing = true;
        try {
            if (session == null || !session.owner.equals(definition.owner()))
                throw new IllegalStateException("Créateur de l’animation invalide.");
            layouts.put(definition);
        } finally {
            writing = false;
        }
    }

    private SequenceDefinition activeDefinition() {
        return layouts.definition(session.name).timeline(session.closing);
    }

    private SequenceDefinition simulationDefinition() {
        return layouts.definition(session.name).timeline(session.prelude ? false : session.closing);
    }

    private int goalTicks() {
        return session.prelude
                ? endTick(layouts.definition(session.name).timeline(false))
                : session.targetTicks;
    }

    private void attachTrace() {
        Session current = session;
        current.controller.setPathListener(pos -> {
            String where =
                    (current.prelude || !current.closing ? "Ouverture" : "Fermeture")
                            + " · page "
                            + (current.elapsed / 2 + 1);
            (current.analysisStage >= 0 && current.analysisStage < 2
                            ? current.analyzedPaths
                            : current.paths)
                    .putIfAbsent(pos.immutable(), where);
        });
    }

    private void recalculate(int page) {
        var doc = layouts.definition(session.name);
        if (!session.realtime
                && !session.readOnly
                && doc.warningsEnabled()
                && session.orphans.isEmpty()
                && (!doc.actions().isEmpty() || !doc.closingActions().isEmpty())) {
            session.returnClosing = session.closing;
            session.returnPage = page;
            session.analysisStage = 0;
            session.analyzedPaths.clear();
            session.closing = false;
            session.dirty = true;
            seek(endTick(doc.timeline(false)) / 2);
        } else {
            session.analysisStage = -1;
            seek(session.orphans.isEmpty() ? page : 0);
        }
    }

    private boolean finishAnalysis() {
        if (session.analysisStage < 0) return false;
        var doc = layouts.definition(session.name);
        if (session.analysisStage == 0 && !doc.closingActions().isEmpty()) {
            session.analysisStage = 1;
            session.closing = true;
            session.dirty = true;
            seek(endTick(doc.timeline(true)) / 2);
            return true;
        }
        if (session.analysisStage < 2) {
            session.paths.clear();
            session.paths.putAll(session.analyzedPaths);
            session.analysisStage = 2;
            session.closing = session.returnClosing;
            session.dirty = true;
            seek(session.returnPage);
            return true;
        }
        session.analysisStage = -1;
        return false;
    }

    private void applyConstruction() throws IOException {
        Session current = session;
        int page = current.elapsed / 2;
        var changes = new LinkedHashMap<>(current.changes);
        current.changes.clear();
        var doc = layouts.definition(current.name);
        for (var entry : changes.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue().state().isAir()) current.warned.remove(pos);
            else if (current.paths.containsKey(pos)) {
                String warning =
                        "Bloc sur un trajet de piston : "
                                + pos.toShortString()
                                + " ("
                                + current.paths.get(pos)
                                + "). Avertissement consultatif.";
                current.warned.put(pos, warning);
                if (doc.warningsEnabled()
                        && !doc.ignoredWarnings()
                                .contains(new BlockPosition(pos.getX(), pos.getY(), pos.getZ())))
                    current.player.sendSystemMessage(
                            fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                            current.player, "[Layout Debugger] " + warning)
                                    .withStyle(ChatFormatting.YELLOW));
            }
        }
        current.snapshot = current.snapshot.withChanges(changes, current.inputChanges);
        current.inputChanges = WorldSnapshot.PendingInputs.empty();
        rebuildTracking();
        current.dirty = true;
        recalculate(page);
        sendOwner();
    }

    private void rebuildTracking() {
        Session current = session;
        if (!current.realtime) ReplayVisibility.begin(current.player, current.snapshot.bounds());
        current.controller.release();
        current.snapshot.restore();
        current.tracked.clear();
        current.orphans.clear();
        for (PistonTarget target : layouts.definition(current.name).pistons()) {
            if (matchesTarget(target, current.level.getBlockState(position(target))))
                current.tracked.put(target.id(), target);
            else current.orphans.add(target.id());
        }
        var occupied = new java.util.HashSet<BlockPos>();
        current.tracked.values().forEach(t -> occupied.add(position(t)));
        int index = 1;
        for (BlockPos pos : current.snapshot.bounds().positions()) {
            var state = current.level.getBlockState(pos);
            if (!(state.getBlock() instanceof PistonBaseBlock) || occupied.contains(pos)) continue;
            while (current.tracked.containsKey("unbound" + index)
                    || current.orphans.contains("unbound" + index)) index++;
            String id = "unbound" + index++;
            current.tracked.put(
                    id,
                    new PistonTarget(
                            id,
                            new BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
                            state.getValue(PistonBaseBlock.FACING).getName(),
                            state.is(Blocks.STICKY_PISTON)));
        }
        current.controller =
                new RedstonePistonController(current.level, List.copyOf(current.tracked.values()));
        attachTrace();
    }

    private static boolean matchesTarget(
            PistonTarget target, net.minecraft.world.level.block.state.BlockState state) {
        return state.is(target.sticky() ? Blocks.STICKY_PISTON : Blocks.PISTON)
                && state.getValue(PistonBaseBlock.FACING).getName().equals(target.facing());
    }

    private void seek(int page) {
        if (page < 0 || page > TimelineProgram.MAX_PAGE)
            throw new IllegalArgumentException("Page hors limites.");
        if (!session.dirty
                && !session.faulted
                && !session.seeking
                && !session.playing
                && session.elapsed == page * 2) return;
        if (!session.realtime) ReplayVisibility.begin(session.player, session.snapshot.bounds());
        boolean rebuild =
                session.dirty
                        || session.faulted
                        || session.restoreRequested
                        || page * 2 < session.elapsed;
        stopReplay();
        session.playAfterSeek = false;
        session.playing = false;
        session.faulted = false;
        session.targetTicks = page * 2;
        session.restoreRequested = rebuild;
        session.seeking = true;
        session.settleTicks = 0;
        session.message =
                page == 0 ? "Retour à l'état initial…" : "Recalcul jusqu'à la page " + page + "…";
    }

    private void end() {
        Session current = session;
        if (current == null) return;
        RuntimeException failure = null;
        try {
            // A join/disconnect can close the session before prepareTick commits the last player
            // edit.
            if (!current.changes.isEmpty()) {
                current.snapshot =
                        current.snapshot.withChanges(current.changes, current.inputChanges);
                current.changes.clear();
                current.inputChanges = WorldSnapshot.PendingInputs.empty();
            }
            if (current.player.level() == current.level && !current.realtime)
                ReplayVisibility.begin(current.player, current.snapshot.bounds());
            stopReplay();
            current.controller.release();
            current.snapshot.restore();
            if (current.player.level() == current.level && !current.realtime)
                SeekPresentation.send(current.player, current.snapshot.bounds());
            else ReplayVisibility.discard(current.player);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            restoreInventory(current);
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
        }
        if (failure != null) {
            ReplayVisibility.discard(current.player);
            current.faulted = true;
            current.playing = false;
            current.stepScheduled = false;
            current.seeking = false;
            current.settleTicks = 0;
            if (session != null && !session.realtime) server.tickRateManager().setFrozen(true);
            throw failure;
        }
        session = null;
        if (!current.realtime) server.tickRateManager().setFrozen(current.wasFrozen);
    }

    private Path inventoryDirectory() {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data/layout-debugger/inventory-backups");
    }

    private void restoreInventory(Session current) {
        if (current.inventory == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(current.owner);
        current.inventory.restore(online == null ? current.player : online);
    }

    public void disconnect(ServerPlayer player) {
        ReplayVisibility.discard(player);
        if (isOwner(player)) restoreInventory(session);
    }

    public void recoverInventory(ServerPlayer player) {
        try {
            if (InventorySnapshot.recoverSaved(player, inventoryDirectory()))
                player.sendSystemMessage(
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                        player, "[Layout Debugger] Inventaire d'origine récupéré.")
                                .withStyle(ChatFormatting.GREEN));
        } catch (RuntimeException exception) {
            fail(player, exception);
        }
    }

    /**
     * Before admitting another player: never leave the shared server frozen after a cleanup
     * failure.
     */
    public void interruptForMultiplayer() {
        if (session == null) return;
        Session current = session;
        try {
            end();
        } finally {
            try {
                current.controller.release();
            } finally {
                ReplayVisibility.discard(current.player);
                SeekSprint.forget(server);
                if (!current.realtime) {
                    server.tickRateManager().stopSprinting();
                    server.tickRateManager().stopStepping();
                }
                if (!current.realtime) server.tickRateManager().setFrozen(current.wasFrozen);
                session = null;
                requests.clear();
            }
        }
    }

    public void shutdown() {
        if (session != null) end();
        requests.clear();
    }

    private void requireOwner(ServerPlayer player) {
        if (!isOwner(player))
            throw new IllegalStateException("Capturer d'abord le montage dans l'éditeur.");
    }

    private void requireReady(ServerPlayer player) {
        requireOwner(player);
        if (session.faulted)
            throw new IllegalStateException(
                    "Prévisualisation interrompue : revenir à la page 0 ou terminer l'édition pour"
                            + " réessayer la restauration.");
        if (session.seeking
                || session.restoreRequested
                || session.elapsed < session.targetTicks
                || session.playing)
            throw new IllegalStateException(
                    "Mettre la lecture en pause et attendre la fin du recalcul.");
    }

    private void requireEditable(ServerPlayer player) {
        requireReady(player);
        if (session.readOnly)
            throw new IllegalStateException(
                    "Mode lecture : terminer la lecture puis capturer pour modifier la séquence.");
        if (session.inventory != null && session.inventory.isRestored())
            throw new IllegalStateException(
                    "L'inventaire a été restauré ; terminer puis relancer la capture.");
    }

    private void authorized(ServerPlayer player) {
        if (!fr.eligaimix.ltdr.platform.EditorAccess.allowed(player))
            throw new IllegalStateException(
                    "Mode créatif et permission Layout Debugger nécessaires.");
    }

    private static void checkMode(String mode) {
        if (!List.of("on", "off").contains(mode))
            throw new IllegalArgumentException("Mode d'outil inconnu.");
    }

    private String selected(ServerPlayer player) {
        String selected = selections.get(player.getUUID());
        if (selected == null
                || !layouts.definitions().containsKey(selected)
                || !player.getUUID().equals(layouts.definition(selected).owner())) {
            selected =
                    layouts.personal(player.getUUID()).values().stream()
                            .map(SequenceDefinition::storageKey)
                            .findFirst()
                            .orElse("");
            selections.put(player.getUUID(), selected);
        }
        return selected;
    }

    private static int endTick(SequenceDefinition definition) {
        long last = definition.actions().stream().mapToLong(SequenceAction::tick).max().orElse(0);
        return (int) Math.min(TimelineProgram.MAX_PAGE * 2L, (last + 7) / 2 * 2);
    }

    private static BlockPos position(PistonTarget target) {
        return new BlockPos(target.position().x(), target.position().y(), target.position().z());
    }

    private void sendOwner() {
        if (session != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.owner);
            if (player != null) send(player, session.message);
        }
    }

    private void fail(ServerPlayer player, Exception exception) {
        if (exception instanceof IOException)
            LayoutDebugger.LOGGER.error("Enregistrement de l'éditeur", exception);
        String message =
                "Erreur : "
                        + (exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage());
        if (isOwner(player)) session.message = message;
        player.sendSystemMessage(
                fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                player, "[Layout Debugger] " + message)
                        .withStyle(ChatFormatting.RED));
        try {
            send(player, message);
        } catch (RuntimeException viewError) {
            LayoutDebugger.LOGGER.warn("Envoi de la vue d'éditeur impossible", viewError);
            try {
                EditorNetworking.send(
                        player,
                        new EditorState(
                                "", 0, 0, false, false, message, List.of(), List.of(), List.of(),
                                16, "on", false));
            } catch (RuntimeException ignored) {
                /* Chat feedback remains available when the document cannot be displayed. */
            }
        }
    }

    private void abort(ServerPlayer player, RuntimeException exception, boolean resetOnce) {
        LayoutDebugger.LOGGER.warn("Prévisualisation interrompue", exception);
        if (session != null) {
            if (resetOnce) {
                session.closing = false;
                session.prelude = false;
            }
            session.analysisStage = -1;
            session.playAfterSeek = false;
            session.playing = false;
            session.targetTicks = 0;
            session.stepScheduled = false;
            session.restoreRequested = resetOnce;
            session.faulted = !resetOnce;
            session.seeking = resetOnce;
            session.settleTicks = 0;
        }
        stopReplay();
        if (!resetOnce && session != null) {
            try {
                if (!session.realtime)
                    SeekPresentation.send(session.player, session.snapshot.bounds());
            } catch (RuntimeException sendError) {
                ReplayVisibility.discard(session.player);
                exception.addSuppressed(sendError);
            }
        }
        if (player != null) fail(player, exception);
    }

    public void refresh(ServerPlayer player, String message) {
        send(player, message);
    }

    private void send(ServerPlayer player, String message) {
        boolean editing = isOwner(player);
        String name = editing ? session.name : selected(player);
        SequenceDefinition document = name.isEmpty() ? null : layouts.definition(name);
        boolean shownClosing =
                editing && (session.analysisStage >= 0 ? session.returnClosing : session.closing);
        SequenceDefinition definition = document == null ? null : document.timeline(shownClosing);
        Map<String, BlockPos> positions = editing ? session.controller.positions() : Map.of();
        var targets = new ArrayList<EditorState.TargetView>();
        if (definition != null)
            for (PistonTarget target : definition.pistons()) {
                BlockPos pos = position(target);
                boolean powered = false, extended = false, moving = false;
                if (editing) {
                    pos = positions.get(target.id());
                    if (pos == null) {
                        pos = position(target);
                    } else {
                        powered = session.controller.isModPowered(target.id());
                        moving = session.controller.isMoving(target.id());
                        var state = session.controller.currentState(target.id());
                        extended =
                                state.hasProperty(PistonBaseBlock.EXTENDED)
                                        && state.getValue(PistonBaseBlock.EXTENDED);
                    }
                }
                targets.add(
                        new EditorState.TargetView(
                                target.id(),
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                powered,
                                extended,
                                moving));
            }
        List<EditorState.ActionView> actions =
                definition == null
                        ? List.of()
                        : definition.actions().stream()
                                .map(a ->
                                        new EditorState.ActionView(
                                                a.tick(),
                                                a.order(),
                                                a.pistonId(),
                                                a.powered(),
                                                a.pulseEdge()))
                                .toList();
        EditorNetworking.send(
                player,
                new EditorState(
                        document == null ? "" : document.name(),
                        editing ? session.elapsed / 2 : 0,
                        editing
                                ? session.analysisStage >= 0
                                        ? session.returnPage
                                        : session.targetTicks / 2
                                : 0,
                        editing
                                && (session.seeking
                                        || session.restoreRequested
                                        || session.elapsed < session.targetTicks),
                        editing && session.playing,
                        message,
                        List.copyOf(layouts.personal(player.getUUID()).keySet()),
                        targets,
                        actions,
                        editing ? session.radius : 16,
                        editing ? session.mode : "on",
                        editing,
                        editing && session.readOnly,
                        definition == null ? 0 : endTick(definition) / 2,
                        editing && session.seeking,
                        shownClosing ? "closing" : "opening",
                        document == null || document.warningsEnabled(),
                        !editing || !document.warningsEnabled()
                                ? List.of()
                                : session.warned.entrySet().stream()
                                        .filter(e ->
                                                !document.ignoredWarnings()
                                                        .contains(
                                                                new BlockPosition(
                                                                        e.getKey().getX(),
                                                                        e.getKey().getY(),
                                                                        e.getKey().getZ())))
                                        .map(e ->
                                                new EditorState.WarningView(
                                                        e.getKey().getX(),
                                                        e.getKey().getY(),
                                                        e.getKey().getZ(),
                                                        e.getValue()))
                                        .toList(),
                        editing ? List.copyOf(session.orphans) : List.of(),
                        editing && session.realtime));
    }

    private record Pending(UUID player, EditorRequest request) {}

    private static final class Session {
        final UUID owner;
        final String name;
        final ServerLevel level;
        WorldSnapshot snapshot;
        final ServerPlayer player;
        final boolean readOnly;
        final InventorySnapshot inventory;
        RedstonePistonController controller;
        final Map<String, PistonTarget> tracked;
        final int radius;
        final boolean wasFrozen;
        Boolean lastPlayedClosing;
        boolean playAfterSeek, realtime;
        boolean closing, prelude, returnClosing;
        int analysisStage = -1, returnPage;
        String bindId = "";
        final java.util.Set<String> orphans = new java.util.LinkedHashSet<>();
        WorldSnapshot.PendingInputs inputChanges = WorldSnapshot.PendingInputs.empty();
        final Map<BlockPos, WorldSnapshot.Cell> changes = new LinkedHashMap<>();
        final Map<BlockPos, String> paths = new LinkedHashMap<>(),
                analyzedPaths = new LinkedHashMap<>(),
                warned = new LinkedHashMap<>();
        int elapsed, targetTicks, settleTicks;
        boolean playing, stepScheduled, restoreRequested, faulted, seeking, dirty;
        String mode = "on", message = "";

        Session(
                ServerPlayer player,
                String name,
                ServerLevel level,
                WorldSnapshot snapshot,
                RedstonePistonController controller,
                Map<String, PistonTarget> tracked,
                int radius,
                boolean wasFrozen,
                boolean readOnly,
                InventorySnapshot inventory) {
            this.owner = player.getUUID();
            this.player = player;
            this.readOnly = readOnly;
            this.inventory = inventory;
            this.name = name;
            this.level = level;
            this.snapshot = snapshot;
            this.controller = controller;
            this.tracked = tracked;
            this.radius = radius;
            this.wasFrozen = wasFrozen;
        }
    }
}
