package com.nobodiiiii.createbiotech.entity.client.animation;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Samples the complete client-side pose for a bionic slime.
 *
 * <p>This module deliberately knows nothing about surgical cubes, connections, pivots or rendering.
 * Its sole contract is an animation context in and one local rotation per anatomical bone out. The
 * solver can therefore keep using these poses if the animation catalogue later grows into state
 * machines or authored keyframes.</p>
 *
 * <p>The two-level arm and leg layout follows Maledictus's exact 1.21.1 model and walk-animation
 * structure in
 * {@code ref/1.21.1/Cataclysm/src/main/java/com/github/L_Ender/cataclysm/client/model/entity/Maledictus_Model.java}
 * and
 * {@code ref/1.21.1/Cataclysm/src/main/java/com/github/L_Ender/cataclysm/client/animation/Maledictus_Animation.java}:
 * front arms are children of upper arms, and front legs are children of upper legs. The lightweight
 * bend curves below preserve that hierarchy without copying Cataclysm's boss-specific animation
 * catalogue. Authored attacks live beside this sampler in their own catalogue class so adding more
 * of them does not grow the geometry solver or its integration contract.</p>
 */
public final class SlimeBionicAnimations {
	private static final float WALK_PHASE_SCALE = 0.6662f;
	private static final float MIN_WALK_ELBOW_DEGREES = 15.0f;
	private static final float MAX_WALK_ELBOW_DEGREES = 37.5f;
	private static final float MIN_WALK_KNEE_DEGREES = 3.0f;
	private static final float MAX_WALK_KNEE_DEGREES = 51.0f;
	@Nullable
	private static HumanoidModel<LivingEntity> humanoidModel;

	private SlimeBionicAnimations() {}

	public static void clearCache() {
		humanoidModel = null;
	}

	public static Pose sample(Context context) {
		if (context == null || context.entity() == null)
			return Pose.EMPTY;
		HumanoidModel<LivingEntity> model = humanoidModel();
		if (model == null)
			return Pose.EMPTY;

		boolean articulatedAttack = context.attackAnimationTick() > 0
			&& context.attackArm() != Arm.NONE;
		model.attackTime = articulatedAttack ? 0.0f : context.attackTime();
		model.riding = context.riding();
		model.young = false;
		model.crouching = false;
		model.swimAmount = context.swimAmount();
		model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		// Stop at the shared humanoid pass: AbstractZombieModel's raised-arm layer would erase the
		// walking shoulder swing and force a non-neutral rest pose onto every installed arm.
		model.setupAnim(context.entity(), context.limbSwing(), context.limbSwingAmount(),
			context.ageInTicks(), context.netHeadYaw(), context.headPitch());

		EnumMap<Bone, Rotation> rotations = new EnumMap<>(Bone.class);
		rotations.put(Bone.HEAD, Rotation.of(model.head));
		rotations.put(Bone.RIGHT_SHOULDER, Rotation.of(model.rightArm));
		rotations.put(Bone.LEFT_SHOULDER, Rotation.of(model.leftArm));
		addElbowPose(rotations, context);
		addArticulatedAttackPose(rotations, context);
		return new Pose(rotations);
	}

	/** Maledictus-inspired arm flexion, expressed locally beneath each upper arm. */
	private static void addElbowPose(EnumMap<Bone, Rotation> rotations, Context context) {
		float weight = Mth.clamp(context.walkWeight(), 0.0f, 1.0f);
		if (weight <= 0.0f) {
			rotations.put(Bone.RIGHT_ELBOW, Rotation.IDENTITY);
			rotations.put(Bone.LEFT_ELBOW, Rotation.IDENTITY);
			return;
		}

		float phase = context.limbSwing() * WALK_PHASE_SCALE;
		float alternating = Mth.cos(phase);
		float rightElbowDegrees = Mth.lerp((1.0f - alternating) * 0.5f,
			MIN_WALK_ELBOW_DEGREES, MAX_WALK_ELBOW_DEGREES);
		float leftElbowDegrees = Mth.lerp((1.0f + alternating) * 0.5f,
			MIN_WALK_ELBOW_DEGREES, MAX_WALK_ELBOW_DEGREES);

		rotations.put(Bone.RIGHT_ELBOW, Rotation.x(-rightElbowDegrees * Mth.DEG_TO_RAD * weight));
		rotations.put(Bone.LEFT_ELBOW, Rotation.x(-leftElbowDegrees * Mth.DEG_TO_RAD * weight));
	}

	/**
	 * Samples one dynamically assigned leg channel. The caller supplies a stable phase for the
	 * complete hip/knee chain, so this same fixed curve works for every gait from two to eight feet.
	 */
	public static Rotation sampleLeg(Context context, boolean knee, float phaseOffset) {
		if (context == null)
			return Rotation.IDENTITY;
		float weight = Mth.clamp(context.walkWeight(), 0.0f, 1.0f);
		if (weight <= 0.0f)
			return Rotation.IDENTITY;
		float phase = context.limbSwing() * WALK_PHASE_SCALE + phaseOffset;
		if (!knee) {
			float swing = Mth.cos(phase) * 1.4f * context.limbSwingAmount();
			return Rotation.x(swing);
		}
		float bend = Math.max(0.0f, Mth.sin(phase));
		float degrees = Mth.lerp(bend, MIN_WALK_KNEE_DEGREES, MAX_WALK_KNEE_DEGREES);
		return Rotation.x(degrees * Mth.DEG_TO_RAD * weight);
	}

