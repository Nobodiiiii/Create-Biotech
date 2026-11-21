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
import com.nobodiiiii.createbiotech.entity.BionicRiding;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;

import net.minecraft.world.phys.Vec3;

/** Native model observations for one pending donor, whether it is in the world or on the table. */
public record DevouredDonorTopology(UUID donor, List<Vec3> centers, List<Seam> seams, BionicAnatomySnapshot anatomy,
	double lowestPointFromPivot, @Nullable Vec3 headCenter, double outwardMidpoint, int cubeCount) {
	public DevouredDonorTopology(UUID donor, List<Vec3> centers, List<Seam> seams, BionicAnatomySnapshot anatomy,
		double lowestPointFromPivot) {
		this(donor, centers, seams, anatomy, lowestPointFromPivot, null, 0, centers.size());
	}

	public static DevouredDonorTopology oversized(UUID donor, int cubeCount) {
		return new DevouredDonorTopology(donor, List.of(), List.of(), BionicAnatomySnapshot.EMPTY, 0, null, 0, cubeCount);
	}

	public DevouredDonorTopology {
		centers = List.copyOf(centers);
		seams = List.copyOf(seams);
		if (donor == null || cubeCount <= 0 || anatomy == null || !(Math.abs(outwardMidpoint) <= 4 * SurgicalAssembly.MAX_BODY_SIZE)
			|| headCenter != null && !(headCenter.lengthSqr() <= 12 * SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE))
			throw new IllegalArgumentException("Invalid native prey model");
		if (centers.isEmpty()) {
			if (!seams.isEmpty() || anatomy != BionicAnatomySnapshot.EMPTY || lowestPointFromPivot != 0
				|| headCenter != null || outwardMidpoint != 0)
				throw new IllegalArgumentException("An oversized model only reports its cube count");
		} else if (centers.size() != cubeCount || !SurgicalAssembly.validTopology(cubeCount, seams)
			|| !anatomy.fits(cubeCount) || !anatomy.hasVolumes()
			|| !(Math.abs(lowestPointFromPivot) <= SurgicalAssembly.MAX_BODY_SIZE * 2d)
			|| centers.stream().anyMatch(v -> !(v.lengthSqr() <= 12 * SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE)))
			throw new IllegalArgumentException("Invalid native prey topology");
	}

	public static int availableCubes(SurgicalAssembly assembly) {
		return SurgicalAssembly.MAX_CUBES - assembly.sources().stream().filter(s -> !s.profile().needsPreyTopology())
			.mapToInt(s -> s.presentCubes().cardinality()).sum();
	}

	/** Expand into a new assembly, keeping all installed sources ahead of new growth for capacity. */
	@Nullable
	public static SurgicalAssembly expand(SurgicalAssembly assembly, List<DevouredDonorTopology> observed) {
		if (observed.isEmpty())
			return assembly.sources().stream().anyMatch(s -> s.profile().needsPreyTopology()) ? null : assembly;
		var observations = observed.iterator();
		var sources = new ArrayList<Source>();
		var anchors = new HashMap<Integer, Integer>();
		var organJoints = new ArrayList<Joint>();
		var geometry = assembly.attackGeometry();
		double floor = geometry == null ? 0 : geometry.carrierCubes().entrySet().stream()
			.filter(e -> !assembly.sources().get(e.getKey().source()).profile().isDevouredForm())
			.flatMap(e -> e.getValue().stream()).mapToDouble(Vec3::y).min().orElse(0);
		int remaining = availableCubes(assembly);
		int[] sourceIds = new int[assembly.sources().size()];
		for (int index = 0; index < sourceIds.length; index++) {
			var source = assembly.sources().get(index);
			sourceIds[index] = sources.size();
			if (source.profile().needsPreyTopology()) {
				if (!observations.hasNext() || source.cubeCount() != 1 || !source.containsCube(0)) return null;
				var model = observations.next();
				if (!source.donorId().equals(model.donor())) return null;
				if (model.centers.isEmpty()) {
					if (model.cubeCount <= remaining) return null;
					sourceIds[index] = -1;
					continue;
				}
				if (model.cubeCount > remaining) return null;
				remaining -= model.cubeCount;
				// Attach existing exterior joints to the substantial central tissue of the intact prey.
				int anchor = 0;
				for (int cube = 1; cube < model.centers.size(); cube++)
					if (model.anatomy.volume(cube) > model.anatomy.volume(anchor)) anchor = cube;
				anchors.put(index, anchor);
				// Native feet, tails or wings may extend outside the original mob hitbox. Lift only that donor.
				Vec3 pivot = geometry == null ? null : geometry.carrierOrigins().get(new CombinationMember(index, 0));
				source = model.expandSource(source, assembly.layoutLayPose(), pivot, floor);
				if (source == null) return null;
				// Detached native parts (for example floating eyes) still belong to the swallowed body.
				// Ordinary glue keeps each island in the component that the table will pack.
				int sourceId = sources.size();
				for (var island : SurgicalAssembly.components(source.cubeCount(), source.presentCubes(), source.seams(), source.cutSeams())) {
					if (island.get(anchor)) continue;
					int root = island.nextSetBit(0);
					for (int cube = island.nextSetBit(root + 1); cube >= 0; cube = island.nextSetBit(cube + 1))
						if (model.anatomy.volume(cube) > model.anatomy.volume(root)) root = cube;
					organJoints.add(new Joint(sourceId, anchor, sourceId, root));
				}
			}
			sources.add(source);
		}
		if (observations.hasNext()) return null;
		var joints = new ArrayList<Joint>();
		for (var j : assembly.joints()) {
			if (sourceIds[j.firstSource()] < 0 || sourceIds[j.secondSource()] < 0) continue;
			var replay = j.replay();
			if (replay != null) replay = new JointReplay(sourceIds[replay.movingSource()],
				anchors.getOrDefault(replay.movingSource(), replay.movingCube()), replay.transform(), replay.anchorContact());
			joints.add(new Joint(sourceIds[j.firstSource()], anchors.getOrDefault(j.firstSource(), j.firstCube()),
				sourceIds[j.secondSource()], anchors.getOrDefault(j.secondSource(), j.secondCube()), replay));
		}
		joints.addAll(organJoints);
		var combinations = assembly.combinations().stream().map(c -> new Combination(c.id(), c.members().stream()
			.map(m -> new CombinationMember(sourceIds[m.source()], anchors.getOrDefault(m.source(), m.cube()))).toList())).toList();
		var limbs = assembly.limbs().stream().map(l -> new Limb(l.type(), sourceIds[l.childSource()],
			anchors.getOrDefault(l.childSource(), l.childCube()), sourceIds[l.parentSource()],
			anchors.getOrDefault(l.parentSource(), l.parentCube()))).toList();
		if (sources.isEmpty()) return null;
		var root = sources.getFirst();
		return SurgicalAssembly.createComposite(sources, joints, combinations, limbs, root.facing(), root.layPose());
	}

	private Source expandSource(Source source, SurgicalLayPose layout, @Nullable Vec3 oldCenter, double floor) {
		BitSet present = new BitSet();
		present.set(0, centers.size());
		Vec3 offset = source.cubeOffsets().getOrDefault(0, Vec3.ZERO);
		var rotation = source.cubeRotations().getOrDefault(0, SurgicalCubeRotation.IDENTITY);
		MimicProfile profile = source.profile();
		Vec3 pivot = new Vec3(0, profile.devouredHeight() / 2, 0);
		Vec3 shift = Vec3.ZERO, extra = Vec3.ZERO;
		var measured = DevouredGrowthPose.measured(source, centers, anatomy, headCenter);
		if (measured != null) {
			var oldRotation = rotation.inverseRotate(layout);
			Vec3 normal = profile.devouredSurfaceNormal();
			Vec3 anchor = measured.rotation().rotate(measured.anchor());
			// Keep the selected face contact while turning the real model about its own pivot.
			shift = oldRotation.rotate(profile.devouredAnchor()).subtract(anchor);
			double outer = outwardMidpoint;
			if (headCenter != null) outer = Math.min(outer, measured.rotation().rotate(headCenter.subtract(pivot)).dot(normal)
				- Math.min(0.04, profile.devouredWidth() * 0.1));
			extra = normal.scale(Math.max(0, anchor.dot(normal) - outer));
			shift = shift.add(extra);
		}
		double lift = oldCenter == null ? 0 : Math.max(0, floor - oldCenter.y - shift.y - lowestPointFromPivot);
		shift = shift.add(0, lift, 0);
		if (measured != null) {
			Vec3 slide = rotation.inverseRotate(layout).rotate(profile.devouredSlide()).add(extra).add(0, lift, 0);
			profile = profile.asDevouredForm(profile.devouredWidth(), profile.devouredHeight(), profile.devouredAt(),
				measured.burial(), measured.anchor(), null, BionicRiding.inverse(measured.rotation()).rotate(slide));
			rotation = measured.rotation().reframe(SurgicalLayPose.IDENTITY, layout);
		}
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
		return Source.create(profile.withNativePreyCubes(), source.donorId(), centers.size(), present,
			anatomy, seams, new BitSet(), List.of(), source.facing(), source.layPose(), source.originOffset().add(layout.rotate(shift)), offsets, rotations);
	}
}
