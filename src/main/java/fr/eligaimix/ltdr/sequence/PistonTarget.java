package fr.eligaimix.ltdr.sequence;

import java.util.Objects;
import java.util.Set;

/** The expected piston identity, including orientation so replacements are detected. */
public record PistonTarget(String id, BlockPosition position, String facing, boolean sticky) {
	private static final Set<String> FACINGS = Set.of("down", "up", "north", "south", "west", "east");

	public PistonTarget {
		SequenceDefinition.validateId(id);
		Objects.requireNonNull(position, "position");
		if (!FACINGS.contains(facing)) {
			throw new IllegalArgumentException("Direction de piston invalide : " + facing);
		}
	}
}
