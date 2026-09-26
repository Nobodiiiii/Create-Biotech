package com.nobodiiiii.createbiotech.entity.trait;

import java.util.Objects;

/**
 * Shared inheritance rule used by resolution, effects and tooltips. A carrier must belong to the
 * trait's scope before this rule is evaluated; whole-body traits accept every organ. Threshold
 * traits may additionally require assembly conditions (for example grounded legs or enough lift),
 * checked by their resolver.
 */
public record BionicTraitRule(BionicTraitType type, double minCoverage) {
	public static final double COVERAGE_EPSILON = 1.0e-8d;

	public BionicTraitRule {
		Objects.requireNonNull(type);
		if (!Double.isFinite(minCoverage) || minCoverage < 0.0d || minCoverage > 1.0d)
			throw new IllegalArgumentException("Trait coverage threshold must be between 0 and 1");
		if (type != BionicTraitType.COVERAGE_THRESHOLD && minCoverage != 0.0d)
			throw new IllegalArgumentException("Only threshold traits have a minimum coverage");
	}

	public static BionicTraitRule presence() {
		return new BionicTraitRule(BionicTraitType.PRESENCE, 0.0d);
	}

	public static BionicTraitRule threshold(double minimum) {
		return new BionicTraitRule(BionicTraitType.COVERAGE_THRESHOLD, minimum);
	}

	public static BionicTraitRule scaled() {
		return new BionicTraitRule(BionicTraitType.COVERAGE_SCALED, 0.0d);
	}

	/** Data-pack min_coverage applies only to threshold traits. */
	public BionicTraitRule withMinCoverage(double minimum) {
		return type == BionicTraitType.COVERAGE_THRESHOLD ? threshold(minimum) : this;
	}

	public boolean isActive(boolean carrierPresent, double coverage) {
		if (!carrierPresent)
			return false;
		// Presence is determined by retained cubes, even when their volume is not recorded.
		if (type == BionicTraitType.PRESENCE)
			return true;
		if (!Double.isFinite(coverage) || coverage <= 0.0d)
			return false;
		return type == BionicTraitType.COVERAGE_SCALED
			|| coverage + COVERAGE_EPSILON >= minCoverage;
	}

	/** Binary traits return 0 or 1; scaled traits return their clamped coverage. */
	public double strength(double coverage) {
		if (!isActive(coverage > 0.0d, coverage))
			return 0.0d;
		return type == BionicTraitType.COVERAGE_SCALED ? Math.min(1.0d, coverage) : 1.0d;
	}
}
