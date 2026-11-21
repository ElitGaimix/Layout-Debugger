package fr.eligaimix.ltdr.preview;

import fr.eligaimix.ltdr.platform.NativeAccess;
import fr.eligaimix.ltdr.platform.NativeAccess.ChunkTickView;
import fr.eligaimix.ltdr.platform.NativeAccess.LevelView;
import fr.eligaimix.ltdr.platform.NativeAccess.TickView;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * In-memory baseline for a bounded redstone layout, captured and restored between level ticks. This
 * reconstructs regional mechanical state, not global time, random generators, entities or activity
 * outside the selected bounds.
 */
public final class WorldSnapshot {
    private java.util.Map<java.util.UUID, net.minecraft.world.entity.item.ItemEntity>
            generatedItems = new java.util.HashMap<>();
    private static final int RESTORE_FLAGS =
            Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_CLIENTS;
    private final ServerLevel level;
    private final EditBounds bounds;
    private final BlockState[] states;
    private final Map<BlockPos, CompoundTag> blockEntities;
    private final List<RelativeTick<Block>> blockTicks;
    private final List<RelativeTick<Fluid>> fluidTicks;
    private final List<BlockEventData> blockEvents;
    private final List<String> warnings;

    private WorldSnapshot(
            ServerLevel level,
            EditBounds bounds,
            BlockState[] states,
            Map<BlockPos, CompoundTag> blockEntities,
            List<RelativeTick<Block>> blockTicks,
            List<RelativeTick<Fluid>> fluidTicks,
            List<BlockEventData> blockEvents,
            List<String> warnings) {
        this.level = level;
        this.bounds = bounds;
        this.states = states;
        this.blockEntities = blockEntities;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
        this.blockEvents = blockEvents;
        this.warnings = List.copyOf(warnings);
    }

