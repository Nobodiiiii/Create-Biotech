package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Combination;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Joint;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.JointReplay;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Limb;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Seam;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.Source;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;

import net.minecraft.world.phys.Vec3;

/** The same native model observations used for fresh captures, keyed to the donor in the held box. */
public record DevouredDonorTopology(UUID donor, List<Vec3> centers, List<Seam> seams, BionicAnatomySnapshot anatomy,
	double lowestPointFromPivot) {
	public DevouredDonorTopology {
		centers = List.copyOf(centers);
		seams = List.copyOf(seams);
		if (donor == null || !SurgicalAssembly.validTopology(centers.size(), seams)
			|| anatomy == null || !anatomy.fits(centers.size()) || !anatomy.hasVolumes()
			|| !(Math.abs(lowestPointFromPivot) <= SurgicalAssembly.MAX_BODY_SIZE * 2d)
			|| centers.stream().anyMatch(v -> !(v.lengthSqr() <= 12 * SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE)))
			throw new IllegalArgumentException("Invalid native prey topology");
	}

	/** Return a placement assembly only. The held entity and its shared NBT remain untouched. */
	@Nullable
	public static SurgicalAssembly expand(SurgicalAssembly assembly, List<DevouredDonorTopology> observed) {
		if (observed.isEmpty())
			return assembly.sources().stream().anyMatch(s -> s.profile().needsPreyTopology()) ? null : assembly;
		var observations = observed.iterator();
		var sources = new ArrayList<Source>();
		var anchors = new HashMap<Integer, Integer>();
		var geometry = assembly.attackGeometry();
		double floor = geometry == null ? 0 : geometry.carrierCubes().entrySet().stream()
			.filter(e -> !assembly.sources().get(e.getKey().source()).profile().isDevouredForm())
			.flatMap(e -> e.getValue().stream()).mapToDouble(Vec3::y).min().orElse(0);
		int total = 0;
		for (var source : assembly.sources()) {
			if (source.profile().needsPreyTopology()) {
				if (!observations.hasNext() || source.cubeCount() != 1 || !source.containsCube(0)) return null;
				var model = observations.next();
				if (!source.donorId().equals(model.donor())) return null;
				// Attach existing exterior joints to the substantial central tissue of the intact prey.
				int anchor = 0;
				for (int cube = 1; cube < model.centers.size(); cube++)
					if (model.anatomy.volume(cube) > model.anatomy.volume(anchor)) anchor = cube;
				anchors.put(sources.size(), anchor);
				// Native feet, tails or wings may extend outside the original mob hitbox. Lift only that donor.
				Vec3 pivot = geometry == null ? null : geometry.carrierOrigins().get(new CombinationMember(sources.size(), 0));
				double lift = pivot == null ? 0 : Math.max(0, floor - pivot.y - model.lowestPointFromPivot());
				source = model.expandSource(source, assembly.layoutLayPose().rotate(new Vec3(0, lift, 0)));
				if (source == null) return null;
			}
			total += source.presentCubes().cardinality();
			if (total > SurgicalAssembly.MAX_CUBES) return null;
			sources.add(source);
		}
		if (observations.hasNext()) return null;
		var joints = assembly.joints().stream().map(j -> {
			var replay = j.replay();
			if (replay != null) replay = new JointReplay(replay.movingSource(),
				anchors.getOrDefault(replay.movingSource(), replay.movingCube()), replay.transform(), replay.anchorContact());
			return new Joint(j.firstSource(), anchors.getOrDefault(j.firstSource(), j.firstCube()),
				j.secondSource(), anchors.getOrDefault(j.secondSource(), j.secondCube()), replay);
		}).toList();
		var combinations = assembly.combinations().stream().map(c -> new Combination(c.id(), c.members().stream()
			.map(m -> new CombinationMember(m.source(), anchors.getOrDefault(m.source(), m.cube()))).toList())).toList();
		var limbs = assembly.limbs().stream().map(l -> new Limb(l.type(), l.childSource(),
			anchors.getOrDefault(l.childSource(), l.childCube()), l.parentSource(),
			anchors.getOrDefault(l.parentSource(), l.parentCube()))).toList();
		return SurgicalAssembly.createComposite(sources, joints, combinations, limbs, assembly.layoutFacing(), assembly.layoutLayPose());
	}

	private Source expandSource(Source source, Vec3 lift) {
		BitSet present = new BitSet();
		present.set(0, centers.size());
		Vec3 offset = source.cubeOffsets().getOrDefault(0, Vec3.ZERO);
		var rotation = source.cubeRotations().getOrDefault(0, SurgicalCubeRotation.IDENTITY);
		Vec3 pivot = new Vec3(0, source.profile().devouredHeight() / 2, 0);
		Map<Integer, Vec3> offsets = new HashMap<>();
		Map<Integer, SurgicalCubeRotation> rotations = new HashMap<>();
		for (int cube = 0; cube < centers.size(); cube++) {
			Vec3 translated = offset;
			if (!rotation.isIdentity()) {
				Vec3 relative = source.layPose().rotate(centers.get(cube).subtract(pivot));
				translated = offset.add(rotation.rotate(relative).subtract(relative));
				rotations.put(cube, rotation);
			}
			if (!translated.equals(Vec3.ZERO)) offsets.put(cube, translated);
		}
		return Source.create(source.profile().withNativePreyCubes(), source.donorId(), centers.size(), present,
			anatomy, seams, new BitSet(), List.of(), source.facing(), source.layPose(), source.originOffset().add(lift), offsets, rotations);
	}
}
