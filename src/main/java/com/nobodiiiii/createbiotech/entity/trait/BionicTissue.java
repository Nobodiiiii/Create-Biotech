package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
	private final Map<SurgicalAssembly.Limb, Set<SurgicalAssembly.CombinationMember>> limbs = new HashMap<>();
	private final BionicTraitRegistry.Snapshot data;
	private final boolean headUsesTorso;

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
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1)) {
				SurgicalAssembly.Limb limb = assembly.primaryLimbOf(sourceId, cube);
				bySlot.get(BionicTraitScope.mountedAt(limb == null ? null : limb.type())).set(cube);
				if (limb != null)
					limbs.computeIfAbsent(limb, ignored -> new HashSet<>())
						.add(new SurgicalAssembly.CombinationMember(sourceId, cube));
			}
			installed.add(bySlot);
		}
		headUsesTorso = limbs.keySet().stream()
			.noneMatch(limb -> BionicTraitScope.mountedAt(limb.type()) == BionicTraitScope.HEAD);
	}

	static BionicTissue of(SurgicalAssembly assembly, BionicTraitRegistry.Snapshot data) {
		return new BionicTissue(assembly, data);
	}

	int sourceCount() { return sources.size(); }
	SurgicalAssembly.Source source(int sourceId) { return sources.get(sourceId); }
	boolean headUsesTorso() { return headUsesTorso; }

	/** Union of installation scopes; without an installed head, HEAD uses the entire torso. */
	BitSet selected(int sourceId, Set<BionicTraitScope> scopes) {
		BitSet cubes = new BitSet();
		for (BionicTraitScope scope : scopes)
			cubes.or(installed.get(sourceId).get(headUsesTorso && scope == BionicTraitScope.HEAD
				? BionicTraitScope.TORSO : scope));
		return cubes;
	}

	/** Separate complete chains, with one shared torso group replacing an absent head. */
	List<Set<SurgicalAssembly.CombinationMember>> limbs(Set<BionicTraitScope> scopes) {
		List<Set<SurgicalAssembly.CombinationMember>> groups = new ArrayList<>(limbs.entrySet().stream()
			.filter(entry -> scopes.contains(BionicTraitScope.mountedAt(entry.getKey().type())))
			.map(Map.Entry::getValue).toList());
		if (headUsesTorso && scopes.contains(BionicTraitScope.HEAD)) {
			Set<SurgicalAssembly.CombinationMember> torso = new HashSet<>();
			for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
				BitSet cubes = installed.get(sourceId).get(BionicTraitScope.TORSO);
				for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
					torso.add(new SurgicalAssembly.CombinationMember(sourceId, cube));
			}
			if (!torso.isEmpty()) groups.add(Set.copyOf(torso));
		}
		return groups;
	}

	/** Count only chains represented by carriers that passed their own coverage rule. */
	int effectiveLimbCount(Set<BionicTraitScope> scopes, Set<SurgicalAssembly.CombinationMember> carriers) {
		int count = 0;
		for (Set<SurgicalAssembly.CombinationMember> limb : limbs(scopes))
			if (limb.stream().anyMatch(carriers::contains))
				count++;
		return count;
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

	/** Each original native arm has its own numeric pool, including tissue removed by later cuts. */
	Map<Integer, Double> originalArmVolumes(int sourceId) {
		SurgicalAssembly.Source source = sources.get(sourceId);
		BitSet arms = roleCubes(sourceId, BionicAnatomyRole.ATTACK_HAND);
		Map<Integer, Integer> sides = new HashMap<>();
		for (int cube = arms.nextSetBit(0); cube >= 0; cube = arms.nextSetBit(cube + 1)) {
			int side = 0;
			for (String label : source.anatomy().parts().getOrDefault(cube, Set.of())) {
				String name = label.toLowerCase(java.util.Locale.ROOT);
				if (name.contains("left")) side |= 1;
				if (name.contains("right")) side |= 2;
			}
			if (side == 1 || side == 2) sides.put(cube, -side);
		}
		Map<Integer, BitSet> groups = new HashMap<>();
		int componentId = 0;
		// Original seams, never current cuts or installation joints, identify unnamed arm segments.
		for (BitSet component : SurgicalAssembly.components(source.cubeCount(), arms, source.seams(), new BitSet())) {
			Set<Integer> known = new HashSet<>();
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1))
				if (sides.containsKey(cube)) known.add(sides.get(cube));
			int fallback = known.size() == 1 ? known.iterator().next() : ++componentId;
			for (int cube = component.nextSetBit(0); cube >= 0; cube = component.nextSetBit(cube + 1)) {
				// An ambiguous bridge between both arms is not another complete native arm.
				if (!sides.containsKey(cube) && known.size() > 1) continue;
				groups.computeIfAbsent(sides.getOrDefault(cube, fallback), ignored -> new BitSet()).set(cube);
			}
		}
		Map<Integer, Double> volumes = new HashMap<>();
		for (BitSet group : groups.values()) {
			double volume = source.anatomy().volume(group);
			if (!Double.isFinite(volume) || volume <= 0) continue;
			for (int cube = group.nextSetBit(0); cube >= 0; cube = group.nextSetBit(cube + 1))
				volumes.put(cube, volume);
		}
		return volumes;
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
