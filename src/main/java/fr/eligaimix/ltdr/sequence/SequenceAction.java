package fr.eligaimix.ltdr.sequence;

import java.util.Comparator;

/** An input change, not an assertion that a piston has finished moving. */
public record SequenceAction(int tick, int order, String pistonId, boolean powered, boolean pulseEdge) {
	public static final Comparator<SequenceAction> PLAYBACK_ORDER = Comparator.comparingInt(SequenceAction::tick)
		.thenComparingInt(SequenceAction::order);

	public SequenceAction {
		if (tick < 0 || order < 0) {
			throw new IllegalArgumentException("Le tick et l'ordre doivent être positifs ou nuls.");
		}
		SequenceDefinition.validateId(pistonId);
	}

	public SequenceAction(int tick, int order, String pistonId, boolean powered) {
		this(tick, order, pistonId, powered, false);
	}
}
