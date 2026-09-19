package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Immutable, already-aggregated whole-body traits for a donor or surgical assembly. */
public final class BionicBodyTraits {
	public static final BionicBodyTraits EMPTY = new BionicBodyTraits(Map.of(), Set.of(), 0.0d);
	private static final double COMPLETE_EPSILON = 1.0e-8d;

	private final Map<BionicBodyTrait, Double> coverage;
	private final Set<ResourceLocation> immuneEffects;
	private final double naturalArmor;

	BionicBodyTraits(Map<BionicBodyTrait, Double> coverage, Set<ResourceLocation> immuneEffects,
		double naturalArmor) {
		EnumMap<BionicBodyTrait, Double> normalized = new EnumMap<>(BionicBodyTrait.class);
		for (Map.Entry<BionicBodyTrait, Double> entry : coverage.entrySet()) {
			double value = entry.getValue() == null ? 0.0d : entry.getValue();
			if (Double.isFinite(value) && value > COMPLETE_EPSILON)
				normalized.put(entry.getKey(), Mth.clamp(value, 0.0d, 1.0d));
		}
		this.coverage = Collections.unmodifiableMap(normalized);
		this.immuneEffects = Set.copyOf(immuneEffects);
		this.naturalArmor = Double.isFinite(naturalArmor) ? Math.max(0.0d, naturalArmor) : 0.0d;
	}

	public double coverage(BionicBodyTrait trait) {
		return coverage.getOrDefault(trait, 0.0d);
	}

	public boolean has(BionicBodyTrait trait) {
		return coverage(trait) > COMPLETE_EPSILON;
	}

	public boolean fullyHas(BionicBodyTrait trait) {
		return coverage(trait) >= 1.0d - COMPLETE_EPSILON;
	}

	public Set<ResourceLocation> immuneEffects() {
		return immuneEffects;
	}

	public boolean isImmuneTo(ResourceLocation effectId) {
		return effectId != null && immuneEffects.contains(effectId);
	}

	public double naturalArmor() {
		return naturalArmor;
	}

	public boolean isEmpty() {
		return coverage.isEmpty() && immuneEffects.isEmpty() && naturalArmor <= COMPLETE_EPSILON;
	}
}
