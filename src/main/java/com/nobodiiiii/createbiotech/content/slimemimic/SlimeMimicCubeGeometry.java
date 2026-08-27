package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/** One rendered model cuboid at the instant a slime mimic dies. */
public record SlimeMimicCubeGeometry(int source, int cube, List<Vec3> corners) {
	public SlimeMimicCubeGeometry {
		corners = List.copyOf(corners);
		if (source < 0 || cube < 0 || corners.size() != 8)
			throw new IllegalArgumentException("Invalid slime-mimic cube geometry");
	}
}
