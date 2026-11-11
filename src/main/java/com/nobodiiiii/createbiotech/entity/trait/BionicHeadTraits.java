package com.nobodiiiii.createbiotech.entity.trait;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nobodiiiii.createbiotech.CreateBiotech;
import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.ai.BionicDisposition;
import com.nobodiiiii.createbiotech.entity.ai.BionicIntelligence;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;

import net.minecraft.resources.ResourceLocation;

/** Head classifications share carrier evaluation, display values and runtime behavior. */
public final class BionicHeadTraits {
	private BionicHeadTraits() {}

	public static ResourceLocation id(Enum<?> value) {
		return CreateBiotech.asResource(value.name().toLowerCase(Locale.ROOT));
	}

	static Map<ResourceLocation, BionicSubtrait> variants(BionicTrait trait) {
		Map<ResourceLocation, BionicSubtrait> result = new HashMap<>();
		if (trait == BionicTrait.DISPOSITION)
			for (BionicDisposition value : BionicDisposition.values())
				result.put(id(value), new BionicSubtrait(id(value), trait.carrier(), Map.of()));
		if (trait == BionicTrait.INTELLIGENCE)
			for (BionicIntelligence value : BionicIntelligence.values())
				result.put(id(value), new BionicSubtrait(id(value), trait.carrier(), Map.of()));
		return Map.copyOf(result);
	}

	public static BionicDisposition disposition(Set<ResourceLocation> values) {
		if (values.size() == 1)
			for (BionicDisposition value : BionicDisposition.values())
				if (values.contains(id(value))) return value;
		return BionicDisposition.NEUTRAL;
	}

	public static BionicIntelligence intelligence(Set<ResourceLocation> values) {
		if (values.contains(id(BionicIntelligence.ADVANCED))) return BionicIntelligence.ADVANCED;
		if (values.contains(id(BionicIntelligence.NORMAL))) return BionicIntelligence.NORMAL;
		return BionicIntelligence.SIMPLE;
	}

	/** The same fallback classification used by the runtime when no carrier qualifies. */
	public static ResourceLocation defaultValue(BionicTrait trait) {
		return combined(trait, Set.of());
	}

	static ResourceLocation combined(BionicTrait trait, Set<ResourceLocation> values) {
		return trait == BionicTrait.DISPOSITION ? id(disposition(values)) : id(intelligence(values));
	}

	/** Keep the original head-retention threshold within the installed head or fallback torso. */
	static BionicTraitResult evaluate(BionicTrait trait, BionicTraitData data, BionicTissue tissue,
		List<BionicTraitDonors.Facts> donors, List<BitSet> bindings) {
		Map<UUID, Head> heads = new HashMap<>();
		for (int sourceId = 0; sourceId < tissue.sourceCount(); sourceId++) {
			Set<ResourceLocation> values = donors.get(sourceId).variants(trait);
			if (values.isEmpty()) continue;
			SurgicalAssembly.Source source = tissue.source(sourceId);
			BitSet original = BionicTraitCarriers.select(trait.carrier(), data, tissue, sourceId);
			BitSet retained = (BitSet) bindings.get(sourceId).clone();
			if (retained.isEmpty()) continue;
			Head head = heads.computeIfAbsent(source.donorId(), ignored -> new Head(source));
			head.original.or(original);
			head.values.addAll(values);
			retained.and(tissue.selected(sourceId, trait.scopes()));
			head.installed.or(retained);
			for (int cube = retained.nextSetBit(0); cube >= 0; cube = retained.nextSetBit(cube + 1))
				head.members.add(new SurgicalAssembly.CombinationMember(sourceId, cube));
		}
		if (heads.isEmpty()) return BionicTraitResult.ABSENT;
		Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		Set<SurgicalAssembly.CombinationMember> evidence = new HashSet<>();
		Set<ResourceLocation> values = new HashSet<>();
		Set<ResourceLocation> evidenceValues = new HashSet<>();
		double coverage = 0;
		for (Head head : heads.values()) {
			double total = head.source.anatomy().volume(head.original);
			double retained = head.source.anatomy().volume(head.installed);
			double localCoverage = total > 0 ? retained / total : 0;
			coverage = Math.max(coverage, localCoverage);
			evidence.addAll(head.members);
			evidenceValues.addAll(head.values);
			if (data.rule().isActive(!head.members.isEmpty(), localCoverage)) {
				members.addAll(head.members);
				values.addAll(head.values);
			}
		}
		boolean active = !members.isEmpty();
		Set<SurgicalAssembly.CombinationMember> carriers = active ? members : evidence;
		BionicSubtrait value = data.subtraits().get(combined(trait, active ? values : evidenceValues));
		return new BionicTraitResult(coverage, active ? 1 : 0, 0, Set.of(), carriers,
			active ? null : evidence.isEmpty() ? InactiveReason.WRONG_SLOT : InactiveReason.INCOMPLETE_HEAD,
			Map.of(), Set.of(), Map.of(value, carriers), Map.of(), Set.of());
	}

	private static final class Head {
		final SurgicalAssembly.Source source;
		final BitSet original = new BitSet();
		final BitSet installed = new BitSet();
		final Set<SurgicalAssembly.CombinationMember> members = new HashSet<>();
		final Set<ResourceLocation> values = new HashSet<>();
		Head(SurgicalAssembly.Source source) { this.source = source; }
	}
}
