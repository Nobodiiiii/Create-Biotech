package com.nobodiiiii.createbiotech.entity.trait;

/** Non-boolean donor properties, classified by the same inheritance rules as named traits. */
public enum BionicBodyProperty {
	/** Every donor contributing tissue anywhere must be immune to an effect (set intersection). */
	IMMUNE_EFFECTS("immune_effects", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(1.0d)),
	NATURAL_ARMOR("natural_armor", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	KNOCKBACK_RESISTANCE("knockback_resistance", BionicBodyTraitScope.TORSO, BionicTraitRule.scaled()),
	PASSIVE_REGENERATION("passive_regeneration", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled());

	private final String id;
	private final BionicBodyTraitScope scope;
	private final BionicTraitRule rule;

	BionicBodyProperty(String id, BionicBodyTraitScope scope, BionicTraitRule rule) {
		this.id = id;
		this.scope = scope;
		this.rule = rule;
	}

	public String id() { return id; }
	public BionicBodyTraitScope scope() { return scope; }
	public BionicTraitRule rule() { return rule; }
}
