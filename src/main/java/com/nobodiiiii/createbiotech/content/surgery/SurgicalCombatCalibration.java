package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.HashMap;
import com.nobodiiiii.createbiotech.api.bionic.BionicStat;
import com.nobodiiiii.createbiotech.content.surgery.stats.BionicStats;

/** Shared anatomical calibration for a stitched body's per-arm melee output and cadence. */
public final class SurgicalCombatCalibration {
	/** A zombie arm is a 4 by 12 by 4 model-pixel cuboid. */
	public static final double ZOMBIE_ARM_LENGTH = 12.0d / 16.0d;
	public static final double ZOMBIE_ARM_THICKNESS = 4.0d / 16.0d;
	public static final double ZOMBIE_ARM_VOLUME = ZOMBIE_ARM_LENGTH
		* ZOMBIE_ARM_THICKNESS * ZOMBIE_ARM_THICKNESS;
	/** Zombies use the inherited melee goal's twenty-tick attack interval. */
	public static final int ZOMBIE_ATTACK_INTERVAL = 20;
	/** Leaves room for distinct hits within Minecraft's hurt immunity and caps multi-arm bursts. */
	public static final int MIN_GLOBAL_ATTACK_INTERVAL = 12;
	public static final int MAX_GLOBAL_ATTACK_INTERVAL = 40;

	private static final double MIN_LENGTH = 1.0d / 64.0d;

	private SurgicalCombatCalibration() {}

	/**
	 * Converts one baked arm into a hit multiplier and whole attack interval.
	 *
	 * <p>Estimated volume alone controls DPS. Its saturating curve passes exactly through a zombie
	 * arm and approaches four zombie DPS as volume grows without bound. At a fixed volume, equivalent
	 * thickness moves that output between larger, slower hits and smaller, faster hits; deriving the
	 * hit multiplier from the final integer interval keeps theoretical DPS unchanged apart from game
	 * timing and damage rounding. Runtime coordination and the global minimum interval are applied
	 * separately after this anatomical calibration.</p>
	 */
	public static ArmCombatStats stats(SurgicalAssembly.ArmAttackGeometry arm) {
		return stats(arm, null);
	}

	public static ArmCombatStats stats(SurgicalAssembly.ArmAttackGeometry arm, Level level) {
		double volume = arm == null ? ZOMBIE_ARM_VOLUME : Math.max(0, arm.volume());
		double length = arm == null ? ZOMBIE_ARM_LENGTH : Math.max(MIN_LENGTH, arm.reach() - arm.radius());
		Map<String, Double> inputs = Map.of("arm_volume", volume, "arm_length", length, "arm_thickness", Math.sqrt(volume / length));
		double dps = BionicStats.evaluate(BionicStat.ARM_DPS_SCALE, level, inputs);
		int interval = (int) Math.round(BionicStats.evaluate(BionicStat.ARM_ATTACK_INTERVAL, level, inputs));
		Map<String, Double> damageInputs = new HashMap<>(inputs);
		damageInputs.put("arm_dps_scale", dps); damageInputs.put("arm_interval", (double) interval);
		return new ArmCombatStats(BionicStats.evaluate(BionicStat.ARM_DAMAGE_MULTIPLIER, level, damageInputs), interval);
	}

	/** The runtime's multi-arm global attack interval multiplier for the currently ready arms. */
	public static float cadenceScale(int readyArmCount) {
		return Math.max(0.8f, 1.0f - Math.max(0, readyArmCount - 1) * 0.1f);
	}

	public static int armRecovery(ArmCombatStats stats) {
		return armRecovery(stats, (Level) null);
	}

	public static int armRecovery(ArmCombatStats stats, Level level) {
		return (int) Math.round(BionicStats.evaluate(BionicStat.ARM_RECOVERY, level,
			Map.of("arm_interval", (double) stats.attackInterval())));
	}

