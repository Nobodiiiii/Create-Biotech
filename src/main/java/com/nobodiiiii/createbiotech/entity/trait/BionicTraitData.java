package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Immutable loaded data for one definition. Value kinds remain typed throughout loading. */
record BionicTraitData(boolean automaticDetection, BionicTraitRule rule,
	BionicDonorOverrides<Boolean> abilities, BionicDonorOverrides<Double> numbers,
	BionicDonorOverrides<Map<ResourceLocation, Boolean>> effects,
	Map<ResourceLocation, Map<BionicAnatomyRole, Set<String>>> carriers,
	Map<String, Double> parameters, Map<ResourceLocation, BionicAttackEffect> attackEffects,
	Map<ResourceLocation, BionicSubtrait> subtraits, Set<BionicTrait> overrides) {
	BionicTraitData {
		carriers = Map.copyOf(carriers);
		parameters = Map.copyOf(parameters);
		attackEffects = Map.copyOf(attackEffects);
		subtraits = Map.copyOf(subtraits);
		overrides = Set.copyOf(overrides);
	}

	static BionicTraitData defaults(BionicTrait trait) {
		return new BionicTraitData(trait.automaticDetection(), trait.rule(),
			BionicDonorOverrides.empty(), BionicDonorOverrides.empty(), BionicDonorOverrides.empty(),
			Map.of(), Map.of(), Map.of(), BionicHeadTraits.variants(trait), Set.of());
	}

	BionicTraitData withOverrides(Set<BionicTrait> targets) {
		return new BionicTraitData(automaticDetection, rule, abilities, numbers, effects, carriers,
			parameters, attackEffects, subtraits, targets);
	}

	double parameter(String key, double fallback) {
		double value = parameters.getOrDefault(key, fallback);
		if (key.equals("max_body_volume_per_wing_volume"))
			return value;
		if (key.equals("chance") || key.endsWith("multiplier"))
			return Math.min(1.0d, value);
		return Math.min(key.endsWith("_ticks") ? 24000.0d : 100.0d, value);
	}
}
