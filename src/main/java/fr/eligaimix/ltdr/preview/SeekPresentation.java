package fr.eligaimix.ltdr.preview;

import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;

/** One client-main-thread bundle replaces all affected chunks with the final frozen frame. */
public final class SeekPresentation {
	private SeekPresentation() {}
	public static void send(ServerPlayer player, EditBounds bounds) {
		ReplayVisibility.finish(player, () -> {
			var level = player.level();
			var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
			packets.add(new ClientboundTickingStepPacket(0));
			packets.add(ClientboundTickingStatePacket.from(level.tickRateManager()));
			for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
				for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
					if (!level.hasChunk(x,z)) continue;
					var packet = new ClientboundLevelChunkWithLightPacket(level.getChunk(x,z),level.getLightEngine(),null,null);
					// Vanilla serializes progressO for interpolation. A frozen frame needs current progress.
					packet.getChunkData().getBlockEntitiesTagsConsumer(x,z).accept((pos,type,tag) -> {
						if (tag != null && bounds.includes(pos) && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving)
							tag.putFloat("progress",moving.getProgress(1.0F));
					});
					packets.add(packet);
				}
			}
			player.connection.send(new ClientboundBundlePacket(packets));
		});
	}
}
