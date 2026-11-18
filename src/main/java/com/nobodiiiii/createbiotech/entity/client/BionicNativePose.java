package com.nobodiiiii.createbiotech.entity.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalNativeAnimations;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalNativeAnimations.CubePose;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalNativeAnimations.SourcePose;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalTablePoseResolver;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator.Member;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator.ResolvedLimb;
import com.nobodiiiii.createbiotech.entity.client.SlimeBionicAnimator.Transform;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;

import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.animation.definitions.WardenAnimation;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Transfers captured donor motion across the installed limb hierarchy in body space. */
final class BionicNativePose {
	private static final SonicArms SONIC_ARMS = new SonicArms();

	record Attachments(Map<Member, Member> parents, Map<Member, ResolvedLimb> joints,
		List<Transform> sourceFrames) {
		Attachments(SurgicalAssembly assembly, List<ResolvedLimb> limbs) {
			this(parents(limbs), joints(limbs), limbs.isEmpty() ? List.of()
				: assembly.sources().stream().map(source -> sourceFrame(assembly, source)).toList());
		}

		private static Map<Member, Member> parents(List<ResolvedLimb> limbs) {
			Map<Member, Member> parents = new HashMap<>();
			for (ResolvedLimb limb : limbs)
				parents.put(limb.anchor(), limb.parent());
			for (ResolvedLimb limb : limbs)
				for (Member member : limb.members())
					if (!member.equals(limb.parent()))
						parents.putIfAbsent(member, limb.anchor());
			return Map.copyOf(parents);
		}

		private static Map<Member, ResolvedLimb> joints(List<ResolvedLimb> limbs) {
			Map<Member, ResolvedLimb> joints = new HashMap<>();
			for (ResolvedLimb limb : limbs)
				joints.put(limb.anchor(), limb);
			return Map.copyOf(joints);
		}

		private static Transform sourceFrame(SurgicalAssembly assembly, SurgicalAssembly.Source source) {
			if (!assembly.preservesLayout() && assembly.sources().size() == 1)
				return Transform.IDENTITY;
			PoseStack stack = new PoseStack();
			SurgicalTablePoseResolver.applyInverseRotation(stack, assembly.layoutLayPose());
			stack.translate(source.originOffset().x, source.originOffset().y, source.originOffset().z);
			if (assembly.preservesLayout())
				SurgicalTablePoseResolver.resolve(source.layPose()).apply(stack);
			var pose = stack.last().pose();
			Quaternionf q = pose.getNormalizedRotation(new Quaternionf());
			return new Transform(new SurgicalCubeRotation(q.x, q.y, q.z, q.w),
				new Vec3(pose.m30(), pose.m31(), pose.m32()));
		}
	}

	private final SlimeBionicAnimator.Rig rig;
	private final Map<Integer, SourcePose> nativePoses;
	private final Map<Integer, Map<Integer, Vec3>> offsets;
	private final Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations;
	private final Set<CombinationMember> sonicCarriers;
	private final Map<Member, CubeFrame> frames = new HashMap<>();
	private final Map<Member, Transform> corrections = new HashMap<>();
	private final Map<Long, ShoulderPose> sonicPoses = new HashMap<>();

	private BionicNativePose(SlimeBionicAnimator.Rig rig, Map<Integer, SourcePose> nativePoses,
		Map<Integer, Map<Integer, Vec3>> offsets, Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations,
		Set<CombinationMember> sonicCarriers) {
		this.rig = rig;
		this.nativePoses = nativePoses;
		this.offsets = offsets;
		this.rotations = rotations;
		this.sonicCarriers = sonicCarriers;
	}

	static void apply(SlimeBionicEntity entity, float partialTick, SlimeBionicAnimator.Rig rig,
		Map<Integer, Map<Integer, Vec3>> offsets, Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations) {
		if (rig.nativeAttachments.parents().isEmpty()) return;
		Map<Integer, SourcePose> nativePoses = SurgicalNativeAnimations.poses(entity, partialTick);
		if (nativePoses.isEmpty()) return;
		BionicNativePose pose = new BionicNativePose(rig, nativePoses, offsets, rotations,
			entity.getBionicTraits().members(BionicTrait.SONIC_BOOM));
		// Resolve every correction against the original procedural frame before changing its maps.
		for (Member member : rig.nativeAttachments.parents().keySet())
			pose.correction(member);
		pose.corrections.forEach(pose::applyCorrection);
	}

	private Transform correction(Member member) {
		Transform cached = corrections.get(member);
		if (cached != null) return cached;
		Member parent = rig.nativeAttachments.parents().get(member);
		if (parent == null || frame(member) == null) return Transform.IDENTITY;
		// Mark the current link before following parent links in case a saved attachment loops.
		corrections.put(member, Transform.IDENTITY);
		CubePose ownPose = nativePose(member);
		CubePose parentPose = nativePose(parent);
		ResolvedLimb joint = rig.nativeAttachments.joints().get(member);
		// A leg starts a separate pose chain at its hip. The baked motion check below still
		// removes the torso's movement when the donor model already applied it to this leg.
		Transform inherited = joint != null && joint.type() == SurgicalLimbType.HIP
			? Transform.IDENTITY : motion(parent, parentPose).then(correction(parent));
		long sonicTime = joint != null && joint.type() == SurgicalLimbType.SHOULDER && joint.arm() != null
			? sonicTime(parent, parentPose) : -1;
		Transform baked = Transform.IDENTITY;
		if (sonicTime >= 0) {
			inherited = shoulderMotion(member, joint, sonicTime).then(inherited);
			// The installed shoulder owns the arm track. Remove whatever motion is already in
			// this cube's captured appearance before playing that track once at the actual joint.
			baked = motion(member, ownPose);
		} else if (member.source() == parent.source() && ownPose != null && parentPose != null
			&& ownPose.inherits(parentPose)) {
			baked = motion(member, parentPose);
		}
		Transform correction = baked.inverse().then(inherited);
		corrections.put(member, correction);
		return correction;
	}

