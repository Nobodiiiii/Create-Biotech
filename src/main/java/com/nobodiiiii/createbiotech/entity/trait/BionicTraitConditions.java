package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait.Carrier;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.MissingPart;

import net.minecraft.resources.ResourceLocation;

/** Optional acquisition conditions, separate from donor detection and coverage measurement. */
final class BionicTraitConditions {
	private BionicTraitConditions() {}

	/** Keep only complete carrier groups; separate heads cannot lend one another missing organs. */
	static BionicTraitResult apply(BionicTrait trait, BionicTraitData data, SurgicalAssembly assembly,
		BionicTissue tissue, BionicTraitSet covered, BionicTraitResult result) {
		if (trait == BionicTrait.MILKABLE)
			return BionicMilking.apply(data, tissue, result);
		List<Carrier> required = switch (trait.condition()) {
		case WOOL_AND_BODY -> List.of(Carrier.of(BionicAnatomyRole.TORSO).excluding(BionicAnatomyRole.WOOL),
			Carrier.of(BionicAnatomyRole.WOOL));
		case SHELL_AND_BODY -> List.of(Carrier.of(BionicAnatomyRole.TORSO).excluding(BionicAnatomyRole.SHELL),
			Carrier.of(BionicAnatomyRole.SHELL));
		case COMPLETE_CHEST -> List.of(Carrier.of(BionicAnatomyRole.TORSO)
			.excluding(BionicAnatomyRole.LEFT_RIBCAGE, BionicAnatomyRole.RIGHT_RIBCAGE),
			Carrier.of(BionicAnatomyRole.LEFT_RIBCAGE), Carrier.of(BionicAnatomyRole.RIGHT_RIBCAGE));
		case HEAD_AND_MOUTH -> List.of(Carrier.of(BionicAnatomyRole.HEAD_CORE).excluding(BionicAnatomyRole.MOUTH),
			Carrier.of(BionicAnatomyRole.MOUTH));
		default -> List.of();
		};
		if (required.isEmpty()) {
			InactiveReason reason = check(trait, data, assembly, tissue, covered);
			return reason == null ? result : result.disabled(reason);
		}
		// A supporting body is a shared prerequisite, never consumed by one appendage.
		Map<Carrier, Set<SurgicalAssembly.CombinationMember>> parts = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> participants = new HashSet<>();
		for (var carrier : required) {
			Set<SurgicalAssembly.CombinationMember> selected = new HashSet<>();
			for (int source = 0; source < tissue.sourceCount(); source++) {
				BitSet cubes = BionicTraitCarriers.select(carrier, data, tissue, source);
				cubes.and(tissue.selected(source, trait.scopes()));
				for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1)) {
					var member = new SurgicalAssembly.CombinationMember(source, cube);
					if (result.bindings().contains(member)) selected.add(member);
				}
			}
			parts.put(carrier, selected);
			participants.addAll(selected);
		}
		var groups = trait.scopes().contains(BionicTraitScope.TORSO) || trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
			? List.of(participants) : tissue.limbs(trait.scopes());
		Set<SurgicalAssembly.CombinationMember> complete = new HashSet<>();
		List<List<MissingPart>> missing = new ArrayList<>();
		for (var group : groups) {
			Map<ResourceLocation, Set<SurgicalAssembly.CombinationMember>> species = new HashMap<>();
			for (var member : group)
				if (participants.contains(member))
					species.computeIfAbsent(tissue.source(member.source()).profile().entityTypeId(), ignored -> new HashSet<>()).add(member);
			for (var entry : species.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
				var members = entry.getValue();
				// An explicitly empty body carrier permits this species' shell to work alone.
				boolean shellOnly = trait == BionicTrait.SHELL_DEFENSE && Set.of().equals(
					data.carriers().getOrDefault(entry.getKey(), Map.of()).get(BionicAnatomyRole.TORSO));
				List<MissingPart> absent = required.stream()
					.filter(carrier -> !shellOnly || !carrier.roles().contains(BionicAnatomyRole.TORSO))
					.filter(carrier -> members.stream().noneMatch(parts.get(carrier)::contains))
					.map(carrier -> new MissingPart(entry.getKey(), carrier.roles().getFirst())).toList();
				if (absent.isEmpty())
					members.stream().filter(result.members()::contains).forEach(complete::add);
				else
					missing.add(absent);
			}
		}
		if (complete.isEmpty())
			return result.missingRequiredParts(missing.stream().distinct()
				.sorted(java.util.Comparator.<List<MissingPart>>comparingInt(List::size)
					.thenComparing(Object::toString)).toList());
		if (trait == BionicTrait.SHELL_DEFENSE) {
			// Incomplete shell/body pairs cannot inflate another donor's damage reduction.
			double total = 0;
			// Sum all retained cube volumes, including overlapping tissue, just like other trait ratios.
			for (int source = 0; source < tissue.sourceCount(); source++)
				total += tissue.weight(source, tissue.source(source).presentCubes());
			double coverage = total > 0 ? tissue.weight(complete) / total : 0;
			InactiveReason reason = data.rule().isActive(true, coverage) ? null : InactiveReason.INSUFFICIENT_COVERAGE;
			return new BionicTraitResult(coverage, data.rule().strength(true, coverage), result.value(),
				result.immuneEffects(), complete, reason).withDisplayEntries(result.displayEntries());
		}
		return new BionicTraitResult(result.coverage(), result.strength(), result.value(), result.immuneEffects(), complete, null)
			.withDisplayEntries(result.displayEntries());
	}

	@Nullable
	static InactiveReason check(BionicTrait trait, BionicTraitData data, SurgicalAssembly assembly,
		BionicTissue tissue, BionicTraitSet covered) {
		if (data.rule().type() != BionicTraitType.COVERAGE_THRESHOLD)
			return null;
		return switch (trait.condition()) {
		case NONE, WOOL_AND_BODY, UDDER_AND_BODY, SHELL_AND_BODY, COMPLETE_CHEST, HEAD_AND_MOUTH -> null;
		case BODY_VOLUME -> !assembly.hasBodyVolume() ? InactiveReason.BODY_MEASUREMENT_UNAVAILABLE
			: assembly.bodyVolume() < data.parameter("min_body_volume", 0.5d) ? InactiveReason.PURPOSE_MISMATCH : null;
		case WING_LIFT -> {
			var members = covered.members(trait);
			if (tissue.effectiveLimbCount(trait.scopes(), members) < (int) data.parameter("min_wings", 2))
				yield InactiveReason.MISSING_PAIRED_WINGS;
			if (!assembly.hasBodyVolume())
				yield InactiveReason.BODY_MEASUREMENT_UNAVAILABLE;
			yield assembly.bodyVolume() > tissue.weight(members)
				* data.parameter("max_body_volume_per_wing_volume", 16)
					? InactiveReason.PURPOSE_MISMATCH : null;
		}
		case LIMB_COUNT -> {
			int minimum = (int) data.parameter(trait == BionicTrait.WING_SLOW_FALL ? "min_wings" : "min_legs",
				switch (trait) {
				case AGILE_LANDING, WALL_CLIMB, STRONG_JUMP, WING_SLOW_FALL -> 2;
				default -> 1;
				});
			boolean supported = tissue.effectiveLimbCount(trait.scopes(), covered.members(trait)) >= minimum;
			yield supported ? null : InactiveReason.PURPOSE_MISMATCH;
		}
		};
	}
}
