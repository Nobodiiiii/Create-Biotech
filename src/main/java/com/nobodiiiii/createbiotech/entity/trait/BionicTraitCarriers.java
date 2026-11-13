package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;

/** Selects carrier cubes from already loaded data; it owns no reload listener or cache. */
final class BionicTraitCarriers {
	private BionicTraitCarriers() {}

	/** Species-specific carrier labels for JEI; an empty set denotes whole-donor tissue. */
	static Set<BionicAnatomyRole> donorRoles(BionicTrait trait, BionicTraitData data,
		ResourceLocation entity, BionicTraitDonors.Facts donor) {
		List<BionicTrait.Carrier> carriers = switch (trait.valueKind()) {
		case ATTACK_EFFECT_SET -> donor.attacks(trait).stream()
			.map(data.attackEffects()::get).map(BionicAttackEffect::carrier).toList();
		case SUBTRAIT_SET -> trait.isHeadClassification() ? List.of(trait.carrier())
			: donor.variants(trait).stream().map(data.subtraits()::get).map(BionicSubtrait::carrier).toList();
		default -> List.of(donorCarrier(trait, data, entity));
		};
		Map<BionicAnatomyRole, Set<String>> declared = data.carriers().get(entity);
		Set<BionicAnatomyRole> result = EnumSet.noneOf(BionicAnatomyRole.class);
		for (var carrier : carriers) {
			Set<BionicAnatomyRole> roles = EnumSet.noneOf(BionicAnatomyRole.class);
			if (carrier.isAllTissue()) {
				if (declared == null || declared.isEmpty()) return Set.of();
				declared.forEach((role, names) -> { if (!names.isEmpty()) roles.add(role); });
			} else if (declared != null && !declared.isEmpty()) {
				for (var role : carrier.roles())
					if (!declared.getOrDefault(role, Set.of()).isEmpty()) roles.add(role);
				if (roles.isEmpty())
					for (var role : carrier.fallback())
						if (!declared.getOrDefault(role, Set.of()).isEmpty()) roles.add(role);
			} else {
				roles.addAll(carrier.roles());
				roles.addAll(carrier.fallback());
			}
			roles.removeAll(carrier.excluded());
			result.addAll(roles);
		}
		return Set.copyOf(result);
	}

	/** Bind the same retained anatomical parts for acquisition and inspection, before slot checks. */
	static BitSet bind(BionicTrait trait, BionicTraitData data, BionicTissue tissue, int source,
		BionicTraitDonors.Facts donor) {
		BitSet cubes = new BitSet();
		if (!donor.supplies(trait)) return cubes;
		if (trait.valueKind() == BionicTrait.ValueKind.ATTACK_EFFECT_SET) {
			for (var id : donor.attacks(trait)) {
				var effect = data.attackEffects().get(id);
				if (effect != null) cubes.or(select(effect.carrier(), data, tissue, source));
			}
		} else if (trait.valueKind() == BionicTrait.ValueKind.SUBTRAIT_SET && !trait.isHeadClassification()) {
			for (var id : donor.variants(trait)) {
				var variant = data.subtraits().get(id);
				if (variant != null) cubes.or(select(variant.carrier(), data, tissue, source));
			}
		} else cubes = BionicTrait.RIDEABLE.equals(trait) ? BionicSaddleCarriers.select(tissue.source(source))
			: select(donorCarrier(trait, data, tissue.source(source).profile().entityTypeId()), data, tissue, source);
		if (!data.extensions().selectors().isEmpty()) {
			BitSet selected = new BitSet();
			for (var selector : data.extensions().selectors()) selected.or(selector.select(tissue.source(source)));
			cubes.and(selected);
		}
		cubes.and(tissue.source(source).presentCubes());
		return cubes;
	}

	/** Explicit knockback donor mappings replace the default head-and-arm carrier roles. */
	private static BionicTrait.Carrier donorCarrier(BionicTrait trait, BionicTraitData data, ResourceLocation entity) {
		return BionicTrait.ATTACK_KNOCKBACK.equals(trait) && !data.carriers().getOrDefault(entity, Map.of()).isEmpty()
			? BionicTrait.Carrier.allTissue() : trait.carrier();
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
