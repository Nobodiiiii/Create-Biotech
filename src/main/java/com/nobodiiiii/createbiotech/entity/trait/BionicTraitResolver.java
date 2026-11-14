package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DetailKind;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.DisplayEntry;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Evaluates one assembly against one data snapshot. No JSON parsing or effect execution. */
public final class BionicTraitResolver {
	private BionicTraitResolver() {}

	public record Revision(long traits, long clientOverrides) {}
	private static volatile Revision lastRevision = new Revision(-1L, -1L);

	/**
	 * Every trait query validates its cache against this, so the same instance is handed out until
	 * the unified catalog or overrides change on a reload or a rules sync.
	 */
	public static Revision revision(Level level) {
		long traits = BionicTraitRegistry.snapshot(level).generation();
		long clientOverrides = BionicTraitOverrides.clientGeneration();
		Revision last = lastRevision;
		if (last.traits != traits || last.clientOverrides != clientOverrides)
			lastRevision = last = new Revision(traits, clientOverrides);
		return last;
	}

	/** Runtime, boxes and table previews share the union of traits bound to retained assembly parts. */
	public static BionicTraitSet resolve(@Nullable SurgicalAssembly assembly, Level level) {
		if (!BionicTraits.ENABLED || assembly == null || level == null || assembly.sources().isEmpty())
			return BionicTraitSet.EMPTY;
		BionicTraitRegistry.Snapshot data = BionicTraitRegistry.snapshot(level);
		BionicTissue tissue = BionicTissue.of(assembly, data);
		List<BionicTraitDonors.Facts> donors = assembly.sources().stream()
			.map(source -> BionicTraitDonors.get(source.profile(), level, data)).toList();
		Map<BionicTrait, BionicTraitResult> results = new LinkedHashMap<>();
		for (BionicTrait trait : data.definitions()) {
			List<BitSet> bindings = new ArrayList<>();
			Set<SurgicalAssembly.CombinationMember> owners = new HashSet<>();
			for (int source = 0; source < tissue.sourceCount(); source++) {
				BitSet cubes = BionicTraitCarriers.bind(trait, data.get(trait), tissue, source, donors.get(source));
				bindings.add(cubes);
				addMembers(owners, source, cubes);
			}
			if (owners.isEmpty()) continue;
			BionicTraitResult result = evaluate(trait, data.get(trait), tissue, donors, bindings).withBindings(owners);
			if (result.present())
				results.put(trait, result);
		}
		// Conditions may depend on another trait, so evaluate them after all coverage decisions.
		BionicTraitSet covered = new BionicTraitSet(results, data.traits(), tissue.headUsesTorso());
		results.replaceAll((trait, result) -> {
			if (!result.active())
				return result;
			return BionicGameplayAnatomy.apply(trait, data.get(trait), assembly, tissue,
				BionicTraitConditions.apply(trait, data.get(trait), assembly, tissue, covered, result), valid -> {
					List<BitSet> restricted = new ArrayList<>();
					for (int source = 0; source < tissue.sourceCount(); source++) restricted.add(new BitSet());
					valid.forEach(member -> restricted.get(member.source()).set(member.cube()));
					return evaluate(trait, data.get(trait), tissue, donors, restricted);
				})
				.withBindings(result.bindings());
		});
		applyExtensionConditions(results, data, assembly, level, tissue.headUsesTorso());
		// Preserve anatomical contributions before behavior replacement removes their labels.
		double tamingChance = tamingChance(results, tissue);
		BionicTraitOverrides.apply(results, data.traits(), level.isClientSide);
		applyExtensionConditions(results, data, assembly, level, tissue.headUsesTorso());
		// Suppressed slow falling does not turn anatomical wings into ordinary attack arms.
		return new BionicTraitSet(results, data.traits(), tissue.headUsesTorso(), covered.wingMembers(),
			tamingChance);
	}

