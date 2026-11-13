package com.nobodiiiii.createbiotech.api.bionic;

import java.util.Set;

/** Formula outputs for anatomy-derived base stats, independent of inherited traits. */
public enum BionicStat {
	MAX_HEALTH(1, 1024, "clamp(health_maximum / (1 + health_offset * pow(health_anchor_volume / max(volume, 1e-12), health_exponent)), 1, health_maximum)", "volume"),
	MOVEMENT_SPEED(0, 1024, "clamp(leg_count <= 1 ? hopping_speed : length_asymptote * leg_length / (leg_length + length_half_saturation) * (1 + clamp(leg_count - 2, 0, 2) * 0.1 + clamp(leg_count - 4, 0, 4) * 0.05) * (0.8 + 0.4 * log1p(8 * leg_volume_ratio) / log(9)) * (1 + clamp(knee_count, 0, 4) * 0.05), 0, movement_maximum)", "leg_length", "leg_count", "knee_count", "leg_volume_ratio"),
	FLYING_SPEED(0, 1024, "flight_maximum * pow(log1p(8 * flight_volume_ratio) / log(9), flight_exponent)", "flight_volume_ratio"),
	SWIMMING_SPEED(0, 1024, "swimming_speed"),
	ATTACK_DAMAGE(0, 2048, "base_attack_damage", "volume"),
	KNOCKBACK_RESISTANCE(0, 1, "base_knockback_resistance", "volume"),
	FOLLOW_RANGE(0, 2048, "base_follow_range", "volume"),
	ARM_DPS_SCALE(0, 1024, "arm_volume <= 0 ? 0 : arm_dps_maximum * cbrt(arm_volume / arm_anchor_volume) / (arm_dps_offset + cbrt(arm_volume / arm_anchor_volume))", "arm_volume", "arm_length", "arm_thickness"),
	ARM_ATTACK_INTERVAL(1, 1200, "clamp(round(arm_reference_interval * sqrt(arm_thickness / arm_anchor_thickness)), arm_minimum_interval, arm_maximum_interval)", "arm_volume", "arm_length", "arm_thickness"),
	ARM_DAMAGE_MULTIPLIER(0, 1024, "arm_dps_scale * arm_interval / arm_reference_interval", "arm_volume", "arm_length", "arm_thickness", "arm_dps_scale", "arm_interval"),
	ARM_RECOVERY(1, 1200, "max(global_minimum_interval, arm_interval)", "arm_interval"),
	ATTACK_INTERVAL(1, 1200, "clamp(round(arm_recovery * max(0.8, 1 - max(0, arm_count - 1) * 0.1)), global_minimum_interval, global_maximum_interval)", "arm_recovery", "arm_count");

	private final double minimum, maximum;
	private final String expression;
	private final Set<String> inputs;
	BionicStat(double minimum, double maximum, String expression, String... inputs) {
		this.minimum = minimum; this.maximum = maximum;
		this.expression = expression; this.inputs = Set.of(inputs);
	}
	public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
	public double minimum() { return minimum; }
	public double maximum() { return maximum; }
	public String defaultExpression() { return expression; }
	public Set<String> inputs() { return inputs; }
}
