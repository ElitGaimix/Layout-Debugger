package fr.eligaimix.ltdr.playback;

import fr.eligaimix.ltdr.LayoutDebugger;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.sequence.SequenceAction;
import fr.eligaimix.ltdr.sequence.SequenceCursor;
import fr.eligaimix.ltdr.sequence.SequenceDefinition;
import fr.eligaimix.ltdr.storage.SequenceStore;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread service. Definitions persist; live playback and redstone ownership do not. */
public final class LayoutService {
    private final MinecraftServer server;
    private final SequenceStore store;
    private final java.nio.file.Path directory;
    private final Map<String, SequenceDefinition> definitions = new LinkedHashMap<>();
    private String loadError;
    private Session session;
    private final EditorService editor;

    public LayoutService(MinecraftServer server) {
        this(
                server,
                server.getWorldPath(LevelResource.ROOT)
                        .resolve("data/layout-debugger/sequences.json"));
    }

    public LayoutService(MinecraftServer server, java.nio.file.Path documents) {
        this.server = server;
        editor = new EditorService(server, this);
        directory = documents.toAbsolutePath().getParent();
        store = new SequenceStore(documents);
        try {
            store.load()
                    .forEach((name, sequence) ->
                            definitions.put(
                                    name,
                                    sequence.withActions(
                                            sequence.actions().stream()
                                                    .map(a ->
                                                            new SequenceAction(
                                                                    a.tick(),
                                                                    a.order(),
                                                                    a.pistonId(),
                                                                    a.powered()))
                                                    .toList())));
        } catch (IOException exception) {
            loadError =
                    "Lecture des séquences impossible ; fichier conservé. Voir le journal et"
                        + " corriger avant de rouvrir le monde.";
            LayoutDebugger.LOGGER.error(loadError, exception);
        }
    }

    public java.nio.file.Path directory() {
        return directory;
    }

    public static String key(UUID owner, String name) {
        SequenceDefinition.validateId(name);
        return java.util.Objects.requireNonNull(owner) + "/" + name;
    }

    public Map<String, SequenceDefinition> personal(UUID owner) {
        checkLoaded();
        var result = new LinkedHashMap<String, SequenceDefinition>();
        definitions.values().stream()
                .filter(d -> owner.equals(d.owner()))
                .forEach(d -> result.put(d.name(), d));
        return java.util.Collections.unmodifiableMap(result);
    }

    public SequenceDefinition owned(UUID owner, String name) {
        return definition(key(owner, name));
    }

    public void create(UUID owner, String name, String dimension) throws IOException {
        String key = key(owner, name);
        checkLoaded();
        if (definitions.containsKey(key))
            throw new IllegalArgumentException("Cette séquence existe déjà : " + name);
        put(new SequenceDefinition(name, dimension, List.of(), List.of()).withOwner(owner));
    }

    public void assignLegacy(String name, UUID owner) throws IOException {
        var old = definition(name);
        if (old.owner() != null)
            throw new IllegalStateException("Cette animation possède déjà un créateur.");
        checkEditable(name);
        var assigned = old.withOwner(owner);
        if (definitions.containsKey(assigned.storageKey()))
            throw new IllegalArgumentException("Ce créateur utilise déjà ce nom.");
        var updated = new LinkedHashMap<>(definitions);
        updated.remove(name);
        updated.put(assigned.storageKey(), assigned);
        store.save(updated);
        definitions.clear();
        definitions.putAll(updated);
    }

    public Map<String, SequenceDefinition> definitions() {
        checkLoaded();
        return java.util.Collections.unmodifiableMap(definitions);
    }

    public SequenceDefinition definition(String name) {
        checkLoaded();
        SequenceDefinition definition = definitions.get(name);
        if (definition == null) throw new IllegalArgumentException("Séquence inconnue : " + name);
        return definition;
    }

    public void create(String name, String dimension) throws IOException {
        checkLoaded();
        if (definitions.containsKey(name))
            throw new IllegalArgumentException("Cette séquence existe déjà : " + name);
        put(new SequenceDefinition(name, dimension, List.of(), List.of()));
    }

    public void put(SequenceDefinition definition) throws IOException {
        checkLoaded();
        checkEditable(definition.storageKey());
        Map<String, SequenceDefinition> updated = new LinkedHashMap<>(definitions);
        updated.put(definition.storageKey(), definition);
        store.save(updated); // Commit memory only after a successful disk write.
        definitions.clear();
        definitions.putAll(updated);
    }

    /** Persist the entire rename before updating memory or editor selections. */
    public void rename(String name, String newName) throws IOException {
        SequenceDefinition original = definition(name);
        SequenceDefinition renamed = original.withName(newName);
        checkEditable(name);
        if (original.name().equals(newName)) return;
        if (definitions.containsKey(renamed.storageKey()))
            throw new IllegalArgumentException("Ce nom est déjà utilisé : " + newName);
        Map<String, SequenceDefinition> updated = new LinkedHashMap<>();
        definitions.forEach((key, value) ->
                updated.put(
                        key.equals(name) ? renamed.storageKey() : key,
                        key.equals(name) ? renamed : value));
        store.save(updated);
        definitions.clear();
        definitions.putAll(updated);
        editor.renamed(name, renamed.storageKey());
    }

    public void delete(String name) throws IOException {
        definition(name);
        checkEditable(name);
        Map<String, SequenceDefinition> updated = new LinkedHashMap<>(definitions);
        updated.remove(name);
        store.save(updated);
        definitions.clear();
        definitions.putAll(updated);
    }

