package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import net.minecraft.resources.ResourceLocation;

/** One immutable assembly evaluation, including inactive reasons and the data used to resolve it. */
public final class BionicTraitSet {
	public static final BionicTraitSet EMPTY = new BionicTraitSet(Map.of(), Map.of());
	private final Map<BionicTrait, BionicTraitResult> results;
	private final Map<BionicTrait, BionicTraitData> data;

	BionicTraitSet(Map<BionicTrait, BionicTraitResult> results, Map<BionicTrait, BionicTraitData> data) {
		this.results = Map.copyOf(results);
		this.data = Map.copyOf(data);
	}

	public BionicTraitResult result(BionicTrait trait) { return results.getOrDefault(trait, BionicTraitResult.ABSENT); }
	public boolean has(BionicTrait trait) { return result(trait).active(); }
	/** Whole-body/torso ratio, or the highest individual chain ratio for limb abilities. */
	public double coverage(BionicTrait trait) { return result(trait).coverage(); }
	public double strength(BionicTrait trait) { return result(trait).strength(); }
	public double value(BionicTrait trait) { return result(trait).value(); }
	public boolean isEmpty() { return results.isEmpty(); }
	public boolean hasFullEffect(BionicTrait trait) {
		return has(trait) && strength(trait) >= 1.0d - BionicTraitRule.COVERAGE_EPSILON;
	}
	/** Inactive organs never supply an attack or interaction, but their result retains the evidence. */
	public Set<SurgicalAssembly.CombinationMember> members(BionicTrait trait) {
		return has(trait) ? result(trait).members() : Set.of();
	}
	public Set<ResourceLocation> immuneEffects() { return result(BionicTrait.IMMUNE_EFFECTS).immuneEffects(); }
	public boolean isImmuneTo(ResourceLocation effect) { return effect != null && immuneEffects().contains(effect); }
	public double parameter(BionicTrait trait, String key, double fallback) {
		BionicTraitData definition = data.get(trait);
		return (definition == null ? BionicTraitData.defaults(trait) : definition).parameter(key, fallback);
	}
	public int maxAirSupply() {
		return has(BionicTrait.LONG_BREATH) ? (int) parameter(BionicTrait.LONG_BREATH, "max_air_supply", 4800) : 300;
	}
}
