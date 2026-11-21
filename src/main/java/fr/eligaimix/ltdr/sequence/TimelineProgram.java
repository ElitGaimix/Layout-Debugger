package fr.eligaimix.ltdr.sequence;

import java.util.ArrayList;
import java.util.List;

/** Editor operations preserve authored pulse pairs and expose pages in redstone ticks. */
public final class TimelineProgram {
	public static final int MAX_PAGE = 10_000;
	private TimelineProgram() {}

	public static int tickForPage(int page) {
		if (page < 1 || page > MAX_PAGE) throw new IllegalArgumentException("Choisir une page entre 1 et " + MAX_PAGE + ".");
		return Math.multiplyExact(page - 1, 2);
	}

	public static boolean modPowerBefore(SequenceDefinition definition, String id, int tick) {
		boolean powered = false;
		for (SequenceAction action : definition.actions()) {
			if (action.tick() >= tick) break;
			if (action.pistonId().equals(id)) powered = action.powered();
		}
		return powered;
	}

	/** Repeating a physical tool removes the same page's signal; a different tool replaces it. */
	public static String toolMode(SequenceDefinition definition, String id, int page, String mode) {
		if (!List.of("on", "off").contains(mode)) throw new IllegalArgumentException("Mode d'outil inconnu.");
		int tick = tickForPage(page);
		var samePage = definition.actions().stream().filter(a -> a.pistonId().equals(id) && a.tick() == tick).toList();
		return !samePage.isEmpty() && samePage.stream().allMatch(a -> a.powered() == mode.equals("on")) ? "clear" : mode;
	}

	public static SequenceDefinition edit(SequenceDefinition definition, String id, int page, String mode) {
		definition.target(id);
		int tick = tickForPage(page);
		if (!List.of("on", "off", "pulse", "clear").contains(mode)) throw new IllegalArgumentException("Mode d'outil inconnu.");
		var actions = new ArrayList<>(definition.actions());
		List<SequenceAction> replaced = actions.stream().filter(a -> a.tick() == tick && a.pistonId().equals(id)).toList();
		for (SequenceAction action : replaced) {
			if (action.pulseEdge() && !action.powered()) throw new IllegalArgumentException("Fin automatique d'impulsion : modifier sa page de départ.");
			removePair(actions, action);
		}
		if (!mode.equals("clear")) {
			boolean pulse = mode.equals("pulse");
			if (pulse && page == MAX_PAGE) throw new IllegalArgumentException("Réserver la page suivante pour la coupure automatique de l'impulsion.");
			if (pulse) {
				// The off edge is two game ticks later; another action at that boundary is ambiguous.
				if (actions.stream().anyMatch(a -> a.pistonId().equals(id) && a.tick() > tick && a.tick() <= tick + 2)) {
					throw new IllegalArgumentException("L'impulsion chevauche une autre action de ce piston.");
				}
				if (modPowerBefore(copy(definition, actions), id, tick)) throw new IllegalArgumentException("Retirer d'abord le signal du mod avant une impulsion.");
			}
			actions.add(new SequenceAction(tick, nextOrder(actions, tick), id, !mode.equals("off"), pulse));
			if (pulse) actions.add(new SequenceAction(tick + 2, nextOrder(actions, tick + 2), id, false, true));
		}
		return copy(definition, actions);
	}

	public static SequenceDefinition remove(SequenceDefinition definition, int tick, int order) {
		var actions = new ArrayList<>(definition.actions());
		SequenceAction selected = actions.stream().filter(a -> a.tick() == tick && a.order() == order).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Action introuvable."));
		removePair(actions, selected);
		return copy(definition, actions);
	}

	public static SequenceDefinition swap(SequenceDefinition definition, int tick, int first, int second) {
		SequenceAction a = definition.actions().stream().filter(x -> x.tick() == tick && x.order() == first).findFirst().orElseThrow();
		SequenceAction b = definition.actions().stream().filter(x -> x.tick() == tick && x.order() == second).findFirst().orElseThrow();
		var actions = new ArrayList<>(definition.actions());
		actions.remove(a); actions.remove(b);
		actions.add(new SequenceAction(tick, second, a.pistonId(), a.powered(), a.pulseEdge()));
		actions.add(new SequenceAction(tick, first, b.pistonId(), b.powered(), b.pulseEdge()));
		return copy(definition, actions);
	}

	private static void removePair(List<SequenceAction> actions, SequenceAction action) {
		actions.remove(action);
		if (action.pulseEdge()) {
			int pairedTick = action.tick() + (action.powered() ? 2 : -2);
			actions.removeIf(a -> a.pulseEdge() && a.pistonId().equals(action.pistonId()) && a.tick() == pairedTick && a.powered() != action.powered());
		}
	}

	private static int nextOrder(List<SequenceAction> actions, int tick) {
		return Math.addExact(actions.stream().filter(a -> a.tick() == tick).mapToInt(SequenceAction::order).max().orElse(-1), 1);
	}

	private static SequenceDefinition copy(SequenceDefinition original, List<SequenceAction> actions) {
		return original.withActions(actions);
	}
}
