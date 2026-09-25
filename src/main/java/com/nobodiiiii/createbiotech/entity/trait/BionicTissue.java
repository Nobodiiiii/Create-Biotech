package com.nobodiiiii.createbiotech.entity.trait;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

/**
 * Per-cube tissue weights, original anatomical roles and installed places of one assembly. Every
 * trait rule measures coverage over it: whole-body traits over the torso, head and organ traits over
 * one region. A cube belongs to a region only while its role is installed in that role's place.
 */
final class BionicTissue {
	private static final double COVERAGE_EPSILON = 1.0e-8d;
	private final List<SurgicalAssembly.Source> sources;
	private final List<Map<BionicAnatomyRole, BitSet>> roles;
	private final List<Map<BionicTraitSlot, BitSet>> installed;

	private BionicTissue(SurgicalAssembly assembly) {
		sources = assembly.sources();
		roles = new ArrayList<>(Collections.nCopies(sources.size(), null));
		installed = new ArrayList<>(sources.size());
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			java.util.EnumMap<BionicTraitSlot, BitSet> bySlot = new java.util.EnumMap<>(BionicTraitSlot.class);
			for (BionicTraitSlot slot : BionicTraitSlot.values())
				bySlot.put(slot, new BitSet());
			BitSet present = sources.get(sourceId).presentCubes();
			for (int cube = present.nextSetBit(0); cube >= 0; cube = present.nextSetBit(cube + 1))
				bySlot.get(BionicTraitSlot.mountedAt(assembly.mountOf(sourceId, cube))).set(cube);
			installed.add(bySlot);
		}
	}

	static BionicTissue of(SurgicalAssembly assembly) {
		return new BionicTissue(assembly);
	}

	int sourceCount() { return sources.size(); }
	SurgicalAssembly.Source source(int sourceId) { return sources.get(sourceId); }

	/** Present cubes of this source installed in the place. */
	BitSet installed(int sourceId, BionicTraitSlot slot) {
		return (BitSet) installed.get(sourceId).get(slot).clone();
	}

	/** Every original cube of this source with the role, whether or not it is still present. */
	BitSet roleCubes(int sourceId, BionicAnatomyRole role) {
		Map<BionicAnatomyRole, BitSet> known = roles.get(sourceId);
		if (known == null) {
			known = BionicAnatomyRegistry.roles(sources.get(sourceId));
			roles.set(sourceId, known);
		}
		BitSet cubes = known.get(role);
		return cubes == null ? new BitSet() : (BitSet) cubes.clone();
	}

	/**
	 * A trait's own JSON carriers when it declares this species and role, else every cube with the
	 * role. The captured head is always head tissue, even when a declared part name does not match.
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

	/**
	 * Weighted share of one region. Its denominator is every present cube with any requested role
	 * installed in that role's place, independently of whether that cube's donor has the trait; its
	 * numerator is the trait carriers within that fixed region. Which donor a cube came from and
	 * whether it is connected do not matter.
	 */
	Share share(BionicAnatomyRole[] region, IntFunction<BitSet> carriersBySource) {
		double regionWeight = 0.0d;
		double carrierWeight = 0.0d;
		boolean misplaced = false;
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			BitSet present = sources.get(sourceId).presentCubes();
			BitSet regionCubes = new BitSet();
			BitSet anywhere = new BitSet();
			for (BionicAnatomyRole role : region) {
				BitSet cubes = roleCubes(sourceId, role);
				anywhere.or(cubes);
				cubes.and(installed.get(sourceId).get(BionicTraitSlot.of(role)));
				regionCubes.or(cubes);
			}
			regionCubes.and(present);
			anywhere.and(present);
			BitSet carriers = carriersBySource.apply(sourceId);
			BitSet carried = (BitSet) carriers.clone();
			carried.and(regionCubes);
			BitSet stray = (BitSet) carriers.clone();
			stray.and(anywhere);
			stray.andNot(regionCubes);
			misplaced |= !stray.isEmpty();
			regionWeight += weight(sourceId, regionCubes);
			carrierWeight += weight(sourceId, carried);
			for (int cube = carried.nextSetBit(0); cube >= 0; cube = carried.nextSetBit(cube + 1))
				members.add(new SurgicalAssembly.CombinationMember(sourceId, cube));
		}
		return new Share(regionWeight > 0.0d ? carrierWeight / regionWeight : 0.0d, carrierWeight,
			members, misplaced);
	}

	/**
	 * Coverage of one region, and the volume and exact present cubes carrying the trait there.
	 * {@code misplaced} reports carriers that are present but installed outside their place.
	 */
	record Share(double coverage, double carrierVolume,
		Set<SurgicalAssembly.CombinationMember> members, boolean misplaced) {
		Share {
			members = Set.copyOf(members);
		}

		boolean reaches(double minimum) {
			return carrierVolume > 0.0d && coverage + COVERAGE_EPSILON >= minimum;
		}
	}
}
