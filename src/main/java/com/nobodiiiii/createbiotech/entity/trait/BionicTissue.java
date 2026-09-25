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
 * Per-cube tissue weights and original anatomical roles of one assembly. Every trait rule measures
 * coverage over it: whole-body traits over all present cubes, head and organ traits over one region.
 */
final class BionicTissue {
	private static final double COVERAGE_EPSILON = 1.0e-8d;
	private final List<SurgicalAssembly.Source> sources;
	private final List<Map<BionicAnatomyRole, BitSet>> roles;
	private final boolean volumetric;

	private BionicTissue(SurgicalAssembly assembly) {
		sources = assembly.sources();
		roles = new ArrayList<>(Collections.nCopies(sources.size(), null));
		// Volumes and cube counts are different units, so one source captured before volumes were
		// recorded returns the whole assembly to per-cube weighting.
		volumetric = sources.stream().allMatch(source -> source.anatomy().hasVolumes());
	}

	static BionicTissue of(SurgicalAssembly assembly) {
		return new BionicTissue(assembly);
	}

	int sourceCount() { return sources.size(); }
	SurgicalAssembly.Source source(int sourceId) { return sources.get(sourceId); }

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

	/** Captured volume of the cubes, or their count when this assembly predates volume capture. */
	double weight(int sourceId, BitSet cubes) {
		if (!volumetric)
			return cubes.cardinality();
		SurgicalAssembly.Source source = sources.get(sourceId);
		double weight = 0.0d;
		for (int cube = cubes.nextSetBit(0); cube >= 0; cube = cubes.nextSetBit(cube + 1))
			weight += source.anatomy().volume(cube);
		return weight;
	}

	/**
	 * Weighted share of one region, made of every present cube with any of the roles, that consists
	 * of trait carriers. Which donor a cube came from and whether it is connected do not matter.
	 */
	Share share(BionicAnatomyRole[] region, IntFunction<BitSet> carriersBySource) {
		double regionWeight = 0.0d;
		double carrierWeight = 0.0d;
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		for (int sourceId = 0; sourceId < sources.size(); sourceId++) {
			BitSet present = sources.get(sourceId).presentCubes();
			BitSet carried = (BitSet) carriersBySource.apply(sourceId).clone();
			carried.and(present);
			BitSet regionCubes = (BitSet) carried.clone();
			for (BionicAnatomyRole role : region)
				regionCubes.or(roleCubes(sourceId, role));
			regionCubes.and(present);
			regionWeight += weight(sourceId, regionCubes);
			carrierWeight += weight(sourceId, carried);
			for (int cube = carried.nextSetBit(0); cube >= 0; cube = carried.nextSetBit(cube + 1))
				members.add(new SurgicalAssembly.CombinationMember(sourceId, cube));
		}
		return new Share(regionWeight > 0.0d ? carrierWeight / regionWeight : 0.0d, members);
	}

	/** Coverage of one region and the exact present cubes that carry the trait there. */
	record Share(double coverage, Set<SurgicalAssembly.CombinationMember> members) {
		Share {
			members = Set.copyOf(members);
		}

		boolean reaches(double minimum) {
			return !members.isEmpty() && coverage + COVERAGE_EPSILON >= minimum;
		}
	}
}
