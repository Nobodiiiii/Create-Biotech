package com.nobodiiiii.createbiotech.entity.trait;

import java.util.*;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.MissingPart;

/** OR of complete part sets, grouping candidates before testing so separate organs cannot lend parts. */
final class BionicGameplayAnatomy {
	static BionicTraitResult apply(BionicTrait trait, BionicTraitData data, SurgicalAssembly assembly,
		BionicTissue tissue, BionicTraitResult result,
		java.util.function.Function<Set<SurgicalAssembly.CombinationMember>, BionicTraitResult> evaluate) {
		var custom = data.extensions().gameplay().anatomyEvaluator;
		if (custom != null && result.active()) {
			try {
				var evaluated = Objects.requireNonNull(custom.evaluate(assembly, trait, result));
				if (!result.members().containsAll(evaluated.members())
					|| evaluated.subtraits().values().stream().anyMatch(m -> !evaluated.members().containsAll(m))
					|| evaluated.attackEffects().values().stream().anyMatch(m -> !evaluated.members().containsAll(m))
					|| !evaluated.members().containsAll(evaluated.memberValues().keySet()))
					throw new IllegalArgumentException("Anatomy evaluator created carriers: " + trait);
				return evaluated.withBindings(result.bindings());
			} catch (RuntimeException exception) {
				com.mojang.logging.LogUtils.getLogger().warn("Bionic anatomy evaluator failed for {}", trait, exception);
				return result.disabled(BionicTraitResult.InactiveReason.EXTENSION_CONDITION);
			}
		}
		var rule = data.extensions().gameplay().anatomy;
		if (rule == null || !result.active()) return result;
		Map<Object, Map<BionicAnatomyRole, Set<SurgicalAssembly.CombinationMember>>> groups = new LinkedHashMap<>();
		for (int source = 0; source < tissue.sourceCount(); source++) {
			for (var role : data.extensions().gameplay().referencedParts()) {
				var cubes = BionicTraitCarriers.select(BionicTrait.Carrier.of(role), data, tissue, source);
				cubes.and(tissue.selected(source, trait.scopes()));
				for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
					var member = new SurgicalAssembly.CombinationMember(source, cube);
					Object key = switch (rule.grouping()) {
					case "source" -> source;
					case "limb" -> Optional.ofNullable(assembly.primaryLimbOf(source, cube)).map(Object.class::cast).orElse("torso");
					case "species" -> tissue.source(source).profile().entityTypeId();
					default -> "body";
					};
					groups.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(role, r -> new HashSet<>()).add(member);
				}
			}
		}
		Set<SurgicalAssembly.CombinationMember> valid = new HashSet<>();
		List<List<MissingPart>> missing = new ArrayList<>();
		for (var parts : groups.values()) {
			var sample = parts.values().stream().flatMap(Set::stream).findFirst().orElseThrow();
			var species = tissue.source(sample.source()).profile().entityTypeId();
			for (var alternative : rule.alternatives()) {
				var absent = alternative.stream().filter(r -> !parts.containsKey(r)).map(r -> new MissingPart(species, r)).toList();
				if (absent.isEmpty()) {
					Set<SurgicalAssembly.CombinationMember> participants = new HashSet<>();
					alternative.forEach(r -> participants.addAll(parts.get(r)));
					participants.stream().filter(result.members()::contains).forEach(valid::add);
				} else missing.add(absent);
			}
		}
		if (valid.isEmpty()) return result.missingRequiredParts(missing.stream().distinct().toList());
		// Reuse the value-kind evaluator so effects, variants, per-limb numbers and displays
		// all lose the same incomplete carriers, including their coverage contributions.
		var evaluated = evaluate.apply(Set.copyOf(valid)).withBindings(result.bindings());
		if (evaluated.active() && trait.scopes().contains(BionicTraitScope.LEG) && data.minLegs() > 0
			&& tissue.effectiveLimbCount(Set.of(BionicTraitScope.LEG), evaluated.members()) < data.minLegs())
			return evaluated.disabled(BionicTraitResult.InactiveReason.PURPOSE_MISMATCH);
		return evaluated;
	}
}
