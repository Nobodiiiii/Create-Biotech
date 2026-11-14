package com.nobodiiiii.createbiotech.entity.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalConnectionGraph;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalCubeRotation;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalClientTopology;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalModelRenderContext;
import com.nobodiiiii.createbiotech.content.surgery.client.SurgicalSourceModelRenderer;
import com.nobodiiiii.createbiotech.entity.SlimeBionicCombat;
import com.nobodiiiii.createbiotech.entity.SlimeBionicEntity;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Arm;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.AttackStyle;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Bone;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Context;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.LegStyle;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Pose;
import com.nobodiiiii.createbiotech.entity.client.animation.SlimeBionicAnimations.Rotation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Internal BionicPoseSolver implementation; public entry points remain on SlimeBionicAnimator. */
final class BionicPoseSolver {
	private BionicPoseSolver() {}


	/**
	 * Resolves one animation frame into per-source cube transforms.
	 *
	 * <p>{@code rig.sources} is indexed like {@link SurgicalAssembly#sources()}; each returned frame
	 * only contains the cubes an installed joint actually moves.</p>
	 */
	static List<SlimeBionicAnimator.Frame> resolve(SlimeBionicEntity entity, SlimeBionicAnimator.Rig rig, float partialTick) {
		SurgicalAssembly assembly = rig.assembly;
		List<SlimeBionicAnimator.SourceState> sources = rig.sources;
		List<SlimeBionicAnimator.ResolvedLimb> limbs = rig.limbs;
		int sourceCount = sources.size();
		List<SlimeBionicAnimator.Frame> frames = new ArrayList<>(sourceCount);
		for (int source = 0; source < sourceCount; source++)
			frames.add(SlimeBionicAnimator.Frame.EMPTY);
		boolean weaponAttack = entity.isAttackAnimationWeapon();
		Arm preferredAttackArm = entity.isAttackAnimationLeft() ? Arm.LEFT : Arm.RIGHT;
		Arm attackArm = BionicRigBuilder.attackArm(limbs, preferredAttackArm);
		// The presentation target is read only while an authored attack is running; idle bodies skip
		// the sector query behind it.
		SlimeBionicAnimator.AttackAnimationTarget attackTarget = entity.getAttackAnimationTick() > 0
			? attackAnimationTarget(entity, assembly) : SlimeBionicAnimator.AttackAnimationTarget.NONE;
		Context context = animationContext(entity, partialTick, rig,
			attackArm, attackArmHasElbow(limbs, attackArm, entity.getAttackAnimationArmSlot()),
			weaponAttack ? AttackStyle.WEAPON : AttackStyle.EMPTY_HAND, attackTarget);
		Pose pose = SlimeBionicAnimations.sample(context);

		Map<Integer, Map<Integer, Vec3>> offsets = new HashMap<>();
		Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations = new HashMap<>();
		Rotation bodyPose = pose.rotation(Bone.BODY);
		SurgicalCubeRotation bodyRotation = SlimeBionicAnimator.BODY_SPACE.reframe(SurgicalCubeRotation.IDENTITY,
			bodyPose.z(), bodyPose.y(), bodyPose.x());
		Vec3 pivot = rig.bodyPivot;
		SlimeBionicAnimator.Transform swimTransform = swimmingTransform(context, pivot, rig.restSwimPitch);
		SlimeBionicAnimator.Transform bodyTransform = swimTransform.rotateAround(swimTransform.apply(pivot),
			conjugate(swimTransform.rotation(), bodyRotation));
		boolean anchored = entity.getBlockAttachment().locksMovement();
		var anchorCarrier = anchored ? entity.getBlockAttachment().carrier() : null;
		if (entity.isVehicle()) {
			var carrier = entity.getBionicEquipment().saddleCarrier();
			SlimeBionicAnimator.CubeBox seatBox = carrier == null ? null : BionicRigBuilder.box(sources, new SlimeBionicAnimator.Member(carrier.source(), carrier.cube()));
			if (seatBox != null) {
				// Mounted torso motion must be reproducible by server-side passenger physics.
				swimTransform = SlimeBionicAnimator.Transform.IDENTITY.rotateAround(seatBox.center(),
					com.nobodiiiii.createbiotech.entity.BionicRiding.bodyRotation(entity, partialTick));
				bodyTransform = swimTransform;
			}
		}
		if (anchored) bodyTransform = swimTransform = SlimeBionicAnimator.Transform.IDENTITY;
		if (!bodyTransform.isIdentity() || !swimTransform.isIdentity()) {
			Set<SlimeBionicAnimator.Member> legMembers = rig.legMembers;
			for (int source = 0; source < sources.size(); source++)
				for (int cube : sources.get(source).boxes().keySet()) {
					SlimeBionicAnimator.Member member = new SlimeBionicAnimator.Member(source, cube);
					applyTransform(member, legMembers.contains(member) ? swimTransform : bodyTransform,
						sources, offsets, rotations);
				}
		}
		SlimeBionicAnimator.Transform[] transforms = new SlimeBionicAnimator.Transform[limbs.size()];
		Set<SurgicalAssembly.CombinationMember> wingMembers = entity.getBionicTraits().wingMembers();
		boolean hasWings = !wingMembers.isEmpty();
		// Parents are solved and applied before their children, so a child's write lands last without
		// tracking which limb wrote each cube.
		for (int limbIndex : rig.applyOrder) {
			SlimeBionicAnimator.ResolvedLimb limb = limbs.get(limbIndex);
			// A base remains rigid even if it was installed in an animated arm, head or leg.
			if (anchorCarrier != null && limb.members().contains(new SlimeBionicAnimator.Member(anchorCarrier.source(), anchorCarrier.cube())))
				continue;
			if (limb.bone() == null && limb.gait() == null && !(context.swimAmount() > 0.0f
				&& (limb.type() == SurgicalLimbType.HIP || limb.type() == SurgicalLimbType.KNEE)))
				continue;
			SlimeBionicAnimator.Transform transform = resolveTransform(limbIndex, rig, pose, context, wingMembers, hasWings,
				bodyTransform, swimTransform, transforms);
			if (transform.isIdentity())
				continue;
			for (SlimeBionicAnimator.Member member : limb.members())
				applyTransform(member, transform, sources, offsets, rotations);
		}
		for (int source = 0; source < sourceCount; source++) {
			Map<Integer, Vec3> sourceOffsets = offsets.get(source);
			Map<Integer, SurgicalCubeRotation> sourceRotations = rotations.get(source);
			if (sourceOffsets != null || sourceRotations != null)
				frames.set(source, new SlimeBionicAnimator.Frame(
					sourceOffsets == null ? Map.of() : sourceOffsets,
					sourceRotations == null ? Map.of() : sourceRotations));
		}
		return frames;
	}

