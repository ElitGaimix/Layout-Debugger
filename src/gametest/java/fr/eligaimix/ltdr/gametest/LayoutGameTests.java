package fr.eligaimix.ltdr.gametest;

import fr.eligaimix.ltdr.mixin.LevelTicksAccessor;
import fr.eligaimix.ltdr.mixin.ServerLevelAccessor;
import fr.eligaimix.ltdr.piston.RedstonePistonController;
import fr.eligaimix.ltdr.preview.EditBounds;
import fr.eligaimix.ltdr.preview.WorldSnapshot;
import fr.eligaimix.ltdr.sequence.BlockPosition;
import fr.eligaimix.ltdr.sequence.PistonTarget;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

/** One sequential scenario: controllers deliberately cannot coexist in a level. */
public final class LayoutGameTests {
	@GameTest(maxTicks = 110, skyAccess = true)
	public void movingPistonsAndReplay(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		fixture.forceChunks();
		helper.runAtTickTime(20, () -> fixture.checked(() -> {
			fixture.prepare();
			var item = helper.spawnItem(Items.SLIME_BALL, new BlockPos(6, 3, 5));
            item.setNoGravity(true);
            fixture.preservedItem=item;
            fixture.snapshot = WorldSnapshot.capture(helper.getLevel(), fixture.bounds);
            helper.assertTrue(item.isAlive(),"Existing items must not prevent capture or be deleted");
			fixture.controller = fixture.newController();
			fixture.controller.setPowered("a", true);
			helper.assertTrue(fixture.controller.isModPowered("a"), "Virtual ON must be immediate");
		}));
		helper.runAtTickTime(26, () -> fixture.checked(() -> {
			fixture.controller.validate();
			helper.assertTrue(fixture.controller.positions().get("b").equals(fixture.absolute(3, 2, 1)), "B must follow the first position in a movement chain");
			helper.assertTrue(fixture.controller.positions().get("c").equals(fixture.absolute(4, 2, 1)), "C must follow the second position without identity overwrite");
			helper.assertTrue(fixture.controller.currentState("a").getValue(PistonBaseBlock.EXTENDED), "A must extend through the vanilla block event");
			fixture.controller.renameTarget("b", fixture.target("visible_b", 2, 2, 1, "up", true));
			fixture.bId = "visible_b";
			helper.assertTrue(fixture.controller.targetIdAt(fixture.absolute(3, 2, 1)).equals(fixture.bId), "Promoting a moved piston must preserve its current position");
			fixture.controller.setPowered(fixture.bId, true);
		}));
		helper.runAtTickTime(32, () -> fixture.checked(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(fixture.absolute(3, 4, 1)).is(Blocks.STONE), "Moved B must push its stone with vanilla motion");
			helper.assertTrue(fixture.controller.currentState(fixture.bId).getValue(PistonBaseBlock.EXTENDED), "Moved B must remain controllable");
			helper.assertTrue(!fixture.controller.currentState("c").getValue(PistonBaseBlock.EXTENDED), "C must retain its independent identity");
			helper.getLevel().setBlock(fixture.absolute(3, 2, 2), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			fixture.controller.setPowered(fixture.bId, false);
		}));
		helper.runAtTickTime(38, () -> fixture.checked(() -> {
			helper.assertTrue(!fixture.controller.isModPowered(fixture.bId) && fixture.controller.hasEffectivePower(fixture.bId), "OFF must remove only virtual power");
			helper.assertTrue(fixture.controller.currentState(fixture.bId).getValue(PistonBaseBlock.EXTENDED), "External redstone must keep B extended after OFF");
			helper.getLevel().setBlock(fixture.absolute(3, 2, 2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}));
		helper.runAtTickTime(44, () -> fixture.checked(() -> {
			helper.assertTrue(!fixture.controller.currentState(fixture.bId).getValue(PistonBaseBlock.EXTENDED), "B must retract after real power is removed");
			helper.assertTrue(helper.getLevel().getBlockState(fixture.absolute(3, 3, 1)).is(Blocks.STONE), "A sticky moved piston must pull its block");
			fixture.controller.setPowered(fixture.bId, true);
			helper.assertTrue(fixture.controller.isModPowered(fixture.bId), "Pulse begins without an observer delay");
		}));
		helper.runAtTickTime(45, () -> fixture.checked(() -> helper.assertTrue(fixture.controller.isModPowered(fixture.bId), "Pulse stays high for its second game tick")));
		helper.runAtTickTime(46, () -> fixture.checked(() -> {
			fixture.controller.setPowered(fixture.bId, false);
			helper.assertTrue(!fixture.controller.isModPowered(fixture.bId), "Pulse ends after exactly two game ticks");
		}));
		helper.runAtTickTime(54, () -> fixture.checked(() -> {
			helper.assertTrue(!fixture.controller.currentState(fixture.bId).getValue(PistonBaseBlock.EXTENDED), "Pulse must allow vanilla retraction");
			fixture.controller.release();
			fixture.snapshot.restore();
			fixture.controller = fixture.newController();
			fixture.controller.setPowered("a", true);
		}));
		helper.runAtTickTime(55, () -> fixture.checked(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(fixture.absolute(2, 2, 1)).is(Blocks.MOVING_PISTON), "Regression fixture must actually be midmovement before restore");
			fixture.controller.release();
			// Contaminate both input queues and the inventory, then rebuild.
			helper.getLevel().scheduleTick(fixture.absolute(6, 3, 6), Blocks.STONE, 50);
			helper.getLevel().blockEvent(fixture.absolute(6, 3, 6), Blocks.STONE, 7, 8);
			((ChestBlockEntity) helper.getLevel().getBlockEntity(fixture.absolute(6, 2, 6))).setItem(0, ItemStack.EMPTY);
			fixture.snapshot.recordSimulationItem(helper.spawnItem(Items.STONE, new BlockPos(6, 3, 5)));
			fixture.snapshot.restore();
			fixture.assertRestored();
		}));
		helper.runAtTickTime(60, () -> fixture.checked(() -> {
			helper.assertTrue(!helper.getLevel().getBlockState(fixture.absolute(6, 2, 4)).getValue(BlockStateProperties.LIT), "Restored lamp tick must execute relative to the new game time");
			fixture.snapshot.restore();
			fixture.assertRestored();
		}));
		helper.runAtTickTime(65, () -> fixture.checked(() -> {
			helper.assertTrue(!helper.getLevel().getBlockState(fixture.absolute(6, 2, 4)).getValue(BlockStateProperties.LIT), "Repeated replay must not retain stale lamp ticks");
			fixture.snapshot.restore();
			fixture.controller = fixture.newController();
			fixture.controller.setPowered("a", true);
			// Both block events are queued before vanilla runs. A moves B first,
			// invalidating B's old-position event. Its persistent virtual input
			// must still be observed when the moving piston settles at x=3.
			fixture.controller.setPowered("b", true);
		}));
		helper.runAtTickTime(75, () -> fixture.checked(() -> {
			fixture.controller.validate();
			helper.assertTrue(fixture.controller.positions().get("b").equals(fixture.absolute(3, 2, 1)), "Early ON must follow the carried piston");
			helper.assertTrue(fixture.controller.currentState("b").getValue(PistonBaseBlock.EXTENDED), "A virtual signal assigned before transport must be applied at settlement");
			helper.assertTrue(helper.getLevel().getBlockState(fixture.absolute(3, 4, 1)).is(Blocks.STONE), "The carried piston must use real vanilla motion after settlement");
			fixture.cleanup();
			helper.succeed();
		}));
	}

	private static final class Fixture {
        net.minecraft.world.entity.item.ItemEntity preservedItem;
		final GameTestHelper helper;
		final EditBounds bounds;
		final List<ChunkPos> forced = new ArrayList<>();
		RedstonePistonController controller;
		WorldSnapshot snapshot;
		String bId = "b";

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			BlockPos min = absolute(0, 1, 0);
			BlockPos max = absolute(7, 6, 7);
			bounds = new EditBounds(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
		}

		BlockPos absolute(int x, int y, int z) { return helper.absolutePos(new BlockPos(x, y, z)); }

		void forceChunks() {
			for (int x = (bounds.minX() >> 4) - 1; x <= (bounds.maxX() >> 4) + 1; x++) {
				for (int z = (bounds.minZ() >> 4) - 1; z <= (bounds.maxZ() >> 4) + 1; z++) {
					if (!helper.getLevel().getForceLoadedChunks().contains(ChunkPos.pack(x, z))) {
						helper.getLevel().setChunkForced(x, z, true);
						forced.add(new ChunkPos(x, z));
					}
					helper.getLevel().getChunk(x, z);
					helper.getLevel().waitForEntities(new ChunkPos(x, z), 0);
				}
			}
		}

		void prepare() {
			var level = helper.getLevel();
			for (BlockPos pos : bounds.positions()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
			level.setBlock(absolute(1, 2, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
			level.setBlock(absolute(2, 2, 1), Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP), Block.UPDATE_ALL);
			level.setBlock(absolute(3, 2, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP), Block.UPDATE_ALL);
			level.setBlock(absolute(3, 3, 1), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(absolute(6, 2, 6), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
			((ChestBlockEntity) level.getBlockEntity(absolute(6, 2, 6))).setItem(0, new ItemStack(Items.SLIME_BALL, 7));
			level.setBlock(absolute(6, 2, 4), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
			level.scheduleTick(absolute(6, 2, 4), Blocks.REDSTONE_LAMP, 4);
			level.scheduleTick(absolute(5, 3, 6), Fluids.WATER, 7);
			level.blockEvent(absolute(5, 2, 6), Blocks.STONE, 1, 2);
		}

		RedstonePistonController newController() {
			return new RedstonePistonController(helper.getLevel(), List.of(target("a", 1, 2, 1, "east", false),
				target("b", 2, 2, 1, "up", true), target("c", 3, 2, 1, "up", false)));
		}

		PistonTarget target(String id, int x, int y, int z, String facing, boolean sticky) {
			BlockPos pos = absolute(x, y, z);
			return new PistonTarget(id, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()), facing, sticky);
		}

		void assertRestored() {
			var level = helper.getLevel();
			for (BlockPos pos : bounds.positions()) helper.assertTrue(!level.getBlockState(pos).is(Blocks.MOVING_PISTON), "No stale moving blocks may survive a baseline reset");
			helper.assertTrue(level.getBlockState(absolute(2, 2, 1)).is(Blocks.STICKY_PISTON), "Pushed B must return to its initial position");
			helper.assertTrue(level.getBlockState(absolute(3, 2, 1)).is(Blocks.PISTON), "Pushed C must return to its initial position");
			helper.assertTrue(((ChestBlockEntity) level.getBlockEntity(absolute(6, 2, 6))).getItem(0).getCount() == 7, "Block entity inventory NBT must be restored");
			helper.assertTrue(preservedItem.isAlive(),"Pre-existing item survives resets");
            helper.assertTrue(level.getEntities((net.minecraft.world.entity.Entity) null,bounds.entityBox(),e -> e instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(Items.STONE)).isEmpty(),"Only owned replay drops are removed");
			ScheduledTick<Block> lamp = scheduled(level.getBlockTicks(), absolute(6, 2, 4));
			helper.assertTrue(lamp != null && lamp.triggerTick() == level.getGameTime() + 4, "Baseline block tick keeps its relative delay");
			var fluid = scheduled(level.getFluidTicks(), absolute(5, 3, 6));
			helper.assertTrue(fluid != null && fluid.triggerTick() == level.getGameTime() + 7, "Baseline fluid tick keeps its relative delay");
			helper.assertTrue(scheduled(level.getBlockTicks(), absolute(6, 3, 6)) == null, "Preview-only scheduled ticks must be discarded");
			var events = ((ServerLevelAccessor) level).layoutDebugger$blockEvents();
			helper.assertTrue(events.contains(new BlockEventData(absolute(5, 2, 6), Blocks.STONE, 1, 2)), "Baseline queued block event must be restored");
			helper.assertTrue(events.stream().noneMatch(event -> event.pos().equals(absolute(6, 3, 6))), "Stale queued block events must be discarded");
		}

		void checked(Runnable action) {
			try { action.run(); }
			catch (RuntimeException failure) { cleanup(); throw failure; }
		}

		void cleanup() {
			if (controller != null) controller.release();
			for (ChunkPos pos : forced) helper.getLevel().setChunkForced(pos.x(), pos.z(), false);
			forced.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> ScheduledTick<T> scheduled(LevelTicks<T> ticks, BlockPos position) {
		var containers = ((LevelTicksAccessor<T>) (Object) ticks).layoutDebugger$containers();
		var container = containers.get(ChunkPos.pack(position));
		return container == null ? null : container.getAll().filter(tick -> tick.pos().equals(position)).findFirst().orElse(null);
	}
}
