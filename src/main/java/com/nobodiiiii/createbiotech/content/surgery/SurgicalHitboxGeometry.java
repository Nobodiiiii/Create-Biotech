package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Bakes side-safe selectable and culling bounds from the renderer's upright rest geometry. */
public final class SurgicalHitboxGeometry {
	private static final double GEOMETRY_EPSILON = 1.0e-10d;

	private SurgicalHitboxGeometry() {}

	/**
	 * Produces one rigid-body envelope and one envelope for every effective primary limb chain.
	 * Secondary joints are folded into the primary chain that owns their parent island.
	 */
	@Nullable
	public static SurgicalAssembly.HitboxGeometry measure(SurgicalAssembly assembly,
		List<Map<Integer, List<Vec3>>> sourceCubes, SurgicalBodyBounds.Envelope visible,
		SurgicalAssembly.BodyBounds bodyBounds) {
		if (assembly == null || sourceCubes == null || visible == null || bodyBounds == null
			|| sourceCubes.size() != assembly.sources().size())
			return null;

		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes = new HashMap<>();
		for (int source = 0; source < sourceCubes.size(); source++)
			for (Map.Entry<Integer, List<Vec3>> entry : sourceCubes.get(source).entrySet()) {
				if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null
					|| entry.getValue().isEmpty())
					return null;
				cubes.put(new SurgicalAssembly.CombinationMember(source, entry.getKey()), entry.getValue());
			}
		if (cubes.isEmpty())
			return null;

		Envelope raw = Envelope.of(cubes.values());
		Envelope visibleEnvelope = Envelope.of(visible);
		if (raw == null || visibleEnvelope == null)
			return null;
		visibleEnvelope = visibleEnvelope.include(raw);

		List<Set<SurgicalAssembly.CombinationMember>> chains = primaryChains(assembly, cubes.keySet());
		if (chains == null || chains.size() > SurgicalAssembly.MAX_HITBOX_LIMBS)
			return null;
		Set<SurgicalAssembly.CombinationMember> moving = new HashSet<>();
		for (Set<SurgicalAssembly.CombinationMember> chain : chains)
			moving.addAll(chain);

		List<List<Vec3>> bodyCubes = new ArrayList<>();
		for (Map.Entry<SurgicalAssembly.CombinationMember, List<Vec3>> entry : cubes.entrySet())
			if (!moving.contains(entry.getKey()))
				bodyCubes.add(entry.getValue());
		Envelope body = bodyCubes.isEmpty() ? visibleEnvelope : Envelope.of(bodyCubes);
		if (body == null)
			return null;

		double[] lowerPadding = {
			Math.max(0.0d, raw.minX - visibleEnvelope.minX),
			Math.max(0.0d, raw.minY - visibleEnvelope.minY),
			Math.max(0.0d, raw.minZ - visibleEnvelope.minZ)
		};
		double[] upperPadding = {
			Math.max(0.0d, visibleEnvelope.maxX - raw.maxX),
			Math.max(0.0d, visibleEnvelope.maxY - raw.maxY),
			Math.max(0.0d, visibleEnvelope.maxZ - raw.maxZ)
		};
		double originX = (visibleEnvelope.minX + visibleEnvelope.maxX) * 0.5d
			+ bodyBounds.centerX();
		double originY = visibleEnvelope.minY;
		double originZ = (visibleEnvelope.minZ + visibleEnvelope.maxZ) * 0.5d
			+ bodyBounds.centerZ();

		SurgicalAssembly.VisualBounds overallBounds = bounds(visibleEnvelope, visibleEnvelope,
			originX, originY, originZ);
		SurgicalAssembly.VisualBounds bodyVisual = bounds(body.pad(lowerPadding, upperPadding)
			.intersect(visibleEnvelope), visibleEnvelope, originX, originY, originZ);
		if (overallBounds == null || bodyVisual == null)
			return null;

