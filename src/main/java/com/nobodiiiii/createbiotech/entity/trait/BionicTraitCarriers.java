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

	/** Bind the same retained anatomical parts for acquisition and inspection, before slot checks. */
	static BitSet bind(BionicTrait trait, BionicTraitData data, BionicTissue tissue, int source,
		BionicTraitDonors.Facts donor) {
		BitSet cubes = new BitSet();
		if (!donor.supplies(trait)) return cubes;
		if (trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET) {
			for (var id : donor.attackEffects()) {
				var effect = data.attackEffects().get(id);
				if (effect != null) cubes.or(select(effect.carrier(), data, tissue, source));
			}
		} else if (trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET && !trait.isHeadClassification()) {
			for (var id : donor.variants(trait)) {
				var variant = data.subtraits().get(id);
				if (variant != null) cubes.or(select(variant.carrier(), data, tissue, source));
			}
		} else cubes = trait == BionicTrait.RIDEABLE ? BionicSaddleCarriers.select(tissue.source(source))
			: select(trait.carrier(), data, tissue, source);
		cubes.and(tissue.source(source).presentCubes());
		return cubes;
	}

	/** Select original carrier tissue before considering how it has been installed or cut. */
	static BitSet select(BionicTrait.Carrier carrier, BionicTraitData data, BionicTissue tissue, int sourceId) {
		BitSet cubes;
		if (carrier.isAllTissue()) {
			var declared = data.carriers().get(tissue.source(sourceId).profile().entityTypeId());
			cubes = declared == null || declared.isEmpty() ? tissue.source(sourceId).presentCubes()
				: selectRoles(List.copyOf(declared.keySet()), data, tissue, sourceId);
		} else {
			cubes = selectRoles(carrier.roles(), data, tissue, sourceId);
			// Fallback concerns original anatomy, not whether a mapped organ is still retained.
			if (cubes.isEmpty()) cubes = selectRoles(carrier.fallback(), data, tissue, sourceId);
		}
		cubes.andNot(selectRoles(carrier.excluded(), data, tissue, sourceId));
		return cubes;
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
		if (names == null || !names.isEmpty() && parts.isEmpty() && names.stream().noneMatch(name -> name.startsWith("#")))
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
