package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

/**
 * The anatomical joints a surgical body can carry.
 *
 * <p>First-level joints consume a body slot. A shoulder or hip also owns a separate allowance for
 * matching second-level joints, so each arm and leg chain is limited independently.</p>
 */
public enum SurgicalLimbType {
	NECK("neck", 3, 1, 0),
	SHOULDER("shoulder", 8, 1, 0),
	HIP("hip", 8, 1, 0),
	// Keep new values after the original three so their network ordinals remain stable.
	ELBOW("elbow", 0, 0, 1),
	KNEE("knee", 0, 0, 1);

	private final String id;
	private final int maxPerBody;
	private final int secondaryCapacity;
	private final int secondaryCost;

	SurgicalLimbType(String id, int maxPerBody, int secondaryCapacity, int secondaryCost) {
		this.id = id;
		this.maxPerBody = maxPerBody;
		this.secondaryCapacity = secondaryCapacity;
		this.secondaryCost = secondaryCost;
	}

	public String id() {
		return id;
	}

	/** Maximum first-level joints of this type on one body. */
	public int maxPerBody() {
		return maxPerBody;
	}

	/** Matching second-level joints that one instance of this first-level joint may own. */
	public int secondaryCapacity() {
		return secondaryCapacity;
	}

	/** Allowance consumed when this second-level joint is attached to its matching first level. */
	public int secondaryCost() {
		return secondaryCost;
	}

	/** First-level joints attach an entire limb (or the head) directly to the body. */
	public boolean primary() {
		return this == NECK || this == SHOULDER || this == HIP;
	}

	/** Second-level joints articulate a limb only after it belongs to the matching first level. */
	public boolean secondary() {
		return this == ELBOW || this == KNEE;
	}

	@Nullable
	public SurgicalLimbType matchingPrimary() {
		return switch (this) {
		case ELBOW -> SHOULDER;
		case KNEE -> HIP;
		default -> null;
		};
	}

	@Nullable
	public static SurgicalLimbType byId(String id) {
		for (SurgicalLimbType type : values())
			if (type.id.equals(id))
				return type;
		return null;
	}

	@Nullable
	public static SurgicalLimbType byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
	}
}