	/** Applies the weapon's main-hand damage attributes without attack speed, enchantments or effects. */
	public static double attackDamageWithWeapon(double baseAttackDamage, ItemStack weapon) {
		AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, attribute -> {});
		damage.setBaseValue(baseAttackDamage);
		weapon.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage.addTransientModifier(modifier);
		});
		return damage.getValue();
	}

	/**
	 * Fixed favourite for simple minds, ranked by equipment-free, single-arm DPS before
	 * checking reach or recovery. Ties keep the first arm (right slots, then left slots).
	 */
	public static int highestDpsArmIndex(SurgicalAssembly.AttackGeometry geometry) {
		int bestIndex = 0;
		double bestDps = -1.0d;
		var arms = geometry.arms();
		for (int index = 0; index < arms.size(); index++) {
			ArmCombatStats stats = stats(arms.get(index));
			double dps = stats.damageMultiplier() * ZOMBIE_ATTACK_INTERVAL / armRecovery(stats);
			if (dps > bestDps + 1.0e-8d) {
				bestDps = dps;
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	/** Compatibility overload; intelligence changes strategy, not anatomical recovery. */
	@Deprecated(forRemoval = false)
	public static int armRecovery(ArmCombatStats stats, BionicIntelligence ignored) {
		return armRecovery(stats);
	}

	/** The caller counts coordination in the generic range before filtering by mounted posture. */
	public static int attackInterval(ArmCombatStats stats, int eligibleArms) {
		return attackInterval(stats, eligibleArms, (Level) null);
	}

	public static int attackInterval(ArmCombatStats stats, int eligibleArms, Level level) {
		return (int) Math.round(BionicStats.evaluate(BionicStat.ATTACK_INTERVAL, level,
			Map.of("arm_recovery", (double) armRecovery(stats, level), "arm_count", (double) Math.max(1, eligibleArms))));
	}

	/** Compatibility overload; strategy supplies eligible arms without an intelligence multiplier. */
	@Deprecated(forRemoval = false)
	public static int attackInterval(ArmCombatStats stats, int eligibleArms,
		BionicIntelligence ignored) {
		return attackInterval(stats, eligibleArms);
	}

	/**
	 * Stable, equipment-free DPS estimate for the boxed bionic creature tooltip.
	 *
	 * <p>Each arm contributes its geometry-normalized DPS, the contributions are averaged because
	 * attacks are selected one at a time, and the full-ready multi-arm cadence is then applied. Target
	 * reach, per-arm recovery state, held weapons and enchantments are intentionally situational and
	 * therefore excluded. Intelligence-dependent strategy does not enter this baseline value.</p>
	 */
	public static double nominalDamagePerSecond(double baseAttackDamage,
		@Nullable SurgicalAssembly.AttackGeometry geometry) {
		return nominalDamagePerSecond(baseAttackDamage, geometry, null);
	}

	public static double nominalDamagePerSecond(double baseAttackDamage,
		@Nullable SurgicalAssembly.AttackGeometry geometry, Level level) {
		if (!Double.isFinite(baseAttackDamage) || baseAttackDamage < 0.0d)
			return 0.0d;
		if (geometry == null || geometry.arms().isEmpty()) {
			ArmCombatStats fallback = stats(null, level);
			return baseAttackDamage * fallback.damageMultiplier() * 20 / attackInterval(fallback, 1, level);
		}
		return nominalDamagePerSecond(baseAttackDamage, geometry.arms(), level);
	}

	/** Entity evaluations can omit installed chains currently acting as wings. */
	public static double nominalDamagePerSecond(double baseAttackDamage,
		java.util.List<SurgicalAssembly.ArmAttackGeometry> arms) {
		return nominalDamagePerSecond(baseAttackDamage, arms, null);
	}

	public static double nominalDamagePerSecond(double baseAttackDamage,
		java.util.List<SurgicalAssembly.ArmAttackGeometry> arms, Level level) {
		if (!Double.isFinite(baseAttackDamage) || baseAttackDamage < 0 || arms.isEmpty()) return 0;

		double totalDps = 0.0d;
		for (SurgicalAssembly.ArmAttackGeometry arm : arms) {
			ArmCombatStats armStats = stats(arm, level);
			totalDps += baseAttackDamage * armStats.damageMultiplier() * ZOMBIE_ATTACK_INTERVAL
				/ attackInterval(armStats, arms.size(), level);
		}
		return totalDps / arms.size();
	}

	public record ArmCombatStats(double damageMultiplier, int attackInterval) {
		public ArmCombatStats {
			if (!Double.isFinite(damageMultiplier) || damageMultiplier < 0.0d
				|| attackInterval < 1 || attackInterval > 1200)
				throw new IllegalArgumentException("Invalid arm combat stats");
		}
	}
}
