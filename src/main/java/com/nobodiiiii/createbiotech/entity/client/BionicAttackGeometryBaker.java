package com.nobodiiiii.createbiotech.entity.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Matrix3f;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Internal BionicAttackGeometryBaker implementation; public entry points remain on SlimeBionicAnimator. */
final class BionicAttackGeometryBaker {
	private BionicAttackGeometryBaker() {}


	/** Bakes immutable dimensions for every articulated or single-piece arm. */
	@Nullable
	static SurgicalAssembly.AttackGeometry bakeAttackGeometry(SurgicalAssembly assembly,
		List<SlimeBionicAnimator.SourceState> sources, Vec3 bodyOrigin) {
		return bakeAttackGeometry(assembly, sources, bodyOrigin, assembly == null ? List.of()
			: assembly.sources().stream().map(source -> SurgicalSourceModelRenderer.donorGeometry(source.profile())).toList());
	}

	/** All native renderer data is frozen by the client before this worker-safe calculation. */
	@Nullable
	static SurgicalAssembly.AttackGeometry bakeAttackGeometry(SurgicalAssembly assembly,
		List<SlimeBionicAnimator.SourceState> sources, Vec3 bodyOrigin, List<SurgicalSourceModelRenderer.DonorGeometry> donors) {
		if (assembly == null || sources == null || bodyOrigin == null
			|| sources.size() != assembly.sources().size() || donors == null || donors.size() != sources.size())
			return null;
		List<SlimeBionicAnimator.ResolvedLimb> limbs = BionicRigBuilder.resolveLimbs(assembly, sources);
		List<SurgicalAssembly.ArmAttackGeometry> right = new ArrayList<>();
		List<SurgicalAssembly.ArmAttackGeometry> left = new ArrayList<>();
		List<Integer> shoulders = new ArrayList<>();
		for (int index = 0; index < limbs.size(); index++)
			if (limbs.get(index).type() == SurgicalLimbType.SHOULDER
				&& limbs.get(index).arm() != null)
				shoulders.add(index);
		shoulders.sort(java.util.Comparator
			.comparing((Integer index) -> limbs.get(index).arm().left())
			.thenComparingInt(index -> limbs.get(index).arm().slot()));
		for (int shoulderIndex : shoulders) {
			SlimeBionicAnimator.ResolvedLimb shoulder = limbs.get(shoulderIndex);
			int elbowIndex = -1;
			for (int candidate = 0; candidate < limbs.size(); candidate++)
				if (limbs.get(candidate).type() == SurgicalLimbType.ELBOW
					&& limbs.get(candidate).parentIndex() == shoulderIndex) {
					elbowIndex = candidate;
					break;
				}
			SurgicalAssembly.ArmAttackGeometry arm = bakeArm(limbs, sources, bodyOrigin,
				shoulderIndex, elbowIndex);
			if (arm == null)
				continue;
			(shoulder.arm().left() ? left : right).add(arm);
		}
		Map<SurgicalAssembly.CombinationMember, Vec3> origins = new HashMap<>();
		Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes = new HashMap<>();
		Map<SurgicalAssembly.CombinationMember, SurgicalAssembly.CarrierEquipmentBounds> equipment = new HashMap<>();
		Map<SurgicalAssembly.CombinationMember, SurgicalAssembly.RidingSeat> seats = new HashMap<>();
		Map<SurgicalAssembly.CombinationMember, Set<String>> parts = new HashMap<>();
		for (int source = 0; source < sources.size(); source++) {
			var donor = donors.get(source);
			var present = assembly.sources().get(source).presentCubes();
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
				SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, new SlimeBionicAnimator.Member(source, cube));
				if (box != null) {
					var member = new SurgicalAssembly.CombinationMember(source, cube);
					origins.put(member, box.center().subtract(bodyOrigin));
					cubes.put(member, box.points().stream().map(point -> point.subtract(bodyOrigin)).toList());
					var original = donor == null ? null : donor.cubes().get(cube);
					if (original != null && !original.partNames().isEmpty()) parts.put(member, original.partNames());
				}
			}
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
				var seat = donor == null ? null : donor.seats().get(cube);
				if (seat == null) continue;
				SlimeBionicAnimator.CubeBox carrier = BionicRigBuilder.box(sources, new SlimeBionicAnimator.Member(source, cube));
				var original = donor.cubes().get(cube);
				if (carrier == null || original == null) continue;
				var saddle = equipmentBounds(donor.saddles().getOrDefault(cube, List.of()),
					original.corners(), carrier.points(), bodyOrigin);
				var chest = equipmentBounds(donor.chests().getOrDefault(cube, List.of()),
					original.corners(), carrier.points(), bodyOrigin);
				if (saddle != null || chest != null) equipment.put(new SurgicalAssembly.CombinationMember(source, cube),
					new SurgicalAssembly.CarrierEquipmentBounds(saddle, chest));
				seats.put(new SurgicalAssembly.CombinationMember(source, cube),
					new SurgicalAssembly.RidingSeat(
						transformCubePoint(seat.first(), original.corners(), carrier.points()).subtract(bodyOrigin),
						transformCubePoint(seat.second(), original.corners(), carrier.points()).subtract(bodyOrigin),
						cubeRotation(original.corners(), carrier.points())));
			}
		}
		var geometry = SurgicalAssembly.AttackGeometry.create(right, left, origins, seats, cubes, equipment);
		return geometry == null ? null : geometry.withCarrierParts(parts);
	}

	/** Translate an already baked rest frame without repeating limb/volume or accessory measurements. */
	@Nullable
	static SurgicalAssembly.AttackGeometry rebaseAttackGeometry(SurgicalAssembly.AttackGeometry geometry,
		Vec3 bodyOrigin) {
		try {
			var right = geometry.right().stream().map(arm -> rebaseArm(arm, bodyOrigin)).toList();
			var left = geometry.left().stream().map(arm -> rebaseArm(arm, bodyOrigin)).toList();
			Map<SurgicalAssembly.CombinationMember, Vec3> origins = new HashMap<>();
			geometry.carrierOrigins().forEach((member, point) -> origins.put(member, point.subtract(bodyOrigin)));
			Map<SurgicalAssembly.CombinationMember, List<Vec3>> cubes = new HashMap<>();
			geometry.carrierCubes().forEach((member, points) ->
				cubes.put(member, points.stream().map(point -> point.subtract(bodyOrigin)).toList()));
			Map<SurgicalAssembly.CombinationMember, SurgicalAssembly.RidingSeat> seats = new HashMap<>();
			geometry.ridingSeats().forEach((member, seat) -> seats.put(member, new SurgicalAssembly.RidingSeat(
				seat.first().subtract(bodyOrigin), seat.second().subtract(bodyOrigin), seat.rotation())));
			Map<SurgicalAssembly.CombinationMember, SurgicalAssembly.CarrierEquipmentBounds> equipment = new HashMap<>();
			geometry.equipmentBounds().forEach((member, bounds) -> equipment.put(member,
				new SurgicalAssembly.CarrierEquipmentBounds(rebaseBounds(bounds.saddle(), bodyOrigin), rebaseBounds(bounds.chest(), bodyOrigin))));
			return new SurgicalAssembly.AttackGeometry(right, left, origins, seats, cubes, equipment, geometry.carrierParts());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	static SurgicalAssembly.ArmAttackGeometry rebaseArm(SurgicalAssembly.ArmAttackGeometry arm, Vec3 origin) {
		return new SurgicalAssembly.ArmAttackGeometry(arm.origin().subtract(origin), arm.reach(),
			arm.minimumY() - (float) origin.y, arm.maximumY() - (float) origin.y,
			arm.radius(), arm.volume(), arm.restDirection(), arm.hasElbow(), arm.sourceId(), arm.cubeId());
	}

	@Nullable
	static SurgicalAssembly.VisualBounds rebaseBounds(@Nullable SurgicalAssembly.VisualBounds bounds, Vec3 origin) {
		return bounds == null ? null : new SurgicalAssembly.VisualBounds((float) (bounds.minX() - origin.x),
			(float) (bounds.minY() - origin.y), (float) (bounds.minZ() - origin.z), (float) (bounds.maxX() - origin.x),
			(float) (bounds.maxY() - origin.y), (float) (bounds.maxZ() - origin.z));
	}

	@Nullable
	static SurgicalAssembly.VisualBounds equipmentBounds(
		List<Vec3> corners, List<Vec3> original, List<Vec3> transformed, Vec3 bodyOrigin) {
		if (corners.isEmpty()) return null;
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
		for (Vec3 corner : corners) {
			Vec3 point = transformCubePoint(corner, original, transformed).subtract(bodyOrigin);
			minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
		}
		return SurgicalAssembly.VisualBounds.create(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** The corner ordering survives table layout, per-cube rotations and the final upright transform. */
	static Vec3 transformCubePoint(Vec3 point, List<Vec3> original, List<Vec3> transformed) {
		Vec3 relative = point.subtract(original.getFirst());
		Vec3 result = transformed.getFirst();
		for (int corner : new int[] { 1, 2, 4 }) {
			Vec3 edge = original.get(corner).subtract(original.getFirst());
			if (edge.lengthSqr() > SlimeBionicAnimator.GEOMETRY_EPSILON)
				result = result.add(transformed.get(corner).subtract(transformed.getFirst())
					.scale(relative.dot(edge) / edge.lengthSqr()));
		}
		return result;
	}

	static SurgicalCubeRotation cubeRotation(List<Vec3> original, List<Vec3> transformed) {
		Vec3 origin = transformCubePoint(Vec3.ZERO, original, transformed);
		Vec3 x = transformCubePoint(new Vec3(1, 0, 0), original, transformed).subtract(origin).normalize();
		Vec3 y = transformCubePoint(new Vec3(0, 1, 0), original, transformed).subtract(origin).normalize();
		Vec3 z = x.cross(y).normalize();
		y = z.cross(x).normalize();
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(
			(float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z,
			(float) z.x, (float) z.y, (float) z.z));
		return new SurgicalCubeRotation(rotation.x, rotation.y, rotation.z, rotation.w);
	}

	@Nullable
	static SurgicalAssembly.ArmAttackGeometry bakeArm(List<SlimeBionicAnimator.ResolvedLimb> limbs,
		List<SlimeBionicAnimator.SourceState> sources, Vec3 bodyOrigin, int shoulderIndex, int elbowIndex) {
		if (shoulderIndex < 0)
			return null;
		SlimeBionicAnimator.ResolvedLimb distal = limbs.get(elbowIndex >= 0 ? elbowIndex : shoulderIndex);
		SlimeBionicAnimator.TipGeometry tip = distalTip(distal, sources);
		if (tip == null)
			return null;
		Vec3 attackOrigin = shoulderIndex >= 0 ? limbs.get(shoulderIndex).pivot() : distal.pivot();
		double boneLength = elbowIndex >= 0 && shoulderIndex >= 0
			? attackOrigin.distanceTo(distal.pivot()) + distal.pivot().distanceTo(tip.center())
			: attackOrigin.distanceTo(tip.center());
		float reach = (float) boneLength + tip.radius();
		float minimumY = (float) Math.min(attackOrigin.y,
			Math.min(distal.pivot().y, tip.center().y)) - tip.radius();
		float maximumY = (float) Math.max(attackOrigin.y,
			Math.max(distal.pivot().y, tip.center().y)) + tip.radius();
		float volume = armVolume(limbs, sources, shoulderIndex, elbowIndex);
		// The shoulder's mounted direction is stable even when a forearm folds back toward it.
		Vec3 restDirection = (elbowIndex >= 0 ? distal.pivot() : tip.center()).subtract(attackOrigin);
		if (restDirection.lengthSqr() <= SlimeBionicAnimator.GEOMETRY_EPSILON)
			restDirection = tip.center().subtract(attackOrigin);
		return SurgicalAssembly.ArmAttackGeometry.create(attackOrigin.subtract(bodyOrigin), reach,
			minimumY - (float) bodyOrigin.y, maximumY - (float) bodyOrigin.y, tip.radius(), volume,
			restDirection.lengthSqr() <= SlimeBionicAnimator.GEOMETRY_EPSILON ? null : restDirection, elbowIndex >= 0,
			distal.anchor().source(), distal.anchor().cube());
	}

	/** Coverage-weighted union of the distinct cuboids driven by this arm's joints. */
	static float armVolume(List<SlimeBionicAnimator.ResolvedLimb> limbs, List<SlimeBionicAnimator.SourceState> sources,
		int shoulderIndex, int elbowIndex) {
		Set<SlimeBionicAnimator.Member> members = new HashSet<>();
		if (shoulderIndex >= 0)
			members.addAll(limbs.get(shoulderIndex).members());
		if (elbowIndex >= 0)
			members.addAll(limbs.get(elbowIndex).members());
		List<List<Vec3>> cuboids = new ArrayList<>(members.size());
		for (SlimeBionicAnimator.Member member : members) {
			SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, member);
			if (box != null)
				cuboids.add(box.points());
		}
		double volume = SurgicalVolumeSampler.unionVolume(cuboids);
		return (float) Mth.clamp(volume, 0.0d,
			SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE
				* SurgicalAssembly.MAX_BODY_SIZE);
	}

	@Nullable
	static SlimeBionicAnimator.TipGeometry distalTip(SlimeBionicAnimator.ResolvedLimb limb, List<SlimeBionicAnimator.SourceState> sources) {
		Vec3 center = BionicRigBuilder.groupCenter(limb.members(), sources);
		if (center == null)
			return null;
		Vec3 direction = center.subtract(limb.pivot());
		if (direction.lengthSqr() < SlimeBionicAnimator.GEOMETRY_EPSILON)
			return null;
		direction = direction.normalize();
		double minimum = Double.POSITIVE_INFINITY;
		double maximum = Double.NEGATIVE_INFINITY;
		List<Vec3> points = new ArrayList<>();
		for (SlimeBionicAnimator.Member member : limb.members()) {
			SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, member);
			if (box == null)
				continue;
			for (Vec3 point : box.points()) {
				points.add(point);
				double projection = point.subtract(limb.pivot()).dot(direction);
				minimum = Math.min(minimum, projection);
				maximum = Math.max(maximum, projection);
			}
		}
		if (points.isEmpty() || !Double.isFinite(minimum) || !Double.isFinite(maximum))
			return null;
		// An irregular multi-cube forearm may have only one mathematically furthest corner. Treat its
		// distal tenth as the hand so the baked radius remains representative instead of collapsing.
		double tolerance = Math.max(1.0e-5d, (maximum - minimum) * 0.10d);
		Vec3 sum = Vec3.ZERO;
		int tipPoints = 0;
		for (Vec3 point : points)
			if (maximum - point.subtract(limb.pivot()).dot(direction) <= tolerance) {
				sum = sum.add(point);
				tipPoints++;
			}
		if (tipPoints == 0)
			return null;
		Vec3 tipCenter = sum.scale(1.0d / tipPoints);
		double radius = 0.0d;
		for (Vec3 point : points)
			if (maximum - point.subtract(limb.pivot()).dot(direction) <= tolerance)
				radius = Math.max(radius, point.distanceTo(tipCenter));
		return new SlimeBionicAnimator.TipGeometry(tipCenter, (float) Mth.clamp(radius, 0.05d, 8.0d));
	}
}
