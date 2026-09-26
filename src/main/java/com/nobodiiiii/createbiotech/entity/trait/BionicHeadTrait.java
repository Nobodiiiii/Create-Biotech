package com.nobodiiiii.createbiotech.entity.trait;

/** Abilities requiring retained donor respiratory or interaction organs. */
public enum BionicHeadTrait {
	WATER_BREATHING("water_breathing", BionicTraitRule.threshold(0.5d)),
	DRY_SUFFOCATION("dry_suffocation", BionicTraitRule.threshold(0.5d)),
	LONG_BREATH("long_breath", BionicTraitRule.threshold(0.5d)),
	TAMEABLE("tameable", BionicTraitRule.presence());

	private final String id;
	private final BionicTraitRule rule;

	BionicHeadTrait(String id, BionicTraitRule rule) {
		this.id = id;
		this.rule = rule;
	}

	public String id() { return id; }
	public BionicTraitRule rule() { return rule; }
	public String descriptionId() { return "create_biotech.trait." + id; }
	public BionicTraitSlot slot() { return BionicTraitSlot.HEAD; }
}
