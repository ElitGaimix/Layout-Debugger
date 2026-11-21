package fr.eligaimix.ltdr.paper;

import fr.eligaimix.ltdr.network.EditorState;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.playback.LayoutService;
import fr.eligaimix.ltdr.sequence.*;

import net.minecraft.server.level.*;

import java.util.*;

/** Live playback never freezes, restores, seeks or changes another player's inventory. */
final class LivePlayback {
    private final LayoutService layouts;
    private Session session;

    private static final class Session {
        final UUID owner;
        final ServerLevel level;
        final SequenceDefinition document;
        final RedstonePistonController controller;
        SequenceCursor cursor;
        boolean closing;
        boolean running;

        Session(ServerPlayer player, SequenceDefinition doc) {
            owner = player.getUUID();
            level = player.level();
            document = doc;
            controller = new RedstonePistonController(level, doc.pistons());
        }
    }

    LivePlayback(LayoutService layouts) {
        this.layouts = layouts;
    }

    boolean active() {
        return session != null;
    }

    void start(ServerPlayer player, String name, boolean closing) {
        if (layouts.hasPlayback())
            throw new IllegalStateException("Une lecture par commande est déjà active.");
        var doc = layouts.owned(player.getUUID(),name);
        var actions = closing ? doc.closingActions() : doc.actions();
        if (actions.isEmpty())
            throw new IllegalArgumentException("Cette timeline ne contient aucune action.");
        if (!doc.dimension().equals(player.level().dimension().identifier().toString()))
            throw new IllegalArgumentException("Rejoindre la dimension du layout.");
        if (session != null
                && (!session.owner.equals(player.getUUID()) || !session.document.equals(doc)))
            throw new IllegalStateException(
                    "Arrêter la lecture active avant de changer de layout ou de propriétaire.");
        if (session == null) session = new Session(player, doc);
        session.controller.validate();
        session.cursor =
                new SequenceCursor(
                        new SequenceDefinition(
                                doc.name(), doc.dimension(), doc.pistons(), actions));
        session.closing = closing;
        session.running = true;
    }

    void stop(ServerPlayer player) {
        if (session != null && !session.owner.equals(player.getUUID()))
            throw new IllegalStateException("Lecture réservée à son propriétaire.");
        shutdown();
    }

    void shutdown() {
        if (session == null) return;
        var current = session;
        try {
            current.controller.release();
        } finally {
            session = null;
        }
    }

    void disconnect(ServerPlayer player) {
        if (session != null && session.owner.equals(player.getUUID())) shutdown();
    }

    void tick(ServerLevel level) {
        var current = session;
        if (current == null || current.level != level) return;
        var owner = level.getServer().getPlayerList().getPlayer(current.owner);
        if (owner == null
                || !owner.isCreative()
                || owner.level() != level
                || !LayoutDebuggerPaper.instance.authorized(owner.getBukkitEntity())) {
            shutdown();
            if (owner != null)
                layouts.editor()
                        .refresh(
                                owner,
                                "Lecture arrêtée : joueur hors du monde, du créatif ou sans les"
                                        + " droits nécessaires.");
            return;
        }
        if (!current.running || !level.tickRateManager().runsNormally()) return;
        try {
            current.controller.validate();
            for (var action : current.cursor.advance())
                current.controller.setPowered(action.pistonId(), action.powered());
            if (current.cursor.isFinished()) current.running = false;
            if ((current.cursor.nextTick() & 1) == 0 || !current.running)
                layouts.editor()
                        .refresh(
                                owner,
                                current.running
                                        ? "Lecture en direct ; aucune pause du monde."
                                        : "Consignes terminées ; signal maintenu. Ouvrir/Fermer"
                                                + " poursuit depuis l’état réel ; Arrêter retire le"
                                                + " signal.");
        } catch (RuntimeException exception) {
            shutdown();
            layouts.editor().refresh(owner, "Lecture interrompue : " + exception.getMessage());
        }
    }

    EditorState decorate(ServerPlayer player, EditorState state) {
        var current = session;
        if (current == null || !current.owner.equals(player.getUUID())) return state;
        var positions = current.controller.positions();
        var targets = new ArrayList<EditorState.TargetView>();
        for (var target : current.document.pistons()) {
            var pos = positions.get(target.id());
            var block = current.controller.currentState(target.id());
            targets.add(
                    new EditorState.TargetView(
                            target.id(),
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            current.controller.isModPowered(target.id()),
                            block.getValue(
                                    net.minecraft.world.level.block.piston.PistonBaseBlock
                                            .EXTENDED),
                            current.controller.isMoving(target.id())));
        }
        var actions =
                (current.closing ? current.document.closingActions() : current.document.actions())
                        .stream()
                                .map(a ->
                                        new EditorState.ActionView(
                                                a.tick(),
                                                a.order(),
                                                a.pistonId(),
                                                a.powered(),
                                                false))
                                .toList();
        int page = (int) (current.cursor.nextTick() / 2);
        return new EditorState(
                current.document.name(),
                page,
                page,
                false,
                true,
                state.message(),
                state.sequences(),
                targets,
                actions,
                state.radius(),
                "on",
                false,
                false,
                state.endPage(),
                false,
                current.closing ? "closing" : "opening",
                current.document.warningsEnabled(),
                List.of(),
                List.of());
    }
}
