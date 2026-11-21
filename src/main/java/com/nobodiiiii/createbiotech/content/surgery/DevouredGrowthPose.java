package com.nobodiiiii.createbiotech.content.surgery;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.slimemimic.MimicProfile;
import com.nobodiiiii.createbiotech.entity.BionicRiding;
import com.nobodiiiii.createbiotech.entity.trait.BionicAnatomySnapshot;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Upright body-space rotation and the corresponding attachment point in native prey coordinates. */
public record DevouredGrowthPose(SurgicalCubeRotation rotation, Vec3 anchor, double burial) {
	private static final Vec3 UP = new Vec3(0, 1, 0);
	private static final Vec3 FORWARD = new Vec3(0, 0, 1);

	@Nullable
	public static DevouredGrowthPose measured(SurgicalAssembly.Source source, List<Vec3> centers,
		BionicAnatomySnapshot anatomy, @Nullable Vec3 headCenter) {
		MimicProfile profile = source.profile();
		Vec3 normal = profile.devouredSurfaceNormal();
		if (normal == null) return null;
		Vec3 axis = FORWARD;
		if (headCenter != null) {
			Vec3 weighted = Vec3.ZERO;
			double total = 0;
			for (int i = 0; i < centers.size(); i++) {
				double volume = anatomy.volume(i);
				weighted = weighted.add(centers.get(i).scale(volume));
				total += volume;
			}
			if (total > 0) axis = headCenter.subtract(weighted.scale(1 / total));
		}
		return create(profile.devouredWidth(), profile.devouredHeight(), normal, source.donorId(), axis, profile.devouredBurial());
	}

	public static DevouredGrowthPose create(double width, double height, Vec3 normal, UUID donor, Vec3 headAxis,
		double wantedBurial) {
		Vec3 head = headAxis.lengthSqr() > 1e-10 ? headAxis.normalize() : FORWARD;
		// Hanging poses can have the native head almost directly below the torso.
		var rest = head.y < -0.82 ? SurgicalCubeRotation.around(FORWARD, 180) : SurgicalCubeRotation.IDENTITY;
		head = rest.rotate(head);
		var random = RandomSource.create(donor.getMostSignificantBits() ^ donor.getLeastSignificantBits());
		double yaw = normal.horizontalDistanceSqr() < 1e-8 ? random.nextDouble() * 360
			: Math.toDegrees(Math.atan2(normal.x, normal.z)) + random.nextDouble() * 32 - 16;
		double headYaw = head.horizontalDistanceSqr() < 1e-8 ? 0 : Math.toDegrees(Math.atan2(head.x, head.z));
		double elevation = Math.toDegrees(Math.asin(Math.clamp(normal.y, -1, 1)));
		double headElevation = Math.toDegrees(Math.asin(Math.clamp(head.y, -1, 1)));
		// Aim between the native horizontal facing and the actual head axis, leaving both exposed.
		double pitch = Math.clamp(Math.clamp(elevation + random.nextDouble() * 12 - 6, 8, 78) - headElevation / 2, -70, 70);
		Vec3 horizontal = SurgicalCubeRotation.around(UP, yaw).rotate(FORWARD);
		var aim = SurgicalCubeRotation.around(UP, yaw - headYaw).then(SurgicalCubeRotation.around(horizontal.cross(UP), pitch));
		var rotation = rest.then(aim).then(SurgicalCubeRotation.around(aim.rotate(head), random.nextDouble() * 20 - 10));
		Vec3 inward = BionicRiding.inverse(rotation).rotate(normal).scale(-1);
		Vec3 support = new Vec3(Math.signum(inward.x) * width / 2, Math.signum(inward.y) * height / 2,
			Math.signum(inward.z) * width / 2);
		double halfDepth = support.dot(inward);
		double burial = Math.min(wantedBurial, halfDepth);
		return new DevouredGrowthPose(rotation, support.scale(1 - burial / halfDepth), burial);
	}
}
