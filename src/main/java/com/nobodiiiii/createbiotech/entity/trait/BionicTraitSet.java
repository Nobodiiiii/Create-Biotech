package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import net.minecraft.resources.ResourceLocation;

/** One immutable assembly evaluation, including inactive reasons and the data used to resolve it. */
public final class BionicTraitSet {
	public static final BionicTraitSet EMPTY = new BionicTraitSet(Map.of(), Map.of(), false);
	private final Map<BionicTrait, BionicTraitResult> results;
	private final Map<BionicTrait, BionicTraitData> data;
	private final Set<SurgicalAssembly.CombinationMember> wings;
	/** The active subset of {@code results}; results are immutable, so it cannot go stale. */
	private final Set<BionicTrait> active = new LinkedHashSet<>();
	private final boolean headUsesTorso;
	private final double tamingChance;

	BionicTraitSet(Map<BionicTrait, BionicTraitResult> results, Map<BionicTrait, BionicTraitData> data,
		boolean headUsesTorso) {
		this(results, data, headUsesTorso, Set.of(), 0.0d);
	}

	BionicTraitSet(Map<BionicTrait, BionicTraitResult> results, Map<BionicTrait, BionicTraitData> data,
		boolean headUsesTorso, Set<SurgicalAssembly.CombinationMember> qualifyingWings, double tamingChance) {
		this.results = Map.copyOf(results);
		this.results.forEach((trait, result) -> {
			if (result.active())
				active.add(trait);
		});
		this.data = Map.copyOf(data);
		this.headUsesTorso = headUsesTorso;
		this.tamingChance = Double.isFinite(tamingChance) ? Math.clamp(tamingChance, 0.0d, 1.0d) : 0.0d;
		var wingMembers = new java.util.HashSet<>(qualifyingWings);
		for (var trait : List.of(BionicTrait.WING_FLIGHT, BionicTrait.WING_SLOW_FALL)) {
			var result = this.results.get(trait);
			if (result != null && (result.active() || result.inactiveReason() == BionicTraitResult.InactiveReason.MISSING_PAIRED_WINGS
				|| result.inactiveReason() == BionicTraitResult.InactiveReason.PURPOSE_MISMATCH
				|| result.inactiveReason() == BionicTraitResult.InactiveReason.BODY_MEASUREMENT_UNAVAILABLE)) wingMembers.addAll(result.members());
		}
		wings = Set.copyOf(wingMembers);
	}
	/** A qualifying wing remains a wing when the assembled body exceeds its lift or wing-count limit. */
	public Set<SurgicalAssembly.CombinationMember> wingMembers() { return wings; }
	/** True only for assembly evaluations with no installed head; donor facts have no such scope. */
	public boolean headUsesTorso() { return headUsesTorso; }

	/** Active wings and levitating torso tissue, counted once against all retained captured tissue. */
	public double flightVolumeRatio(SurgicalAssembly assembly) {
		var carriers = new java.util.HashSet<>(members(BionicTrait.WING_FLIGHT));
		carriers.addAll(members(BionicTrait.WINGLESS_FLIGHT));
		if (carriers.isEmpty()) return 0.0d;
		double totalVolume = 0.0d;
		for (var source : assembly.sources())
			totalVolume += source.anatomy().volume(source.presentCubes());
		if (!Double.isFinite(totalVolume) || totalVolume <= 0.0d) return 0.0d;
		double flightVolume = 0.0d;
		for (var member : carriers)
			if (member.source() >= 0 && member.source() < assembly.sources().size()) {
				double volume = assembly.sources().get(member.source()).anatomy().volume(member.cube());
				if (Double.isFinite(volume) && volume > 0.0d) flightVolume += volume;
			}
		return Math.clamp(flightVolume / totalVolume, 0.0d, 1.0d);
	}

	public List<BionicTrait> definitions() { return data.keySet().stream().sorted().toList(); }
	public BionicTraitResult result(BionicTrait trait) { return results.getOrDefault(trait, BionicTraitResult.ABSENT); }
	public boolean has(BionicTrait trait) { return active.contains(trait); }
	public boolean isTameable() { return has(BionicTrait.TAMEABLE) || has(BionicTrait.LIVESTOCK_TAMEABLE); }
	/** Runtime roll only; kept separate from trait values and tooltip strength. */
	public double tamingChance() { return tamingChance; }
	public boolean hasStrongJump() { return has(BionicTrait.STRONG_JUMP); }
	public double strongJumpBonus() {
		return hasStrongJump() ? parameter(BionicTrait.STRONG_JUMP, "jump_bonus", 0.2d) : 0;
	}
	/** Whole-body/torso ratio, or the highest individual chain ratio for limb abilities. */
	public double coverage(BionicTrait trait) { return result(trait).coverage(); }
	public double strength(BionicTrait trait) { return result(trait).strength(); }
	public double value(BionicTrait trait) { return result(trait).value(); }
	public boolean hasVariant(BionicTrait trait, ResourceLocation id) {
		return has(trait) && result(trait).subtraits().keySet().stream().anyMatch(v -> v.id().equals(id));
	}
	public double armValue(BionicTrait trait, java.util.Collection<SurgicalAssembly.CombinationMember> arm) {
		return has(trait) ? BionicTraitResolver.numericValue(trait, arm.stream().distinct()
			.mapToDouble(member -> result(trait).memberValues().getOrDefault(member, 0.0d)).sum()) : 0;
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
