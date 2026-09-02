package com.nobodiiiii.createbiotech.entity.ai;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;

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
		if (assembly == null || level == null)
			return FALLBACK;
		BionicDisposition disposition = null;
		BionicIntelligence intelligence = BionicIntelligence.SIMPLE;
		int recognizedHeads = 0;
		for (SurgicalAssembly.Source source : assembly.sources()) {
			if (source.headCubes().isEmpty())
				continue;
			recognizedHeads++;
			BionicDisposition candidateDisposition = BionicDispositionRegistry.get(source.profile(), level);
			if (disposition == null)
				disposition = candidateDisposition;
			else if (disposition != candidateDisposition)
				disposition = BionicDisposition.NEUTRAL;
			intelligence = BionicIntelligence.highest(intelligence,
				BionicIntelligenceRegistry.get(source.profile()));
		}
		return recognizedHeads == 0 ? FALLBACK
			: new BionicMind(disposition, intelligence, recognizedHeads);
	}
}
