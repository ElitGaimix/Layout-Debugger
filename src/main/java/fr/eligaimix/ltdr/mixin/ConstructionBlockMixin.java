package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.preview.ConstructionEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ConstructionBlockMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void layoutDebugger$recordConstruction(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> callback) {
		if (callback.getReturnValue()) ConstructionEdits.changed((Level) (Object) this, pos);
	}
}
