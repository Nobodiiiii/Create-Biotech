package com.nobodiiiii.createbiotech.entity.ai;

/** Stable cognitive tier of a recognized head; tiers do not alter AI behavior yet. */
public enum BionicIntelligence {
	SIMPLE,
	NORMAL,
	ADVANCED;

	public static BionicIntelligence highest(BionicIntelligence first, BionicIntelligence second) {
		return first.ordinal() >= second.ordinal() ? first : second;
	}
}
