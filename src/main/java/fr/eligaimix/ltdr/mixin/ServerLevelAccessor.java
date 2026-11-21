package fr.eligaimix.ltdr.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Scoped block-event snapshotting; the original linked ordering is retained. */
@Mixin(ServerLevel.class)
public interface ServerLevelAccessor extends fr.eligaimix.ltdr.platform.NativeAccess.LevelView {
	@Accessor("blockEvents")
	ObjectLinkedOpenHashSet<BlockEventData> layoutDebugger$blockEvents();

	@Accessor("blockEventsToReschedule")
	List<BlockEventData> layoutDebugger$blockEventsToReschedule();

	@Accessor("entityManager")
	PersistentEntitySectionManager<Entity> layoutDebugger$entityManager();
}
