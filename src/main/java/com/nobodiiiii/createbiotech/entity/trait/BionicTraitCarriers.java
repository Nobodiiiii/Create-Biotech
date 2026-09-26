package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;

/** Selects carrier cubes from already loaded data; it owns no reload listener or cache. */
final class BionicTraitCarriers {
	private BionicTraitCarriers() {}

	/** Select original carrier tissue before considering how it has been installed or cut. */
	static BitSet select(BionicTrait.Carrier carrier, BionicTraitData data, BionicTissue tissue, int sourceId) {
		if (carrier.isAllTissue())
			return tissue.source(sourceId).presentCubes();
		BitSet cubes = selectRoles(carrier.roles(), data, tissue, sourceId);
		// A cut-off mapped gill must not fall back to an ordinary head. Fallback concerns the
		// donor's original anatomy, so intersect with retained/installed tissue only afterwards.
		return cubes.isEmpty() ? selectRoles(carrier.fallback(), data, tissue, sourceId) : cubes;
	}

	private static BitSet selectRoles(List<BionicAnatomyRole> roles, BionicTraitData data,
		BionicTissue tissue, int sourceId) {
		SurgicalAssembly.Source source = tissue.source(sourceId);
		BitSet cubes = new BitSet();
		for (BionicAnatomyRole role : roles)
			cubes.or(tissue.carriers(sourceId, role, cubesFor(data, source.profile().entityTypeId(),
				source.anatomy().parts(), source.cubeCount(), role)));
		return cubes;
	}

	static Map<BionicAnatomyRole, BitSet> rolesFor(BionicTraitRegistry.Snapshot data,
		ResourceLocation entity, Map<Integer, Set<String>> parts, int cubeCount) {
		EnumMap<BionicAnatomyRole, BitSet> result = new EnumMap<>(BionicAnatomyRole.class);
		data.traits().values().forEach(trait -> trait.carriers().getOrDefault(entity, Map.of())
			.forEach((role, names) -> result.computeIfAbsent(role, ignored -> new BitSet())
				.or(selected(names, parts, cubeCount))));
		return result;
	}

	/** Null preserves saved-role/template fallback when no usable carrier mapping was supplied. */
	@Nullable
	static BitSet cubesFor(BionicTraitData data, ResourceLocation entity,
		Map<Integer, Set<String>> parts, int cubeCount, BionicAnatomyRole role) {
		Set<String> names = data.carriers().getOrDefault(entity, Map.of()).get(role);
		if (names == null || parts.isEmpty() && names.stream().noneMatch(name -> name.startsWith("#")))
			return null;
		return selected(names, parts, cubeCount);
	}

	private static BitSet selected(Set<String> names, Map<Integer, Set<String>> parts, int cubeCount) {
		BitSet result = new BitSet();
		for (String name : names)
			if (name.startsWith("#")) {
				int cube = Integer.parseInt(name.substring(1));
				if (cube >= 0 && cube < cubeCount)
					result.set(cube);
			}
		parts.forEach((cube, labels) -> {
			if (cube >= 0 && cube < cubeCount && labels.stream().anyMatch(names::contains))
				result.set(cube);
		});
		return result;
	}
}
