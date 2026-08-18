package com.nobodiiiii.createbiotech.content.shulkerpackager;

import net.minecraft.core.BlockPos;

public final class ShulkerPackagerRange {

	private ShulkerPackagerRange() {}

	public static boolean isWithinCube(BlockPos center, BlockPos candidate, int radius) {
		if (center == null || candidate == null || radius < 0)
			return false;

		return Math.abs((long) candidate.getX() - center.getX()) <= radius
			&& Math.abs((long) candidate.getY() - center.getY()) <= radius
			&& Math.abs((long) candidate.getZ() - center.getZ()) <= radius;
	}
}
