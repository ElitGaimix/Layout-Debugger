package fr.eligaimix.ltdr.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Persist a restored player before deleting their temporary-inventory recovery file. */
@Mixin(PlayerList.class)
public interface PlayerListSaveAccessor extends fr.eligaimix.ltdr.platform.NativeAccess.PlayerListView {
	@Invoker("save")
	void layoutDebugger$save(ServerPlayer player);
}