	/** Lay an upright head/torso axis forward; already horizontal aquatic bodies keep their rest axis. */
	static float restSwimPitch(List<SlimeBionicAnimator.ResolvedLimb> limbs, List<SlimeBionicAnimator.SourceState> sources, Vec3 pivot) {
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs) {
			if (limb.type() != SurgicalLimbType.NECK)
				continue;
			Vec3 head = BionicRigBuilder.groupCenter(limb.members(), sources);
			if (head == null)
				continue;
			Vec3 direction = head.subtract(pivot);
			return Mth.clamp((float) Mth.atan2(-SlimeBionicAnimator.BODY_SPACE.project(direction, SlimeBionicAnimator.AXIS_Y),
				-SlimeBionicAnimator.BODY_SPACE.project(direction, SlimeBionicAnimator.AXIS_Z)), -Mth.HALF_PI, Mth.HALF_PI);
		}
		return 0.0f;
	}

	static SlimeBionicAnimator.Transform swimmingTransform(Context context, Vec3 pivot, float restPitch) {
		float amount = context.swimAmount();
		if (amount <= 0.0f)
			return SlimeBionicAnimator.Transform.IDENTITY;
		float moving = context.swimMotion();
		float hoveringPitch = -0.15f + 0.075f * Mth.cos(context.ageInTicks() * 0.075f);
		float movingPitch = Mth.lerp(context.partialTick(), context.entity().xRotO,
			context.entity().getXRot()) * Mth.DEG_TO_RAD
			+ 0.13f * Mth.sin(context.ageInTicks() * 0.33f);
		float pitch = (restPitch + Mth.lerp(moving, hoveringPitch, movingPitch)) * amount;
		SurgicalCubeRotation rotation = SlimeBionicAnimator.BODY_SPACE.reframe(SurgicalCubeRotation.IDENTITY, 0, 0, pitch);
		return SlimeBionicAnimator.Transform.IDENTITY.rotateAround(pivot, rotation);
	}

	/** Torso attack rotation excludes hip and knee groups so planted legs remain stable. */
	static Set<SlimeBionicAnimator.Member> legMembers(List<SlimeBionicAnimator.ResolvedLimb> limbs) {
		Set<SlimeBionicAnimator.Member> members = new HashSet<>();
		for (SlimeBionicAnimator.ResolvedLimb limb : limbs)
			if (limb.type() == SurgicalLimbType.HIP || limb.type() == SurgicalLimbType.KNEE)
				members.addAll(limb.members());
		return members;
	}

	static void applyTransform(SlimeBionicAnimator.Member member, SlimeBionicAnimator.Transform transform, List<SlimeBionicAnimator.SourceState> sources,
		Map<Integer, Map<Integer, Vec3>> offsets,
		Map<Integer, Map<Integer, SurgicalCubeRotation>> rotations) {
		if (member.source() < 0 || member.source() >= sources.size())
			return;
		SlimeBionicAnimator.SourceState state = sources.get(member.source());
		SlimeBionicAnimator.CubeBox box = state.boxes().get(member.cube());
		if (box == null)
			return;
		// The measured centre already includes its static offset. The animation frame therefore
		// contributes only the hierarchical centre delta plus the inherited final orientation.
		Vec3 staticOffset = state.offsets().getOrDefault(member.cube(), Vec3.ZERO);
		SurgicalCubeRotation staticRotation = state.rotations()
			.getOrDefault(member.cube(), SurgicalCubeRotation.IDENTITY);
		offsets.computeIfAbsent(member.source(), ignored -> new HashMap<>())
			.put(member.cube(), staticOffset.add(transform.apply(box.center()).subtract(box.center())));
		rotations.computeIfAbsent(member.source(), ignored -> new HashMap<>())
			.put(member.cube(), staticRotation.then(transform.rotation()));
	}

	/**
	 * Converts the server-selected logical sector into a presentation target for the hand path. The
	 * gameplay query never observes this value, so damage timing and collision stay animation-free.
	 */
	static SlimeBionicAnimator.AttackAnimationTarget attackAnimationTarget(SlimeBionicEntity entity,
		SurgicalAssembly assembly) {
		float aimYaw = entity.getAttackAimYaw();
		float aimPitch = entity.getAttackAimPitch();
		SurgicalAssembly.AttackGeometry geometry = assembly.attackGeometry();
		SurgicalAssembly.ArmAttackGeometry arm = geometry == null ? null
			: geometry.arm(entity.isAttackAnimationLeft(), entity.getAttackAnimationArmSlot());
		if (arm == null)
			return new SlimeBionicAnimator.AttackAnimationTarget(aimYaw, aimPitch, 0.0f);

		float attackBodyYaw = entity.getAttackBodyYaw();
		SlimeBionicCombat.AngularRange range = SlimeBionicCombat.attackRange(arm);
		if (range.isEmpty())
			return new SlimeBionicAnimator.AttackAnimationTarget(aimYaw, aimPitch, 0.0f);
		float referencePitch = arm.restDirection() == null
			? 0.0f : SlimeBionicAnimator.HANGING_ARM_REFERENCE_CENTER_PITCH;
		return new SlimeBionicAnimator.AttackAnimationTarget(attackBodyYaw + range.centerYaw(),
			range.centerPitch(), referencePitch);
	}

	/** Reports whether the exact server-selected upper arm owns a linked elbow joint. */
	static boolean attackArmHasElbow(List<SlimeBionicAnimator.ResolvedLimb> limbs, Arm arm, int slot) {
		if (arm == Arm.NONE)
			return false;
		boolean left = arm == Arm.LEFT;
		for (int shoulderIndex = 0; shoulderIndex < limbs.size(); shoulderIndex++) {
			SlimeBionicAnimator.ResolvedLimb shoulder = limbs.get(shoulderIndex);
			if (shoulder.type() != SurgicalLimbType.SHOULDER || shoulder.arm() == null
				|| shoulder.arm().left() != left || shoulder.arm().slot() != slot)
				continue;
			for (SlimeBionicAnimator.ResolvedLimb candidate : limbs)
				if (candidate.type() == SurgicalLimbType.ELBOW
					&& candidate.parentIndex() == shoulderIndex)
					return true;
			return false;
		}
		return false;
	}

	static SlimeBionicAnimator.Transform resolveTransform(int index, SlimeBionicAnimator.Rig rig, Pose pose,
		Context context, Set<SurgicalAssembly.CombinationMember> wingMembers, boolean hasWings,
		SlimeBionicAnimator.Transform bodyTransform, SlimeBionicAnimator.Transform swimTransform, SlimeBionicAnimator.Transform[] cache) {
		if (cache[index] != null)
			return cache[index];
		List<SlimeBionicAnimator.ResolvedLimb> limbs = rig.limbs;
		SlimeBionicAnimator.ResolvedLimb limb = limbs.get(index);
		SlimeBionicAnimator.Transform parent = limb.parentIndex() < 0
			? inheritsBodyRotation(limb) ? bodyTransform : swimTransform
			: resolveTransform(limb.parentIndex(), rig, pose, context, wingMembers, hasWings, bodyTransform,
				swimTransform, cache);
		SlimeBionicAnimator.Member wing = !hasWings || limb.arm() == null ? null : limb.members().stream()
			.filter(member -> wingMembers.contains(
				new SurgicalAssembly.CombinationMember(member.source(), member.cube())))
			.findFirst().orElse(null);
		if (wing != null) {
			var source = rig.assembly.sources().get(wing.source());
			SlimeBionicAnimator.WingHinge hinge = rig.wingHinges.computeIfAbsent(index, ignored -> wingHinge(rig, limb, wing));
			Rotation flight = BionicWingAnimation.sample(source.profile(),
				source.anatomy().parts().getOrDefault(wing.cube(), Set.of()), limb.arm().left(), context.ageInTicks());
			Vec3 channel = BionicWingAnimation.flapAxis(source.profile());
			double angle = flight.x() * channel.x + flight.y() * channel.y + flight.z() * channel.z;
			SurgicalCubeRotation delta = SurgicalCubeRotation.around(hinge.end().subtract(hinge.start()),
				Math.toDegrees(angle));
			SlimeBionicAnimator.Transform resolved = parent.rotateAround(parent.apply(hinge.start()), conjugate(parent.rotation(), delta));
			cache[index] = resolved;
			return resolved;
		}
		boolean mirrorAttackAcrossHorizontal = mirrorsUpwardArmAttack(index, limbs, context);
		Rotation sampled = limb.gait() != null ? SlimeBionicAnimations.sampleLeg(context,
				limb.type() == SurgicalLimbType.KNEE, limb.gait().style(),
				limb.gait().left(), limb.gait().phase())
			: limb.arm() != null ? SlimeBionicAnimations.sampleArm(context,
				pose.rotation(limb.bone()), limb.type() == SurgicalLimbType.ELBOW,
				limb.arm().left(), limb.arm().slot(), limb.arm().phase(),
				mirrorAttackAcrossHorizontal)
			: context.swimAmount() > 0.0f && (limb.type() == SurgicalLimbType.HIP
				|| limb.type() == SurgicalLimbType.KNEE)
				? SlimeBionicAnimations.swimmingLimb(context, limb.type() == SurgicalLimbType.KNEE,
					false, limb.side() > 0, (float) limb.longitudinal()).scale(context.swimAmount())
				: pose.rotation(limb.bone());
		SurgicalCubeRotation local = limb.gait() != null
			&& limb.gait().style() == LegStyle.SPIDER && limb.type() == SurgicalLimbType.HIP
			&& context.swimAmount() <= 0.0f
				? SlimeBionicAnimator.BODY_SPACE.spiderReframe(limb.restDirection(), limb.gait().left(),
					sampled.z(), sampled.y())
				: mirrorAttackAcrossHorizontal
					? SlimeBionicAnimator.BODY_SPACE.mirroredArmReframe(limb.type(), limb.restDirection(),
						sampled.z(), sampled.y(), sampled.x())
				: SlimeBionicAnimator.BODY_SPACE.reframe(limb.restAlignment(),
					sampled.z(), sampled.y(), sampled.x());
		SurgicalCubeRotation inheritedLocal;
		if (carriesAuthoredAttack(limb, context)) {
			// The torso and shoulder tracks were authored as one canonical humanoid motion. Retarget
			// their composite together, then express it after the already applied torso. An upward arm
			// uses the exact horizontal reflection of the equivalent hanging-arm transform.
			SurgicalCubeRotation retargeted = mirrorAttackAcrossHorizontal
				? SlimeBionicAnimator.BODY_SPACE.mirroredArmReframeComposite(limb.type(), limb.restDirection(),
					pose.rotation(Bone.BODY), sampled)
				: SlimeBionicAnimator.BODY_SPACE.reframeComposite(limb.restAlignment(),
					pose.rotation(Bone.BODY), sampled);
			inheritedLocal = deltaAfter(parent.rotation(), retargeted.then(swimTransform.rotation()));
		} else {
			inheritedLocal = conjugate(parent.rotation(), local);
		}
		SlimeBionicAnimator.Transform resolved = parent.rotateAround(parent.apply(limb.pivot()), inheritedLocal);
		cache[index] = resolved;
		return resolved;
	}

	/** Measures the actual seam endpoints once, retaining source-axis direction after surgical placement. */
	static SlimeBionicAnimator.WingHinge wingHinge(SlimeBionicAnimator.Rig rig, SlimeBionicAnimator.ResolvedLimb limb, SlimeBionicAnimator.Member wing) {
		var profile = rig.assembly.sources().get(wing.source()).profile();
		Vec3 channel = BionicWingAnimation.flapAxis(profile);
		Vec3 axis = SlimeBionicAnimator.BODY_SPACE.point(channel.x, channel.y, channel.z);
		SlimeBionicAnimator.CubeBox wingBox = BionicRigBuilder.box(rig.sources, wing);
		var original = SurgicalSourceModelRenderer.singleCubeGeometry(profile, wing.cube());
		if (original != null && wingBox != null) {
			Vec3 origin = BionicAttackGeometryBaker.transformCubePoint(Vec3.ZERO, original.corners(), wingBox.points());
			Vec3 installedAxis = BionicAttackGeometryBaker.transformCubePoint(axis, original.corners(), wingBox.points()).subtract(origin);
			if (installedAxis.lengthSqr() > SlimeBionicAnimator.GEOMETRY_EPSILON)
				axis = installedAxis.normalize();
		}
		SurgicalConnectionGraph<Integer> graph = BionicRigBuilder.connectionGraph(rig.assembly);
		SlimeBionicAnimator.Connection connection = graph == null ? null : BionicRigBuilder.connection(graph, limb.anchor(), limb.parent(),
			limb.members(), BionicRigBuilder.group(rig.assembly, limb.parent().source(), limb.parent().cube()));
		SlimeBionicAnimator.CubeBox child = BionicRigBuilder.box(rig.sources, connection == null ? limb.anchor() : connection.child());
		SlimeBionicAnimator.CubeBox parent = BionicRigBuilder.box(rig.sources, connection == null ? limb.parent() : connection.parent());
		return child == null || parent == null ? new SlimeBionicAnimator.WingHinge(limb.pivot(), limb.pivot().add(axis))
			: wingHinge(child, parent, axis, limb.pivot());
	}

	/**
	 * Uses an edge of the clipped contact boundary as a fixed hinge line. Native-channel alignment
	 * selects the span; proximity to the exposed wing selects the body's exit edge when cubes overlap.
	 * Both endpoints then remain on the parent throughout the flap, including for tilted/flat wings.
	 */
	static SlimeBionicAnimator.WingHinge wingHinge(SlimeBionicAnimator.CubeBox child, SlimeBionicAnimator.CubeBox parent, Vec3 nativeAxis, Vec3 restPivot) {
		var edges = SurgicalClientTopology.intersectionEdges(
			new SurgicalModelRenderContext.CubeGeometry(0, child.points()),
			new SurgicalModelRenderContext.CubeGeometry(1, parent.points()));
		SlimeBionicAnimator.WingHinge best = null;
		double bestAlignment = -1.0d;
		double bestWingDistance = Double.POSITIVE_INFINITY;
		double bestPivotDistance = Double.POSITIVE_INFINITY;
		for (var edge : edges) {
			Vec3 span = edge.end().subtract(edge.start());
			if (span.lengthSqr() <= SlimeBionicAnimator.GEOMETRY_EPSILON)
				continue;
			Vec3 axis = span.normalize();
			double alignment = Math.abs(axis.dot(nativeAxis));
			double wingDistance = distanceToLineSqr(child.center(), edge.start(), axis);
			double pivotDistance = distanceToLineSqr(restPivot, edge.start(), axis);
			if (alignment > bestAlignment + SlimeBionicAnimator.GEOMETRY_EPSILON
				|| Math.abs(alignment - bestAlignment) <= SlimeBionicAnimator.GEOMETRY_EPSILON
					&& (wingDistance < bestWingDistance - SlimeBionicAnimator.GEOMETRY_EPSILON
						|| Math.abs(wingDistance - bestWingDistance) <= SlimeBionicAnimator.GEOMETRY_EPSILON
							&& pivotDistance < bestPivotDistance - SlimeBionicAnimator.GEOMETRY_EPSILON)) {
				best = axis.dot(nativeAxis) >= 0 ? new SlimeBionicAnimator.WingHinge(edge.start(), edge.end())
					: new SlimeBionicAnimator.WingHinge(edge.end(), edge.start());
				bestAlignment = alignment;
				bestWingDistance = wingDistance;
				bestPivotDistance = pivotDistance;
			}
		}
		if (best != null)
			return best;
		// Legacy glue gaps have no shared boundary. Keep a line through the connecting region.
		Vec3 center = child.contactCenter(parent, SlimeBionicAnimator.BODY_SPACE);
		return new SlimeBionicAnimator.WingHinge(center, center.add(nativeAxis));
	}

	static double distanceToLineSqr(Vec3 point, Vec3 origin, Vec3 unitAxis) {
		Vec3 relative = point.subtract(origin);
		return relative.subtract(unitAxis.scale(relative.dot(unitAxis))).lengthSqr();
	}

	/** True for the attacking shoulder and the one opposite shoulder carrying its balance track. */
	static boolean carriesAuthoredAttack(SlimeBionicAnimator.ResolvedLimb limb, Context context) {
		return limb.type() == SurgicalLimbType.SHOULDER
			&& carriesAuthoredArmChannel(limb, context);
	}

	/** True for either joint in the selected attack chain or its authored balancing chain. */
	static boolean carriesAuthoredArmChannel(SlimeBionicAnimator.ResolvedLimb limb, Context context) {
		if ((limb.type() != SurgicalLimbType.SHOULDER
			&& limb.type() != SurgicalLimbType.ELBOW) || limb.arm() == null
			|| context.attackArm() == Arm.NONE || context.attackAnimationTick() <= 0)
			return false;
		boolean attackingSide = (context.attackArm() == Arm.LEFT) == limb.arm().left();
		return attackingSide
			? limb.arm().slot() == context.attackArmSlot()
			: limb.arm().slot() == 0;
	}

	/**
	 * Upward attack chains copy the corresponding hanging chain across the shoulder's horizontal
	 * attachment plane. The shoulder decides the mode for its elbow as well, so the two bones cannot
	 * choose incompatible frames when the forearm happens to fold back across the plane at rest.
	 */
	static boolean mirrorsUpwardArmAttack(int index, List<SlimeBionicAnimator.ResolvedLimb> limbs,
		Context context) {
		SlimeBionicAnimator.ResolvedLimb limb = limbs.get(index);
		if (!carriesAuthoredArmChannel(limb, context))
			return false;
		int shoulderIndex = limb.type() == SurgicalLimbType.SHOULDER ? index : limb.parentIndex();
		return shoulderIndex >= 0 && shoulderIndex < limbs.size()
			&& SlimeBionicAnimator.BODY_SPACE.pointsUp(limbs.get(shoulderIndex).restDirection());
	}

	/** Rotation which, when applied after {@code parent}, leaves {@code target} as the result. */
	static SurgicalCubeRotation deltaAfter(SurgicalCubeRotation parent,
		SurgicalCubeRotation target) {
		if (parent.isIdentity())
			return target;
		SurgicalCubeRotation inverse = new SurgicalCubeRotation(
			-parent.x(), -parent.y(), -parent.z(), parent.w());
		return inverse.then(target);
	}

	static boolean inheritsBodyRotation(SlimeBionicAnimator.ResolvedLimb limb) {
		return limb.type() != SurgicalLimbType.HIP && limb.type() != SurgicalLimbType.KNEE;
	}

	/** Changes a child-local rotation into the already rotated parent frame. */
	static SurgicalCubeRotation conjugate(SurgicalCubeRotation parent,
		SurgicalCubeRotation local) {
		if (parent.isIdentity() || local.isIdentity())
			return local;
		SurgicalCubeRotation inverse = new SurgicalCubeRotation(
			-parent.x(), -parent.y(), -parent.z(), parent.w());
		return inverse.then(local).then(parent);
	}

	static int hierarchyDepth(int index, List<SlimeBionicAnimator.ResolvedLimb> limbs) {
		int depth = 0;
		for (int cursor = limbs.get(index).parentIndex(); cursor >= 0 && depth < limbs.size();
			cursor = limbs.get(cursor).parentIndex())
			depth++;
		return depth;
	}


	/** Adapts entity state to the animation-only module's narrow, immutable input contract. */
	static Context animationContext(SlimeBionicEntity entity, float partialTick, SlimeBionicAnimator.Rig rig,
		Arm attackArm, boolean attackArmHasElbow, AttackStyle attackStyle,
		SlimeBionicAnimator.AttackAnimationTarget attackTarget) {
		float bodyRot = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		float headRot = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
		float netHeadYaw = Mth.wrapDegrees(headRot - bodyRot);
		float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		float swimAmount = entity.getSwimAmount(partialTick);
		headPitch *= 1.0f - swimAmount;
		float ageInTicks = entity.tickCount + partialTick;
		float limbSwing = 0.0f;
		float limbSwingAmount = 0.0f;
		float walkWeight = 0.0f;
		float vanillaLimbSwing = 0.0f;
		float vanillaLimbSwingAmount = 0.0f;
		if (!entity.isPassenger() && entity.isAlive()) {
			vanillaLimbSwing = entity.walkAnimation.position(partialTick);
			vanillaLimbSwingAmount = Math.min(entity.walkAnimation.speed(partialTick), 1.0f);
			// WalkAnimation.position keeps accumulating at the full movement-derived speed. Capping only
			// the amount therefore converts speed beyond this point into faster steps, not wider swings.
			float maximumAmount = rig.maximumSwingAmount;
			limbSwingAmount = Math.min(vanillaLimbSwingAmount, maximumAmount);
			walkWeight = maximumAmount <= 0.0f ? 0.0f : limbSwingAmount / maximumAmount;
			limbSwing = vanillaLimbSwing * rig.animationFrequencyScale;
		}
		limbSwingAmount *= 1.0f - swimAmount;
		walkWeight *= 1.0f - swimAmount;
		return new Context(entity, limbSwing, limbSwingAmount, walkWeight, vanillaLimbSwing,
			vanillaLimbSwingAmount, ageInTicks, netHeadYaw, headPitch,
			entity.getAttackAnim(partialTick), entity.isPassenger(), swimAmount,
			entity.getAttackAnimationTick(), entity.getAttackAnimationDuration(), partialTick,
			bodyRot, attackTarget.yaw(), attackTarget.pitch(), attackTarget.referencePitch(), attackArm,
			entity.getAttackAnimationArmSlot(), attackArmHasElbow, attackStyle);
	}
}
