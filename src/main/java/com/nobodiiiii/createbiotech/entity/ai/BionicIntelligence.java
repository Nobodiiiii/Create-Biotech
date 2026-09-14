package com.nobodiiiii.createbiotech.entity.ai;

/** Head-derived attack strategy; anatomical combat values remain independent of intelligence. */
public enum BionicIntelligence {
	SIMPLE,
	NORMAL,
	ADVANCED;

	public boolean pursuesDuringAttack() {
		return this == ADVANCED;
	}

	/** @deprecated Every intelligence tier now uses the same anatomy-driven melee interval. */
	@Deprecated(forRemoval = false)
	public float meleeIntervalScale() { return 1.0f; }

	/** @deprecated Every intelligence tier now turns at the same fixed combat rate. */
	@Deprecated(forRemoval = false)
	public float bodyTurnDegrees() { return 10.0f; }

	/** @deprecated Every intelligence tier now tracks attacks at the same fixed rate. */
	@Deprecated(forRemoval = false)
	public float aimTrackingDegrees() { return 12.0f; }

	/** @deprecated Intelligence no longer weights posture selection. */
	@Deprecated(forRemoval = false)
	public float posturePreferenceWeight() {
		return 1.0f;
	}

	public static BionicIntelligence highest(BionicIntelligence first, BionicIntelligence second) {
		return first.ordinal() >= second.ordinal() ? first : second;
	}
}
