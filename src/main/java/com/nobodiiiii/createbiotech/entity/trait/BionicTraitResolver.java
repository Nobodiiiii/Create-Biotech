package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Evaluates one assembly against one data snapshot. No JSON parsing or effect execution. */
public final class BionicTraitResolver {
	private BionicTraitResolver() {}

	public record Revision(long traits, long anatomy) {}
	public static Revision revision() {
		return new Revision(BionicTraitRegistry.generation(), BionicAnatomyRegistry.generation());
	}

	public static BionicTraitSet resolve(@Nullable SurgicalAssembly assembly, Level level) {
		if (!BionicTraits.ENABLED || assembly == null || level == null || assembly.sources().isEmpty())
			return BionicTraitSet.EMPTY;
		BionicTraitRegistry.Snapshot data = BionicTraitRegistry.snapshot();
		BionicTissue tissue = BionicTissue.of(assembly, data);
		List<BionicTraitDonors.Facts> donors = assembly.sources().stream()
			.map(source -> BionicTraitDonors.get(source.profile(), level, data)).toList();
		EnumMap<BionicTrait, BionicTraitResult> results = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values()) {
			BionicTraitResult result = evaluate(trait, data.get(trait), tissue, donors);
			if (result.present())
				results.put(trait, result);
		}
		// Conditions may depend on another trait, so evaluate them after all coverage decisions.
		BionicTraitSet covered = new BionicTraitSet(results, data.traits());
		results.replaceAll((trait, result) -> {
			if (!result.active())
				return result;
			InactiveReason reason = BionicTraitConditions.check(trait, data.get(trait), assembly,
				tissue, covered);
			return reason == null ? result : result.disabled(reason);
		});
		return new BionicTraitSet(results, data.traits());
	}

	/** Donor tissue properties for box inspection; this does not claim organs are installed. */
	public static BionicTraitSet donorProperties(BionicTraitDonors.Facts donor) {
		if (!BionicTraits.ENABLED)
			return BionicTraitSet.EMPTY;
		EnumMap<BionicTrait, BionicTraitResult> results = new EnumMap<>(BionicTrait.class);
		for (BionicTrait trait : BionicTrait.values()) {
			if (!trait.carrier().isAllTissue())
				continue;
			boolean present = switch (trait.valueKind()) {
			case ABILITY -> donor.has(trait);
			case NUMBER -> donor.value(trait) > 0.0d;
			case EFFECT_SET -> !donor.immuneEffects().isEmpty();
			case ATTACK_EFFECT_SET -> !donor.attackEffects().isEmpty();
			};
			if (present)
				results.put(trait, new BionicTraitResult(1, 1, donor.value(trait),
					trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET ? donor.immuneEffects() : Set.of(),
					Set.of(), null));
		}
		return new BionicTraitSet(results, BionicTraitRegistry.snapshot().traits());
	}

	private static BionicTraitResult evaluate(BionicTrait trait, BionicTraitData data,
		BionicTissue tissue, List<BionicTraitDonors.Facts> donors) {
		if (trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET)
			return evaluateAttacks(data, tissue, donors);
		double total = 0, contributing = 0;
		java.util.List<NumericContribution> numericVolumes = new java.util.ArrayList<>();
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		Map<ResourceLocation, Double> effectVolumes = new HashMap<>();
		Set<ResourceLocation> commonEffects = null;
		Set<ResourceLocation> carrierTargets = new HashSet<>();
		boolean misplaced = false;
		for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++) {
			BitSet cubes = tissue.selected(sourceId, trait.scopes());
			double volume = tissue.weight(sourceId, cubes);
			total += volume;
			BionicTraitDonors.Facts donor = donors.get(sourceId);
			boolean supplies = switch (trait.valueKind()) {
			case ABILITY -> donor.has(trait);
			case NUMBER -> donor.value(trait) > 0.0d;
			case EFFECT_SET -> !donor.immuneEffects().isEmpty();
			case ATTACK_EFFECT_SET -> !donor.attackEffects().isEmpty();
			};
			BitSet carriers = supplies ? BionicTraitCarriers.select(trait.carrier(), data, tissue, sourceId)
				: new BitSet();
			carriers.and(tissue.source(sourceId).presentCubes());
			if (trait == BionicTrait.DETERRENCE && !carriers.isEmpty())
				carrierTargets.addAll(donor.deterrenceTargets());
			BitSet stray = (BitSet) carriers.clone();
			stray.andNot(cubes);
			misplaced |= !stray.isEmpty();
			carriers.and(cubes);
			double carrierVolume = tissue.weight(sourceId, carriers);
			contributing += carrierVolume;
			if (trait.valueKind() == BionicTrait.ValueKind.NUMBER)
				numericVolumes.add(new NumericContribution(donor.value(trait), carrierVolume));
			addMembers(members, sourceId, carriers);
			if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET && volume > 0.0d) {
				for (ResourceLocation effect : donor.immuneEffects())
					effectVolumes.merge(effect, carrierVolume, Double::sum);
				if (commonEffects == null)
					commonEffects = new HashSet<>(carrierVolume == volume ? donor.immuneEffects() : Set.of());
				else
					commonEffects.retainAll(carrierVolume == volume ? donor.immuneEffects() : Set.of());
			}
		}
		if (members.isEmpty() && !misplaced)
			return BionicTraitResult.ABSENT;
		if (!trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
			&& !trait.scopes().contains(BionicTraitScope.TORSO)) {
			BionicTraitResult limbResult = evaluateLimbs(trait, data.rule(), tissue, members, misplaced);
			if (trait != BionicTrait.DETERRENCE) return limbResult;
			Set<ResourceLocation> targets = new HashSet<>();
			for (var member : limbResult.members()) targets.addAll(donors.get(member.source()).deterrenceTargets());
			return new BionicTraitResult(limbResult.coverage(), limbResult.strength(), 0, Set.of(),
				limbResult.members(), limbResult.inactiveReason(), Map.of(), limbResult.active() ? targets : carrierTargets);
		}
		double coverage = total > 0 ? contributing / total : 0;
		BionicTraitResult result = acquisition(data.rule(), coverage, members, misplaced);
		if (!result.active())
			return result;
		if (trait.valueKind() == BionicTrait.ValueKind.NUMBER) {
			// Pick the highest-valued tissue first; missing positive tissue leaves zero-valued space.
			double budget = total * data.rule().minCoverage();
			double remaining = budget, weighted = 0;
			numericVolumes.sort((a, b) -> Double.compare(b.value(), a.value()));
			for (NumericContribution contribution : numericVolumes) {
				double used = Math.min(remaining, contribution.volume());
				weighted += used * contribution.value();
				remaining -= used;
				if (remaining <= 0) break;
			}
			return new BionicTraitResult(coverage, result.strength(), budget > 0 ? weighted / budget : 0,
				Set.of(), members, null);
		}
		if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET) {
			Set<ResourceLocation> effects = new HashSet<>();
			for (var effect : effectVolumes.entrySet()) {
				boolean unanimous = data.rule().minCoverage() < 1.0d
					|| commonEffects != null && commonEffects.contains(effect.getKey());
				if (unanimous && data.rule().isActive(true, total > 0 ? effect.getValue() / total : 0))
					effects.add(effect.getKey());
			}
			return effects.isEmpty() ? result.disabled(InactiveReason.INSUFFICIENT_COVERAGE)
				: new BionicTraitResult(coverage, result.strength(), 0, effects, members, null);
		}
		return result;
	}

	private record NumericContribution(double value, double volume) {}

	private static BionicTraitResult evaluateAttacks(BionicTraitData data, BionicTissue tissue,
		List<BionicTraitDonors.Facts> donors) {
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attacks = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		boolean misplaced = false;
		for (int source = 0; source < tissue.sourceCount(); source++) {
			for (ResourceLocation id : donors.get(source).attackEffects()) {
				BionicAttackEffect effect = data.attackEffects().get(id);
				if (effect == null) continue;
				BitSet carriers = BionicTraitCarriers.select(effect.carrier(), data, tissue, source);
				carriers.and(tissue.source(source).presentCubes());
				BitSet installed = tissue.selected(source, BionicTrait.EFFECT_ATTACK.scopes());
				BitSet stray = (BitSet) carriers.clone();
				stray.andNot(installed);
				misplaced |= !stray.isEmpty();
				if (!carriers.isEmpty()) {
					carriers.and(installed);
					addMembers(attacks.computeIfAbsent(effect, ignored -> new HashSet<>()), source, carriers);
				}
			}
		}
		attacks.values().forEach(members::addAll);
		if (members.isEmpty() && !misplaced) return BionicTraitResult.ABSENT;
		BionicTraitResult result = evaluateLimbs(BionicTrait.EFFECT_ATTACK, data.rule(), tissue, members, misplaced);
		if (result.active()) attacks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		return new BionicTraitResult(result.coverage(), result.strength(), 0, Set.of(), result.members(),
			result.inactiveReason(), attacks, Set.of());
	}

	/** Limb abilities use each chain's own denominator; only qualifying chains supply carriers. */
	private static BionicTraitResult evaluateLimbs(BionicTrait trait, BionicTraitRule rule,
		BionicTissue tissue, Set<SurgicalAssembly.CombinationMember> members, boolean misplaced) {
		double coverage = 0;
		Set<SurgicalAssembly.CombinationMember> effectiveMembers = new HashSet<>();
		for (Set<SurgicalAssembly.CombinationMember> limb : tissue.limbs(trait.scopes())) {
			Set<SurgicalAssembly.CombinationMember> carriers = new HashSet<>(limb);
			carriers.retainAll(members);
			double total = tissue.weight(limb);
			double localCoverage = total > 0 ? tissue.weight(carriers) / total : 0;
			coverage = Math.max(coverage, localCoverage);
			if (rule.isActive(!carriers.isEmpty(), localCoverage))
				effectiveMembers.addAll(carriers);
		}
		// Retain failed carriers as diagnostic evidence when no chain qualifies. For active traits,
		// exclude failed chains from limb counts, lift and attack effects.
		return acquisition(rule, coverage, effectiveMembers.isEmpty() ? members : effectiveMembers, misplaced);
	}

	private static BionicTraitResult acquisition(BionicTraitRule rule, double coverage,
		Set<SurgicalAssembly.CombinationMember> members, boolean misplaced) {
		boolean active = rule.isActive(!members.isEmpty(), coverage);
		double strength = rule.strength(!members.isEmpty(), coverage);
		return new BionicTraitResult(coverage, strength, 0, Set.of(), members,
			active ? null : members.isEmpty() && misplaced
				? InactiveReason.WRONG_SLOT : InactiveReason.INSUFFICIENT_COVERAGE);
	}

	private static void addMembers(Set<SurgicalAssembly.CombinationMember> members, int source, BitSet cubes) {
		for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
			members.add(new SurgicalAssembly.CombinationMember(source, cube));
	}
}
