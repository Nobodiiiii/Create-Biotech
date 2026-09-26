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
		case RANGED_ATTACK -> InactiveReason.NO_RANGED_ATTACK;
		case WING_LIFT -> {
			var members = covered.members(trait);
			if (!BionicTraitCarriers.hasEachRole(trait.carrier(), data, tissue, members))
				yield InactiveReason.MISSING_PAIRED_WINGS;
			if (!assembly.hasBodyVolume())
				yield InactiveReason.BODY_MEASUREMENT_UNAVAILABLE;
			yield assembly.bodyVolume() > tissue.weight(members)
				* data.parameter("max_body_volume_per_wing_volume", 16)
					? InactiveReason.PURPOSE_MISMATCH : null;
		}
		case LEG_SUPPORT -> {
			if (assembly.bodyBounds() == null)
				yield InactiveReason.BODY_MEASUREMENT_UNAVAILABLE;
			int minimum = (int) data.parameter("min_legs",
				trait == BionicTrait.AGILE_LANDING || trait == BionicTrait.WALL_CLIMB ? 2 : 1);
			double carrierVolume = tissue.weight(covered.members(trait));
			boolean supported = assembly.bodyBounds().groundedLegCount() >= minimum && carrierVolume > 0
				&& !(assembly.hasBodyVolume() && assembly.bodyVolume() > carrierVolume
					* data.parameter("max_body_volume_per_leg_volume", 32));
			yield supported ? null : InactiveReason.PURPOSE_MISMATCH;
		}
		};
	}
}