	/** Retimes the selected authored attack to the entity's synced attack-cadence window. */
	private static void addArticulatedAttackPose(EnumMap<Bone, Rotation> rotations,
		Context context) {
		if (context.attackArm() == Arm.NONE || context.attackAnimationTick() <= 0)
			return;
		float duration = Math.max(1.0f, context.attackAnimationDuration());
		float remainingTicks = Mth.clamp(context.attackAnimationTick() - context.partialTick(),
			0.0f, duration);
		float progress = 1.0f - remainingTicks / duration;
		addArticulatedAttackPose(rotations, context.attackArm(), context.attackStyle(), progress);
	}

	/** Samples only the authored attack channels for generation-time combat-path baking. */
	public static Pose sampleAttack(float progress, Arm arm, AttackStyle style) {
		if (arm == null || arm == Arm.NONE || style == null)
			return Pose.EMPTY;
		EnumMap<Bone, Rotation> rotations = new EnumMap<>(Bone.class);
		addArticulatedAttackPose(rotations, arm, style, Mth.clamp(progress, 0.0f, 1.0f));
		return new Pose(rotations);
	}

	private static void addArticulatedAttackPose(EnumMap<Bone, Rotation> rotations,
		Arm arm, AttackStyle style, float progress) {
		SlimeBionicAttackAnimations.AttackPose attack =
			style == AttackStyle.WEAPON
				? SlimeBionicAttackAnimations.weaponSwing(progress)
				: SlimeBionicAttackAnimations.emptyHandGolemSwing(progress);
		Rotation body = attack.body();
		Rotation attackingShoulder = attack.attackingShoulder();
		Rotation attackingElbow = attack.attackingElbow();
		Rotation oppositeShoulder = attack.oppositeShoulder();
		Rotation oppositeElbow = attack.oppositeElbow();
		Bone attackingShoulderBone = Bone.RIGHT_SHOULDER;
		Bone attackingElbowBone = Bone.RIGHT_ELBOW;
		Bone oppositeShoulderBone = Bone.LEFT_SHOULDER;
		Bone oppositeElbowBone = Bone.LEFT_ELBOW;
		if (arm == Arm.LEFT) {
			body = body.mirrorLeft();
			attackingShoulder = attackingShoulder.mirrorLeft();
			attackingElbow = attackingElbow.mirrorLeft();
			oppositeShoulder = oppositeShoulder.mirrorLeft();
			oppositeElbow = oppositeElbow.mirrorLeft();
			attackingShoulderBone = Bone.LEFT_SHOULDER;
			attackingElbowBone = Bone.LEFT_ELBOW;
			oppositeShoulderBone = Bone.RIGHT_SHOULDER;
			oppositeElbowBone = Bone.RIGHT_ELBOW;
		}
		rotations.merge(Bone.BODY, body, Rotation::plus);
		rotations.merge(attackingShoulderBone, attackingShoulder, Rotation::plus);
		rotations.merge(attackingElbowBone, attackingElbow, Rotation::plus);
		// A body without the opposite elbow still consumes its shoulder channel, making the
		// complete rigid arm follow the authored upper-arm pose instead of remaining static.
		rotations.merge(oppositeShoulderBone, oppositeShoulder, Rotation::plus);
		rotations.merge(oppositeElbowBone, oppositeElbow, Rotation::plus);
	}

	@Nullable
	private static HumanoidModel<LivingEntity> humanoidModel() {
		if (humanoidModel != null)
			return humanoidModel;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getEntityModels() == null)
			return null;
		humanoidModel = new HumanoidModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.ZOMBIE));
		return humanoidModel;
	}

	public enum Bone {
		BODY,
		HEAD,
		RIGHT_SHOULDER,
		LEFT_SHOULDER,
		RIGHT_ELBOW,
		LEFT_ELBOW
	}

	/** Which installed articulated arm performs the current one-handed attack. */
	public enum Arm {
		NONE,
		RIGHT,
		LEFT
	}

	public enum AttackStyle {
		EMPTY_HAND,
		WEAPON
	}

	/** All time-varying inputs needed to sample one pose; no assembly or renderer state leaks in. */
	public record Context(LivingEntity entity, float limbSwing, float limbSwingAmount,
		float walkWeight, float ageInTicks, float netHeadYaw, float headPitch, float attackTime,
		boolean riding, float swimAmount, int attackAnimationTick, int attackAnimationDuration,
		float partialTick, Arm attackArm, AttackStyle attackStyle) {
		public Context {
			attackArm = attackArm == null ? Arm.NONE : attackArm;
			attackStyle = attackStyle == null ? AttackStyle.EMPTY_HAND : attackStyle;
		}
	}

	/** Euler rotation in the same Z-Y-X order used by vanilla model parts. */
	public record Rotation(float x, float y, float z) {
		public static final Rotation IDENTITY = new Rotation(0.0f, 0.0f, 0.0f);

		private static Rotation of(ModelPart part) {
			return new Rotation(part.xRot, part.yRot, part.zRot);
		}

		private static Rotation x(float radians) {
			return new Rotation(radians, 0.0f, 0.0f);
		}

		static Rotation degrees(float x, float y, float z) {
			return new Rotation(x * Mth.DEG_TO_RAD, y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD);
		}

		private Rotation plus(Rotation other) {
			return new Rotation(x + other.x, y + other.y, z + other.z);
		}

		/** Mirrors a right-arm rotation across the body's sagittal plane. */
		Rotation mirrorLeft() {
			return new Rotation(x, -y, -z);
		}
	}

	public record Pose(Map<Bone, Rotation> rotations) {
		public static final Pose EMPTY = new Pose(Map.of());

		public Pose {
			rotations = rotations == null || rotations.isEmpty() ? Map.of() : Map.copyOf(rotations);
		}

		public Rotation rotation(@Nullable Bone bone) {
			return bone == null ? Rotation.IDENTITY : rotations.getOrDefault(bone, Rotation.IDENTITY);
		}
	}
}
