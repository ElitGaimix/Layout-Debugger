package fr.eligaimix.ltdr.piston;

import fr.eligaimix.ltdr.platform.NativeAccess;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-thread controller. Virtual input is additive: off removes this mod's
 * signal, and real redstone remains authoritative. Position changes are observed
 * from the actual vanilla movement batches, never inferred by scanning for blocks.
 */
public final class RedstonePistonController {
    private static final Map<ServerLevel, RedstonePistonController> ACTIVE = new IdentityHashMap<>();

    private final ServerLevel level;
    private final Map<String, PistonTarget> targets = new LinkedHashMap<>();
    private final Map<String, BlockPos> locations = new LinkedHashMap<>();
    private final Map<BlockPos, String> idsByPosition = new java.util.HashMap<>();
    private final Set<String> powered = new LinkedHashSet<>();
    private final Set<String> destroyed = new HashSet<>();
    private boolean attached;
    private java.util.function.Consumer<BlockPos> pathListener = pos -> {};
    public void setPathListener(java.util.function.Consumer<BlockPos> listener) { pathListener = listener; }
    public static void recordSpace(Level level, BlockPos position) { if (level instanceof ServerLevel server) { var c = ACTIVE.get(server); if (c != null) c.pathListener.accept(position.immutable()); } }

    public RedstonePistonController(ServerLevel level, List<PistonTarget> selectedTargets) {
        this(level, selectedTargets, true);
    }

    private RedstonePistonController(ServerLevel level, List<PistonTarget> selectedTargets, boolean attach) {
        this.level = level;
        Set<BlockPos> positions = new HashSet<>();
        for (PistonTarget target : selectedTargets) {
            if (targets.putIfAbsent(target.id(), target) != null || !positions.add(position(target))) {
                throw new IllegalArgumentException("Identifiant ou position de piston dupliqué : " + target.id());
            }
            locations.put(target.id(), position(target));
            idsByPosition.put(position(target), target.id());
        }
        validate();
        if (attach) {
            if (ACTIVE.containsKey(level)) {
                throw new IllegalStateException("Une alimentation Layout Debugger est déjà active dans cette dimension.");
            }
            ACTIVE.put(level, this);
            attached = true;
        }
    }

    /** Read-only selection checks; does not install a temporary active controller. */
    public static void validateTargets(ServerLevel level, List<PistonTarget> selectedTargets) {
        new RedstonePistonController(level, selectedTargets, false);
    }

