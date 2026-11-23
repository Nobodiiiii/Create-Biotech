package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.DevouredDonorTopology;
import com.nobodiiiii.createbiotech.content.surgery.DevouredGrowthPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLayPose;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;

import net.minecraft.world.phys.Vec3;

/** Resolve intact prey against the local renderer, exactly once per placement and resource generation. */
final class DevouredPlacementModels {
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private final SurgicalAssembly boxed;
	private final List<SurgicalAssembly.Source> prey;
	private final List<MimicProfile> nativeProfiles;
	private final List<DevouredDonorTopology> measured = new ArrayList<>();
	private SurgicalClientJob<DevouredDonorTopology> job;
	private SurgicalClientJob<SurgicalAssembly> expansion;
	private int generation = -1;
	private int remainingCubes;

	DevouredPlacementModels(SurgicalAssembly boxed) {
		this.boxed = boxed;
		prey = boxed.sources().stream().filter(source -> source.profile().needsPreyTopology()).toList();
		nativeProfiles = prey.stream().map(source -> source.profile().withNativePreyCubes()).toList();
	}

	List<DevouredDonorTopology> observations() { return measured; }
	void cancel() {
		if (job != null) job.cancel();
		if (expansion != null) expansion.cancel();
	}

	SurgicalAssembly prepare(int resources) {
		if (prey.isEmpty()) return boxed;
		if (generation != resources) {
			cancel();
			generation = resources;
			job = null;
			measured.clear();
			remainingCubes = DevouredDonorTopology.availableCubes(boxed);
			expansion = null;
		}
		if (expansion != null) return expansion.poll();
		int index = measured.size();
		if (job == null) {
			var preview = SurgicalSourceModelRenderer.previewDeferred(nativeProfiles.get(index));
			if (preview == null) return null;
			var snapshot = SurgicalSourceModelRenderer.geometryDeferred(preview, 0, new BitSet(), Map.of(), Map.of(),
				new PoseStack(), 0, null);
			if (snapshot == null) return null;
			int remaining = remainingCubes;
			var source = prey.get(index);
			job = new SurgicalClientJob<>("placement(prey topology)", () -> snapshot.observedCubeCount() > remaining
				? DevouredDonorTopology.oversized(source.donorId(), snapshot.observedCubeCount()) : measure(source, snapshot));
		}
		var topology = job.poll();
		if (topology == null) return null;
		measured.add(topology);
		remainingCubes -= topology.centers().size();
		job = null;
		if (measured.size() == prey.size()) {
			var observations = List.copyOf(measured);
			expansion = new SurgicalClientJob<>("placement(native sources)", () -> DevouredDonorTopology.expand(boxed, observations));
			return expansion.poll();
		}
		return null;
	}

	private DevouredDonorTopology measure(SurgicalAssembly.Source source, SurgicalModelRenderContext.Snapshot snapshot) {
		int count = snapshot.observedCubeCount();
		if (!SurgicalAssembly.validCubeCount(count) || snapshot.cubes().size() != count) return null;
		var centers = new Vec3[count];
		var seams = SurgicalClientTopology.buildContactTopology(count, snapshot.cubes()).seams();
		var anatomy = SurgicalPlacementController.discoveredAnatomy(snapshot.cubes(), count, source.profile(), seams);
		if (!anatomy.hasVolumes()) return null;
		Vec3 headSum = Vec3.ZERO;
		double headVolume = 0;
		for (var cube : snapshot.cubes()) {
			int id = cube.cubeId();
			if (id < 0 || id >= count || centers[id] != null) return null;
			centers[id] = cube.corners().stream().reduce(Vec3.ZERO, Vec3::add).scale(1d / 8);
			if (cube.head()) {
				double volume = anatomy.volume(id);
				headSum = headSum.add(centers[id].scale(volume));
				headVolume += volume;
			}
		}
		Vec3 headCenter = headVolume > 0 ? headSum.scale(1 / headVolume) : null;
		var nativeCenters = Arrays.asList(centers);
		var growth = DevouredGrowthPose.measured(source, nativeCenters, anatomy, headCenter);
		var pose = boxed.layoutLayPose();
		var rotation = growth == null ? source.cubeRotations().getOrDefault(0, SurgicalCubeRotation.IDENTITY)
			: growth.rotation().reframe(SurgicalLayPose.IDENTITY, pose);
		var pivot = new Vec3(0, source.profile().devouredHeight() / 2d, 0);
		double lowest = Double.POSITIVE_INFINITY;
		double anchorVolume = -1;
		DevouredDonorTopology.Contact contact = null;
		var normal = source.profile().devouredSurfaceNormal();
		for (var cube : snapshot.cubes()) {
			var uprightCorners = new ArrayList<Vec3>(8);
			for (var point : cube.corners()) {
				Vec3 fromPivot = source.layPose().rotate(point.subtract(pivot));
				Vec3 upright = pose.inverseRotate(rotation.rotate(fromPivot));
				lowest = Math.min(lowest, upright.y);
				if (normal != null) uprightCorners.add(upright);
			}
			if (normal == null) continue;
			Vec3 origin = uprightCorners.getFirst();
			double volume = Math.abs(uprightCorners.get(1).subtract(origin).dot(
				uprightCorners.get(2).subtract(origin).cross(uprightCorners.get(4).subtract(origin))));
			if (volume < 1e-10) volume = 0;
			if (contact != null && (volume < anchorVolume || volume == anchorVolume && cube.cubeId() >= contact.cube())) continue;
			anchorVolume = volume;
			Vec3 center = origin.lerp(uprightCorners.getLast(), 0.5);
			Vec3 inward = supportPoint(uprightCorners, normal);
			double depth = center.subtract(inward).dot(normal);
			double inset = depth > 1e-8 ? Math.clamp(source.profile().devouredBurial() / depth, 0.1, 0.65) : 0.1;
			// Points inset toward the center stay on this cube when the shell needs extra clearance.
			contact = new DevouredDonorTopology.Contact(cube.cubeId(), inward.lerp(center, inset),
				supportPoint(uprightCorners, UP).lerp(center, 0.1));
		}
		return new DevouredDonorTopology(source.donorId(), nativeCenters, seams, anatomy, lowest, headCenter, contact, count);
	}

	private static Vec3 supportPoint(List<Vec3> corners, Vec3 direction) {
		double nearest = corners.stream().mapToDouble(point -> point.dot(direction)).min().orElseThrow();
		Vec3 sum = Vec3.ZERO;
		int count = 0;
		for (var point : corners) {
			if (point.dot(direction) > nearest + 1e-8) continue;
			sum = sum.add(point);
			count++;
		}
		return sum.scale(1d / count);
	}
}
