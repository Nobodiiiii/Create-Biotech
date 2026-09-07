package com.nobodiiiii.createbiotech.entity.ai;

/** Head-derived coordination; small melee timing and tracking differences, never extra reach or damage. */
public enum BionicIntelligence {
	SIMPLE(1.1f, 8.0f, 10.0f),
	NORMAL(1.0f, 10.0f, 12.0f),
	ADVANCED(0.9f, 12.0f, 14.0f);

	private final float meleeIntervalScale;
	private final float bodyTurnDegrees;
	private final float aimTrackingDegrees;

	BionicIntelligence(float meleeIntervalScale, float bodyTurnDegrees, float aimTrackingDegrees) {
		this.meleeIntervalScale = meleeIntervalScale;
		this.bodyTurnDegrees = bodyTurnDegrees;
		this.aimTrackingDegrees = aimTrackingDegrees;
	}

	public float meleeIntervalScale() { return meleeIntervalScale; }
	public float bodyTurnDegrees() { return bodyTurnDegrees; }
	public float aimTrackingDegrees() { return aimTrackingDegrees; }

	/** Small selection preference only; mounted range and cooldown remain independent of this weight. */
	public float posturePreferenceWeight() {
		return switch (this) {
			case SIMPLE -> 0.8f;
			case NORMAL -> 1.0f;
			case ADVANCED -> 1.2f;
		};
	}

	public static BionicIntelligence highest(BionicIntelligence first, BionicIntelligence second) {
		return first.ordinal() >= second.ordinal() ? first : second;
	}
}
