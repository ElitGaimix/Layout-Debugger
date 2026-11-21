package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.preview.SeekSprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps internal page reconstruction from producing /tick sprint chat reports. */
@Mixin(ServerTickRateManager.class)
public abstract class ServerTickRateManagerMixin {
	@Shadow @Final private MinecraftServer server;

	@Redirect(method = "finishTickSprint", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/MinecraftServer;createCommandSourceStack()Lnet/minecraft/commands/CommandSourceStack;"))
	private CommandSourceStack layoutDebugger$quietSeekReport(MinecraftServer sourceServer) {
		CommandSourceStack source = sourceServer.createCommandSourceStack();
		return SeekSprint.isOwned(sourceServer) ? source.withSuppressedOutput() : source;
	}

	@Inject(method = "finishTickSprint", at = @At("RETURN"))
	private void layoutDebugger$finishSeek(CallbackInfo callback) {
		SeekSprint.finished(server);
	}
}
