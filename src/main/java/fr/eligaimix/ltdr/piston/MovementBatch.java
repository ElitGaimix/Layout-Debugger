package fr.eligaimix.ltdr.piston;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Pure identity transfer. A whole movement is derived from the same old map. */
public final class MovementBatch {
    private MovementBatch() {}

    public static <P> Map<String, P> translate(Map<String, P> identities, Set<P> pushed, UnaryOperator<P> destination) {
        Map<String, P> result = new LinkedHashMap<>();
        identities.forEach((id, position) -> result.put(id, pushed.contains(position) ? destination.apply(position) : position));
        if (result.values().stream().distinct().count() != result.size()) {
            throw new IllegalStateException("Deux identités de piston occupent la même position après un déplacement.");
        }
        return Collections.unmodifiableMap(result);
    }
}
