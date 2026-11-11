package com.nobodiiiii.createbiotech.entity.ai;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicHeadTraits;
import com.nobodiiiii.createbiotech.entity.trait.BionicTrait;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResolver;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Result of resolving the captured model head retained by each packed source. Several heads keep a
 * disposition only when they agree; disagreement is neutral, while cognition uses the highest tier.
 */
public record BionicMind(BionicDisposition disposition, BionicIntelligence intelligence,
	int recognizedHeadCount) {
	public static final BionicMind FALLBACK =
		new BionicMind(BionicDisposition.NEUTRAL, BionicIntelligence.SIMPLE, 0);

	public BionicMind {
		if (disposition == null || intelligence == null || recognizedHeadCount < 0)
			throw new IllegalArgumentException("Invalid bionic mind classification");
	}

	public boolean hasRecognizedHead() {
		return recognizedHeadCount > 0;
	}

	public static BionicMind resolve(@Nullable SurgicalAssembly assembly, @Nullable Level level) {
		return fromTraits(assembly, BionicTraitResolver.resolve(assembly, level));
	}

	public static BionicMind fromTraits(@Nullable SurgicalAssembly assembly, BionicTraitSet traits) {
		if (assembly == null)
			return FALLBACK;
		int recognizedHeads = (int) Stream.concat(traits.members(BionicTrait.DISPOSITION).stream(),
			traits.members(BionicTrait.INTELLIGENCE).stream())
			.map(member -> assembly.sources().get(member.source()).donorId()).distinct().count();
		return recognizedHeads == 0 ? FALLBACK
			: new BionicMind(BionicHeadTraits.disposition(values(traits, BionicTrait.DISPOSITION)),
				BionicHeadTraits.intelligence(values(traits, BionicTrait.INTELLIGENCE)), recognizedHeads);
	}

	private static Set<ResourceLocation> values(BionicTraitSet traits, BionicTrait trait) {
		return !traits.has(trait) ? Set.of() : traits.result(trait).subtraits().keySet().stream()
			.map(value -> value.id()).collect(Collectors.toUnmodifiableSet());
	}
}
