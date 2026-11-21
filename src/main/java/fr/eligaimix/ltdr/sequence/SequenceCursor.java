package fr.eligaimix.ltdr.sequence;

import java.util.List;
import java.util.Objects;

/** Reads a snapshot one game tick at a time, without touching Minecraft or the clock. */
public final class SequenceCursor {
	private final List<SequenceAction> actions;
	private int actionIndex;
	private long nextTick;

	public SequenceCursor(SequenceDefinition snapshot) {
		actions = Objects.requireNonNull(snapshot).actions();
	}

	public long nextTick() {
		return nextTick;
	}

	public boolean isFinished() {
		return actionIndex == actions.size();
	}

	public List<SequenceAction> advance() {
		if (isFinished()) {
			return List.of();
		}
		int from = actionIndex;
		while (actionIndex < actions.size() && actions.get(actionIndex).tick() == nextTick) {
			actionIndex++;
		}
		nextTick++;
		return actions.subList(from, actionIndex);
	}
}
