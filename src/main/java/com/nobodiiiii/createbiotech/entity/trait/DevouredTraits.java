package com.nobodiiiii.createbiotech.entity.trait;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly.CombinationMember;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DetailKind;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DisplayEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A whole prey organ carries the intact donor's abilities. All evidence points at that organ,
 * so removing it removes precisely its contribution on the next normal assembly resolution. */
final class DevouredTraits {
	private DevouredTraits() {}

	static void apply(SurgicalAssembly assembly, Level level, BionicTraitRegistry.Snapshot data,
		List<BionicTraitDonors.Facts> donors, Map<BionicTrait, BionicTraitResult> results) {
		for (int index = 0; index < assembly.sources().size(); index++) {
			var source = assembly.sources().get(index);
			if (!source.isWholePrey() || !source.containsCube(0)) continue;
			var organ = new CombinationMember(index, 0);
			var donor = donors.get(index);
			var species = source.profile().entityTypeId();
			var nested = source.profile().devouredAssembly();
			var inherited = nested == null ? null : BionicTraitResolver.resolve(nested, level);
			for (var trait : data.definitions()) {
				// Devour always requires actual grounded outer and inner tissue in this body.
				if (trait.equals(BionicTrait.DEVOUR)) continue;
				BionicTraitResult whole;
				if (inherited != null) {
					if (!inherited.has(trait)) continue;
					whole = inherited.result(trait);
				} else {
					if (!donor.supplies(trait)) continue;
					whole = whole(trait, data.get(trait), donor, species);
				}
				var previous = results.getOrDefault(trait, BionicTraitResult.ABSENT);
				results.put(trait, add(previous, whole, organ));
			}
		}
	}

	private static BionicTraitResult whole(BionicTrait trait, BionicTraitData data, BionicTraitDonors.Facts facts, ResourceLocation species) {
		if (trait.equals(BionicTrait.MILKABLE)) return BionicMilking.donorProperties(species);
		Map<BionicSubtrait, Set<CombinationMember>> variants = new HashMap<>();
		facts.variants(trait).forEach(id -> variants.put(data.subtraits().get(id), Set.of()));
		Map<BionicAttackEffect, Set<CombinationMember>> attacks = new HashMap<>();
		facts.attacks(trait).forEach(id -> attacks.put(data.attackEffects().get(id), Set.of()));
		double value = trait.equals(BionicTrait.RIDEABLE) ? (species.getPath().equals("camel") ? 2 : 1) : facts.value(trait);
		var result = new BionicTraitResult(1, 1, value, facts.effects(trait), Set.of(), null, attacks,
			trait.equals(BionicTrait.DETERRENCE) ? facts.deterrenceTargets() : Set.of(), variants, Map.of(),
			trait.equals(BionicTrait.HUNTING_INSTINCT) ? Set.of(species) : Set.of());
		if (trait.equals(BionicTrait.EGG_LAYING))
			return result.withDisplayEntries(List.of(new DisplayEntry(DetailKind.SUBTRAIT, species, null)));
		if (trait.equals(BionicTrait.PACK_ALERT) || trait.equals(BionicTrait.HUNTING_INSTINCT)) {
			var targets = trait.equals(BionicTrait.PACK_ALERT) ? facts.packAlertTargets()
				: BionicGameplayRelations.targets(data.extensions().gameplay(), species, BionicHunting.targets(Set.of(species)));
			return result.withDisplayEntries(targets.stream().sorted().map(id -> new DisplayEntry(DetailKind.TARGET, id, null)).toList());
		}
		return result;
	}

	private static BionicTraitResult add(BionicTraitResult previous, BionicTraitResult acquired, CombinationMember organ) {
		var base = previous.active() ? previous : BionicTraitResult.ABSENT;
		var values = new HashMap<>(base.memberValues());
		if (acquired.value() != 0) values.put(organ, acquired.value());
		var merged = new BionicTraitResult(1, 1, base.value() + acquired.value(), union(base.immuneEffects(), acquired.immuneEffects()),
			union(base.members(), Set.of(organ)), null, bind(base.attackEffects(), acquired.attackEffects().keySet(), organ),
			union(base.deterrenceTargets(), acquired.deterrenceTargets()), bind(base.subtraits(), acquired.subtraits().keySet(), organ),
			values, union(base.sourceTypes(), acquired.sourceTypes()), union(previous.bindings(), Set.of(organ)));
		var entries = new java.util.LinkedHashSet<>(base.displayEntries());
		entries.addAll(acquired.displayEntries());
		return merged.withDisplayEntries(List.copyOf(entries));
	}

	private static <K> Map<K, Set<CombinationMember>> bind(Map<K, Set<CombinationMember>> previous, Set<K> acquired, CombinationMember organ) {
		var result = new HashMap<>(previous);
		for (var key : acquired) result.merge(key, Set.of(organ), DevouredTraits::union);
		return result;
	}

	private static <T> Set<T> union(Set<T> first, Set<T> second) {
		var result = new HashSet<>(first);
		result.addAll(second);
		return result;
	}
}
