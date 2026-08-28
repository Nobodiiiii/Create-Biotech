package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Measures a stable horizontal collision core without allowing low-volume protrusions to dominate it. */
public final class SurgicalBodyBounds {
	private static final double TAIL_FRACTION = 0.05d;
	private static final double CENTRAL_FRACTION = 1.0d - TAIL_FRACTION * 2.0d;
	private static final double GEOMETRY_EPSILON = 1.0e-10d;

	private SurgicalBodyBounds() {}

	/**
	 * Measures the horizontally weighted core and complete vertical span of {@code bodyCubes}, then
	 * expresses them relative to the complete visible envelope. Callers leave arm-driven cubes
	 * out of {@code bodyCubes}, while keeping them in {@code allCubes}, so arms neither enlarge nor
	 * recenter the physical body.
	 */
	@Nullable
	public static SurgicalAssembly.BodyBounds measure(List<List<Vec3>> bodyCubes,
		List<List<Vec3>> allCubes, @Nullable Envelope visibleEnvelope) {
		List<CubeMass> all = cubes(allCubes);
		if (all.isEmpty())
			return null;
		List<CubeMass> body = cubes(bodyCubes);
		if (body.isEmpty())
			body = all;
		Envelope rawEnvelope = Envelope.of(all);
		Envelope bodyEnvelope = Envelope.of(body);
		if (rawEnvelope == null || bodyEnvelope == null)
			return null;
		Envelope visible = visibleEnvelope != null && visibleEnvelope.valid()
			? visibleEnvelope.include(rawEnvelope) : rawEnvelope;

		double[] collisionMin = new double[3];
		double[] collisionMax = new double[3];
		for (int axis = 0; axis < 3; axis++) {
			double coreMin;
			double coreMax;
			if (axis == 1) {
				// Height is anatomical: retain the complete vertical span of every non-arm cube.
				coreMin = bodyEnvelope.min(axis);
				coreMax = bodyEnvelope.max(axis);
			} else {
				double lower = quantile(body, axis, TAIL_FRACTION);
				double upper = quantile(body, axis, 1.0d - TAIL_FRACTION);
				if (!Double.isFinite(lower) || !Double.isFinite(upper) || upper <= lower)
					return null;
				double center = (lower + upper) * 0.5d;
				double halfSize = (upper - lower) * 0.5d / CENTRAL_FRACTION;
				coreMin = Math.max(rawEnvelope.min(axis), center - halfSize);
				coreMax = Math.min(rawEnvelope.max(axis), center + halfSize);
			}
			double lowerPadding = Math.max(0.0d, rawEnvelope.min(axis) - visible.min(axis));
			double upperPadding = Math.max(0.0d, visible.max(axis) - rawEnvelope.max(axis));
			collisionMin[axis] = Math.max(visible.min(axis), coreMin - lowerPadding);
			collisionMax[axis] = Math.min(visible.max(axis), coreMax + upperPadding);
			double collisionSize = collisionMax[axis] - collisionMin[axis];
			if (collisionSize < SurgicalAssembly.MIN_BODY_SIZE) {
				double missing = SurgicalAssembly.MIN_BODY_SIZE - collisionSize;
				if (missing > GEOMETRY_EPSILON)
					return null;
				if (axis == 1) {
					collisionMax[axis] += missing;
				} else {
					collisionMin[axis] -= missing * 0.5d;
					collisionMax[axis] += missing * 0.5d;
				}
			}
		}

		return SurgicalAssembly.BodyBounds.create(
			collisionMax[0] - collisionMin[0],
			collisionMax[1] - collisionMin[1],
			collisionMax[2] - collisionMin[2],
			(collisionMin[0] + collisionMax[0]) * 0.5d - visible.center(0),
			collisionMin[1] - visible.min(1),
			(collisionMin[2] + collisionMax[2]) * 0.5d - visible.center(2));
	}

	private static List<CubeMass> cubes(List<List<Vec3>> cubes) {
		if (cubes == null || cubes.isEmpty())
			return List.of();
		List<CubeMass> measured = new ArrayList<>(cubes.size());
		for (List<Vec3> corners : cubes) {
			CubeMass cube = CubeMass.of(corners);
			if (cube != null)
				measured.add(cube);
		}
		return List.copyOf(measured);
	}

	/** Volume-weighted percentile of a mixture of uniform cuboid projections. */
	private static double quantile(List<CubeMass> cubes, int axis, double fraction) {
		double totalVolume = 0.0d;
		List<Double> boundaries = new ArrayList<>(cubes.size() * 2);
		for (CubeMass cube : cubes) {
			totalVolume += cube.volume;
			boundaries.add(cube.min[axis]);
			boundaries.add(cube.max[axis]);
		}
		if (!(totalVolume > GEOMETRY_EPSILON))
			return Double.NaN;
		boundaries.sort(Comparator.naturalOrder());
		double target = totalVolume * fraction;
		double accumulated = 0.0d;
		for (int index = 1; index < boundaries.size(); index++) {
			double start = boundaries.get(index - 1);
			double end = boundaries.get(index);
			if (end - start <= GEOMETRY_EPSILON)
				continue;
			double midpoint = (start + end) * 0.5d;
			double density = 0.0d;
			for (CubeMass cube : cubes)
				if (midpoint > cube.min[axis] && midpoint < cube.max[axis])
					density += cube.volume / (cube.max[axis] - cube.min[axis]);
			if (!(density > 0.0d))
				continue;
			double segmentMass = density * (end - start);
			if (accumulated + segmentMass >= target)
				return start + (target - accumulated) / density;
			accumulated += segmentMass;
		}
		return boundaries.getLast();
	}

