package fr.eligaimix.ltdr.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PistonBaseBlock.class)
public interface PistonBaseBlockAccessor extends fr.eligaimix.ltdr.platform.NativeAccess.PistonView {
    @Invoker("getNeighborSignal")
    boolean layoutDebugger$getNeighborSignal(SignalGetter level, BlockPos position, Direction direction);
}