    private void checkEditable(String name) {
        if (editor.owns(name) && !editor.isWriting())
            throw new IllegalStateException(
                    "Modifier cette séquence depuis l'éditeur ou terminer l'édition.");
        if (session != null && session.definition.storageKey().equals(name)) {
            throw new IllegalStateException(
                    "Séquence réservée par la lecture. Utiliser /ld stop avant modification.");
        }
    }

    private void checkLoaded() {
        if (loadError != null) throw new IllegalStateException(loadError);
    }

    public void start(String name, ServerPlayer player, ServerLevel level) {
        if (editor.isActive())
            throw new IllegalStateException(
                    "Terminer l'édition avant de lancer une lecture par commande.");
        if (session != null)
            throw new IllegalStateException(
                    "Une session existe déjà. Utiliser /ld stop avant de relancer.");
        SequenceDefinition definition = owned(player.getUUID(), name);
        if (!definition.dimension().equals(level.dimension().identifier().toString())) {
            throw new IllegalArgumentException(
                    "Cette séquence appartient à " + definition.dimension());
        }
        if (definition.actions().isEmpty())
            throw new IllegalArgumentException("La séquence ne contient aucune action.");
        RedstonePistonController controller =
                new RedstonePistonController(level, definition.pistons());
        session = new Session(definition, player.getUUID(), level, controller);
    }

    /** Tick zero is the first non-frozen START_LEVEL_TICK after /ld run. */
    public void tick(ServerLevel level) {
        Session current = session;
        if (current == null || current.level != level || current.cleanupPending) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(current.owner);
        if (owner == null || !owner.isCreative() || owner.level() != level) {
            stopWithMessage(
                    "Lecture arrêtée : joueur absent, hors créatif ou dans une autre dimension.");
            return;
        }
        if (!current.running || !level.tickRateManager().runsNormally()) return;
        try {
            current.controller.validate();
            long tick = current.cursor.nextTick();
            for (SequenceAction action : current.cursor.advance()) {
                if (action.pulseEdge()
                        && action.powered()
                        && current.controller.hasEffectivePower(action.pistonId())) {
                    throw new IllegalStateException(
                            "Impulsion refusée : " + action.pistonId() + " est déjà alimenté.");
                }
                current.controller.setPowered(action.pistonId(), action.powered());
                current.trace.addLast(
                        "t="
                                + tick
                                + " ordre="
                                + action.order()
                                + " "
                                + action.pistonId()
                                + " "
                                + (action.powered() ? "on" : "off"));
                while (current.trace.size() > 64) current.trace.removeFirst();
            }
            if (current.cursor.isFinished()) {
                current.running = false;
                current.state = "consignes terminées";
                owner.sendSystemMessage(
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                        owner,
                                        "[Layout Debugger] Consignes terminées. Les mouvements"
                                            + " vanilla peuvent continuer ; /ld stop retire le"
                                            + " signal du mod.")
                                .withStyle(ChatFormatting.GREEN));
            }
        } catch (RuntimeException exception) {
            LayoutDebugger.LOGGER.warn(
                    "Lecture {} interrompue", current.definition.name(), exception);
            stopWithMessage("Lecture interrompue : " + exception.getMessage());
        }
    }

    public void requirePlaybackOwner(UUID player) {
        if (session != null && !session.owner.equals(player))
            throw new IllegalStateException("Cette lecture appartient à un autre joueur.");
    }

    public List<String> stop() {
        if (session == null) return List.of();
        session.running = false;
        session.cleanupPending = true;
        session.state = "arrêtée, nettoyage à réessayer avec /ld stop";
        List<String> warnings = session.controller.release();
        if (!session.controller.hasOwnedSources()) session = null;
        return warnings;
    }

    private void stopWithMessage(String message) {
        UUID ownerId = session.owner;
        List<String> warnings = stop();
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(
                    fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                    owner, "[Layout Debugger] " + message)
                            .withStyle(ChatFormatting.YELLOW));
            warnings.forEach(warning ->
                    owner.sendSystemMessage(
                            fr.eligaimix.ltdr.i18n.ServerLanguage.component(owner, warning)
                                    .withStyle(ChatFormatting.RED)));
        }
        warnings.forEach(LayoutDebugger.LOGGER::warn);
    }

    public List<String> status() {
        if (session == null) return List.of("Aucune session de lecture.");
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add(
                session.definition.name()
                        + " : "
                        + session.state
                        + ", prochain tick="
                        + session.cursor.nextTick());
        lines.add(
                "Historique des consignes appliquées (64 dernières, pas une mesure des mouvements)"
                    + " :");
        lines.addAll(session.trace);
        return lines;
    }

    public void shutdown() {
        editor.shutdown();
        stop().forEach(LayoutDebugger.LOGGER::warn);
    }

    public EditorService editor() {
        return editor;
    }

    public boolean hasPlayback() {
        return session != null;
    }

    private static final class Session {
        final SequenceDefinition definition;
        final UUID owner;
        final ServerLevel level;
        final RedstonePistonController controller;
        final SequenceCursor cursor;
        final ArrayDeque<String> trace = new ArrayDeque<>();
        boolean running = true;
        boolean cleanupPending;
        String state = "en cours";

        Session(
                SequenceDefinition definition,
                UUID owner,
                ServerLevel level,
                RedstonePistonController controller) {
            this.definition = definition;
            this.owner = owner;
            this.level = level;
            this.controller = controller;
            this.cursor = new SequenceCursor(definition);
        }
    }
}
