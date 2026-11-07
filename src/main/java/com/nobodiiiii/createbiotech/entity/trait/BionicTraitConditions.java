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
		if (data.rule().type() != BionicTraitType.COVERAGE_THRESHOLD
			&& trait.condition() != BionicTrait.Condition.WOOL_AND_BODY)
			return null;
		return switch (trait.condition()) {
		case NONE -> null;
		case WOOL_AND_BODY -> hasCarrierRole(BionicAnatomyRole.TORSO, trait, data, tissue, covered)
			&& hasCarrierRole(BionicAnatomyRole.WOOL, trait, data, tissue, covered)
				? null : InactiveReason.MISSING_WOOL_OR_BODY;
		case BODY_VOLUME -> !assembly.hasBodyVolume() ? InactiveReason.BODY_MEASUREMENT_UNAVAILABLE
			: assembly.bodyVolume() < data.parameter("min_body_volume", 0.5d) ? InactiveReason.PURPOSE_MISMATCH : null;
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
			int minimum = (int) data.parameter(trait == BionicTrait.WING_SLOW_FALL ? "min_wings" : "min_legs",
				switch (trait) {
				case AGILE_LANDING, WALL_CLIMB, STRONG_JUMP, WING_SLOW_FALL -> 2;
				default -> 1;
				});
			boolean supported = tissue.effectiveLimbCount(trait.scopes(), covered.members(trait)) >= minimum;
			yield supported ? null : InactiveReason.PURPOSE_MISMATCH;
		}
		};
	}

	private static boolean hasCarrierRole(BionicAnatomyRole role, BionicTrait trait, BionicTraitData data,
		BionicTissue tissue, BionicTraitSet covered) {
		for (var member : covered.members(trait))
			if (BionicTraitCarriers.select(BionicTrait.Carrier.of(role), data, tissue, member.source())
				.get(member.cube()))
				return true;
		return false;
	}
}
