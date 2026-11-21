package fr.eligaimix.ltdr.preview;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Hides only fast internal replay. Vanilla still computes every server tick. */
public final class ReplayVisibility {
	private static final Map<UUID, Gate> GATES = new ConcurrentHashMap<>();
	private static final AtomicLong SUPPRESSED = new AtomicLong();
	private ReplayVisibility() {}
	private static final class Gate {
		final ServerLevel level; final EditBounds bounds;
		volatile boolean publishing;
		Gate(ServerPlayer player, EditBounds bounds) { this.level = player.level(); this.bounds = bounds; }
		boolean chunk(int x, int z) { return x >= bounds.minX() >> 4 && x <= bounds.maxX() >> 4 && z >= bounds.minZ() >> 4 && z <= bounds.maxZ() >> 4; }
		boolean position(double x, double z) { return chunk(net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4); }
	}
	public static void begin(ServerPlayer player, EditBounds bounds) {
		if (GATES.putIfAbsent(player.getUUID(), new Gate(player,bounds)) != null) return;
		publish(player, () -> {
			player.connection.send(new ClientboundTickingStepPacket(0));
			player.connection.send(new ClientboundTickingStatePacket(player.level().tickRateManager().tickrate(),true));
		});
	}
	public static boolean active(ServerPlayer player) { return GATES.containsKey(player.getUUID()); }
	public static long suppressedPackets() { return SUPPRESSED.get(); }
	public static void discard(ServerPlayer player) { GATES.remove(player.getUUID()); }
	/** Final bundles bypass the filter; gates are retired even if sending fails. */
	public static void finish(ServerPlayer player, Runnable sendFinalFrame) {
		Gate gate = GATES.get(player.getUUID());
		try { if (gate == null || gate.level == player.level()) publish(player,sendFinalFrame); }
		finally { discard(player); }
	}
	private static void publish(ServerPlayer player, Runnable action) {
		Gate gate = GATES.get(player.getUUID());
		if (gate != null) gate.publishing = true;
		try { action.run(); } finally { if (gate != null) gate.publishing = false; }
	}
	public static boolean suppress(ServerPlayer player, Packet<?> packet) {
		Gate gate = GATES.get(player.getUUID());
		if (gate == null || gate.publishing || player.level() != gate.level) return false;
		boolean hidden = false;
		if (packet instanceof ClientboundTickingStatePacket || packet instanceof ClientboundTickingStepPacket) hidden = true;
		else if (packet instanceof ClientboundBlockUpdatePacket p) hidden = gate.position(p.getPos().getX(),p.getPos().getZ());
		else if (packet instanceof ClientboundBlockEntityDataPacket p) hidden = gate.position(p.getPos().getX(),p.getPos().getZ());
		else if (packet instanceof ClientboundBlockEventPacket p) hidden = gate.position(p.getPos().getX(),p.getPos().getZ());
		else if (packet instanceof ClientboundLevelChunkWithLightPacket p) hidden = gate.chunk(p.getX(),p.getZ());
		else if (packet instanceof ClientboundLightUpdatePacket p) hidden = gate.chunk(p.getX(),p.getZ());
		else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
			boolean[] match = {false}; p.runUpdates((pos,state) -> { if (gate.position(pos.getX(),pos.getZ())) match[0] = true; }); hidden = match[0];
		} else if (packet instanceof ClientboundSoundPacket p) hidden = gate.position(p.getX(),p.getZ());
		else if (packet instanceof ClientboundLevelParticlesPacket p) hidden = gate.position(p.getX(),p.getZ());
		else if (packet instanceof ClientboundLevelEventPacket p) hidden = gate.position(p.getPos().getX(),p.getPos().getZ());
		if (hidden) SUPPRESSED.incrementAndGet();
		return hidden;
	}
}
