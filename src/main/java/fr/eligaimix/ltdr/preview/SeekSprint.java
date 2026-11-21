package fr.eligaimix.ltdr.preview;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/** Owns a vanilla accelerated replay without changing the configured tick rate. */
public final class SeekSprint {
	private static final Set<MinecraftServer> OWNED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	private SeekSprint() {}

	/** Call between level ticks, before TickRateManager.tick() evaluates this tick. */
	public static void start(MinecraftServer server, int gameTicks) {
		if (gameTicks <= 0) throw new IllegalArgumentException("Le rejeu accéléré exige au moins un tick.");
		var manager = server.tickRateManager();
		if (manager.isSprinting() && !isOwned(server)) {
			throw new IllegalStateException("Une accélération Minecraft est déjà en cours. Attendre sa fin avant de naviguer.");
		}
		if (isOwned(server)) stop(server);
		manager.stopStepping();
		if (!manager.isFrozen()) manager.setFrozen(true);
		OWNED.add(server);
		try {
			// Vanilla remembers the frozen state and restores it at sprint completion.
			manager.requestGameToSprint(gameTicks);
		} catch (RuntimeException exception) {
			OWNED.remove(server);
			manager.setFrozen(true);
			throw exception;
		}
	}

	/** The caller's elapsed-tick counter is authoritative for the exact stop boundary. */
	public static void stop(MinecraftServer server) {
		var manager = server.tickRateManager();
		if (isOwned(server)) manager.stopSprinting();
		manager.stopStepping();
		if (!manager.isFrozen()) manager.setFrozen(true);
		// With zero remaining ticks vanilla finishes at the next main-loop boundary.
		// Keep ownership until the finish callback so its report is suppressed too.
	}

	public static boolean isOwned(MinecraftServer server) { return OWNED.contains(server); }

	/** Called by the targeted vanilla completion hook. */
	public static void finished(MinecraftServer server) { OWNED.remove(server); }

	/** Final lifecycle cleanup if the server stops before vanilla can finish. */
	public static void forget(MinecraftServer server) { OWNED.remove(server); }
}
