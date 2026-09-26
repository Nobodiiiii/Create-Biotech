package com.nobodiiiii.createbiotech.entity.trait;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;

/**
 * The four anatomical places for torso, head and organ traits. Whole-body traits use
 * {@link BionicBodyTraitScope#WHOLE_BODY} and accept retained tissue in every place.
 */
public enum BionicTraitSlot {
	BODY, HEAD, ARM, LEG;

	public String descriptionId() {
		return "create_biotech.trait.slot." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Where an original anatomical role must be installed to work. */
	public static BionicTraitSlot of(BionicAnatomyRole role) {
		return switch (role) {
		case HEAD, GILL, MOUTH -> HEAD;
		case ATTACK_HAND -> ARM;
		case LEG, FOOT -> LEG;
		case TORSO, LEFT_WING, RIGHT_WING, FIN, TAIL, TENTACLE, SHELL, SPINE -> BODY;
		};
	}

	/** The place a cube installed through this first-level joint occupies; null is the torso. */
	public static BionicTraitSlot mountedAt(@Nullable SurgicalLimbType mount) {
		if (mount == null)
			return BODY;
		return switch (mount) {
		case NECK -> HEAD;
		case SHOULDER, ELBOW -> ARM;
		case HIP, KNEE -> LEG;
		};
	}
}
