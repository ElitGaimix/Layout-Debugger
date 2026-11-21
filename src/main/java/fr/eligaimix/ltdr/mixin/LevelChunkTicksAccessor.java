package fr.eligaimix.ltdr.mixin;

import java.util.List;
import java.util.Set;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Serialized tick queues of loaded chunks which have not resumed simulation. */
@Mixin(LevelChunkTicks.class)
public interface LevelChunkTicksAccessor<T> extends fr.eligaimix.ltdr.platform.NativeAccess.ChunkTickView<T> {
	@Accessor("pendingTicks")
	@Nullable List<SavedTick<T>> layoutDebugger$pending();

	@Accessor("pendingTicks")
	void layoutDebugger$pending(@Nullable List<SavedTick<T>> pending);

	@Accessor("ticksPerPosition")
	Set<ScheduledTick<?>> layoutDebugger$positions();
}
