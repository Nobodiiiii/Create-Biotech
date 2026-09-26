package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/** Captured volumes, donor labels and actual installation scopes for one assembly evaluation. */
final class BionicTissue {
	private final List<SurgicalAssembly.Source> sources;
	private final List<Map<BionicAnatomyRole, BitSet>> roles;
	private final List<Map<BionicTraitScope, BitSet>> installed;
	private final BionicTraitRegistry.Snapshot data;

	private BionicTissue(SurgicalAssembly assembly, BionicTraitRegistry.Snapshot data) {
		this.data = data;
		sources = assembly.sources();
		roles = new ArrayList<>(Collections.nCopies(sources.size(), null));
		installed = new ArrayList<>(sources.size());
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			java.util.EnumMap<BionicTraitScope, BitSet> bySlot = new java.util.EnumMap<>(BionicTraitScope.class);
			for (BionicTraitScope slot : BionicTraitScope.values())
				bySlot.put(slot, new BitSet());
			BitSet present = sources.get(sourceId).presentCubes();
			bySlot.put(BionicTraitScope.WHOLE_BODY, (BitSet) present.clone());
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
				bySlot.get(BionicTraitScope.mountedAt(assembly.mountOf(sourceId, cube))).set(cube);
			installed.add(bySlot);
		}
	}

	static BionicTissue of(SurgicalAssembly assembly, BionicTraitRegistry.Snapshot data) {
		return new BionicTissue(assembly, data);
	}

	int sourceCount() { return sources.size(); }
	SurgicalAssembly.Source source(int sourceId) { return sources.get(sourceId); }

	/** Union of real installation scopes, including ordinary tissue with no carrier label. */
	BitSet selected(int sourceId, Set<BionicTraitScope> scopes) {
		BitSet cubes = new BitSet();
		for (BionicTraitScope scope : scopes)
			cubes.or(installed.get(sourceId).get(scope));
		return cubes;
	}

	/** Every original cube of this source with the role, whether or not it is still present. */
	BitSet roleCubes(int sourceId, BionicAnatomyRole role) {
		Map<BionicAnatomyRole, BitSet> known = roles.get(sourceId);
		if (known == null) {
			known = BionicAnatomyRegistry.roles(sources.get(sourceId), data);
			roles.set(sourceId, known);
		}
		BitSet cubes = known.get(role);
		return cubes == null ? new BitSet() : (BitSet) cubes.clone();
	}

	/**
	 * A trait's own JSON carriers when it declares this species and role, else every cube with the
	 * role. The donor's captured head always matches the HEAD label, regardless of installation.
	 */
	BitSet carriers(int sourceId, BionicAnatomyRole role, @Nullable BitSet named) {
		BitSet cubes = named != null ? (BitSet) named.clone() : roleCubes(sourceId, role);
		if (role == BionicAnatomyRole.HEAD)
			cubes.or(capturedHead(sources.get(sourceId)));
		return cubes;
	}

	static BitSet capturedHead(SurgicalAssembly.Source source) {
		BitSet head = source.originalHeadCubes();
		head.or(source.headCubes());
		return head;
	}

	/**
	 * Captured volume of the cubes. A source captured before volumes were recorded weighs nothing,
	 * because cube counts are not a unit of tissue.
	 */
	double weight(int sourceId, BitSet cubes) {
		return sources.get(sourceId).anatomy().volume(cubes);
	}

	/** Captured volume of assembly members. */
	double weight(Set<SurgicalAssembly.CombinationMember> members) {
		double weight = 0.0d;
		for (SurgicalAssembly.CombinationMember member : members)
			if (member.source() >= 0 && member.source() < sources.size()) {
				double volume = sources.get(member.source()).anatomy().volume(member.cube());
				if (volume > 0.0d)
					weight += volume;
			}
		return weight;
	}

}