	private static void applyExtensionConditions(Map<BionicTrait, BionicTraitResult> results,
		BionicTraitRegistry.Snapshot data, SurgicalAssembly assembly, Level level, boolean headUsesTorso) {
		if (results.keySet().stream().noneMatch(t -> !data.get(t).extensions().conditions().isEmpty())) return;
		// Conditions may only disable: converge dependencies after native prerequisites, before overrides.
		for (int pass = 0; pass < results.size(); pass++) {
			BionicTraitSet conditioned = new BionicTraitSet(results, data.traits(), headUsesTorso);
			boolean changed = false;
			for (var entry : results.entrySet()) {
				if (!entry.getValue().active()) continue;
				var context = new com.nobodiiiii.createbiotech.api.bionic.BionicCondition.Context(
					assembly, level, entry.getKey(), conditioned, entry.getValue());
				if (data.get(entry.getKey()).extensions().conditions().stream().anyMatch(condition -> !condition.matches(context))) {
					entry.setValue(entry.getValue().disabled(InactiveReason.EXTENSION_CONDITION));
					changed = true;
				}
			}
			if (!changed) break;
		}
	}

	private static double tamingChance(Map<BionicTrait, BionicTraitResult> results, BionicTissue tissue) {
		Set<SurgicalAssembly.CombinationMember> carriers = new HashSet<>();
		// Behavior suppression must not discard tameable tissue from the probability's numerator.
		for (BionicTrait trait : List.of(BionicTrait.TAMEABLE, BionicTrait.LIVESTOCK_TAMEABLE)) {
			BionicTraitResult result = results.getOrDefault(trait, BionicTraitResult.ABSENT);
			if (result.active()) carriers.addAll(result.members());
		}
		return carriers.isEmpty() ? 0.0d
			: evaluateLimbs(BionicTrait.TAMEABLE, BionicTraitRule.presence(), tissue, carriers, false).coverage();
	}

	/** Donor tissue properties for box inspection; this does not claim organs are installed. */
	public static BionicTraitSet donorProperties(BionicTraitDonors.Facts donor, ResourceLocation donorType, Level level) {
		if (!BionicTraits.ENABLED)
			return BionicTraitSet.EMPTY;
		BionicTraitRegistry.Snapshot data = BionicTraitRegistry.snapshot(level);
		Map<BionicTrait, BionicTraitResult> results = new LinkedHashMap<>();
		for (BionicTrait trait : data.definitions()) {
			if (BionicTrait.MILKABLE.equals(trait) && donor.has(trait)) {
				results.put(trait, BionicMilking.donorProperties(donorType));
				continue;
			}
			if (!trait.carrier().isAllTissue() && !trait.isHeadClassification())
				continue;
			boolean present = switch (trait.valueKind()) {
			case ABILITY -> donor.has(trait);
			case NUMBER -> numericValue(trait, donor.value(trait)) != 0.0d;
			case SUBTRAIT_SET -> !donor.variants(trait).isEmpty();
			case EFFECT_SET -> !donor.effects(trait).isEmpty();
			case ATTACK_EFFECT_SET -> !donor.attacks(trait).isEmpty();
			};
			if (present && trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET) {
				Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> variants = new HashMap<>();
				var definition = data.get(trait);
				Set<ResourceLocation> ids = trait.isHeadClassification()
					? Set.of(BionicHeadTraits.combined(trait, donor.variants(trait))) : donor.variants(trait);
				for (ResourceLocation id : ids)
					variants.put(definition.subtraits().get(id), Set.of());
				results.put(trait, new BionicTraitResult(1, 1, 0, Set.of(), Set.of(), null,
					Map.of(), Set.of(), variants, Map.of(), Set.of()));
			} else if (present)
				results.put(trait, new BionicTraitResult(1, 1, numericValue(trait, donor.value(trait)),
					trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET ? donor.effects(trait) : Set.of(),
					Set.of(), null));
		}
		BionicTraitOverrides.apply(results, data.traits(), level.isClientSide);
		return new BionicTraitSet(results, data.traits(), false);
	}

