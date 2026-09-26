package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Effective head abilities after each trait's presence or coverage rule and conditions pass. */
public record BionicHeadTraits(Set<BionicHeadTrait> enabled, int maxAirSupply) {
	public static final BionicHeadTraits EMPTY = new BionicHeadTraits(Set.of(), 300);

	public BionicHeadTraits {
		EnumSet<BionicHeadTrait> copy = EnumSet.noneOf(BionicHeadTrait.class);
		copy.addAll(enabled);
		enabled = Collections.unmodifiableSet(copy);
		maxAirSupply = Math.max(300, Math.min(maxAirSupply, 12000));
	}

	public boolean has(BionicHeadTrait trait) { return enabled.contains(trait); }
	public boolean isEmpty() { return enabled.isEmpty(); }
}
