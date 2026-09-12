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
	 * <p>The two-level arm and leg layout models each lower limb as a child of its corresponding upper
	 * limb. The lightweight bend curves below preserve that hierarchy. Authored attacks live beside
	 * this sampler in their own catalogue class so adding more of them does not grow the geometry
	 * solver or its integration contract.</p>
 */
public final class SlimeBionicAnimations {
	private static final float WALK_PHASE_SCALE = 0.6662f;
	private static final float MIN_WALK_ELBOW_DEGREES = 15.0f;
	private static final float MAX_WALK_ELBOW_DEGREES = 37.5f;
	private static final float MIN_WALK_KNEE_DEGREES = 3.0f;
	private static final float MAX_WALK_KNEE_DEGREES = 51.0f;
	/** The dynamic yaw and lift amplitudes used by Minecraft's 1.21.1 SpiderModel. */
	private static final float SPIDER_LEG_SWING = 0.4f;
	private static final float SPIDER_LEG_LIFT = 0.4f;
	/** Counter-rotating the lower leg twice keeps a two-segment M leg from folding upward. */
	private static final float SPIDER_KNEE_COUNTER_ROTATION = 2.0f;
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

	/** Alternating arm flexion, expressed locally beneath each upper arm. */
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

	/** Samples one dynamically assigned humanoid or spider leg channel. */
	public static Rotation sampleLeg(Context context, boolean knee, LegStyle style, boolean left,
		float phaseOffset) {
		if (context == null)
			return Rotation.IDENTITY;
		if (style == LegStyle.SPIDER)
			return sampleSpiderLeg(context, knee, left, phaseOffset);
		float weight = Mth.clamp(context.walkWeight(), 0.0f, 1.0f);
		if (weight <= 0.0f)
			return Rotation.IDENTITY;
		return sampleHumanoidLeg(context, knee, phaseOffset, weight);
	}

	/** Samples one of as many as eight independently phased shoulder/elbow chains. */
	public static Rotation sampleArm(Context context, Rotation sampled, boolean elbow, boolean left,
		int slot, float phaseOffset) {
		if (context == null)
			return Rotation.IDENTITY;
		Rotation result = sampled == null ? Rotation.IDENTITY : sampled;
		float phase = context.limbSwing() * WALK_PHASE_SCALE;
		if (context.walkWeight() > 0.0f) {
			if (elbow) {
				float canonicalOffset = left ? Mth.PI : 0.0f;
				float desiredOffset = phaseOffset + Mth.PI;
				float canonicalDegrees = elbowDegrees(phase + canonicalOffset);
				float desiredDegrees = elbowDegrees(phase + desiredOffset);
				result = result.plus(Rotation.x((canonicalDegrees - desiredDegrees)
					* Mth.DEG_TO_RAD * Mth.clamp(context.walkWeight(), 0.0f, 1.0f)));
			} else {
				float canonicalOffset = left ? 0.0f : Mth.PI;
				float delta = (Mth.cos(phase + phaseOffset) - Mth.cos(phase + canonicalOffset))
					* context.limbSwingAmount();
				result = result.plus(Rotation.x(delta));
			}
		}

		Rotation attack = armAttackRotation(context, elbow, left);
		if (attack == null)
			return result;
		// The shared pose initially contains the attack channel. Retain it only on the selected arm,
		// plus one balancing arm on the opposite side.
		result = result.minus(attack);
		boolean attackingSide = (context.attackArm() == Arm.LEFT) == left;
		if (attackingSide && slot == context.attackArmSlot()) {
			result = result.plus(attack);
			if (!elbow)
				result = result.plus(attackDirectionBias(context));
			return result;
		}
		return !attackingSide && slot == 0 ? result.plus(attack) : result;
	}

	/** Coarsely turns the authored shoulder swing toward the selected attack sector's midpoint. */
	private static Rotation attackDirectionBias(Context context) {
		float duration = Math.max(1.0f, context.attackAnimationDuration());
		float remaining = Mth.clamp(context.attackAnimationTick() - context.partialTick(), 0.0f, duration);
		float elapsed = duration - remaining;
		// Presentation-only blending; changing this envelope cannot move the server's contact window.
		float activeStart = duration / 3.0f;
		float activeEnd = duration * 0.6f;
		float weight;
		if (elapsed < activeStart) {
			weight = smoothStep(activeStart <= 0.0f ? 1.0f : elapsed / activeStart);
		} else if (elapsed < activeEnd) {
			weight = 1.0f;
		} else {
			float recovery = duration - activeEnd;
			weight = 1.0f - smoothStep(recovery <= 0.0f ? 1.0f : (elapsed - activeEnd) / recovery);
		}
		float relativeYaw = Mth.clamp(Mth.wrapDegrees(context.attackRangeCenterYaw() - context.bodyYaw()),
			-80.0f, 80.0f);
		float pitch = Mth.clamp(context.attackRangeCenterPitch() - context.attackReferencePitch(),
			-75.0f, 60.0f);
		return new Rotation(pitch * 0.75f * Mth.DEG_TO_RAD * weight,
			relativeYaw * 0.6f * Mth.DEG_TO_RAD * weight, 0.0f);
	}

