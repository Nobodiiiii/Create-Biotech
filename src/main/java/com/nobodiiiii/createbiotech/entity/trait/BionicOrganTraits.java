package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/** Immutable effective abilities and the exact retained cubes that supplied them. */
public final class BionicOrganTraits {
	public static final BionicOrganTraits EMPTY = new BionicOrganTraits(Map.of());
	private final Map<BionicOrganTrait, Set<SurgicalAssembly.CombinationMember>> members;

	public BionicOrganTraits(Map<BionicOrganTrait, Set<SurgicalAssembly.CombinationMember>> members) {
		EnumMap<BionicOrganTrait, Set<SurgicalAssembly.CombinationMember>> copy =
			new EnumMap<>(BionicOrganTrait.class);
		members.forEach((trait, cubes) -> {
			if (!cubes.isEmpty())
				copy.put(trait, Set.copyOf(cubes));
		});
		this.members = Collections.unmodifiableMap(copy);
	}

	public boolean has(BionicOrganTrait trait) { return members.containsKey(trait); }
	public boolean isEmpty() { return members.isEmpty(); }
	public Set<SurgicalAssembly.CombinationMember> members(BionicOrganTrait trait) {
		return members.getOrDefault(trait, Set.of());
	}
	public Set<BionicOrganTrait> enabled() {
		return members.isEmpty() ? Set.of() : EnumSet.copyOf(members.keySet());
	}
}
