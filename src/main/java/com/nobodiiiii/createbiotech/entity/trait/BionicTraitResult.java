package com.nobodiiiii.createbiotech.entity.trait;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import net.minecraft.resources.ResourceLocation;

/** One evaluated trait. UI and effects consume this same decision and its evidence. */
public record BionicTraitResult(double coverage, double strength, double value,
	Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
	@Nullable InactiveReason inactiveReason,
	Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
	Set<ResourceLocation> deterrenceTargets,
	Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> subtraits,
	Map<SurgicalAssembly.CombinationMember, Double> memberValues, Set<ResourceLocation> sourceTypes,
	Set<SurgicalAssembly.CombinationMember> bindings, List<List<MissingPart>> missingParts,
	List<DisplayEntry> displayEntries) {
	public BionicTraitResult(double coverage, double strength, double value,
		Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
		@Nullable InactiveReason inactiveReason,
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
		Set<ResourceLocation> deterrenceTargets,
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> subtraits,
		Map<SurgicalAssembly.CombinationMember, Double> memberValues, Set<ResourceLocation> sourceTypes,
		Set<SurgicalAssembly.CombinationMember> bindings, List<List<MissingPart>> missingParts) {
		this(coverage, strength, value, immuneEffects, members, inactiveReason, attackEffects,
			deterrenceTargets, subtraits, memberValues, sourceTypes, bindings, missingParts,
			defaultDisplayEntries(immuneEffects, attackEffects, deterrenceTargets, subtraits, inactiveReason, missingParts));
	}

	public BionicTraitResult(double coverage, double strength, double value,
		Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
		@Nullable InactiveReason inactiveReason,
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
		Set<ResourceLocation> deterrenceTargets,
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> subtraits,
		Map<SurgicalAssembly.CombinationMember, Double> memberValues, Set<ResourceLocation> sourceTypes,
		Set<SurgicalAssembly.CombinationMember> bindings) {
		this(coverage, strength, value, immuneEffects, members, inactiveReason, attackEffects,
			deterrenceTargets, subtraits, memberValues, sourceTypes, bindings, List.of());
	}

	public BionicTraitResult(double coverage, double strength, double value,
		Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
		@Nullable InactiveReason inactiveReason,
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
		Set<ResourceLocation> deterrenceTargets,
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> subtraits,
		Map<SurgicalAssembly.CombinationMember, Double> memberValues, Set<ResourceLocation> sourceTypes) {
		this(coverage, strength, value, immuneEffects, members, inactiveReason, attackEffects,
			deterrenceTargets, subtraits, memberValues, sourceTypes, members);
	}

	public BionicTraitResult(double coverage, double strength, double value,
		Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
		@Nullable InactiveReason inactiveReason,
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
		Set<ResourceLocation> deterrenceTargets) {
		this(coverage, strength, value, immuneEffects, members, inactiveReason, attackEffects, deterrenceTargets,
			Map.of(), Map.of(), Set.of());
	}
	public static final BionicTraitResult ABSENT = new BionicTraitResult(0, 0, 0,
		Set.of(), Set.of(), InactiveReason.NO_CARRIER);

	public BionicTraitResult(double coverage, double strength, double value,
		Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
		@Nullable InactiveReason inactiveReason) {
		this(coverage, strength, value, immuneEffects, members, inactiveReason, Map.of(), Set.of());
	}

	public BionicTraitResult {
		coverage = Double.isFinite(coverage) ? Math.clamp(coverage, 0.0d, 1.0d) : 0.0d;
		strength = Double.isFinite(strength) ? Math.clamp(strength, 0.0d, 1.0d) : 0.0d;
		value = Double.isFinite(value) ? Math.max(0.0d, value) : 0.0d;
		immuneEffects = Set.copyOf(immuneEffects);
		members = Set.copyOf(members);
		bindings = Set.copyOf(bindings);
		missingParts = missingParts.stream().map(List::copyOf).distinct().toList();
		displayEntries = List.copyOf(displayEntries);
		deterrenceTargets = Set.copyOf(deterrenceTargets);
		memberValues = Map.copyOf(memberValues);
		sourceTypes = Set.copyOf(sourceTypes);
		subtraits = subtraits.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
			Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
		attackEffects = attackEffects.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
			Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
	}

	public boolean active() { return inactiveReason == null; }
	public boolean present() { return inactiveReason != InactiveReason.NO_CARRIER; }

	/** Physical ownership survives failed coverage/conditions; it never changes runtime members. */
	public BionicTraitResult withBindings(Set<SurgicalAssembly.CombinationMember> bindings) {
		return new BionicTraitResult(coverage, strength, value, immuneEffects, members, inactiveReason,
			attackEffects, deterrenceTargets, subtraits, memberValues, sourceTypes, bindings, missingParts, displayEntries);
	}

	/** Display evidence never grants a runtime effect, variant, target or contributing member. */
	public BionicTraitResult withDisplayEntries(List<DisplayEntry> entries) {
		return new BionicTraitResult(coverage, strength, value, immuneEffects, members, inactiveReason,
			attackEffects, deterrenceTargets, subtraits, memberValues, sourceTypes, bindings, missingParts, entries);
	}

	public BionicTraitResult disabled(InactiveReason reason) {
		if (!active()) return this;
		return new BionicTraitResult(coverage, 0, 0, Set.of(), members, reason).withBindings(bindings)
			.withDisplayEntries(displayEntries.stream().map(entry -> entry.disabled(reason)).toList());
	}

	/** Each inner list completes one candidate group; different lists are alternatives. */
	public BionicTraitResult missingRequiredParts(List<List<MissingPart>> alternatives) {
		if (!active()) return this;
		return new BionicTraitResult(coverage, 0, 0, Set.of(), members, InactiveReason.MISSING_REQUIRED_PARTS,
			Map.of(), Set.of(), Map.of(), Map.of(), Set.of(), bindings, alternatives,
			displayEntries.stream().map(entry -> entry.disabled(InactiveReason.MISSING_REQUIRED_PARTS, alternatives)).toList());
	}

	public record MissingPart(ResourceLocation species, BionicAnatomyRole role) {}

	public enum DetailKind { TARGET, EFFECT, SUBTRAIT }

	/** One independently evaluated item; a later phase cannot replace an earlier failure. */
	public record DisplayEntry(DetailKind kind, ResourceLocation id, @Nullable InactiveReason inactiveReason,
		List<List<MissingPart>> missingParts) {
		public DisplayEntry(DetailKind kind, ResourceLocation id, @Nullable InactiveReason inactiveReason) {
			this(kind, id, inactiveReason, List.of());
		}
		public DisplayEntry { missingParts = missingParts.stream().map(List::copyOf).distinct().toList(); }
		public boolean active() { return inactiveReason == null; }
		public DisplayEntry disabled(InactiveReason reason) {
			return disabled(reason, List.of());
		}
		public DisplayEntry disabled(InactiveReason reason, List<List<MissingPart>> alternatives) {
			return active() ? new DisplayEntry(kind, id, reason, alternatives) : this;
		}
	}

	private static List<DisplayEntry> defaultDisplayEntries(Set<ResourceLocation> immuneEffects,
		Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attacks,
		Set<ResourceLocation> targets, Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> variants,
		@Nullable InactiveReason reason, List<List<MissingPart>> missingParts) {
		java.util.List<DisplayEntry> entries = new java.util.ArrayList<>();
		immuneEffects.forEach(id -> entries.add(new DisplayEntry(DetailKind.EFFECT, id, reason, missingParts)));
		attacks.keySet().forEach(effect -> entries.add(new DisplayEntry(DetailKind.EFFECT, effect.effect(), reason, missingParts)));
		targets.forEach(id -> entries.add(new DisplayEntry(DetailKind.TARGET, id, reason, missingParts)));
		variants.keySet().forEach(variant -> entries.add(new DisplayEntry(DetailKind.SUBTRAIT, variant.id(), reason, missingParts)));
		return entries.stream().distinct().toList();
	}

	public enum InactiveReason {
		NO_CARRIER, EXTENSION_CONDITION, INSUFFICIENT_COVERAGE, INCOMPLETE_HEAD, WRONG_SLOT, BODY_MEASUREMENT_UNAVAILABLE,
		PURPOSE_MISMATCH, BREATHING_NOT_REQUIRED, MISSING_PAIRED_WINGS, MISSING_REQUIRED_PARTS;

		public String descriptionId() {
			return "create_biotech.trait.inactive." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