    /** Neither force-loads chunks nor rejects an otherwise valid extended piston. */
    public void validate() {
        for (PistonTarget target : targets.values()) {
            BlockPos position = locations.get(target.id());
            if (destroyed.contains(target.id())) {
                throw new IllegalStateException("Piston détruit pendant la lecture : " + target.id());
            }
            if (level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)) {
                throw new IllegalStateException("Piston hors des limites du monde : " + target.id());
            }
            if (!level.isLoaded(position) || !level.shouldTickBlocksAt(position)) {
                throw new IllegalStateException("Le chunk du piston " + target.id() + " doit être chargé et simulé.");
            }
            if (!matches(target, movedState(position))) {
                throw new IllegalStateException("Piston absent, détruit ou modifié : " + target.id() + " à " + position.toShortString());
            }
        }
    }

    public void setPowered(String id, boolean requestedPower) {
        requireTarget(id);
        if (!attached) throw new IllegalStateException("Contrôleur de pistons détaché.");
        validate();
        boolean changed = requestedPower ? powered.add(id) : powered.remove(id);
        if (changed) reevaluate(locations.get(id));
    }

    /** Actual vanilla power test, including this controller's additive input. */
    public boolean hasEffectivePower(String id) {
        PistonTarget target = requireTarget(id);
        BlockPos position = locations.get(id);
        BlockState state = movedState(position);
        if (!matches(target, state)) return false;
        return NativeAccess.signal((PistonBaseBlock) state.getBlock(),level, position, direction(target));
    }

    public boolean isModPowered(String id) {
        requireTarget(id);
        return attached && powered.contains(id);
    }

    /** The underlying piston state, including while carried by a moving block. */
    public BlockState currentState(String id) {
        requireTarget(id);
        return movedState(locations.get(id));
    }

    public boolean isMoving(String id) {
        requireTarget(id);
        return level.getBlockState(locations.get(id)).is(Blocks.MOVING_PISTON);
    }

    public Map<String, BlockPos> positions() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(locations));
    }

    public String targetIdAt(BlockPos position) {
        return idsByPosition.get(position);
    }

    /** A head click belongs to its base, including vanilla's moving source head. */
    public String targetIdForHit(BlockPos hit) {
        String direct = targetIdAt(hit);
        if (direct != null) return direct;
        BlockState state = level.getBlockState(hit);
        if (level.getBlockEntity(hit) instanceof PistonMovingBlockEntity moving && moving.isSourcePiston()) state = moving.getMovedState();
        if (!state.is(Blocks.PISTON_HEAD)) return null;
        Direction facing = state.getValue(PistonBaseBlock.FACING);
        String id = targetIdAt(hit.relative(facing.getOpposite()));
        return id != null && direction(targets.get(id)) == facing ? id : null;
    }

    /** Promotes a tracked baseline piston to a user-facing id without losing its movement. */
    public void renameTarget(String existingId, PistonTarget replacement) {
        PistonTarget previous = requireTarget(existingId);
        if (!attached) throw new IllegalStateException("Contrôleur de pistons détaché.");
        if (!previous.position().equals(replacement.position()) || !previous.facing().equals(replacement.facing())
                || previous.sticky() != replacement.sticky()) {
            throw new IllegalArgumentException("Le nouvel identifiant doit désigner le même piston initial.");
        }
        if (!existingId.equals(replacement.id()) && targets.containsKey(replacement.id())) {
            throw new IllegalArgumentException("Identifiant de piston déjà utilisé : " + replacement.id());
        }
        Map<String, PistonTarget> renamedTargets = new LinkedHashMap<>();
        Map<String, BlockPos> renamedLocations = new LinkedHashMap<>();
        targets.forEach((id, target) -> {
            String renamed = id.equals(existingId) ? replacement.id() : id;
            renamedTargets.put(renamed, id.equals(existingId) ? replacement : target);
            renamedLocations.put(renamed, locations.get(id));
        });
        targets.clear(); targets.putAll(renamedTargets);
        locations.clear(); locations.putAll(renamedLocations);
        Set<String> renamedPower = new LinkedHashSet<>();
        powered.forEach(id -> renamedPower.add(id.equals(existingId) ? replacement.id() : id));
        powered.clear(); powered.addAll(renamedPower);
        if (destroyed.remove(existingId)) destroyed.add(replacement.id());
        rebuildPositionIndex();
    }

    /**
     * Stops supplying power before reevaluating pistons. No world blocks are
     * restored or deleted. A source chunk may be loaded for the final update;
     * vanilla keeps its own queued event until the chunk is simulated again.
     */
    public List<String> release() {
        if (!attached) return List.of();
        List<String> previouslyPowered = List.copyOf(powered);
        powered.clear();
        ACTIVE.remove(level, this);
        attached = false;
        List<String> warnings = new ArrayList<>();
        for (String id : previouslyPowered) {
            BlockPos position = locations.get(id);
            try {
                level.getChunkAt(position);
                reevaluate(position);
            } catch (RuntimeException exception) {
                warnings.add("Signal du mod retiré pour " + id + ", mise à jour vanilla impossible : " + exception.getMessage());
            }
        }
        return List.copyOf(warnings);
    }

    public boolean hasOwnedSources() {
        return attached && !powered.isEmpty();
    }

    /** Used by lifecycle shutdown as a final guard against a leaked controller. */
    public static void releaseAll(MinecraftServer server) {
        for (var entry : List.copyOf(ACTIVE.entrySet())) {
            if (entry.getKey().getServer() == server) entry.getValue().release();
        }
    }

    /** Mixin power hook: no influence on clients, other dimensions or unselected pistons. */
    public static boolean hasVirtualPower(SignalGetter signalGetter, BlockPos position) {
        if (!(signalGetter instanceof ServerLevel level)) return false;
        RedstonePistonController controller = ACTIVE.get(level);
        if (controller == null) return false;
        String id = controller.targetIdAt(position);
        return id != null && controller.powered.contains(id) && !controller.destroyed.contains(id);
    }

    /** Called after the real vanilla resolver succeeds, before any block mutation. */
    public static void beforeMove(Level movingLevel, List<BlockPos> pushed, List<BlockPos> removed, Direction direction) {
        if (!(movingLevel instanceof ServerLevel level)) return;
        RedstonePistonController controller = ACTIVE.get(level);
        if (controller == null) return;
        for (BlockPos pos : pushed) { controller.pathListener.accept(pos.immutable()); controller.pathListener.accept(pos.relative(direction).immutable()); }
        Set<BlockPos> removalSet = new HashSet<>(removed);
        for (var entry : controller.locations.entrySet()) {
            if (removalSet.contains(entry.getValue())) controller.destroyed.add(entry.getKey());
        }
        Map<String, BlockPos> moved = MovementBatch.translate(controller.locations, new HashSet<>(pushed), pos -> pos.relative(direction).immutable());
        controller.locations.clear();
        controller.locations.putAll(moved);
        controller.rebuildPositionIndex();
    }

    /** Retained for old command callers; virtual input no longer occupies this slot. */
    public static BlockPos sourcePosition(PistonTarget target) {
        return position(target).relative(direction(target).getOpposite());
    }

    private void reevaluate(BlockPos position) {
        BlockState state = level.getBlockState(position);
        // A signal may expire while the piston is carried. Do not replay that
        // pulse later: onPlace/settlement will query the then-current input.
        if (state.getBlock() instanceof PistonBaseBlock) {
            level.neighborChanged(position, Blocks.REDSTONE_BLOCK, null);
        }
    }

    private BlockState movedState(BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (state.is(Blocks.MOVING_PISTON) && level.getBlockEntity(position) instanceof PistonMovingBlockEntity moving) {
            return moving.getMovedState();
        }
        return state;
    }

    private PistonTarget requireTarget(String id) {
        PistonTarget target = targets.get(id);
        if (target == null) throw new IllegalArgumentException("Piston inconnu : " + id);
        return target;
    }

    private void rebuildPositionIndex() {
        idsByPosition.clear();
        locations.forEach((id, position) -> idsByPosition.put(position, id));
    }

    private static boolean matches(PistonTarget target, BlockState state) {
        return state.is(target.sticky() ? Blocks.STICKY_PISTON : Blocks.PISTON)
            && state.getValue(PistonBaseBlock.FACING) == direction(target);
    }

    private static BlockPos position(PistonTarget target) {
        return new BlockPos(target.position().x(), target.position().y(), target.position().z());
    }

    private static Direction direction(PistonTarget target) {
        Direction direction = Direction.byName(target.facing());
        if (direction == null) throw new IllegalArgumentException("Orientation invalide : " + target.facing());
        return direction;
    }
}
