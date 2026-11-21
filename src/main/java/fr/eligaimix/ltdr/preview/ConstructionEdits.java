package fr.eligaimix.ltdr.preview;

import fr.eligaimix.ltdr.LayoutDebugger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Scopes block changes to a real player interaction, excluding simulation/reconstruction. */
public final class ConstructionEdits {
	private static final ThreadLocal<WorldSnapshot.PendingInputs> BEFORE = new ThreadLocal<>();
	private static final ThreadLocal<ServerPlayer> ACTOR = new ThreadLocal<>();
	private static final ThreadLocal<Set<BlockPos>> CHANGED = ThreadLocal.withInitial(LinkedHashSet::new);
	private ConstructionEdits() {}
	public static void begin(ServerPlayer player) {
		if (!player.level().getServer().isSameThread()) return;
		clear();
		var editor = LayoutDebugger.service(player.level().getServer()).editor();
		if (editor.mayBuild(player)) { ACTOR.set(player); BEFORE.set(editor.constructionInputs()); }
	}
	public static void changed(Level level, BlockPos position) {
		ServerPlayer player = ACTOR.get();
		if (player != null && player.level() == level) CHANGED.get().add(position.immutable());
	}
	public static void end() {
		ServerPlayer player = ACTOR.get();
		if (player == null) return;
		try {
			var cells = new LinkedHashMap<BlockPos, WorldSnapshot.Cell>();
			for (BlockPos pos : CHANGED.get()) {
				var entity = player.level().getBlockEntity(pos);
				cells.put(pos, new WorldSnapshot.Cell(player.level().getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata(player.registryAccess())));
			}
			var editor = LayoutDebugger.service(player.level().getServer()).editor();
			editor.constructionChanged(player, cells, editor.constructionInputs().addedSince(BEFORE.get()));
		} finally { clear(); }
	}
	public static void clear() { ACTOR.remove(); BEFORE.remove(); CHANGED.get().clear(); }
}