	private static float smoothStep(float value) {
		float clamped = Mth.clamp(value, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}

	private static float elbowDegrees(float phase) {
		return Mth.lerp((1.0f - Mth.cos(phase)) * 0.5f,
			MIN_WALK_ELBOW_DEGREES, MAX_WALK_ELBOW_DEGREES);
	}

	@Nullable
	private static Rotation armAttackRotation(Context context, boolean elbow, boolean left) {
		if (context.attackArm() == Arm.NONE || context.attackAnimationTick() <= 0)
			return null;
		float duration = Math.max(1.0f, context.attackAnimationDuration());
		float remainingTicks = Mth.clamp(context.attackAnimationTick() - context.partialTick(),
			0.0f, duration);
		float progress = 1.0f - remainingTicks / duration;
		SlimeBionicAttackAnimations.AttackPose attack = attackPose(progress,
			context.attackStyle(), context.attackArmHasElbow());
		boolean attackingSide = (context.attackArm() == Arm.LEFT) == left;
		Rotation rotation = attackingSide
			? elbow ? attack.attackingElbow() : attack.attackingShoulder()
			: elbow ? attack.oppositeElbow() : attack.oppositeShoulder();
		return context.attackArm() == Arm.LEFT ? rotation.mirrorLeft() : rotation;
	}

	/** Preserves the original pendulum-and-knee gait for downward, humanoid-like legs. */
	private static Rotation sampleHumanoidLeg(Context context, boolean knee, float phaseOffset,
		float weight) {
		float phase = context.limbSwing() * WALK_PHASE_SCALE + phaseOffset;
		if (!knee) {
			float swing = Mth.cos(phase) * 1.4f * context.limbSwingAmount();
			return Rotation.x(swing);
		}
		float bend = Math.max(0.0f, Mth.sin(phase));
		float degrees = Mth.lerp(bend, MIN_WALK_KNEE_DEGREES, MAX_WALK_KNEE_DEGREES);
		return Rotation.x(degrees * Mth.DEG_TO_RAD * weight);
	}

	/**
	 * Reproduces SpiderModel's horizontal sweep and mirrored lift as deltas from the installed rest
	 * pose. An articulated lower leg counter-rotates in the radial plane, so an M-shaped leg flexes
	 * at its knee instead of making its complete lower half follow the hip as one rigid bar.
	 */
	private static Rotation sampleSpiderLeg(Context context, boolean knee, boolean left,
		float phaseOffset) {
		float amount = Mth.clamp(context.vanillaLimbSwingAmount(), 0.0f, 1.0f);
		if (amount <= 0.0f)
			return Rotation.IDENTITY;
		float phase = context.vanillaLimbSwing() * WALK_PHASE_SCALE;
		float mirror = left ? -1.0f : 1.0f;
		float lift = Math.abs(Mth.sin(phase + phaseOffset)) * SPIDER_LEG_LIFT * amount
			* mirror;
		if (knee)
			return Rotation.z(-lift * SPIDER_KNEE_COUNTER_ROTATION);
		float sweep = -Mth.cos(phase * 2.0f + phaseOffset) * SPIDER_LEG_SWING * amount
			* mirror;
		return new Rotation(0.0f, sweep, lift);
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
		addArticulatedAttackPose(rotations, context.attackArm(), context.attackStyle(),
			context.attackArmHasElbow(), progress);
	}

	/** Samples only the authored attack channels for generation-time combat-path baking. */
	public static Pose sampleAttack(float progress, Arm arm, AttackStyle style) {
		if (arm == null || arm == Arm.NONE || style == null)
			return Pose.EMPTY;
		EnumMap<Bone, Rotation> rotations = new EnumMap<>(Bone.class);
		addArticulatedAttackPose(rotations, arm, style, true,
			Mth.clamp(progress, 0.0f, 1.0f));
		return new Pose(rotations);
	}

	private static void addArticulatedAttackPose(EnumMap<Bone, Rotation> rotations,
		Arm arm, AttackStyle style, boolean attackArmHasElbow, float progress) {
		SlimeBionicAttackAnimations.AttackPose attack = attackPose(progress, style,
			attackArmHasElbow);
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

	private static SlimeBionicAttackAnimations.AttackPose attackPose(float progress,
		AttackStyle style, boolean attackArmHasElbow) {
		if (!attackArmHasElbow)
			return SlimeBionicAttackAnimations.rigidArmSwing(progress);
		return style == AttackStyle.WEAPON
			? SlimeBionicAttackAnimations.weaponSwing(progress)
			: SlimeBionicAttackAnimations.articulatedEmptyHandSwing(progress);
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

	/** Geometry-selected walking style. Each installed hip owns one independent value. */
	public enum LegStyle {
		HUMANOID,
		SPIDER
	}

	/** All time-varying inputs needed to sample one pose; no assembly or renderer state leaks in. */
	public record Context(LivingEntity entity, float limbSwing, float limbSwingAmount,
		float walkWeight, float vanillaLimbSwing, float vanillaLimbSwingAmount, float ageInTicks,
		float netHeadYaw, float headPitch, float attackTime, boolean riding, float swimAmount,
		int attackAnimationTick, int attackAnimationDuration, float partialTick, float bodyYaw,
		float attackRangeCenterYaw, float attackRangeCenterPitch, float attackReferencePitch,
		Arm attackArm, int attackArmSlot,
		boolean attackArmHasElbow, AttackStyle attackStyle) {
		public Context {
			attackArm = attackArm == null ? Arm.NONE : attackArm;
			attackArmSlot = Mth.clamp(attackArmSlot, 0, 7);
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

		private static Rotation z(float radians) {
			return new Rotation(0.0f, 0.0f, radians);
		}

		static Rotation degrees(float x, float y, float z) {
			return new Rotation(x * Mth.DEG_TO_RAD, y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD);
		}

		private Rotation plus(Rotation other) {
			return new Rotation(x + other.x, y + other.y, z + other.z);
		}

		private Rotation minus(Rotation other) {
			return new Rotation(x - other.x, y - other.y, z - other.z);
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
