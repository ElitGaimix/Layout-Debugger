package fr.eligaimix.ltdr.sequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared immutable document for commands and a future timeline editor. */
public record SequenceDefinition(
        String name,
        String dimension,
        List<PistonTarget> pistons,
        List<SequenceAction> actions,
        List<SequenceAction> closingActions,
        boolean warningsEnabled,
        List<BlockPosition> ignoredWarnings,
        java.util.UUID owner) {
    private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_-]{1,48}");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    /** Null ownership is reserved for explicitly unassigned legacy documents. */
    public SequenceDefinition(
            String name,
            String dimension,
            List<PistonTarget> pistons,
            List<SequenceAction> actions,
            List<SequenceAction> closingActions,
            boolean warningsEnabled,
            List<BlockPosition> ignoredWarnings) {
        this(
                name,
                dimension,
                pistons,
                actions,
                closingActions,
                warningsEnabled,
                ignoredWarnings,
                null);
    }

    public String storageKey() {
        return owner == null ? name : owner + "/" + name;
    }

    public SequenceDefinition withOwner(java.util.UUID creator) {
        return new SequenceDefinition(
                name,
                dimension,
                pistons,
                actions,
                closingActions,
                warningsEnabled,
                ignoredWarnings,
                java.util.Objects.requireNonNull(creator));
    }

    public SequenceDefinition {
        validateId(name);
        if (dimension == null || !DIMENSION.matcher(dimension).matches()) {
            throw new IllegalArgumentException("Identifiant de dimension invalide : " + dimension);
        }
        pistons = List.copyOf(pistons);
        actions = actions.stream().sorted(SequenceAction.PLAYBACK_ORDER).toList();
        var ids = new HashSet<String>();
        var positions = new HashSet<BlockPosition>();
        for (PistonTarget target : pistons) {
            if (!ids.add(target.id())) {
                throw new IllegalArgumentException(
                        "Identifiant de piston déjà utilisé : " + target.id());
            }
            if (!positions.add(target.position())) {
                throw new IllegalArgumentException(
                        "Position de piston déjà sélectionnée : " + target.position());
            }
        }
        closingActions = closingActions.stream().sorted(SequenceAction.PLAYBACK_ORDER).toList();
        ignoredWarnings = List.copyOf(ignoredWarnings);
        validateActions(actions, ids);
        validateActions(closingActions, ids);
    }

    private static void validateActions(List<SequenceAction> actions, java.util.Set<String> ids) {
        SequenceAction previous = null;
        for (SequenceAction action : actions) {
            if (!ids.contains(action.pistonId())) {
                throw new IllegalArgumentException(
                        "Piston inconnu dans l'action : " + action.pistonId());
            }
            if (previous != null
                    && action.tick() == previous.tick()
                    && action.order() == previous.order()) {
                throw new IllegalArgumentException(
                        "Une action existe déjà au tick "
                                + action.tick()
                                + ", ordre "
                                + action.order());
            }
            previous = action;
        }
        for (SequenceAction action : actions) {
            if (!action.pulseEdge()) continue;
            long partnerTick = (long) action.tick() + (action.powered() ? 2 : -2);
            if (actions.stream()
                            .filter(other ->
                                    other.pulseEdge()
                                            && other.pistonId().equals(action.pistonId())
                                            && other.tick() == partnerTick
                                            && other.powered() != action.powered())
                            .count()
                    != 1) {
                throw new IllegalArgumentException(
                        "Impulsion incomplète pour " + action.pistonId());
            }
            if (action.powered()) {
                for (SequenceAction other : actions) {
                    if (other != action
                            && other.pistonId().equals(action.pistonId())
                            && other.tick() >= action.tick()
                            && other.tick() <= partnerTick
                            && !(other.pulseEdge()
                                    && !other.powered()
                                    && other.tick() == partnerTick)) {
                        throw new IllegalArgumentException(
                                "Action en conflit avec une impulsion de " + action.pistonId());
                    }
                }
            }
        }
    }

    public SequenceDefinition(
            String name,
            String dimension,
            List<PistonTarget> pistons,
            List<SequenceAction> actions) {
        this(name, dimension, pistons, actions, List.of(), true, List.of());
    }

    public SequenceDefinition withName(String updated) {
        return new SequenceDefinition(
                updated,
                dimension,
                pistons,
                actions,
                closingActions,
                warningsEnabled,
                ignoredWarnings,
                owner);
    }

    public SequenceDefinition withActions(List<SequenceAction> updated) {
        return new SequenceDefinition(
                name,
                dimension,
                pistons,
                updated,
                closingActions,
                warningsEnabled,
                ignoredWarnings,
                owner);
    }

    public SequenceDefinition withClosingActions(List<SequenceAction> updated) {
        return new SequenceDefinition(
                name,
                dimension,
                pistons,
                actions,
                updated,
                warningsEnabled,
                ignoredWarnings,
                owner);
    }

    public SequenceDefinition withPistons(List<PistonTarget> updated) {
        return new SequenceDefinition(
                name,
                dimension,
                updated,
                actions,
                closingActions,
                warningsEnabled,
                ignoredWarnings,
                owner);
    }

    public SequenceDefinition withWarnings(boolean enabled, List<BlockPosition> ignored) {
        return new SequenceDefinition(
                name, dimension, pistons, actions, closingActions, enabled, ignored, owner);
    }

    public SequenceDefinition timeline(boolean closing) {
        return new SequenceDefinition(
                name,
                dimension,
                pistons,
                closing ? closingActions : actions,
                List.of(),
                warningsEnabled,
                ignoredWarnings,
                owner);
    }

    public static void validateId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Identifiant invalide (1 à 48 caractères : lettres, chiffres, _ ou -) : " + id);
        }
    }

    public PistonTarget target(String id) {
        return pistons.stream()
                .filter(piston -> piston.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piston inconnu : " + id));
    }

    public SequenceDefinition withPiston(PistonTarget piston) {
        var updated = new ArrayList<>(pistons);
        updated.add(Objects.requireNonNull(piston));
        return withPistons(updated);
    }

    public SequenceDefinition withoutPiston(String id) {
        target(id);
        if (java.util.stream.Stream.concat(actions.stream(), closingActions.stream())
                .anyMatch(action -> action.pistonId().equals(id))) {
            throw new IllegalArgumentException(
                    "Supprime d'abord les actions du piston " + id + ".");
        }
        return withPistons(pistons.stream().filter(piston -> !piston.id().equals(id)).toList());
    }

    public SequenceDefinition withAction(SequenceAction action) {
        var updated = new ArrayList<>(actions);
        updated.add(Objects.requireNonNull(action));
        return withActions(updated);
    }

    public SequenceDefinition withoutAction(int tick, int order) {
        return TimelineProgram.remove(this, tick, order);
    }
}
