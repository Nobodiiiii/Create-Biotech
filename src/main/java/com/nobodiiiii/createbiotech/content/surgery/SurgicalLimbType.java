package com.nobodiiiii.createbiotech.content.surgery;

import org.jetbrains.annotations.Nullable;

/**
 * The anatomical joints a surgical body can carry.
 *
 * <p>Each joint pins one cube group to another and declares how many of its kind one body may
 * own. Arms remain paired, while the procedural gait accepts as many as eight hip/knee chains.</p>
 */
public enum SurgicalLimbType {
	NECK("neck", 1),
	SHOULDER("shoulder", 2),
	HIP("hip", 8),
	// Keep new values after the original three so their network ordinals remain stable.
	ELBOW("elbow", 2),
	KNEE("knee", 8);

	private final String id;
	private final int maxPerBody;

	SurgicalLimbType(String id, int maxPerBody) {
		this.id = id;
		this.maxPerBody = maxPerBody;
	}

	public String id() {
		return id;
	}

	public int maxPerBody() {
		return maxPerBody;
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
