package fr.eligaimix.ltdr.mixin;

import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** MOVING_PISTON.newBlockEntity returns null: materialize its received BE before vanilla loads NBT. */
@Mixin(LevelChunk.class)
public abstract class ReplayChunkMixin {
	@ModifyVariable(method = "replaceWithPacketData", at = @At("HEAD"), argsOnly = true)
	private Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> layoutDebugger$loadMovingPistons(
			Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> original) {
		LevelChunk chunk = (LevelChunk)(Object)this;
		if (!chunk.getLevel().isClientSide()) return original;
		return output -> original.accept((pos,type,tag) -> {
			if (tag != null && type == BlockEntityTypes.PISTON && chunk.getBlockState(pos).is(Blocks.MOVING_PISTON)
					&& chunk.getBlockEntity(pos) == null) {
				chunk.setBlockEntity(new PistonMovingBlockEntity(pos.immutable(),chunk.getBlockState(pos)));
			}
			output.accept(pos,type,tag);
		});
	}
}
