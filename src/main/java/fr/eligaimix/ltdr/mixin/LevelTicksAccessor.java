package fr.eligaimix.ltdr.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Queue;
import java.util.Set;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to unexecuted scheduled inputs, including the active tick's lookup cache. */
@Mixin(LevelTicks.class)
public interface LevelTicksAccessor<T> extends fr.eligaimix.ltdr.platform.NativeAccess.TickView<T> {
	@Accessor("allContainers")
	Long2ObjectMap<LevelChunkTicks<T>> layoutDebugger$containers();

	@Accessor("toRunThisTick")
	Queue<ScheduledTick<T>> layoutDebugger$toRun();

	@Accessor("toRunThisTickSet")
	Set<ScheduledTick<?>> layoutDebugger$toRunSet();
}
