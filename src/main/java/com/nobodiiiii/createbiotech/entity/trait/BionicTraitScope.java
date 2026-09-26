package com.nobodiiiii.createbiotech.entity.trait;

import org.jetbrains.annotations.Nullable;

import com.nobodiiiii.createbiotech.content.surgery.SurgicalLimbType;

/** Only actual assembly regions: whole body, torso and the three installable organ types. */
public enum BionicTraitScope {
	WHOLE_BODY, TORSO, HEAD, ARM, LEG;

	public String descriptionId() {
		return this == WHOLE_BODY ? "create_biotech.trait.scope.whole_body"
			: "create_biotech.trait.slot." + (this == TORSO ? "body" : name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Elbows and knees belong to their arm/leg chain, not additional organ types. */
	public static BionicTraitScope mountedAt(@Nullable SurgicalLimbType mount) {
		if (mount == null)
			return TORSO;
		return switch (mount) {
		case NECK -> HEAD;
		case SHOULDER, ELBOW -> ARM;
		case HIP, KNEE -> LEG;
		};
	}
}
