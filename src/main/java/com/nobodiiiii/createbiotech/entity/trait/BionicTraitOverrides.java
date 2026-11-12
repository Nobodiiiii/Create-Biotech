package com.nobodiiiii.createbiotech.entity.trait;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.logging.LogUtils;

/** Directed replacement rules, applied only after the overriding traits pass their own conditions. */
public final class BionicTraitOverrides {
	@Nullable
	private static Map<BionicTrait, Set<BionicTrait>> clientRules;
	private static long clientGeneration;

	private BionicTraitOverrides() {}

	public static void acceptClientRules(Map<BionicTrait, Set<BionicTrait>> rules) {
		clientRules = validate(rules);
		clientGeneration++;
	}

	public static void resetClientRules() {
		clientRules = null;
		clientGeneration++;
	}

	/** Server-synced replacement targets; absent rules have no display entry. */
	public static Set<BionicTrait> clientTargets(BionicTrait trait) {
		return clientRules == null ? Set.of() : clientRules.getOrDefault(trait, Set.of());
	}

	static long clientGeneration() { return clientGeneration; }

	/** Ignore every edge participating in a cycle; unrelated rules remain usable. */
	static Map<BionicTrait, Set<BionicTrait>> validate(Map<BionicTrait, Set<BionicTrait>> rules) {
		Map<BionicTrait, Set<BionicTrait>> valid = new EnumMap<>(BionicTrait.class);
		rules.forEach((source, targets) -> {
			Set<BionicTrait> accepted = EnumSet.noneOf(BionicTrait.class);
			for (BionicTrait target : targets) {
				if (reaches(target, source, rules, EnumSet.noneOf(BionicTrait.class)))
					LogUtils.getLogger().warn("Ignoring cyclic bionic trait override {} -> {}", source.id(), target.id());
				else
					accepted.add(target);
			}
			valid.put(source, Set.copyOf(accepted));
		});
		return Map.copyOf(valid);
	}

	private static boolean reaches(BionicTrait current, BionicTrait target,
		Map<BionicTrait, Set<BionicTrait>> rules, Set<BionicTrait> visited) {
		if (current == target) return true;
		if (!visited.add(current)) return false;
		return rules.getOrDefault(current, Set.of()).stream().anyMatch(next -> reaches(next, target, rules, visited));
	}

	static void apply(Map<BionicTrait, BionicTraitResult> results, Map<BionicTrait, BionicTraitData> data,
		boolean clientSide) {
		Map<BionicTrait, Set<BionicTrait>> rules = clientSide ? clientRules : null;
		if (rules == null) {
			rules = new EnumMap<>(BionicTrait.class);
			for (var entry : data.entrySet()) rules.put(entry.getKey(), entry.getValue().overrides());
		}
		applyRules(results, rules);
	}

	private static void applyRules(Map<BionicTrait, BionicTraitResult> results,
		Map<BionicTrait, Set<BionicTrait>> rules) {
		Set<BionicTrait> pending = EnumSet.noneOf(BionicTrait.class);
		results.forEach((trait, result) -> { if (result.active()) pending.add(trait); });
		while (!pending.isEmpty()) {
			List<BionicTrait> strongest = pending.stream().filter(target -> pending.stream()
				.noneMatch(source -> rules.getOrDefault(source, Set.of()).contains(target))).toList();
			if (strongest.isEmpty())
				throw new IllegalStateException("Cyclic bionic trait overrides were not validated");
			for (BionicTrait trait : strongest) {
				pending.remove(trait);
				for (BionicTrait target : rules.getOrDefault(trait, Set.of())) {
					results.remove(target);
					pending.remove(target);
				}
			}
		}
	}
}
