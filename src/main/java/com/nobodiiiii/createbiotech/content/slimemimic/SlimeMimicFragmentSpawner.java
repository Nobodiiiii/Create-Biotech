package com.nobodiiiii.createbiotech.content.slimemimic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.SlimeMimicCubeEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared falling-cuboid to slime conversion used by deaths and unsupported surgical subjects. */
public final class SlimeMimicFragmentSpawner {
	public static final double SMALL_SLIME_VOLUME = 1.0d / 8.0d;
	private static final double MIN_EDGE = 1.0d / 1024.0d;
	private static final double MAX_EDGE = 64.0d;

	private SlimeMimicFragmentSpawner() {}

	public static boolean spawn(ServerLevel level, MimicProfile profile, int cube, List<Vec3> corners) {
		Measure measure = measure(corners);
		if (level == null || profile == null || cube < 0 || measure == null)
			return false;
		SlimeMimicCubeEntity fragment = SlimeMimicCubeEntity.create(level, profile, cube, measure.center,
			(float) measure.width, (float) measure.height, (float) measure.depth,
			measure.volume, slimeSize(measure.volume), corners);
		if (fragment == null)
			return false;
		return level.addFreshEntity(fragment);
	}

	public static int slimeSize(double volume) {
		if (volume + 1.0e-9d < SMALL_SLIME_VOLUME)
			return 0;
		return Math.max(1, Math.min(127,
			(int) Math.floor(Math.cbrt(volume / SMALL_SLIME_VOLUME) + 1.0e-9d)));
	}

	@Nullable
	public static Measure measure(List<Vec3> corners) {
		if (corners == null || corners.size() != 8)
			return null;
		for (Vec3 corner : corners)
			if (corner == null || !Double.isFinite(corner.x) || !Double.isFinite(corner.y)
				|| !Double.isFinite(corner.z))
				return null;
		Vec3 a = corners.get(1).subtract(corners.get(0));
		Vec3 b = corners.get(2).subtract(corners.get(0));
		Vec3 c = corners.get(4).subtract(corners.get(0));
		double edgeA = a.length();
		double edgeB = b.length();
		double edgeC = c.length();
		if (!reasonableEdge(edgeA) || !reasonableEdge(edgeB) || !reasonableEdge(edgeC))
			return null;
		int dimensionalEdges = (edgeA >= MIN_EDGE ? 1 : 0) + (edgeB >= MIN_EDGE ? 1 : 0)
			+ (edgeC >= MIN_EDGE ? 1 : 0);
		if (dimensionalEdges < 2)
			return null;
		double volume = Math.abs(a.dot(b.cross(c)));
		if (!Double.isFinite(volume))
			return null;
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		Vec3 sum = Vec3.ZERO;
		for (Vec3 corner : corners) {
			minX = Math.min(minX, corner.x);
			minY = Math.min(minY, corner.y);
			minZ = Math.min(minZ, corner.z);
			maxX = Math.max(maxX, corner.x);
			maxY = Math.max(maxY, corner.y);
			maxZ = Math.max(maxZ, corner.z);
			sum = sum.add(corner);
		}
		return new Measure(sum.scale(1.0d / 8.0d), Math.max(MIN_EDGE, maxX - minX),
			Math.max(MIN_EDGE, maxY - minY), Math.max(MIN_EDGE, maxZ - minZ), volume,
			new AABB(minX, minY, minZ, maxX, maxY, maxZ));
	}

	public static boolean cornersInside(List<Vec3> corners, AABB allowed) {
		if (corners == null || corners.size() != 8 || allowed == null)
			return false;
		for (Vec3 corner : corners)
			if (corner == null || !Double.isFinite(corner.x) || !Double.isFinite(corner.y)
				|| !Double.isFinite(corner.z) || !allowed.contains(corner))
				return false;
		return true;
	}

	private static boolean reasonableEdge(double edge) {
		return Double.isFinite(edge) && edge >= 0.0d && edge <= MAX_EDGE;
	}

	public record Measure(Vec3 center, double width, double height, double depth, double volume,
		AABB bounds) {}
}
