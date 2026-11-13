package com.nobodiiiii.createbiotech.content.surgery.stats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numeric calibration only: formula syntax belongs to data packs, never the TOML config. */
public final class BionicStatParameters {
	public record Setting(String key, double value, double min, double max, String comment) {}
	public static final List<Setting> SETTINGS = List.of(
		new Setting("health_maximum", 400, 1.001, 1024, "Health ceiling; both health anchors are preserved by recalibration."),
		new Setting("health_anchor_volume", 0.40625, 1e-9, 262144, "First health anchor volume in cubic blocks (standard zombie)."),
		new Setting("health_anchor_value", 20, 1, 1023, "First health anchor value; must be below the second anchor and ceiling."),
		new Setting("health_second_anchor_volume", 5.184234619140625, 1e-9, 262144, "Second health anchor volume (complete ravager); must exceed the first volume."),
		new Setting("health_second_anchor_value", 100, 1, 1023, "Second health anchor value; must be below the ceiling."),
		new Setting("movement_maximum", 0.6, 1e-6, 1024, "Land-speed ceiling after anatomical multipliers."),
		new Setting("leg_anchor_length", 0.75, 1e-6, 64, "First effective-leg-length anchor in blocks."),
		new Setting("leg_anchor_speed", 0.23, 1e-6, 1024, "Length-only speed at the first leg anchor."),
		new Setting("leg_second_anchor_length", 1.875, 1e-6, 64, "Second effective-leg-length anchor; must exceed the first."),
		new Setting("leg_second_anchor_speed", 0.36, 1e-6, 1024, "Second length-only speed; anchors must describe a saturating length curve."),
		new Setting("hopping_speed", 0.23, 0, 1024, "Ground speed for zero or one grounded leg."),
		new Setting("flight_maximum", 0.6, 1e-6, 1024, "Flight speed at 100 percent active flight tissue."),
		new Setting("flight_anchor_ratio", 0.25, 1e-6, 0.999999, "Active-flight-tissue ratio at the flight anchor."),
		new Setting("flight_anchor_speed", 0.3, 1e-6, 1024, "Flight speed at the anchor; must be below flight_maximum."),
		new Setting("swimming_speed", 1, 0, 1024, "Base movement attribute while specialist swimming is active."),
		new Setting("base_attack_damage", 3, 0, 2048, "Equipment-free base melee damage before arm geometry."),
		new Setting("base_knockback_resistance", 0, 0, 1, "Innate resistance before inherited trait contributions."),
		new Setting("base_follow_range", 35, 0, 2048, "Base detection range before special-attack range requirements."),
		new Setting("arm_dps_maximum", 4, 1e-6, 1024, "Asymptotic arm DPS multiplier; must exceed arm_anchor_dps_scale."),
		new Setting("arm_anchor_volume", 0.046875, 1e-9, 262144, "Arm-volume DPS anchor in cubic blocks (standard zombie arm)."),
		new Setting("arm_anchor_dps_scale", 1, 1e-6, 1024, "DPS multiplier at arm_anchor_volume."),
		new Setting("arm_anchor_thickness", 0.25, 1e-6, 64, "Equivalent arm thickness at the interval anchor, in blocks."),
		new Setting("arm_reference_interval", 20, 1, 1200, "Attack interval in ticks at the thickness anchor."),
		new Setting("arm_minimum_interval", 10, 1, 1200, "Minimum anatomical arm interval in ticks."),
		new Setting("arm_maximum_interval", 40, 1, 1200, "Maximum anatomical arm interval in ticks."),
		new Setting("global_minimum_interval", 12, 1, 1200, "Minimum actual attack interval and per-arm recovery in ticks."),
		new Setting("global_maximum_interval", 40, 1, 1200, "Maximum actual attack interval in ticks.")
	);
	private BionicStatParameters() {}
	public static Map<String, Double> defaults() {
		Map<String, Double> values = new LinkedHashMap<>();
		SETTINGS.forEach(setting -> values.put(setting.key(), setting.value()));
		return calibrate(values);
	}
	public static Map<String, Double> calibrate(Map<String, Double> values) {
		Map<String, Double> p = new LinkedHashMap<>();
		for (Setting s : SETTINGS) {
			double v = values.getOrDefault(s.key(), s.value());
			if (!Double.isFinite(v) || v < s.min() || v > s.max())
				throw new IllegalArgumentException("Invalid bionic stat parameter " + s.key());
			p.put(s.key(), v);
		}
		double cap = p.get("health_maximum"), h1 = p.get("health_anchor_value"), h2 = p.get("health_second_anchor_value");
		require(h1 < h2 && h2 < cap && p.get("health_anchor_volume") < p.get("health_second_anchor_volume"), "Health anchors must increase in volume and health, below the ceiling");
		double offset = cap / h1 - 1;
		p.put("health_offset", offset);
		p.put("health_exponent", Math.log(offset * h2 / (cap - h2)) / Math.log(p.get("health_second_anchor_volume") / p.get("health_anchor_volume")));
		double l1 = p.get("leg_anchor_length"), l2 = p.get("leg_second_anchor_length");
		double s1 = p.get("leg_anchor_speed"), s2 = p.get("leg_second_anchor_speed");
		require(l1 < l2 && s1 < s2 && s2 / s1 < l2 / l1 && s2 <= p.get("movement_maximum"), "Leg anchors must describe an increasing saturating curve below the movement ceiling");
		double half = (s2 - s1) * l1 * l2 / (s1 * l2 - s2 * l1);
		p.put("length_half_saturation", half);
		p.put("length_asymptote", s1 * (l1 + half) / l1);
		require(p.get("flight_anchor_speed") < p.get("flight_maximum"), "Flight anchor speed must be below its ceiling");
		p.put("flight_exponent", Math.log(p.get("flight_anchor_speed") / p.get("flight_maximum")) / Math.log(Math.log1p(8 * p.get("flight_anchor_ratio")) / Math.log(9)));
		require(p.get("arm_anchor_dps_scale") < p.get("arm_dps_maximum"), "Arm DPS anchor must be below its ceiling");
		p.put("arm_dps_offset", p.get("arm_dps_maximum") / p.get("arm_anchor_dps_scale") - 1);
		require(p.get("arm_minimum_interval") <= p.get("arm_reference_interval") && p.get("arm_reference_interval") <= p.get("arm_maximum_interval"), "Arm interval anchor must lie between its minimum and maximum");
		require(p.get("global_minimum_interval") <= p.get("global_maximum_interval"), "Global minimum interval exceeds maximum");
		p.forEach((key, value) -> require(Double.isFinite(value), "Non-finite calibration: " + key));
		return Map.copyOf(p);
	}
	private static void require(boolean condition, String message) {
		if (!condition) throw new IllegalArgumentException(message);
	}
}
