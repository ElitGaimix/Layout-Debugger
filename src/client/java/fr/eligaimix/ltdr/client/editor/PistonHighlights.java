package fr.eligaimix.ltdr.client.editor;

import fr.eligaimix.ltdr.network.EditorState;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;

/** Translucent per-page markers, submitted through vanilla's depth-tested gizmo pipeline. */
public final class PistonHighlights {
	private static final RenderStateDataKey<List<Highlight>> HIGHLIGHTS = RenderStateDataKey.create(() -> "layout-debugger page highlights");
	private PistonHighlights() {}

	public static void initialize() {
		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			EditorState state = EditorClient.state();
			context.levelState().setData(HIGHLIGHTS, List.of());
			if (!state.editing() || state.seeking()) return;
			var highlights = new ArrayList<Highlight>();
			for (var warning : state.warnings()) {
				BlockPos pos = new BlockPos(warning.x(),warning.y(),warning.z());
				if (!context.level().getBlockState(pos).isAir()) highlights.add(new Highlight(new AABB(pos).inflate(0.007),0xFFD069));
			}
			int tick = (state.page() - 1) * 2;
			var colors = new LinkedHashMap<String, Integer>();
			for (var action : state.actions()) {
				if (action.tick() < tick || action.tick() >= tick + 2) continue;
				int color = EditorClient.modeColor(action.powered() ? "on" : "off");
				colors.put(action.pistonId(), color & 0xFFFFFF);
			}
			if (colors.isEmpty()) { context.levelState().setData(HIGHLIGHTS, List.copyOf(highlights)); return; }
			var camera = context.camera().position();
			float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
			for (var piston : state.pistons()) {
				Integer color = colors.get(piston.id());
				if (color == null || camera.distanceToSqr(piston.x() + 0.5, piston.y() + 0.5, piston.z() + 0.5) > 256 * 256) continue;
				AABB box = PistonPicking.body(context.level(), piston, partialTick).inflate(0.003);
				highlights.add(new Highlight(box, color));
			}
			context.levelState().setData(HIGHLIGHTS, List.copyOf(highlights));
		});
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			List<Highlight> highlights = context.levelState().getDataOrDefault(HIGHLIGHTS, List.of());
			if (highlights.isEmpty()) return;
			try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
				for (Highlight highlight : highlights) {
					Gizmos.cuboid(highlight.box(), GizmoStyle.strokeAndFill(0xB0000000 | highlight.color(), 1.8F, 0x2E000000 | highlight.color()));
				}
			}
		});
	}

	private record Highlight(AABB box, int color) {}
}
