package com.nobodiiiii.createbiotech.content.surgery;

import net.minecraft.util.Mth;

/** Shared anatomical calibration for a stitched body's per-arm melee output and cadence. */
public final class SurgicalCombatCalibration {
	/** A zombie arm is a 4 by 12 by 4 model-pixel cuboid. */
	public static final double ZOMBIE_ARM_LENGTH = 12.0d / 16.0d;
	public static final double ZOMBIE_ARM_THICKNESS = 4.0d / 16.0d;
	public static final double ZOMBIE_ARM_VOLUME = ZOMBIE_ARM_LENGTH
		* ZOMBIE_ARM_THICKNESS * ZOMBIE_ARM_THICKNESS;
	/** Zombies use the inherited melee goal's twenty-tick attack interval. */
	public static final int ZOMBIE_ATTACK_INTERVAL = 20;

	private static final double MAX_DPS_SCALE = 8.0d;
	/** 256 zombie-arm volumes produce ninety percent of the asymptotic DPS ceiling. */
	private static final double DPS_VOLUME_EXPONENT = Math.log(63.0d) / Math.log(256.0d);
	/** Makes an isometrically scaled 256-volume arm reach the forty-tick cadence ceiling. */
	private static final double INTERVAL_THICKNESS_EXPONENT = 3.0d / 8.0d;
	private static final int MIN_ATTACK_INTERVAL = 10;
	private static final int MAX_ATTACK_INTERVAL = 40;
	private static final double MIN_LENGTH = 1.0d / 64.0d;

	private SurgicalCombatCalibration() {}

	/**
	 * Converts one baked arm into a hit multiplier and whole attack interval.
	 *
	 * <p>Estimated volume alone controls DPS. Its saturating curve passes exactly through a zombie
	 * arm, reaches ninety percent of its ceiling at 256 zombie-arm volumes, and approaches eight
	 * zombie DPS as volume grows without bound. At a fixed volume, equivalent thickness moves that
	 * output between larger, slower hits and smaller, faster hits; deriving the hit multiplier from
	 * the final integer interval keeps theoretical DPS unchanged apart from game timing and damage
	 * rounding.</p>
	 */
	public static ArmCombatStats stats(SurgicalAssembly.ArmAttackGeometry arm) {
		if (arm == null)
			return ArmCombatStats.ZOMBIE;
		double volume = Math.max(0.0d, arm.volume());
		double volumeScale = volume / ZOMBIE_ARM_VOLUME;
		double dpsInput = Math.pow(volumeScale, DPS_VOLUME_EXPONENT);
		double dpsScale = dpsInput <= 0.0d ? 0.0d
			: MAX_DPS_SCALE * dpsInput / (MAX_DPS_SCALE - 1.0d + dpsInput);

		double length = Math.max(MIN_LENGTH, arm.reach() - arm.radius());
		double thickness = Math.sqrt(volume / length);
		double intervalScale = Math.pow(thickness / ZOMBIE_ARM_THICKNESS,
			INTERVAL_THICKNESS_EXPONENT);
		int attackInterval = Mth.clamp((int) Math.round(ZOMBIE_ATTACK_INTERVAL * intervalScale),
			MIN_ATTACK_INTERVAL, MAX_ATTACK_INTERVAL);
		double damageMultiplier = dpsScale * attackInterval / ZOMBIE_ATTACK_INTERVAL;
		return new ArmCombatStats(damageMultiplier, attackInterval);
	}

	public record ArmCombatStats(double damageMultiplier, int attackInterval) {
		private static final ArmCombatStats ZOMBIE =
			new ArmCombatStats(1.0d, ZOMBIE_ATTACK_INTERVAL);

		public ArmCombatStats {
			if (!Double.isFinite(damageMultiplier) || damageMultiplier < 0.0d
				|| attackInterval < MIN_ATTACK_INTERVAL || attackInterval > MAX_ATTACK_INTERVAL)
				throw new IllegalArgumentException("Invalid arm combat stats");
		}
	}
}
