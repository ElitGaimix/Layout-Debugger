package fr.eligaimix.ltdr.network;

import java.util.List;
import java.util.Objects;

/** Server-owned view. No client classes or mutable world references cross the network. */
public record EditorState(
        String sequence,
        int page,
        int targetPage,
        boolean busy,
        boolean playing,
        String message,
        List<String> sequences,
        List<TargetView> pistons,
        List<ActionView> actions,
        int radius,
        String mode,
        boolean editing,
        boolean readOnly,
        int endPage,
        boolean seeking,
        String timeline,
        boolean warningsEnabled,
        List<WarningView> warnings,
        List<String> orphanPistons,
        boolean realtime) {
    public EditorState(
            String sequence,
            int page,
            int targetPage,
            boolean busy,
            boolean playing,
            String message,
            List<String> sequences,
            List<TargetView> pistons,
            List<ActionView> actions,
            int radius,
            String mode,
            boolean editing,
            boolean readOnly,
            int endPage,
            boolean seeking,
            String timeline,
            boolean warningsEnabled,
            List<WarningView> warnings,
            List<String> orphanPistons) {
        this(
                sequence,
                page,
                targetPage,
                busy,
                playing,
                message,
                sequences,
                pistons,
                actions,
                radius,
                mode,
                editing,
                readOnly,
                endPage,
                seeking,
                timeline,
                warningsEnabled,
                warnings,
                orphanPistons,
                false);
    }

    public EditorState {
        Objects.requireNonNull(sequence);
        Objects.requireNonNull(message);
        Objects.requireNonNull(mode);
        sequences = List.copyOf(sequences);
        pistons = List.copyOf(pistons);
        actions = List.copyOf(actions);
        warnings = List.copyOf(warnings);
        orphanPistons = List.copyOf(orphanPistons);
    }

    public EditorState(
            String sequence,
            int page,
            int targetPage,
            boolean busy,
            boolean playing,
            String message,
            List<String> sequences,
            List<TargetView> pistons,
            List<ActionView> actions,
            int radius,
            String mode,
            boolean editing,
            boolean readOnly,
            int endPage,
            boolean seeking) {
        this(
                sequence,
                page,
                targetPage,
                busy,
                playing,
                message,
                sequences,
                pistons,
                actions,
                radius,
                mode,
                editing,
                readOnly,
                endPage,
                seeking,
                "opening",
                true,
                List.of(),
                List.of());
    }

    public record WarningView(int x, int y, int z, String message) {}

    /** Compatibility for previously stored test fixtures and ordinary idle views. */
    public EditorState(
            String sequence,
            int page,
            int targetPage,
            boolean busy,
            boolean playing,
            String message,
            List<String> sequences,
            List<TargetView> pistons,
            List<ActionView> actions,
            int radius,
            String mode,
            boolean editing) {
        this(
                sequence,
                page,
                targetPage,
                busy,
                playing,
                message,
                sequences,
                pistons,
                actions,
                radius,
                mode,
                editing,
                false,
                (int)
                        Math.min(
                                10_000L,
                                (actions.stream().mapToLong(ActionView::tick).max().orElse(0) + 7)
                                        / 2),
                false);
    }

    public static EditorState empty() {
        return new EditorState(
                "",
                0,
                0,
                false,
                false,
                "Choisir ou créer une séquence pour commencer.",
                List.of(),
                List.of(),
                List.of(),
                16,
                "on",
                false);
    }

    public record TargetView(
            String id, int x, int y, int z, boolean modPowered, boolean extended, boolean moving) {}

    public record ActionView(
            int tick, int order, String pistonId, boolean powered, boolean pulseEdge) {}
}
