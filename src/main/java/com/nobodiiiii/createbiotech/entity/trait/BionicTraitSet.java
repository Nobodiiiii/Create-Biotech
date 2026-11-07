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
	private final Set<SurgicalAssembly.CombinationMember> wings;

	BionicTraitSet(Map<BionicTrait, BionicTraitResult> results, Map<BionicTrait, BionicTraitData> data) {
		this.results = Map.copyOf(results);
		this.data = Map.copyOf(data);
		var wingMembers = new java.util.HashSet<SurgicalAssembly.CombinationMember>();
		for (var trait : java.util.List.of(BionicTrait.WING_FLIGHT, BionicTrait.WING_SLOW_FALL)) {
			var result = this.results.get(trait);
			if (result != null && (result.active() || result.inactiveReason() == BionicTraitResult.InactiveReason.MISSING_PAIRED_WINGS
				|| result.inactiveReason() == BionicTraitResult.InactiveReason.PURPOSE_MISMATCH
				|| result.inactiveReason() == BionicTraitResult.InactiveReason.BODY_MEASUREMENT_UNAVAILABLE)) wingMembers.addAll(result.members());
		}
		wings = Set.copyOf(wingMembers);
	}
	/** A qualifying wing remains a wing when the assembled body exceeds its lift or wing-count limit. */
	public Set<SurgicalAssembly.CombinationMember> wingMembers() { return wings; }

	public BionicTraitResult result(BionicTrait trait) { return results.getOrDefault(trait, BionicTraitResult.ABSENT); }
	public boolean has(BionicTrait trait) { return result(trait).active(); }
	/** Whole-body/torso ratio, or the highest individual chain ratio for limb abilities. */
	public double coverage(BionicTrait trait) { return result(trait).coverage(); }
	public double strength(BionicTrait trait) { return result(trait).strength(); }
	public double value(BionicTrait trait) { return result(trait).value(); }
	public boolean hasVariant(BionicTrait trait, String id) {
		return has(trait) && result(trait).subtraits().keySet().stream().anyMatch(v -> v.id().getPath().equals(id));
	}
	public double armValue(BionicTrait trait, java.util.Collection<SurgicalAssembly.CombinationMember> arm) {
		return has(trait) ? arm.stream().mapToDouble(member -> result(trait).memberValues().getOrDefault(member, 0.0d))
			.max().orElse(0) : 0;
	}
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
}
