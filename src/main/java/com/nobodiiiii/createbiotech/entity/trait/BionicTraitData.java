package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable loaded data for one definition. Value kinds remain typed throughout loading. */
record BionicTraitData(boolean automaticDetection, BionicTraitRule rule,
	BionicDonorOverrides<Boolean> abilities, BionicDonorOverrides<Double> numbers,
	BionicDonorOverrides<Map<ResourceLocation, Boolean>> effects,
	Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>> carriers,
	Map<String, Double> parameters) {
	BionicTraitData {
		carriers = Map.copyOf(carriers);
		parameters = Map.copyOf(parameters);
	}

	static BionicTraitData defaults(BionicTrait trait) {
		return new BionicTraitData(trait.automaticDetection(), trait.rule(),
			BionicDonorOverrides.empty(), BionicDonorOverrides.empty(), BionicDonorOverrides.empty(),
			Map.of(), Map.of());
	}

	double parameter(String key, double fallback) {
		double value = parameters.getOrDefault(key, fallback);
		if (key.equals("max_air_supply"))
			return Math.max(300, Math.min(12000, value));
		if (key.equals("max_body_volume_per_wing_volume"))
			return value;
		if (key.equals("chance") || key.endsWith("multiplier"))
			return Math.min(1.0d, value);
		return Math.min(key.endsWith("duration_ticks") ? 12000.0d : 100.0d, value);
	}
}
