package fr.eligaimix.ltdr.mixin;

import fr.eligaimix.ltdr.piston.RedstonePistonController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "getNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void layoutDebugger$addInput(SignalGetter level, BlockPos position, Direction direction, CallbackInfoReturnable<Boolean> callback) {
        if (RedstonePistonController.hasVirtualPower(level, position)) callback.setReturnValue(true);
    }

    @Redirect(method = "moveBlocks", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean layoutDebugger$trackMovement(PistonStructureResolver resolver, Level level, BlockPos position, Direction direction, boolean extending) {
        boolean resolved = resolver.resolve();
        if (resolved) {
            if (extending) RedstonePistonController.recordSpace(level, position.relative(direction));
            RedstonePistonController.beforeMove(level, resolver.getToPush(), resolver.getToDestroy(), resolver.getPushDirection());
        }
        return resolved;
    }
}
