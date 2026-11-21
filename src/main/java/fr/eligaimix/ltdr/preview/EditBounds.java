package fr.eligaimix.ltdr.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/** Inclusive block bounds. A baseline always captures air as well as occupied cells. */
public record EditBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public static final int MAX_VOLUME = 262_144;

	public EditBounds {
		long width = (long) maxX - minX + 1;
		long height = (long) maxY - minY + 1;
		long depth = (long) maxZ - minZ + 1;
		if (width <= 0 || height <= 0 || depth <= 0) {
			throw new IllegalArgumentException("Les limites de la zone sont inversées.");
		}
		if (width > MAX_VOLUME || height > MAX_VOLUME || depth > MAX_VOLUME
			|| width * height > MAX_VOLUME || width * height * depth > MAX_VOLUME) {
			throw new IllegalArgumentException("La zone d'édition doit contenir au maximum " + MAX_VOLUME + " blocs.");
		}
	}

	public static EditBounds around(BlockPos center, int radius) {
		if (radius < 1 || radius > 31) throw new IllegalArgumentException("Le rayon doit être compris entre 1 et 31 blocs.");
		return new EditBounds(Math.subtractExact(center.getX(), radius), Math.subtractExact(center.getY(), radius),
			Math.subtractExact(center.getZ(), radius), Math.addExact(center.getX(), radius),
			Math.addExact(center.getY(), radius), Math.addExact(center.getZ(), radius));
	}

	public boolean includes(BlockPos pos) {
		return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
			&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
	}

	public int volume() { return width() * height() * depth(); }
	public int width() { return maxX - minX + 1; }
	public int height() { return maxY - minY + 1; }
	public int depth() { return maxZ - minZ + 1; }
	public BoundingBox box() { return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ); }
	public AABB entityBox() { return new AABB(minX, minY, minZ, (double) maxX + 1, (double) maxY + 1, (double) maxZ + 1); }
	public Iterable<BlockPos> positions() { return BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ); }

	@Override public String toString() {
		return minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
	}
}