	@Nullable
	private CubePose nativePose(Member member) {
		SourcePose source = nativePoses.get(member.source());
		return source == null ? null : source.cubes().get(member.cube());
	}

	private long sonicTime(Member parent, @Nullable CubePose parentPose) {
		SourcePose source = nativePoses.get(parent.source());
		if (source == null || source.sonicTimeMillis() < 0) return -1;
		for (CombinationMember carrier : sonicCarriers) {
			if (carrier.source() != parent.source()) continue;
			CubePose carrierPose = source.cubes().get(carrier.cube());
			if (carrier.cube() == parent.cube()
				|| (parentPose != null && carrierPose != null && parentPose.inherits(carrierPose)))
				return source.sonicTimeMillis();
		}
		return -1;
	}

	private Transform shoulderMotion(Member member, ResolvedLimb shoulder, long millis) {
		ShoulderPose pose = sonicPoses.computeIfAbsent(millis, SONIC_ARMS::sample);
		PartPose arm = shoulder.arm().left() ? pose.left() : pose.right();
		var basis = SlimeBionicAnimator.BODY_SPACE;
		SurgicalCubeRotation turn = basis.reframe(shoulder.restAlignment(), arm.zRot, arm.yRot, arm.xRot);
		Vec3 shift = basis.point(arm.x, arm.y, arm.z).scale(1.0 / 16.0);
		Vec3 pivot = shoulder.pivot();
		Transform local = new Transform(turn, pivot.add(shift).subtract(turn.rotate(pivot)));
		Transform animation = frame(member).animation();
		return animation.inverse().then(local).then(animation);
	}

	private Transform motion(Member member, @Nullable CubePose pose) {
		if (pose == null) return Transform.IDENTITY;
		Transform donor = new Transform(pose.rotation(), pose.translation());
		if (donor.isIdentity()) return Transform.IDENTITY;
		CubeFrame frame = frame(member);
		if (frame == null) return Transform.IDENTITY;
		Transform placement = frame.placement();
		return placement.inverse().then(donor).then(placement);
	}

	@Nullable
	private CubeFrame frame(Member member) {
		return frames.computeIfAbsent(member, key -> {
			var state = rig.sources.get(key.source());
			var box = state.boxes().get(key.cube());
			if (box == null) return null;
			Vec3 restOffset = state.offsets().getOrDefault(key.cube(), Vec3.ZERO);
			SurgicalCubeRotation restRotation = state.rotations()
				.getOrDefault(key.cube(), SurgicalCubeRotation.IDENTITY);
			Vec3 offset = offsets.getOrDefault(key.source(), Map.of()).getOrDefault(key.cube(), restOffset);
			SurgicalCubeRotation rotation = rotations.getOrDefault(key.source(), Map.of())
				.getOrDefault(key.cube(), restRotation);
			Vec3 originalCenter = box.center().subtract(restOffset);
			Vec3 center = originalCenter.add(offset);
			Transform placement = rig.nativeAttachments.sourceFrames().get(key.source())
				.then(new Transform(rotation, center.subtract(rotation.rotate(originalCenter))));
			SurgicalCubeRotation animatedTurn = BionicPoseSolver.deltaAfter(restRotation, rotation);
			Transform animation = new Transform(animatedTurn, center.subtract(animatedTurn.rotate(box.center())));
			return new CubeFrame(center, offset, rotation, placement, animation);
		});
	}

	private void applyCorrection(Member member, Transform correction) {
		if (correction.isIdentity()) return;
		CubeFrame frame = frame(member);
		offsets.computeIfAbsent(member.source(), unused -> new HashMap<>())
			.put(member.cube(), frame.offset().add(correction.apply(frame.center()).subtract(frame.center())));
		rotations.computeIfAbsent(member.source(), unused -> new HashMap<>())
			.put(member.cube(), frame.rotation().then(correction.rotation()));
	}

	private record CubeFrame(Vec3 center, Vec3 offset, SurgicalCubeRotation rotation,
		Transform placement, Transform animation) {}

	private record ShoulderPose(PartPose left, PartPose right) {}

	/** Samples vanilla's two arm tracks with its own interpolator. */
	private static final class SonicArms extends HierarchicalModel<Entity> {
		private final Vector3f scratch = new Vector3f();
		private final ModelPart left = new ModelPart(List.of(), Map.of());
		private final ModelPart right = new ModelPart(List.of(), Map.of());
		private final ModelPart root = new ModelPart(List.of(), Map.of("left_arm", left, "right_arm", right));

		private ShoulderPose sample(long millis) {
			left.resetPose();
			right.resetPose();
			KeyframeAnimations.animate(this, WardenAnimation.WARDEN_SONIC_BOOM, millis, 1.0f, scratch);
			return new ShoulderPose(left.storePose(), right.storePose());
		}

		@Override
		public ModelPart root() { return root; }

		@Override
		public void setupAnim(Entity entity, float walk, float amount, float age, float yaw, float pitch) {}
	}
}