	/** Complete visible envelope in the same upright model frame as the cube corners. */
	public record Envelope(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ) {
		@Nullable
		private static Envelope of(List<CubeMass> cubes) {
			if (cubes == null || cubes.isEmpty())
				return null;
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (CubeMass cube : cubes) {
				minX = Math.min(minX, cube.min[0]);
				minY = Math.min(minY, cube.min[1]);
				minZ = Math.min(minZ, cube.min[2]);
				maxX = Math.max(maxX, cube.max[0]);
				maxY = Math.max(maxY, cube.max[1]);
				maxZ = Math.max(maxZ, cube.max[2]);
			}
			Envelope envelope = new Envelope(minX, minY, minZ, maxX, maxY, maxZ);
			return envelope.valid() ? envelope : null;
		}

		private boolean valid() {
			return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
				&& Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
				&& maxX > minX && maxY > minY && maxZ > minZ;
		}

		private double min(int axis) {
			return axis == 0 ? minX : axis == 1 ? minY : minZ;
		}

		private double max(int axis) {
			return axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
		}

		private double center(int axis) {
			return (min(axis) + max(axis)) * 0.5d;
		}

		private Envelope include(Envelope other) {
			return new Envelope(Math.min(minX, other.minX), Math.min(minY, other.minY),
				Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
				Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
		}
	}

	private record CubeMass(double[] min, double[] max, double volume) {
		@Nullable
		private static CubeMass of(List<Vec3> corners) {
			if (corners == null || corners.size() != 8)
				return null;
			double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
				Double.POSITIVE_INFINITY};
			double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.NEGATIVE_INFINITY};
			for (Vec3 corner : corners) {
				if (corner == null)
					return null;
				min[0] = Math.min(min[0], corner.x);
				min[1] = Math.min(min[1], corner.y);
				min[2] = Math.min(min[2], corner.z);
				max[0] = Math.max(max[0], corner.x);
				max[1] = Math.max(max[1], corner.y);
				max[2] = Math.max(max[2], corner.z);
			}
			double volume = orientedVolume(corners);
			if (!(volume > GEOMETRY_EPSILON)) {
				double area = orientedArea(corners);
				if (!(area > GEOMETRY_EPSILON))
					return null;
				ensureMinimumSpans(min, max);
				volume = area * SurgicalAssembly.MIN_BODY_SIZE;
			}
			double axisVolume = (max[0] - min[0]) * (max[1] - min[1]) * (max[2] - min[2]);
			if (!Double.isFinite(axisVolume) || axisVolume <= GEOMETRY_EPSILON)
				return null;
			volume = Math.min(volume, axisVolume);
			return Double.isFinite(volume) && volume > GEOMETRY_EPSILON
				? new CubeMass(min, max, volume) : null;
		}

		/** Capture orders corners by x, then y, then z, so 1, 2 and 4 are the three box edges. */
		private static double orientedVolume(List<Vec3> corners) {
			Vec3 origin = corners.getFirst();
			Vec3 xEdge = corners.get(1).subtract(origin);
			Vec3 yEdge = corners.get(2).subtract(origin);
			Vec3 zEdge = corners.get(4).subtract(origin);
			return Math.abs(xEdge.dot(yEdge.cross(zEdge)));
		}

		/** Largest real face area; a flat ModelPart has two non-zero edges and one zero edge. */
		private static double orientedArea(List<Vec3> corners) {
			Vec3 origin = corners.getFirst();
			Vec3[] edges = {
				corners.get(1).subtract(origin),
				corners.get(2).subtract(origin),
				corners.get(4).subtract(origin)
			};
			double area = 0.0d;
			for (int first = 0; first < edges.length; first++)
				for (int second = first + 1; second < edges.length; second++)
					area = Math.max(area, edges[first].cross(edges[second]).length());
			return area;
		}

		/**
		 * Gives a planar cube the smallest legal axis-aligned collision span. Horizontal spans stay
		 * centred on the rendered sheet; vertical span grows upward so {@code minY} remains relative
		 * to the real visible bottom rather than moving below it.
		 */
		private static void ensureMinimumSpans(double[] min, double[] max) {
			for (int axis = 0; axis < 3; axis++) {
				double missing = SurgicalAssembly.MIN_BODY_SIZE - (max[axis] - min[axis]);
				if (!(missing > 0.0d))
					continue;
				if (axis == 1) {
					max[axis] += missing;
				} else {
					min[axis] -= missing * 0.5d;
					max[axis] += missing * 0.5d;
				}
			}
		}
	}
}