    public static WorldSnapshot capture(ServerLevel level, EditBounds bounds) {
        validateArea(level, bounds);
        awaitEntities(level, bounds);

        var states = new BlockState[bounds.volume()];
        var entities = new LinkedHashMap<BlockPos, CompoundTag>();
        boolean randomTicks = false;
        int index = 0;
        for (BlockPos pos : bounds.positions()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.MOVING_PISTON)) {
                throw new IllegalStateException(
                        "Attendre la fin des mouvements de pistons avant de capturer l'état initial"
                            + " : "
                                + pos.toShortString());
            }
            states[index++] = state;
            randomTicks |= state.isRandomlyTicking();
            if (state.hasBlockEntity()) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity == null)
                    throw new IllegalStateException(
                            "Entité de bloc absente à " + pos.toShortString());
                entities.put(
                        pos.immutable(),
                        entity.saveWithFullMetadata(level.registryAccess()).copy());
            }
        }
        long now = level.getGameTime();
        var events = new LinkedHashSet<BlockEventData>();
        LevelView accessor = NativeAccess.level(level);
        accessor.layoutDebugger$blockEvents().stream()
                .filter(event -> bounds.includes(event.pos()))
                .forEach(events::add);
        accessor.layoutDebugger$blockEventsToReschedule().stream()
                .filter(event -> bounds.includes(event.pos()))
                .forEach(events::add);
        var warnings = new ArrayList<String>();
        if (randomTicks)
            warnings.add(
                    "La zone contient des blocs à ticks aléatoires ; leur évolution ne peut pas"
                        + " être reproduite à l'identique.");
        warnings.add(
                "Prévisualisation régionale : isoler le montage des circuits extérieurs ; le temps"
                    + " global et les entités ne sont pas rembobinés.");
        return new WorldSnapshot(
                level,
                bounds,
                states,
                entities,
                captureTicks(level.getBlockTicks(), bounds, now),
                captureTicks(level.getFluidTicks(), bounds, now),
                List.copyOf(events),
                warnings);
    }

    /** No neighbor callbacks or item drops are generated while rebuilding the baseline. */
    public void restore() {
        validateBounds(level, bounds);
        // Cleanup after disconnect/dimension change must work even if the area is
        // no longer simulated. Load only the actual captured chunks, with no ticket.
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        awaitEntities(level, bounds);
        // Only drops owned by this preview are disposable. Existing entities and player drops
        // survive.
        generatedItems.values().stream()
                .filter(net.minecraft.world.entity.Entity::isAlive)
                .forEach(net.minecraft.world.entity.Entity::discard);
        generatedItems.clear();
        clearScheduledInputs();
        // Remove all current BEs first, including MOVING_PISTON. Their on-remove
        // effects must not finish a move into a cell which is about to be restored.
        for (BlockPos pos : bounds.positions()) {
            if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
        }
        int index = 0;
        for (BlockPos pos : bounds.positions()) {
            BlockState saved = states[index++];
            if (level.getBlockState(pos) != saved && !level.setBlock(pos, saved, RESTORE_FLAGS)) {
                throw new IllegalStateException("Restauration impossible à " + pos.toShortString());
            }
        }
        for (var entry : blockEntities.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            BlockEntity loaded =
                    BlockEntity.loadStatic(
                            pos, state, entry.getValue().copy(), level.registryAccess());
            if (loaded == null)
                throw new IllegalStateException(
                        "Restauration de l'entité de bloc impossible à " + pos.toShortString());
            // setBlock may have created a default BE. Unregister its listener and
            // ticker explicitly before replacing it with the saved instance.
            if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
            level.setBlockEntity(loaded);
            // BlockEntity.setChanged would notify comparators while other BEs
            // are still being restored. Mark persistence dirty without redstone.
            level.blockEntityChanged(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        // Block placement can create fresh block entities; discard any incidental
        // scheduled inputs before putting back the actual baseline queues.
        clearScheduledInputs();
        long now = level.getGameTime();
        restoreTicks(level.getBlockTicks(), blockTicks, now);
        restoreTicks(level.getFluidTicks(), fluidTicks, now);
        NativeAccess.level(level).layoutDebugger$blockEvents().addAll(blockEvents);
    }

    public EditBounds bounds() {
        return bounds;
    }

    public record Cell(BlockState state, CompoundTag entity) {
        public Cell {
            if (entity != null) entity = entity.copy();
        }
    }

    /** A construction revision changes the shared baseline, not a timeline frame. */
    public WorldSnapshot withChanges(Map<BlockPos, Cell> changes) {
        return withChanges(changes, PendingInputs.empty());
    }

    public WorldSnapshot withChanges(Map<BlockPos, Cell> changes, PendingInputs inputs) {
        BlockState[] updated = states.clone();
        var entities = new LinkedHashMap<>(blockEntities);
        int index = 0;
        for (BlockPos pos : bounds.positions()) {
            Cell cell = changes.get(pos);
            if (cell != null) {
                updated[index] = cell.state();
                entities.remove(pos);
                if (cell.entity() != null) entities.put(pos.immutable(), cell.entity().copy());
            }
            index++;
        }
        var changed =
                new WorldSnapshot(
                        level,
                        bounds,
                        updated,
                        entities,
                        merge(
                                blockTicks.stream()
                                        .filter(t -> !changes.containsKey(t.pos()))
                                        .toList(),
                                inputs.blocks),
                        merge(
                                fluidTicks.stream()
                                        .filter(t -> !changes.containsKey(t.pos()))
                                        .toList(),
                                inputs.fluids),
                        merge(
                                blockEvents.stream()
                                        .filter(e -> !changes.containsKey(e.pos()))
                                        .toList(),
                                inputs.events),
                        warnings);
        changed.generatedItems = generatedItems;
        return changed;
    }

    /** Only inputs newly queued by the player's packet enter the construction revision. */
    public PendingInputs pendingInputs() {
        var accessor = NativeAccess.level(level);
        return new PendingInputs(
                captureTicks(level.getBlockTicks(), bounds, level.getGameTime()),
                captureTicks(level.getFluidTicks(), bounds, level.getGameTime()),
                merge(
                        accessor.layoutDebugger$blockEvents().stream()
                                .filter(e -> bounds.includes(e.pos()))
                                .toList(),
                        accessor.layoutDebugger$blockEventsToReschedule().stream()
                                .filter(e -> bounds.includes(e.pos()))
                                .toList()));
    }

    private static <T> List<T> merge(List<T> first, List<T> second) {
        var result = new LinkedHashSet<T>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    public static final class PendingInputs {
        private final List<RelativeTick<Block>> blocks;
        private final List<RelativeTick<Fluid>> fluids;
        private final List<BlockEventData> events;

        private PendingInputs(
                List<RelativeTick<Block>> blocks,
                List<RelativeTick<Fluid>> fluids,
                List<BlockEventData> events) {
            this.blocks = blocks;
            this.fluids = fluids;
            this.events = events;
        }

        public static PendingInputs empty() {
            return new PendingInputs(List.of(), List.of(), List.of());
        }

        public PendingInputs addedSince(PendingInputs before) {
            return new PendingInputs(
                    blocks.stream().filter(t -> !before.blocks.contains(t)).toList(),
                    fluids.stream().filter(t -> !before.fluids.contains(t)).toList(),
                    events.stream().filter(e -> !before.events.contains(e)).toList());
        }

        public PendingInputs plus(PendingInputs other) {
            return new PendingInputs(
                    merge(blocks, other.blocks),
                    merge(fluids, other.fluids),
                    merge(events, other.events));
        }
    }

    public List<String> warnings() {
        return warnings;
    }

    public void validateArea() {
        validateArea(level, bounds);
    }

    private void clearScheduledInputs() {
        clearTicks(level.getBlockTicks(), bounds);
        clearTicks(level.getFluidTicks(), bounds);
        LevelView accessor = NativeAccess.level(level);
        accessor.layoutDebugger$blockEvents().removeIf(event -> bounds.includes(event.pos()));
        accessor.layoutDebugger$blockEventsToReschedule()
                .removeIf(event -> bounds.includes(event.pos()));
    }

    private static void validateArea(ServerLevel level, EditBounds bounds) {
        validateBounds(level, bounds);
        // Also require a chunk of simulated margin for immediate boundary updates.
        for (int x = (bounds.minX() >> 4) - 1; x <= (bounds.maxX() >> 4) + 1; x++) {
            for (int z = (bounds.minZ() >> 4) - 1; z <= (bounds.maxZ() >> 4) + 1; z++) {
                BlockPos sample = new BlockPos(x << 4, bounds.minY(), z << 4);
                if (!level.isLoaded(sample) || !level.shouldTickBlocksAt(sample)) {
                    throw new IllegalStateException(
                            "La zone d'édition et sa marge doivent être chargées et simulées."
                                + " Rapprochez-vous du montage ou réduisez le rayon.");
                }
            }
        }
    }

    private static void validateBounds(ServerLevel level, EditBounds bounds) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException(
                    "La prévisualisation doit être exécutée sur le serveur.");
        BlockPos minimum = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockPos maximum = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (level.isOutsideBuildHeight(minimum)
                || level.isOutsideBuildHeight(maximum)
                || !level.getWorldBorder().isWithinBounds(minimum)
                || !level.getWorldBorder().isWithinBounds(maximum)) {
            throw new IllegalArgumentException("La zone d'édition dépasse les limites du monde.");
        }
    }

    public void recordSimulationItem(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                && item.getOwner() == null
                && bounds.includes(entity.blockPosition())) {
            item.setNeverPickUp();
            generatedItems.put(entity.getUUID(), item);
        }
    }

    /**
     * Vanilla's waitForEntities has no deadline when entity-file I/O fails. Process the same inbox
     * with a bounded wait, preserving the baseline for a later retry instead of permanently
     * blocking the server thread.
     */
    private static void awaitEntities(ServerLevel level, EditBounds bounds) {
        if (entitiesLoaded(level, bounds)) return;
        long deadline = System.nanoTime() + 5_000_000_000L;
        var manager = NativeAccess.level(level).layoutDebugger$entityManager();
        level.getServer()
                .managedBlock(() -> {
                    if (manager != null) manager.processPendingLoads();
                    return entitiesLoaded(level, bounds) || System.nanoTime() - deadline >= 0;
                });
        if (!entitiesLoaded(level, bounds)) {
            throw new IllegalStateException(
                    "Chargement des entités de la zone trop long ou impossible. État initial"
                        + " conservé ; consulter le journal puis réessayer la restauration.");
        }
    }

    private static boolean entitiesLoaded(ServerLevel level, EditBounds bounds) {
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> TickView<T> ticksAccessor(LevelTicks<T> ticks) {
        return NativeAccess.ticks(ticks);
    }

    private static <T> List<RelativeTick<T>> captureTicks(
            LevelTicks<T> ticks, EditBounds bounds, long now) {
        TickView<T> accessor = ticksAccessor(ticks);
        var scheduled = new ArrayList<ScheduledTick<T>>();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                var container = accessor.layoutDebugger$containers().get(ChunkPos.pack(x, z));
                if (container != null)
                    container
                            .getAll()
                            .filter(tick -> bounds.includes(tick.pos()))
                            .forEach(scheduled::add);
            }
        }
        accessor.layoutDebugger$toRun().stream()
                .filter(tick -> bounds.includes(tick.pos()))
                .forEach(scheduled::add);
        scheduled.sort(ScheduledTick.DRAIN_ORDER);
        return scheduled.stream()
                .map(tick ->
                        new RelativeTick<>(
                                tick.type(),
                                tick.pos(),
                                tick.triggerTick() - now,
                                tick.priority(),
                                tick.subTickOrder()))
                .toList();
    }

    private static <T> void clearTicks(LevelTicks<T> ticks, EditBounds bounds) {
        ticks.clearArea(bounds.box());
        TickView<T> accessor = ticksAccessor(ticks);
        accessor.layoutDebugger$toRunSet().removeIf(tick -> bounds.includes(tick.pos()));
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                LevelChunkTicks<T> container =
                        accessor.layoutDebugger$containers().get(ChunkPos.pack(x, z));
                if (container == null) continue;
                ChunkTickView<T> chunk = chunkTicksAccessor(container);
                List<SavedTick<T>> pending = chunk.layoutDebugger$pending();
                if (pending != null) {
                    chunk.layoutDebugger$pending(
                            new ArrayList<>(
                                    pending.stream()
                                            .filter(tick -> !bounds.includes(tick.pos()))
                                            .toList()));
                    chunk.layoutDebugger$positions().removeIf(tick -> bounds.includes(tick.pos()));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ChunkTickView<T> chunkTicksAccessor(LevelChunkTicks<T> ticks) {
        return NativeAccess.chunkTicks(ticks);
    }

    private static <T> void restoreTicks(
            LevelTicks<T> ticks, List<RelativeTick<T>> saved, long now) {
        for (RelativeTick<T> tick : saved) {
            LevelChunkTicks<T> container =
                    ticksAccessor(ticks).layoutDebugger$containers().get(ChunkPos.pack(tick.pos()));
            if (container == null)
                throw new IllegalStateException(
                        "Conteneur de ticks absent à " + tick.pos().toShortString());
            ChunkTickView<T> chunk = chunkTicksAccessor(container);
            List<SavedTick<T>> pending = chunk.layoutDebugger$pending();
            if (pending == null) {
                ticks.schedule(
                        new ScheduledTick<>(
                                tick.type(),
                                tick.pos(),
                                now + tick.delay(),
                                tick.priority(),
                                tick.order()));
            } else {
                // Only a serialized, not-yet-unpacked queue is parked until chunk
                // activation. An already-unpacked queue keeps vanilla absolute
                // scheduling even when outside the current ticking range.
                var updated = new ArrayList<>(pending);
                updated.add(
                        new SavedTick<>(
                                tick.type(),
                                tick.pos(),
                                Math.toIntExact(tick.delay()),
                                tick.priority()));
                chunk.layoutDebugger$pending(updated);
                chunk.layoutDebugger$positions().add(ScheduledTick.probe(tick.type(), tick.pos()));
            }
        }
    }

    private record RelativeTick<T>(
            T type,
            BlockPos pos,
            long delay,
            net.minecraft.world.ticks.TickPriority priority,
            long order) {}
}
