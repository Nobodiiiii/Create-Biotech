package com.nobodiiiii.createbiotech.content.surgery;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
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
	double lowestPointFromPivot, @Nullable Vec3 headCenter, @Nullable Contact contact, int cubeCount) {
	/** Two interior points of the same native cube in rotated body space, relative to the prey pivot. */
	public record Contact(int cube, Vec3 inward, Vec3 lowest) {
		public Contact {
			double limit = 4 * SurgicalAssembly.MAX_BODY_SIZE;
			if (cube < 0 || !(inward.lengthSqr() <= limit * limit) || !(lowest.lengthSqr() <= limit * limit)
				|| lowest.y > inward.y + 1e-6)
				throw new IllegalArgumentException("Invalid native prey contact");
		}
	}

	public static DevouredDonorTopology oversized(UUID donor, int cubeCount) {
		return new DevouredDonorTopology(donor, List.of(), List.of(), BionicAnatomySnapshot.EMPTY, 0, null, null, cubeCount);
	}

	public DevouredDonorTopology {
		centers = List.copyOf(centers);
		seams = List.copyOf(seams);
		if (donor == null || cubeCount <= 0 || anatomy == null || contact != null && contact.cube() >= centers.size()
			|| headCenter != null && !(headCenter.lengthSqr() <= 12 * SurgicalAssembly.MAX_BODY_SIZE * SurgicalAssembly.MAX_BODY_SIZE))
			throw new IllegalArgumentException("Invalid native prey model");
		if (centers.isEmpty()) {
			if (!seams.isEmpty() || anatomy != BionicAnatomySnapshot.EMPTY || lowestPointFromPivot != 0
				|| headCenter != null)
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
		var shells = new HashMap<Integer, List<Vec3>>();
		if (geometry != null) {
			// Growth appends the prey after its shell and records their joint in source order.
			for (var joint : assembly.joints()) {
				if (joint.firstSource() == joint.secondSource() || joint.secondCube() != 0
					|| !assembly.sources().get(joint.secondSource()).profile().needsPreyTopology()) continue;
				var shell = new CombinationMember(joint.firstSource(), joint.firstCube());
				var corners = geometry.carrierCubes().get(shell);
				if (corners != null) shells.putIfAbsent(joint.secondSource(), corners);
			}
		}
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
				int anchor = model.contact == null ? model.largestCube() : model.contact.cube();
				anchors.put(index, anchor);
				Vec3 pivot = geometry == null ? null : geometry.carrierOrigins().get(new CombinationMember(index, 0));
				source = model.expandSource(source, assembly.layoutLayPose(), pivot, shells.get(index), floor);
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

	private int largestCube() {
		int largest = 0;
		for (int cube = 1; cube < centers.size(); cube++)
			if (anatomy.volume(cube) > anatomy.volume(largest)) largest = cube;
		return largest;
	}

	private Source expandSource(Source source, SurgicalLayPose layout, @Nullable Vec3 oldCenter,
		@Nullable List<Vec3> shell, double floor) {
		BitSet present = new BitSet();
		present.set(0, centers.size());
		Vec3 offset = source.cubeOffsets().getOrDefault(0, Vec3.ZERO);
		var rotation = source.cubeRotations().getOrDefault(0, SurgicalCubeRotation.IDENTITY);
		MimicProfile profile = source.profile();
		Vec3 pivot = new Vec3(0, profile.devouredHeight() / 2, 0);
		Vec3 shift;
		var measured = DevouredGrowthPose.measured(source, centers, anatomy, headCenter);
		if (measured != null) {
			if (contact == null) return null;
			var oldRotation = rotation.inverseRotate(layout);
			Vec3 oldAnchor = oldRotation.rotate(profile.devouredAnchor());
			Vec3 start = oldAnchor.subtract(oldRotation.rotate(profile.devouredSlide()));
			shift = oldCenter == null || shell == null ? start.subtract(contact.inward())
				: contactShift(oldCenter, oldAnchor, shell, floor);
			// Keep the growth start in the shell after moving the native model's pivot.
			Vec3 localStart = BionicRiding.inverse(measured.rotation()).rotate(start.subtract(shift));
			profile = profile.asDevouredForm(profile.devouredWidth(), profile.devouredHeight(), profile.devouredAt(),
				measured.burial(), measured.anchor(), null, measured.anchor().subtract(localStart));
			rotation = measured.rotation().reframe(SurgicalLayPose.IDENTITY, layout);
		} else {
			double lift = oldCenter == null ? 0 : Math.max(0, floor - oldCenter.y - lowestPointFromPivot);
			shift = new Vec3(0, lift, 0);
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

	private Vec3 contactShift(Vec3 oldCenter, Vec3 oldAnchor, List<Vec3> shell, double floor) {
		var corners = DevouredGrowthPose.orderedCorners(shell);
		Vec3 origin = corners.getFirst();
		Vec3 desired = oldCenter.add(oldAnchor).subtract(origin);
		Vec3 target = origin;
		double inset = 0.025;
		// Clamp in the shell's local axes to leave overlap even at a tilted edge or corner.
		for (int bit = 1; bit <= 4; bit <<= 1) {
			Vec3 edge = corners.get(bit).subtract(origin);
			double lengthSqr = edge.lengthSqr();
			if (lengthSqr > 1e-12)
				target = target.add(edge.scale(Math.clamp(desired.dot(edge) / lengthSqr, inset, 1 - inset)));
		}
		Vec3 center = origin.lerp(corners.getLast(), 0.5);
		Vec3 highest = corners.stream().max(Comparator.comparingDouble(Vec3::y)).orElseThrow().lerp(center, 2 * inset);
		Vec3 nativePoint = contact.inward();
		double requiredY = floor - lowestPointFromPivot;
		double drop = nativePoint.y - contact.lowest().y;
		if (requiredY + nativePoint.y > highest.y && drop > 1e-8)
			nativePoint = nativePoint.lerp(contact.lowest(), Math.min(1, (requiredY + nativePoint.y - highest.y) / drop));
		if (requiredY + nativePoint.y > target.y && highest.y > target.y)
			target = target.lerp(highest, Math.min(1, (requiredY + nativePoint.y - target.y) / (highest.y - target.y)));
		return target.subtract(oldCenter).subtract(nativePoint);
	}
}
