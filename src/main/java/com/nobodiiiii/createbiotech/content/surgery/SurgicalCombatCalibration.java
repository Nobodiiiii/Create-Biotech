package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

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

	private static final double MAX_DPS_SCALE = 4.0d;
	private static final int MIN_ATTACK_INTERVAL = 10;
	private static final int MAX_ATTACK_INTERVAL = 40;
	private static final double MIN_LENGTH = 1.0d / 64.0d;

	private SurgicalCombatCalibration() {}

	/**
	 * Converts one baked arm into a hit multiplier and whole attack interval.
	 *
	 * <p>Estimated volume alone controls DPS. Its saturating curve passes exactly through a zombie
	 * arm and approaches four zombie DPS as volume grows without bound. At a fixed volume, equivalent
	 * thickness moves that output between larger, slower hits and smaller, faster hits; deriving the
	 * hit multiplier from the final integer interval keeps theoretical DPS unchanged apart from game
	 * timing and damage rounding.</p>
	 */
	public static ArmCombatStats stats(SurgicalAssembly.ArmAttackGeometry arm) {
		if (arm == null)
			return ArmCombatStats.ZOMBIE;
		double volume = Math.max(0.0d, arm.volume());
		double volumeRoot = Math.cbrt(volume / ZOMBIE_ARM_VOLUME);
		double dpsScale = volumeRoot <= 0.0d ? 0.0d
			: MAX_DPS_SCALE * volumeRoot / (MAX_DPS_SCALE - 1.0d + volumeRoot);

		double length = Math.max(MIN_LENGTH, arm.reach() - arm.radius());
		double thickness = Math.sqrt(volume / length);
		double intervalScale = Math.sqrt(thickness / ZOMBIE_ARM_THICKNESS);
		int attackInterval = Mth.clamp((int) Math.round(ZOMBIE_ATTACK_INTERVAL * intervalScale),
			MIN_ATTACK_INTERVAL, MAX_ATTACK_INTERVAL);
		double damageMultiplier = dpsScale * attackInterval / ZOMBIE_ATTACK_INTERVAL;
		return new ArmCombatStats(damageMultiplier, attackInterval);
	}

	/** The runtime's multi-arm global attack interval multiplier for the currently ready arms. */
	public static float cadenceScale(int readyArmCount) {
		return Math.max(0.7f, 1.0f - Math.max(0, readyArmCount - 1) * 0.1f);
	}

	/**
	 * Stable, equipment-free DPS estimate for the boxed bionic creature tooltip.
	 *
	 * <p>Each arm contributes its geometry-normalized DPS, the contributions are averaged because
	 * attacks are selected one at a time, and the full-ready multi-arm cadence is then applied. Target
	 * reach, per-arm recovery state, held weapons and enchantments are intentionally situational and
	 * therefore excluded from this base value.</p>
	 */
	public static double nominalDamagePerSecond(double baseAttackDamage,
		@Nullable SurgicalAssembly.AttackGeometry geometry) {
		if (!Double.isFinite(baseAttackDamage) || baseAttackDamage < 0.0d)
			return 0.0d;
		if (geometry == null || geometry.arms().isEmpty())
			return baseAttackDamage;

		double totalDps = 0.0d;
		for (SurgicalAssembly.ArmAttackGeometry arm : geometry.arms()) {
			ArmCombatStats armStats = stats(arm);
			totalDps += baseAttackDamage * armStats.damageMultiplier()
				* ZOMBIE_ATTACK_INTERVAL / armStats.attackInterval();
		}
		return totalDps / geometry.armCount() / cadenceScale(geometry.armCount());
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
