package com.nobodiiiii.createbiotech.entity.trait;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalAssembly;
import com.nobodiiiii.createbiotech.entity.trait.BionicTraitResult.InactiveReason;

/** Optional acquisition conditions, separate from donor detection and coverage measurement. */
final class BionicTraitConditions {
	private BionicTraitConditions() {}

	@Nullable
	static InactiveReason check(BionicTrait trait, BionicTraitData data, SurgicalAssembly assembly,
		BionicTissue tissue, BionicTraitSet covered) {
		if (data.rule().type() != BionicTraitType.COVERAGE_THRESHOLD)
			return null;
		return switch (trait.condition()) {
		case NONE -> null;
		case BREATHING_REQUIRED -> covered.has(BionicTrait.NO_BREATHING)
			? InactiveReason.BREATHING_NOT_REQUIRED : null;
		case WING_LIFT -> {
			var members = covered.members(trait);
			if (tissue.effectiveLimbCount(trait.scopes(), members) < (int) data.parameter("min_wings", 2))
				yield InactiveReason.MISSING_PAIRED_WINGS;
			if (!assembly.hasBodyVolume())
				yield InactiveReason.BODY_MEASUREMENT_UNAVAILABLE;
			yield assembly.bodyVolume() > tissue.weight(members)
				* data.parameter("max_body_volume_per_wing_volume", 16)
					? InactiveReason.PURPOSE_MISMATCH : null;
		}
		case LIMB_COUNT -> {
			int minimum = (int) data.parameter("min_legs",
				switch (trait) {
				case AGILE_LANDING, WALL_CLIMB -> 2;
				default -> 1;
				});
			boolean supported = tissue.effectiveLimbCount(trait.scopes(), covered.members(trait)) >= minimum;
			yield supported ? null : InactiveReason.PURPOSE_MISMATCH;
		}
		};
	}
}
