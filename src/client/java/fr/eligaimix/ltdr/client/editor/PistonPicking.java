package fr.eligaimix.ltdr.client.editor;

import fr.eligaimix.ltdr.network.EditorState;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared visible-body bounds for highlights and picking, including transported pistons. */
public final class PistonPicking {
    private PistonPicking() {}

    public static AABB body(ClientLevel level, EditorState.TargetView target, float partialTick) {
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        AABB box = new AABB(pos);
        if (level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving
                && !moving.isSourcePiston())
            box =
                    box.move(
                            moving.getXOff(partialTick),
                            moving.getYOff(partialTick),
                            moving.getZOff(partialTick));
        return box;
    }

    private static List<AABB> hitBoxes(
            ClientLevel level, EditorState.TargetView target, float partialTick) {
        var boxes = new ArrayList<AABB>();
        boxes.add(body(level, target, partialTick));
        BlockPos base = new BlockPos(target.x(), target.y(), target.z());
        var state = level.getBlockState(base);
        if (level.getBlockEntity(base) instanceof PistonMovingBlockEntity moving)
            state = moving.getMovedState();
        if (!state.hasProperty(PistonBaseBlock.FACING)
                || !state.hasProperty(PistonBaseBlock.EXTENDED)
                || !state.getValue(PistonBaseBlock.EXTENDED)) return boxes;
        var direction = state.getValue(PistonBaseBlock.FACING);
        BlockPos front = base.relative(direction);
        var frontState = level.getBlockState(front);
        if (frontState.is(Blocks.PISTON_HEAD)
                && frontState.getValue(PistonBaseBlock.FACING) == direction)
            boxes.add(new AABB(front));
        else if (level.getBlockEntity(front) instanceof PistonMovingBlockEntity moving
                && moving.isSourcePiston()
                && moving.getDirection() == direction)
            boxes.add(
                    new AABB(front)
                            .move(
                                    moving.getXOff(partialTick),
                                    moving.getYOff(partialTick),
                                    moving.getZOff(partialTick)));
        return boxes;
    }

    public static EditorState.TargetView pick(
            ClientLevel level,
            List<EditorState.TargetView> targets,
            Vec3 start,
            Vec3 end,
            HitResult hit,
            float partialTick) {
        double nearest = start.distanceToSqr(end);
        if (hit != null && hit.getType() != HitResult.Type.MISS)
            nearest = Math.min(nearest, start.distanceToSqr(hit.getLocation()) + 0.04);
        EditorState.TargetView selected = null;
        for (var target : targets)
            for (var box : hitBoxes(level, target, partialTick)) {
                var intersection = box.inflate(0.003).clip(start, end);
                if (intersection.isEmpty()) continue;
                double distance = start.distanceToSqr(intersection.get());
                if (distance < nearest) {
                    nearest = distance;
                    selected = target;
                }
            }
        // Vanilla's thin head/rod collision can be closer than the interpolated full-body box.
        // Keep the actual block hit as a fallback, resolving heads to their tracked base.
        if (selected == null && hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            var state = level.getBlockState(pos);
            if (level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving)
                state = moving.getMovedState();
            BlockPos base =
                    state.is(Blocks.PISTON_HEAD)
                            ? pos.relative(state.getValue(PistonBaseBlock.FACING).getOpposite())
                            : pos;
            if (state.getBlock() instanceof PistonBaseBlock || state.is(Blocks.PISTON_HEAD)) {
                for (var target : targets)
                    if (base.equals(new BlockPos(target.x(), target.y(), target.z())))
                        return target;
            }
        }
        return selected;
    }
}