		List<SurgicalAssembly.VisualBounds> limbBounds = new ArrayList<>(chains.size());
		for (Set<SurgicalAssembly.CombinationMember> chain : chains) {
			List<List<Vec3>> chainCubes = new ArrayList<>(chain.size());
			for (SurgicalAssembly.CombinationMember member : chain) {
				List<Vec3> points = cubes.get(member);
				if (points != null)
					chainCubes.add(points);
			}
			Envelope limb = Envelope.of(chainCubes);
			if (limb == null)
				return null;
			SurgicalAssembly.VisualBounds limbVisual = bounds(limb.pad(lowerPadding, upperPadding)
				.intersect(visibleEnvelope), visibleEnvelope, originX, originY, originZ);
			if (limbVisual == null)
				return null;
			limbBounds.add(limbVisual);
		}
		return SurgicalAssembly.HitboxGeometry.create(overallBounds, bodyVisual, limbBounds);
	}

	@Nullable
	private static List<Set<SurgicalAssembly.CombinationMember>> primaryChains(
		SurgicalAssembly assembly, Set<SurgicalAssembly.CombinationMember> existingCubes) {
		List<SurgicalAssembly.Limb> effective = assembly.effectiveLimbs();
		List<Set<SurgicalAssembly.CombinationMember>> chains = new ArrayList<>();
		for (SurgicalAssembly.Limb primary : effective) {
			if (!primary.type().primary())
				continue;
			LinkedHashSet<SurgicalAssembly.CombinationMember> chain = new LinkedHashSet<>(
				assembly.rotatingGroup(primary.childSource(), primary.childCube()));
			for (SurgicalAssembly.Limb secondary : effective) {
				if (secondary.type().matchingPrimary() != primary.type())
					continue;
				SurgicalAssembly.CombinationMember parent = new SurgicalAssembly.CombinationMember(
					secondary.parentSource(), secondary.parentCube());
				if (chain.contains(parent))
					chain.addAll(assembly.rotatingGroup(secondary.childSource(), secondary.childCube()));
			}
			chain.retainAll(existingCubes);
			if (chain.isEmpty())
				return null;
			chains.add(Set.copyOf(chain));
		}
		return List.copyOf(chains);
	}

	@Nullable
	private static SurgicalAssembly.VisualBounds bounds(Envelope envelope, Envelope overall,
		double originX, double originY, double originZ) {
		if (envelope == null || overall == null)
			return null;
		Envelope minimum = envelope.ensureMinimumSpans(overall.withMinimumSpans());
		return SurgicalAssembly.VisualBounds.create(minimum.minX - originX, minimum.minY - originY,
			minimum.minZ - originZ, minimum.maxX - originX, minimum.maxY - originY,
			minimum.maxZ - originZ);
	}

	private record Envelope(double minX, double minY, double minZ,
		double maxX, double maxY, double maxZ) {
		@Nullable
		private static Envelope of(Iterable<List<Vec3>> cubes) {
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (List<Vec3> cube : cubes)
				for (Vec3 point : cube) {
					if (point == null)
						return null;
					minX = Math.min(minX, point.x);
					minY = Math.min(minY, point.y);
					minZ = Math.min(minZ, point.z);
					maxX = Math.max(maxX, point.x);
					maxY = Math.max(maxY, point.y);
					maxZ = Math.max(maxZ, point.z);
				}
			Envelope result = new Envelope(minX, minY, minZ, maxX, maxY, maxZ);
			return result.valid() ? result : null;
		}

		@Nullable
		private static Envelope of(SurgicalBodyBounds.Envelope envelope) {
			Envelope result = new Envelope(envelope.minX(), envelope.minY(), envelope.minZ(),
				envelope.maxX(), envelope.maxY(), envelope.maxZ());
			return result.valid() ? result : null;
		}

		private boolean valid() {
			if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
				|| !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ))
				return false;
			int dimensions = 0;
			if (maxX - minX > GEOMETRY_EPSILON)
				dimensions++;
			if (maxY - minY > GEOMETRY_EPSILON)
				dimensions++;
			if (maxZ - minZ > GEOMETRY_EPSILON)
				dimensions++;
			return dimensions >= 2
				&& maxX >= minX && maxY >= minY && maxZ >= minZ;
		}

		private Envelope include(Envelope other) {
			return new Envelope(Math.min(minX, other.minX), Math.min(minY, other.minY),
				Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
				Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
		}

		private Envelope pad(double[] lower, double[] upper) {
			return new Envelope(minX - lower[0], minY - lower[1], minZ - lower[2],
				maxX + upper[0], maxY + upper[1], maxZ + upper[2]);
		}

		private Envelope intersect(Envelope other) {
			return new Envelope(Math.max(minX, other.minX), Math.max(minY, other.minY),
				Math.max(minZ, other.minZ), Math.min(maxX, other.maxX),
				Math.min(maxY, other.maxY), Math.min(maxZ, other.maxZ));
		}

		private Envelope withMinimumSpans() {
			double[] minimum = {minX, minY, minZ};
			double[] maximum = {maxX, maxY, maxZ};
			for (int axis = 0; axis < 3; axis++) {
				double missing = SurgicalAssembly.MIN_BODY_SIZE - (maximum[axis] - minimum[axis]);
				if (!(missing > 0.0d))
					continue;
				if (axis == 1)
					maximum[axis] += missing;
				else {
					minimum[axis] -= missing * 0.5d;
					maximum[axis] += missing * 0.5d;
				}
			}
			return new Envelope(minimum[0], minimum[1], minimum[2],
				maximum[0], maximum[1], maximum[2]);
		}

		private Envelope ensureMinimumSpans(Envelope limits) {
			double[] minimum = {minX, minY, minZ};
			double[] maximum = {maxX, maxY, maxZ};
			double[] limitMinimum = {limits.minX, limits.minY, limits.minZ};
			double[] limitMaximum = {limits.maxX, limits.maxY, limits.maxZ};
			for (int axis = 0; axis < 3; axis++) {
				double missing = SurgicalAssembly.MIN_BODY_SIZE - (maximum[axis] - minimum[axis]);
				if (!(missing > 0.0d))
					continue;
				if (axis == 1) {
					maximum[axis] = Math.min(limitMaximum[axis], maximum[axis] + missing);
					minimum[axis] = Math.max(limitMinimum[axis], maximum[axis]
						- SurgicalAssembly.MIN_BODY_SIZE);
				} else {
					double center = (minimum[axis] + maximum[axis]) * 0.5d;
					minimum[axis] = center - SurgicalAssembly.MIN_BODY_SIZE * 0.5d;
					maximum[axis] = center + SurgicalAssembly.MIN_BODY_SIZE * 0.5d;
					if (minimum[axis] < limitMinimum[axis]) {
						maximum[axis] += limitMinimum[axis] - minimum[axis];
						minimum[axis] = limitMinimum[axis];
					}
					if (maximum[axis] > limitMaximum[axis]) {
						minimum[axis] -= maximum[axis] - limitMaximum[axis];
						maximum[axis] = limitMaximum[axis];
					}
				}
			}
			return new Envelope(minimum[0], minimum[1], minimum[2],
				maximum[0], maximum[1], maximum[2]);
		}
	}
}
