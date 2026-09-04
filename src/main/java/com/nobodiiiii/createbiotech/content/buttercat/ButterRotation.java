package com.nobodiiiii.createbiotech.content.buttercat;

import com.nobodiiiii.createbiotech.content.buttercat.mob_effect.ButterRotationEffect;
import com.nobodiiiii.createbiotech.registry.CBMobEffects;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ButterRotation {
	public static final float MELEE_HALF_ANGLE_DEGREES = 60.0F;
	private static final double MELEE_MIN_DOT =
		Math.cos(MELEE_HALF_ANGLE_DEGREES * Mth.DEG_TO_RAD);

	private ButterRotation() {}

	public static MobEffectInstance getEffect(LivingEntity entity) {
		return entity.getEffect(CBMobEffects.BUTTER_ROTATION.getDelegate());
	}

	public static float getVisualRotationDegrees(LivingEntity entity, float partialTick) {
		if (entity instanceof Player)
			return 0.0F;

		ButterRotationAccess access = (ButterRotationAccess) entity;
		int amplifier = access.createBiotech$getButterRotationAmplifier();
		if (amplifier < 0) {
			MobEffectInstance effect = getEffect(entity);
			if (effect == null)
				return 0.0F;
			amplifier = effect.getAmplifier();
		}

		long phaseStartTick = access.createBiotech$getButterRotationPhaseStartTick();
		if (phaseStartTick < 0L)
			return 0.0F;

		double elapsedTicks = Math.max(0.0D,
			entity.level().getGameTime() + partialTick - phaseStartTick);
		double degrees = access.createBiotech$getButterRotationPhase()
			+ elapsedTicks * getTickAngleSpeed(amplifier);
		return Mth.wrapDegrees((float) degrees);
	}

	public static void updateAmplifier(LivingEntity entity, int amplifier) {
		ButterRotationAccess access = (ButterRotationAccess) entity;
		int previousAmplifier = access.createBiotech$getButterRotationAmplifier();
		if (previousAmplifier == amplifier)
			return;

		if (entity instanceof Player)
			return;

		float phase = previousAmplifier < 0 ? 0.0F : getVisualRotationDegrees(entity, 0.0F);
		access.createBiotech$setButterRotationState(amplifier, phase, entity.level().getGameTime());
	}

	public static void clearRotationState(LivingEntity entity) {
		ButterRotationAccess access = (ButterRotationAccess) entity;
		access.createBiotech$setButterRotationState(-1, 0.0F, -1L);
	}

	public static float getTickAngleSpeed(int amplifier) {
		return ButterRotationEffect.getRotationAngularSpeed()
			* (6 * Math.max(0, amplifier) + 1);
	}

	public static Vec3 rotateHorizontal(Vec3 movement, float degrees) {
		return movement.yRot(-degrees * Mth.DEG_TO_RAD);
	}

	public static boolean isFacingForMelee(LivingEntity attacker, LivingEntity target) {
		double targetX = (target.getBoundingBox().minX + target.getBoundingBox().maxX) * 0.5D;
		double targetZ = (target.getBoundingBox().minZ + target.getBoundingBox().maxZ) * 0.5D;
		double deltaX = targetX - attacker.getX();
		double deltaZ = targetZ - attacker.getZ();
		double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		if (horizontalDistance < 1.0E-5D)
			return true;

		float visualYaw = attacker instanceof Player
			? attacker.getYRot()
			: attacker.getYHeadRot() + getVisualRotationDegrees(attacker, 0.0F);
		float yawRadians = visualYaw * Mth.DEG_TO_RAD;
		double facingX = -Mth.sin(yawRadians);
		double facingZ = Mth.cos(yawRadians);
		double dot = (facingX * deltaX + facingZ * deltaZ) / horizontalDistance;
		return dot >= MELEE_MIN_DOT;
	}
}
