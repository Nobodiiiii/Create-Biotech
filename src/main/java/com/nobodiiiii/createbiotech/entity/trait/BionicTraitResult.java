package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Set;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import net.minecraft.resources.ResourceLocation;

/** One evaluated trait. UI and effects consume this same decision and its evidence. */
public record BionicTraitResult(double coverage, double strength, double value,
	Set<ResourceLocation> immuneEffects, Set<SurgicalAssembly.CombinationMember> members,
	@Nullable InactiveReason inactiveReason,
	Map<BionicAttackEffect, Set<SurgicalAssembly.CombinationMember>> attackEffects,
	Set<ResourceLocation> deterrenceTargets) {
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
		deterrenceTargets = Set.copyOf(deterrenceTargets);
		attackEffects = attackEffects.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
			Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
	}

	public boolean active() { return inactiveReason == null; }
	public boolean present() { return inactiveReason != InactiveReason.NO_CARRIER; }

	public BionicTraitResult disabled(InactiveReason reason) {
		return new BionicTraitResult(coverage, 0, 0, Set.of(), members, reason);
	}

	public enum InactiveReason {
		NO_CARRIER, INSUFFICIENT_COVERAGE, WRONG_SLOT, BODY_MEASUREMENT_UNAVAILABLE,
		PURPOSE_MISMATCH, BREATHING_NOT_REQUIRED, MISSING_PAIRED_WINGS;

		public String descriptionId() {
			return "create_biotech.trait.inactive." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}
}
