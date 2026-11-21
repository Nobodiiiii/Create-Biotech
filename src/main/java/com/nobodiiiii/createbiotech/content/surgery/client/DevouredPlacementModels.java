package com.nobodiiiii.createbiotech.content.surgery.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.content.surgery.DevouredDonorTopology;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;

import net.minecraft.world.phys.Vec3;

/** Resolve intact prey against the local renderer, exactly once per placement and resource generation. */
final class DevouredPlacementModels {
	private final SurgicalAssembly boxed;
	private final List<SurgicalAssembly.Source> prey;
	private final List<MimicProfile> nativeProfiles;
	private final List<DevouredDonorTopology> measured = new ArrayList<>();
	private SurgicalClientJob<DevouredDonorTopology> job;
	private int generation = -1;
	private SurgicalAssembly expanded;

	DevouredPlacementModels(SurgicalAssembly boxed) {
		this.boxed = boxed;
		prey = boxed.sources().stream().filter(source -> source.profile().needsPreyTopology()).toList();
		nativeProfiles = prey.stream().map(source -> source.profile().withNativePreyCubes()).toList();
	}

	List<DevouredDonorTopology> observations() { return measured; }
	void cancel() { if (job != null) job.cancel(); }

	SurgicalAssembly prepare(int resources) {
		if (prey.isEmpty()) return boxed;
		if (generation != resources) {
			cancel();
			generation = resources;
			job = null;
			measured.clear();
			expanded = null;
		}
		if (measured.size() == prey.size()) return expanded;
		int index = measured.size();
		if (job == null) {
			var preview = SurgicalSourceModelRenderer.previewDeferred(nativeProfiles.get(index));
			if (preview == null) return null;
			var snapshot = SurgicalSourceModelRenderer.geometryDeferred(preview, 0, new BitSet(), Map.of(), Map.of(),
				new PoseStack(), 0, null);
			if (snapshot == null) return null;
			job = new SurgicalClientJob<>("placement(prey topology)", () -> measure(prey.get(index), snapshot));
		}
		var topology = job.poll();
		if (topology == null) return null;
		measured.add(topology);
		job = null;
		if (measured.size() == prey.size()) expanded = DevouredDonorTopology.expand(boxed, measured);
		return expanded;
	}

	private DevouredDonorTopology measure(SurgicalAssembly.Source source, SurgicalModelRenderContext.Snapshot snapshot) {
		int count = snapshot.observedCubeCount();
		if (!SurgicalAssembly.validCubeCount(count) || snapshot.cubes().size() != count) return null;
		var centers = new Vec3[count];
		var pose = boxed.layoutLayPose();
		var rotation = source.cubeRotations().getOrDefault(0, SurgicalCubeRotation.IDENTITY);
		var pivot = new Vec3(0, source.profile().devouredHeight() / 2d, 0);
		double lowest = Double.POSITIVE_INFINITY;
		for (var cube : snapshot.cubes()) {
			if (cube.cubeId() < 0 || cube.cubeId() >= count || centers[cube.cubeId()] != null) return null;
			centers[cube.cubeId()] = cube.corners().stream().reduce(Vec3.ZERO, Vec3::add).scale(1d / 8);
			for (Vec3 point : cube.corners()) {
				Vec3 fromPivot = source.layPose().rotate(point.subtract(pivot));
				lowest = Math.min(lowest, pose.inverseRotate(rotation.rotate(fromPivot)).y);
			}
		}
		var anatomy = SurgicalPlacementController.discoveredAnatomy(snapshot.cubes(), count);
		if (!anatomy.hasVolumes()) return null;
		return new DevouredDonorTopology(source.donorId(), Arrays.asList(centers),
			SurgicalClientTopology.buildContactTopology(count, snapshot.cubes()).seams(), anatomy, lowest);
	}
}
