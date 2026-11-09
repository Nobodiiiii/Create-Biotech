package com.nobodiiiii.createbiotech.entity.trait;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

import net.minecraft.resources.ResourceLocation;

/** Udders are interchangeable; retained bodies determine the available products. */
final class BionicMilking {
	private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
	private static final ResourceLocation MOOSHROOM = ResourceLocation.withDefaultNamespace("mooshroom");
	private static final ResourceLocation GOAT = ResourceLocation.withDefaultNamespace("goat");
	private static final BionicSubtrait MILK = product("milk_bucket");
	private static final BionicSubtrait STEW = product("mushroom_stew");

	private BionicMilking() {}

	static BionicTraitResult apply(BionicTraitData data, BionicTissue tissue, BionicTraitResult result) {
		Set<SurgicalAssembly.CombinationMember> udders = new HashSet<>();
		Map<ResourceLocation, Set<SurgicalAssembly.CombinationMember>> bodies = new HashMap<>();
		for (int source = 0; source < tissue.sourceCount(); source++) {
			var udder = BionicTraitCarriers.select(BionicTrait.Carrier.of(BionicAnatomyRole.UDDER), data, tissue, source);
			var body = BionicTraitCarriers.select(BionicTrait.Carrier.of(BionicAnatomyRole.TORSO), data, tissue, source);
			// CowModel names both cuboids "body". An udder cannot also satisfy the body prerequisite.
			body.andNot(udder);
			var species = tissue.source(source).profile().entityTypeId();
			for (var member : result.members()) {
				if (member.source() != source) continue;
				if (udder.get(member.cube())) udders.add(member);
				if (body.get(member.cube()))
					bodies.computeIfAbsent(species, ignored -> new HashSet<>()).add(member);
			}
		}
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> products = new HashMap<>();
		if (!udders.isEmpty()) {
			addProduct(products, MILK, udders, bodies.getOrDefault(COW, Set.of()));
			addProduct(products, STEW, udders, bodies.getOrDefault(MOOSHROOM, Set.of()));
		}
		// Vanilla goat models have no separate udder cuboid.
		addProduct(products, MILK, Set.of(), bodies.getOrDefault(GOAT, Set.of()));
		if (products.isEmpty())
			return result.disabled(BionicTraitResult.InactiveReason.MISSING_REQUIRED_PARTS);
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		products.values().forEach(members::addAll);
		return new BionicTraitResult(result.coverage(), result.strength(), 0, Set.of(), members, null,
			Map.of(), Set.of(), products, Map.of(), Set.of());
	}

	/** Whole captured animals retain their native interactions, without assembly prerequisites. */
	static BionicTraitResult donorProperties(ResourceLocation type) {
		Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> products = new HashMap<>();
		products.put(MILK, Set.of());
		if (type.equals(MOOSHROOM)) products.put(STEW, Set.of());
		return new BionicTraitResult(1, 1, 0, Set.of(), Set.of(), null,
			Map.of(), Set.of(), products, Map.of(), Set.of());
	}

	private static void addProduct(Map<BionicSubtrait, Set<SurgicalAssembly.CombinationMember>> products,
		BionicSubtrait product, Set<SurgicalAssembly.CombinationMember> udders,
		Set<SurgicalAssembly.CombinationMember> bodies) {
		if (bodies.isEmpty()) return;
		Set<SurgicalAssembly.CombinationMember> members = products.computeIfAbsent(product, ignored -> new HashSet<>());
		members.addAll(udders);
		members.addAll(bodies);
	}

	private static BionicSubtrait product(String name) {
		return new BionicSubtrait(ResourceLocation.fromNamespaceAndPath("create_biotech", name),
			BionicTrait.Carrier.of(BionicAnatomyRole.UDDER), Map.of());
	}
}
