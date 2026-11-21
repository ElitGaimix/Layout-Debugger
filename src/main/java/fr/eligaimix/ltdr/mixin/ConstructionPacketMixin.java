package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.preview.ConstructionEdits;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ConstructionPacketMixin {
	@Shadow public ServerPlayer player;
	@Inject(method = {"handleUseItemOn", "handlePlayerAction"}, at = @At("HEAD"))
	private void layoutDebugger$beginConstruction(CallbackInfo callback) { ConstructionEdits.begin(player); }
	@Inject(method = {"handleUseItemOn", "handlePlayerAction"}, at = @At("RETURN"))
	private void layoutDebugger$endConstruction(CallbackInfo callback) { ConstructionEdits.end(); }
}
