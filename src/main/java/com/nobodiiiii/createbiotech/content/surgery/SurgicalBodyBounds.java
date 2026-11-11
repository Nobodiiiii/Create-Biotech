package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Measures a stable collision core from the non-overlapping spatial volume distribution. */
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
		return measure(bodyCubes, SurgicalVolumeSampler.prepare(allCubes), visibleEnvelope);
	}

	/** Shares cuboid preparation, and union samples when possible, with the health measurement. */
	@Nullable
	public static Measurement measureWithVolume(List<List<Vec3>> bodyCubes,
		List<List<Vec3>> allCubes, @Nullable Envelope visibleEnvelope) {
		var geometry = SurgicalVolumeSampler.prepare(allCubes);
		SurgicalAssembly.BodyBounds bounds = measure(bodyCubes, geometry, visibleEnvelope);
		return bounds == null ? null : new Measurement(bounds, geometry.union(false).volume());
	}

	public record Measurement(SurgicalAssembly.BodyBounds bounds, double volume) {}

	@Nullable
	private static SurgicalAssembly.BodyBounds measure(List<List<Vec3>> bodyCubes,
		SurgicalVolumeSampler.PreparedGeometry geometry, @Nullable Envelope visibleEnvelope) {
		List<CubeMass> all = cubes(geometry.cubes());
		if (all.isEmpty())
			return null;
		List<CubeMass> body = cubes(geometry.select(bodyCubes));
		if (body.isEmpty())
			body = all;
		Envelope rawEnvelope = Envelope.of(all);
		Envelope bodyEnvelope = Envelope.of(body);
		if (rawEnvelope == null || bodyEnvelope == null)
			return null;
		// Callers read the offsets back against their own visible envelope, and the baked hitbox only
		// pads that envelope to the minimum span. Thin-sheet padding in rawEnvelope must therefore
		// neither move the reference nor carry the collision box outside it.
		Envelope corners = Envelope.ofCorners(all);
		Envelope reference = visibleEnvelope != null && visibleEnvelope.finite()
			? visibleEnvelope.include(corners) : corners;
		Envelope visible = reference.withMinimumSpans();
		var distribution = geometry.collisionUnion(body.stream().map(CubeMass::box).toList());

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
				double[] quantiles = distribution.quantiles(axis, TAIL_FRACTION, 1.0d - TAIL_FRACTION);
				double lower = quantiles[0];
				double upper = quantiles[1];
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
			ensureMinimumSpan(collisionMin, collisionMax, axis, visible);
		}

		return SurgicalAssembly.BodyBounds.create(
			collisionMax[0] - collisionMin[0],
			collisionMax[1] - collisionMin[1],
			collisionMax[2] - collisionMin[2],
			(collisionMin[0] + collisionMax[0]) * 0.5d - reference.center(0),
			collisionMin[1] - reference.min(1),
			(collisionMin[2] + collisionMax[2]) * 0.5d - reference.center(2));
	}

	/**
	 * Grows one collision span that is shorter than the minimum body size, then slides it back inside
	 * {@code limits}, which always spans at least that size. Vertical growth goes upward so the box
	 * keeps standing on the visible bottom.
	 */
	private static void ensureMinimumSpan(double[] min, double[] max, int axis, Envelope limits) {
		double missing = SurgicalAssembly.MIN_BODY_SIZE - (max[axis] - min[axis]);
		if (!(missing > 0.0d))
			return;
		if (axis == 1) {
			max[axis] += missing;
		} else {
			min[axis] -= missing * 0.5d;
			max[axis] += missing * 0.5d;
		}
		if (max[axis] > limits.max(axis)) {
			min[axis] -= max[axis] - limits.max(axis);
			max[axis] = limits.max(axis);
		}
		if (min[axis] < limits.min(axis)) {
			max[axis] = Math.min(limits.max(axis), max[axis] + limits.min(axis) - min[axis]);
			min[axis] = limits.min(axis);
		}
	}

	/**
	 * Gives zero or sub-minimum spans the smallest legal size. Horizontal spans stay centred on the
	 * rendered geometry; vertical span grows upward so {@code minY} remains relative to the real
	 * visible bottom rather than moving below it.
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

	private static List<CubeMass> cubes(List<SurgicalVolumeSampler.Cuboid> cubes) {
		if (cubes == null || cubes.isEmpty())
			return List.of();
		List<CubeMass> measured = new ArrayList<>(cubes.size());
		for (SurgicalVolumeSampler.Cuboid cuboid : cubes) {
			CubeMass cube = CubeMass.of(cuboid);
			if (cube != null)
				measured.add(cube);
		}
		return List.copyOf(measured);
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

		/** Envelope of the captured corners themselves, before thin sheets were padded. */
		private static Envelope ofCorners(List<CubeMass> cubes) {
			Envelope envelope = cubes.getFirst().corners;
			for (CubeMass cube : cubes)
				envelope = envelope.include(cube.corners);
			return envelope;
		}

		private boolean finite() {
			return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
				&& Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
				&& maxX >= minX && maxY >= minY && maxZ >= minZ;
		}

		/** Pads a flat or sub-minimum axis exactly as the baked hitbox envelope does. */
		private Envelope withMinimumSpans() {
			double[] min = {minX, minY, minZ};
			double[] max = {maxX, maxY, maxZ};
			ensureMinimumSpans(min, max);
			return new Envelope(min[0], min[1], min[2], max[0], max[1], max[2]);
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

	/**
	 * One cube's collision sampling box and axis spans, plus its unpadded visible envelope.
	 */
	private record CubeMass(double[] min, double[] max, Envelope corners, SurgicalVolumeSampler.VolumeBox box) {
		@Nullable
		private static CubeMass of(SurgicalVolumeSampler.Cuboid cube) {
			if (cube.collision() == null || cube.collision().volume() <= GEOMETRY_EPSILON)
				return null;
			double[] min = cube.minimum().clone();
			double[] max = cube.maximum().clone();
			Envelope cornerBounds = new Envelope(min[0], min[1], min[2], max[0], max[1], max[2]);
			ensureMinimumSpans(min, max);
			return new CubeMass(min, max, cornerBounds, cube.collision());
		}
	}
}
