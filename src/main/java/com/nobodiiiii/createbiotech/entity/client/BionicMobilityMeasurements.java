package com.nobodiiiii.createbiotech.entity.client;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalGait;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalVolumeSampler;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Bone;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Internal BionicMobilityMeasurements implementation; public entry points remain on SlimeBionicAnimator. */
final class BionicMobilityMeasurements {
	private BionicMobilityMeasurements() {}


	/**
	 * Measures the grounded legs that may animate and contribute to movement. A leg touches the
	 * body's rest-pose ground plane when its lowest point is less than two model pixels above the
	 * complete body's lowest point. Its effective length is the geometric mean of vertical reach and
	 * the hip-to-sole joint-chain length, so a tilted leg gains some real-length benefit without a
	 * nearly horizontal appendage receiving its full span as stride.
	 */
	static SlimeBionicAnimator.MobilityMetrics measureMobility(SurgicalAssembly assembly, List<SlimeBionicAnimator.SourceState> sources) {
		if (assembly == null || sources == null || sources.size() != assembly.sources().size())
			return SlimeBionicAnimator.MobilityMetrics.EMPTY;
		return measureMobility(BionicRigBuilder.resolveLimbs(assembly, sources), sources);
	}

	/** Y centre of the same neck-driven group assigned to the visible head animation. */
	static double primaryHeadCenterY(SurgicalAssembly assembly, List<SlimeBionicAnimator.SourceState> sources) {
		if (assembly == null || sources == null || sources.size() != assembly.sources().size())
			return Double.NaN;
		for (SlimeBionicAnimator.ResolvedLimb limb : BionicRigBuilder.resolveLimbs(assembly, sources))
			if (limb.bone() == Bone.HEAD) {
				double minY = Double.POSITIVE_INFINITY;
				double maxY = Double.NEGATIVE_INFINITY;
				for (SlimeBionicAnimator.Member member : limb.members()) {
					SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, member);
					if (box == null)
						continue;
					for (Vec3 point : box.points()) {
						minY = Math.min(minY, point.y);
						maxY = Math.max(maxY, point.y);
					}
				}
				return Double.isFinite(minY) && Double.isFinite(maxY)
					? (minY + maxY) * 0.5d : Double.NaN;
			}
		return Double.NaN;
	}

	static SlimeBionicAnimator.MobilityMetrics measureMobility(List<SlimeBionicAnimator.ResolvedLimb> limbs,
		List<SlimeBionicAnimator.SourceState> sources) {
		Set<Integer> groundedHips = groundedHipIndices(limbs, sources);
		if (groundedHips.isEmpty())
			return SlimeBionicAnimator.MobilityMetrics.EMPTY;

		double totalLength = 0.0d;
		Set<SlimeBionicAnimator.Member> groundedLegMembers = new HashSet<>();
		for (int hipIndex : groundedHips) {
			SlimeBionicAnimator.LegMeasurement measurement = measureLeg(hipIndex, limbs, sources);
			if (measurement != null)
				totalLength += measurement.effectiveLength();
			collectDescendantMembers(hipIndex, limbs, groundedLegMembers);
		}
		int groundedKnees = 0;
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs)
			if (limb.type() == SurgicalLimbType.KNEE && limb.parentIndex() >= 0
				&& groundedHips.contains(limb.parentIndex()))
				groundedKnees++;

		List<List<Vec3>> allCuboids = new ArrayList<>();
		List<List<Vec3>> legCuboids = new ArrayList<>(groundedLegMembers.size());
		for (int source = 0; source < sources.size(); source++)
			for (Map.Entry<Integer, SlimeBionicAnimator.CubeBox> entry : sources.get(source).boxes().entrySet()) {
				allCuboids.add(entry.getValue().points());
				if (groundedLegMembers.contains(new SlimeBionicAnimator.Member(source, entry.getKey())))
					legCuboids.add(entry.getValue().points());
			}
		double totalVolume = SurgicalVolumeSampler.unionVolume(allCuboids);
		double legVolume = SurgicalVolumeSampler.unionVolume(legCuboids);
		float legVolumeRatio = totalVolume <= SlimeBionicAnimator.GEOMETRY_EPSILON ? 0.0f
			: (float) Mth.clamp(legVolume / totalVolume, 0.0d, 1.0d);
		return new SlimeBionicAnimator.MobilityMetrics((float) (totalLength / groundedHips.size()),
			groundedHips.size(), groundedKnees, legVolumeRatio);
	}

	static float averageEffectiveLegLength(List<SlimeBionicAnimator.ResolvedLimb> limbs,
		List<SlimeBionicAnimator.SourceState> sources) {
		Set<Integer> groundedHips = groundedHipIndices(limbs, sources);
		if (groundedHips.isEmpty())
			return 0.0f;
		double totalLength = 0.0d;
		for (int hipIndex : groundedHips) {
			SlimeBionicAnimator.LegMeasurement measurement = measureLeg(hipIndex, limbs, sources);
			if (measurement != null)
				totalLength += measurement.effectiveLength();
		}
		return (float) (totalLength / groundedHips.size());
	}

	static Set<Integer> groundedHipIndices(List<SlimeBionicAnimator.ResolvedLimb> limbs,
		List<SlimeBionicAnimator.SourceState> sources) {
		double groundHeight = Double.NEGATIVE_INFINITY;
		for (SlimeBionicAnimator.SourceState source : sources)
			for (SlimeBionicAnimator.CubeBox box : source.boxes().values())
				groundHeight = Math.max(groundHeight, box.max()[SlimeBionicAnimator.AXIS_Y]);
		if (!Double.isFinite(groundHeight))
			return Set.of();
		Set<Integer> grounded = new HashSet<>();
		for (int index = 0; index < limbs.size(); index++) {
			if (limbs.get(index).type() != SurgicalLimbType.HIP)
				continue;
			SlimeBionicAnimator.LegMeasurement measurement = measureLeg(index, limbs, sources);
			if (measurement != null
				&& groundHeight - measurement.soleHeight() < SurgicalGait.GROUND_CONTACT_TOLERANCE)
				grounded.add(index);
		}
		return Set.copyOf(grounded);
	}

	@Nullable
	static SlimeBionicAnimator.LegMeasurement measureLeg(int hipIndex, List<SlimeBionicAnimator.ResolvedLimb> limbs,
		List<SlimeBionicAnimator.SourceState> sources) {
		SlimeBionicAnimator.ResolvedLimb hip = limbs.get(hipIndex);
		double pivotHeight = SlimeBionicAnimator.BODY_SPACE.project(hip.pivot(), SlimeBionicAnimator.AXIS_Y);
		double soleHeight = Double.NEGATIVE_INFINITY;
		Set<SlimeBionicAnimator.Member> members = new HashSet<>();
		collectDescendantMembers(hipIndex, limbs, members);
		for (SlimeBionicAnimator.Member member : members) {
			SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, member);
			if (box == null)
				continue;
			for (Vec3 point : box.points())
				soleHeight = Math.max(soleHeight, SlimeBionicAnimator.BODY_SPACE.project(point, SlimeBionicAnimator.AXIS_Y));
		}
		if (!Double.isFinite(soleHeight))
			return null;
		Vec3 soleSum = Vec3.ZERO;
		int solePoints = 0;
		for (SlimeBionicAnimator.Member member : members) {
			SlimeBionicAnimator.CubeBox box = BionicRigBuilder.box(sources, member);
			if (box == null)
				continue;
			for (Vec3 point : box.points())
				if (soleHeight - SlimeBionicAnimator.BODY_SPACE.project(point, SlimeBionicAnimator.AXIS_Y) <= SlimeBionicAnimator.GEOMETRY_EPSILON) {
					soleSum = soleSum.add(point);
					solePoints++;
				}
		}
		if (solePoints == 0)
			return null;
		Vec3 soleCenter = soleSum.scale(1.0d / solePoints);
		double verticalHeight = Math.max(0.0d, soleHeight - pivotHeight);
		double actualLength = hip.pivot().distanceTo(soleCenter);
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs)
			if (limb.type() == SurgicalLimbType.KNEE && limb.parentIndex() == hipIndex) {
				actualLength = hip.pivot().distanceTo(limb.pivot())
					+ limb.pivot().distanceTo(soleCenter);
				break;
			}
		double effectiveLength = Math.sqrt(verticalHeight * actualLength);
		return new SlimeBionicAnimator.LegMeasurement(soleHeight,
			Double.isFinite(effectiveLength)
				? Mth.clamp(effectiveLength, 0.0d, SurgicalAssembly.MAX_BODY_SIZE) : 0.0d);
	}

	static void collectDescendantMembers(int hipIndex, List<SlimeBionicAnimator.ResolvedLimb> limbs,
		Set<SlimeBionicAnimator.Member> members) {
		for (int candidateIndex = 0; candidateIndex < limbs.size(); candidateIndex++)
			if (isDescendantOrSelf(candidateIndex, hipIndex, limbs))
				members.addAll(limbs.get(candidateIndex).members());
	}

	static boolean isDescendantOrSelf(int candidate, int ancestor,
		List<SlimeBionicAnimator.ResolvedLimb> limbs) {
		int cursor = candidate;
		for (int depth = 0; cursor >= 0 && depth <= limbs.size(); depth++) {
			if (cursor == ancestor)
				return true;
			cursor = limbs.get(cursor).parentIndex();
		}
		return false;
	}
}