	private static BionicTraitResult evaluate(BionicTrait trait, BionicTraitData data,
		BionicTissue tissue, List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		if (trait.valueKind() == BionicTrait.ValueKind.NUMBER)
			return evaluateNumbers(trait, tissue, donors, bindings);
		if (trait.isHeadClassification())
			return BionicHeadTraits.evaluate(trait, data, tissue, donors, bindings);
		if (trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET)
			return evaluateSubtraits(trait, data, tissue, donors, bindings);
		if (trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET)
			return evaluateAttacks(trait, data, tissue, donors, bindings);
		double total = 0, contributing = 0;
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		Map<ResourceLocation, Double> effectVolumes = new HashMap<>();
		Set<ResourceLocation> commonEffects = null;
		Map<ResourceLocation, Set<SurgicalAssembly.CombinationMember>> detailCarriers = new HashMap<>();
		boolean misplaced = false;
		for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++) {
			BitSet cubes = tissue.selected(sourceId, trait.scopes());
			double volume = tissue.weight(sourceId, cubes);
			total += volume;
			BionicTraitDonors.Facts donor = donors.get(sourceId);
			BitSet carriers = (BitSet) bindings.get(sourceId).clone();
			Set<ResourceLocation> details = switch (trait.id().toString()) {
			case "create_biotech:deterrence" -> donor.deterrenceTargets();
			case "create_biotech:hunting_instinct" -> BionicGameplayRelations.targets(data.extensions().gameplay(), tissue.source(sourceId).profile().entityTypeId(),
				BionicHunting.targets(Set.of(tissue.source(sourceId).profile().entityTypeId())));
			case "create_biotech:pack_alert" -> donor.packAlertTargets();
			case "create_biotech:immune_effects" -> donor.effects(trait);
			default -> trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET ? donor.effects(trait) : Set.of();
			};
			if (!carriers.isEmpty())
				for (var id : details)
					addMembers(detailCarriers.computeIfAbsent(id, ignored -> new HashSet<>()), sourceId, carriers);
			BitSet stray = (BitSet) carriers.clone();
			stray.andNot(cubes);
			misplaced |= !stray.isEmpty();
			carriers.and(cubes);
			double carrierVolume = tissue.weight(sourceId, carriers);
			contributing += carrierVolume;
			addMembers(members, sourceId, carriers);
			if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET && volume > 0.0d) {
				for (ResourceLocation effect : donor.effects(trait))
					effectVolumes.merge(effect, carrierVolume, Double::sum);
				if (commonEffects == null)
					commonEffects = new HashSet<>(carrierVolume == volume ? donor.effects(trait) : Set.of());
				else
					commonEffects.retainAll(carrierVolume == volume ? donor.effects(trait) : Set.of());
			}
		}
		if (members.isEmpty() && !misplaced)
			return BionicTraitResult.ABSENT;
		if (!trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
			&& !trait.scopes().contains(BionicTraitScope.TORSO)) {
			BionicTraitResult limbResult = evaluateLimbs(trait, data.rule(), tissue, members, misplaced);
			if (BionicTrait.PACK_ALERT.equals(trait))
				return limbResult.withDisplayEntries(carrierEntries(trait, data.rule(), tissue, detailCarriers, DetailKind.TARGET));
			if (BionicTrait.HUNTING_INSTINCT.equals(trait)) {
				Set<ResourceLocation> types = new HashSet<>();
				for (var member : limbResult.members())
					types.add(tissue.source(member.source()).profile().entityTypeId());
				return new BionicTraitResult(limbResult.coverage(), limbResult.strength(), 0, Set.of(),
					limbResult.members(), limbResult.inactiveReason(), Map.of(), Set.of(), Map.of(), Map.of(), types)
					.withDisplayEntries(carrierEntries(trait, data.rule(), tissue, detailCarriers, DetailKind.TARGET));
			}
			if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET) {
				var entries = carrierEntries(trait, data.rule(), tissue, detailCarriers, DetailKind.EFFECT);
				Set<ResourceLocation> effects = entries.stream().filter(e -> e.inactiveReason() == null)
					.map(DisplayEntry::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
				var evaluated = effects.isEmpty() ? limbResult.disabled(InactiveReason.INSUFFICIENT_COVERAGE)
					: new BionicTraitResult(limbResult.coverage(), limbResult.strength(), 0, effects, limbResult.members(), null);
				return evaluated.withDisplayEntries(entries);
			}
			if (!BionicTrait.DETERRENCE.equals(trait)) return limbResult;
			Set<ResourceLocation> targets = new HashSet<>();
			for (var member : limbResult.members()) targets.addAll(donors.get(member.source()).deterrenceTargets());
			return new BionicTraitResult(limbResult.coverage(), limbResult.strength(), 0, Set.of(),
				limbResult.members(), limbResult.inactiveReason(), Map.of(), limbResult.active() ? targets : Set.of())
				.withDisplayEntries(carrierEntries(trait, data.rule(), tissue, detailCarriers, DetailKind.TARGET));
		}
		double coverage = total > 0 ? contributing / total : 0;
		BionicTraitResult result = acquisition(data.rule(), coverage, members, misplaced);
		if (BionicTrait.RIDEABLE.equals(trait)) {
			int seats = members.stream().mapToInt(member -> tissue.source(member.source()).profile()
				.entityTypeId().getPath().equals("camel") ? 2 : 1).sum();
			return new BionicTraitResult(coverage, result.strength(), seats, Set.of(), members,
				result.inactiveReason());
		}
		if (!result.active())
			return result.withDisplayEntries(detailCarriers.keySet().stream().sorted()
				.map(id -> new DisplayEntry(DetailKind.EFFECT, id, result.inactiveReason())).toList());
		if (trait.valueKind() == BionicTrait.ValueKind.EFFECT_SET) {
			Set<ResourceLocation> effects = new HashSet<>();
			for (var effect : effectVolumes.entrySet()) {
				boolean unanimous = data.rule().minCoverage() < 1.0d
					|| commonEffects != null && commonEffects.contains(effect.getKey());
				if (unanimous && data.rule().isActive(true, total > 0 ? effect.getValue() / total : 0))
					effects.add(effect.getKey());
			}
			BionicTraitResult evaluated = effects.isEmpty() ? result.disabled(InactiveReason.INSUFFICIENT_COVERAGE)
				: new BionicTraitResult(coverage, result.strength(), 0, effects, members, null);
			return evaluated.withDisplayEntries(detailCarriers.keySet().stream().sorted()
				.map(id -> new DisplayEntry(DetailKind.EFFECT, id,
					effects.contains(id) ? null : InactiveReason.INSUFFICIENT_COVERAGE)).toList());
		}
		return result;
	}

	/** Each retained cube keeps its original donor's fixed share, independently of the new body. */
	private static BionicTraitResult evaluateNumbers(BionicTrait trait,
		BionicTissue tissue, List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		Map<SurgicalAssembly.CombinationMember, Double> values = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		double total = 0;
		boolean misplaced = false;
		for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++) {
			BitSet installed = tissue.selected(sourceId, trait.scopes());
			total += tissue.weight(sourceId, installed);
			BitSet carriers = (BitSet) bindings.get(sourceId).clone();
			BitSet stray = (BitSet) carriers.clone();
			stray.andNot(installed);
			misplaced |= !stray.isEmpty();
			carriers.and(installed);
			addMembers(members, sourceId, carriers);
			if (carriers.isEmpty()) continue;
			var anatomy = tissue.source(sourceId).anatomy();
			double originalVolume = anatomy.originalVolume();
			if (originalVolume <= 0) continue;
			boolean nativeArm = BionicTrait.DAMAGE_OFFSET.equals(trait);
			Map<Integer, Double> armVolumes = nativeArm ? tissue.originalArmVolumes(sourceId) : Map.of();
			double donorValue = rawNumericValue(trait, donors.get(sourceId).value(trait));
			for (int cube = carriers.nextSetBit(0); cube >= 0; cube = carriers.nextSetBit(cube + 1)) {
				double denominator = nativeArm ? armVolumes.getOrDefault(cube, 0.0d) : originalVolume;
				if (denominator <= 0) continue;
				double share = donorValue * (anatomy.volume(cube) / denominator);
				if (Double.isFinite(share) && share != 0)
					values.put(new SurgicalAssembly.CombinationMember(sourceId, cube), share);
			}
		}
		boolean regional = trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
			|| trait.scopes().contains(BionicTraitScope.TORSO);
		// min_coverage no longer supplies a budget or a threshold for numeric traits.
		BionicTraitResult placement = regional
			? acquisition(BionicTraitRule.scaled(), total > 0 ? tissue.weight(members) / total : 0, members, misplaced)
			: evaluateLimbs(trait, BionicTraitRule.scaled(), tissue, members, misplaced);
		if (!placement.active()) return placement;
		double value = 0;
		if (regional) {
			value = numericValue(trait, values.values().stream().mapToDouble(Double::doubleValue).sum());
		} else {
			// The shared label shows the strongest chain; actual attacks sum only their own cubes.
			for (var limb : tissue.limbs(trait.scopes())) {
				double local = numericValue(trait,
					limb.stream().mapToDouble(member -> values.getOrDefault(member, 0.0d)).sum());
				if (Math.abs(local) > Math.abs(value)
					|| Math.abs(local) == Math.abs(value) && local > value) value = local;
			}
		}
		return value == 0 ? BionicTraitResult.ABSENT
			: new BionicTraitResult(placement.coverage(), placement.strength(), value, Set.of(), members,
				null, Map.of(), Set.of(), Map.of(), values, Set.of());
	}

	/** Final sums below 0.1 are absent; other sums take effect rounded to one decimal place. */
	public static double numericValue(double value) {
		return roundedNumericValue(Math.max(0.0d, value));
	}

	public static double numericValue(BionicTrait trait, double value) {
		return roundedNumericValue(rawNumericValue(trait, value));
	}

	/** Keep donor pools and cube shares exact until the complete applicable region is summed. */
	private static double rawNumericValue(BionicTrait trait, double value) {
		if (!Double.isFinite(value)) return 0.0d;
		return trait.isStatOffset() ? value : Math.max(0.0d, value);
	}

	private static double roundedNumericValue(double value) {
		if (!Double.isFinite(value) || Math.abs(value) < 0.1d) return 0.0d;
		return java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
	}

	private static BionicTraitResult evaluateSubtraits(BionicTrait trait, BionicTraitData data,
		BionicTissue tissue, List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> active = new HashMap<>();
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> evidence = new HashMap<>();
		List<DisplayEntry> entries = new ArrayList<>();
		Set<SurgicalAssembly.CombinationMember> allMembers = new HashSet<>();
		double maximumCoverage = 0;
		boolean misplaced = false;
		for (BionicSubtrait variant : data.subtraits().values()) {
			Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
			double total = 0, contributing = 0;
			boolean variantMisplaced = false;
			for (int source = 0; source < tissue.sourceCount(); source++) {
				BitSet installed = tissue.selected(source, trait.scopes());
				total += tissue.weight(source, installed);
				if (!donors.get(source).variants(trait).contains(variant.id())) continue;
				BitSet carriers = BionicTraitCarriers.select(variant.carrier(), data, tissue, source);
				carriers.and(bindings.get(source));
				if (!carriers.isEmpty()) evidence.computeIfAbsent(variant, ignored -> new HashSet<>());
				BitSet stray = (BitSet) carriers.clone();
				stray.andNot(installed);
				variantMisplaced |= !stray.isEmpty();
				carriers.and(installed);
				contributing += tissue.weight(source, carriers);
				addMembers(members, source, carriers);
			}
			double coverage = total > 0 ? contributing / total : 0;
			BionicTraitResult evaluated = trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
				|| trait.scopes().contains(BionicTraitScope.TORSO)
				? acquisition(data.rule(), coverage, members, variantMisplaced)
				: evaluateLimbs(trait, data.rule(), tissue, members, variantMisplaced);
			misplaced |= variantMisplaced;
			maximumCoverage = Math.max(maximumCoverage, evaluated.coverage());
			allMembers.addAll(members);
			if (!members.isEmpty()) evidence.put(variant, members);
			if (evaluated.active()) active.put(variant, evaluated.members());
			if (evidence.containsKey(variant))
				entries.add(new DisplayEntry(DetailKind.SUBTRAIT, variant.id(), evaluated.inactiveReason()));
		}
		if (allMembers.isEmpty() && !misplaced) return BionicTraitResult.ABSENT;
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		active.values().forEach(members::addAll);
		return new BionicTraitResult(maximumCoverage, active.isEmpty() ? 0 : 1, 0, Set.of(),
			active.isEmpty() ? allMembers : members, active.isEmpty()
				? allMembers.isEmpty() ? InactiveReason.WRONG_SLOT : InactiveReason.INSUFFICIENT_COVERAGE : null,
			Map.of(), Set.of(), active.isEmpty() ? evidence : active, Map.of(), Set.of()).withDisplayEntries(entries);
	}


	private static BionicTraitResult evaluateAttacks(BionicTrait trait, BionicTraitData data, BionicTissue tissue,
		List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attacks = new HashMap<>();
		Map<ResourceLocation, Set<SurgicalAssembly.CombinationMember>> detailCarriers = new HashMap<>();
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		boolean misplaced = false;
		for (int source = 0; source < tissue.sourceCount(); source++) {
			for (ResourceLocation id : donors.get(source).attacks(trait)) {
				BionicAttackEffect effect = data.attackEffects().get(id);
				if (effect == null) continue;
				BitSet carriers = BionicTraitCarriers.select(effect.carrier(), data, tissue, source);
				carriers.and(bindings.get(source));
				BitSet installed = tissue.selected(source, trait.scopes());
				BitSet stray = (BitSet) carriers.clone();
				stray.andNot(installed);
				misplaced |= !stray.isEmpty();
				if (!carriers.isEmpty()) {
					addMembers(detailCarriers.computeIfAbsent(effect.effect(), ignored -> new HashSet<>()), source, carriers);
					carriers.and(installed);
					addMembers(attacks.computeIfAbsent(effect, ignored -> new HashSet<>()), source, carriers);
				}
			}
		}
		attacks.values().forEach(members::addAll);
		if (members.isEmpty() && !misplaced) return BionicTraitResult.ABSENT;
		double total = 0;
		for (int source = 0; source < tissue.sourceCount(); source++)
			total += tissue.weight(source, tissue.selected(source, trait.scopes()));
		BionicTraitResult result = trait.scopes().contains(BionicTraitScope.WHOLE_BODY) || trait.scopes().contains(BionicTraitScope.TORSO)
			? acquisition(data.rule(), total > 0 ? tissue.weight(members) / total : 0, members, misplaced)
			: evaluateLimbs(trait, data.rule(), tissue, members, misplaced);
		if (result.active()) attacks.entrySet().removeIf(entry -> entry.getValue().isEmpty());
		return new BionicTraitResult(result.coverage(), result.strength(), 0, Set.of(), result.members(),
			result.inactiveReason(), attacks, Set.of())
			.withDisplayEntries(carrierEntries(trait, data.rule(), tissue, detailCarriers, DetailKind.EFFECT));
	}

	/** Evaluate each item once across all its retained carriers, preserving the acquisition priority. */
	private static List<DisplayEntry> carrierEntries(BionicTrait trait, BionicTraitRule rule, BionicTissue tissue,
		Map<ResourceLocation, Set<SurgicalAssembly.CombinationMember>> carriers, DetailKind kind) {
		List<DisplayEntry> entries = new ArrayList<>();
		for (var entry : carriers.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
			boolean misplaced = false;
			double total = 0;
			for (int source = 0; source < tissue.sourceCount(); source++) {
				BitSet installed = tissue.selected(source, trait.scopes());
				total += tissue.weight(source, installed);
				for (var member : entry.getValue()) {
					if (member.source() != source) continue;
					if (installed.get(member.cube())) members.add(member);
					else misplaced = true;
				}
			}
			BionicTraitResult evaluated = trait.scopes().contains(BionicTraitScope.WHOLE_BODY)
				|| trait.scopes().contains(BionicTraitScope.TORSO)
				? acquisition(rule, total > 0 ? tissue.weight(members) / total : 0, members, misplaced)
				: evaluateLimbs(trait, rule, tissue, members, misplaced);
			entries.add(new DisplayEntry(kind, entry.getKey(), evaluated.inactiveReason()));
		}
		return entries;
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
