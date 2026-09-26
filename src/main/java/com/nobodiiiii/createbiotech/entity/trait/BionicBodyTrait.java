package com.nobodiiiii.createbiotech.entity.trait;

/** Tissue-based donor facts, with an explicit torso or whole-body scope for each trait. */
public enum BionicBodyTrait {
	FIRE_IMMUNE("fire_immune", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	WATER_SENSITIVE("water_sensitive", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	FREEZE_IMMUNE("freeze_immune", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	FREEZE_VULNERABLE("freeze_vulnerable", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	SUN_SENSITIVE("sun_sensitive", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	MOISTURE_DEPENDENT("moisture_dependent", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	HEAT_SENSITIVE("heat_sensitive", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled()),
	INVERTED_HEALING("inverted_healing", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(0.5d)),
	FALL_DAMAGE_IMMUNE("fall_damage_immune", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(0.5d)),
	WEB_ADAPTED("web_adapted", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(0.5d)),
	NO_BREATHING("no_breathing", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(0.5d)),
	WINGLESS_FLIGHT("wingless_flight", BionicBodyTraitScope.TORSO, BionicTraitRule.threshold(0.5d)),
	BODY_BOUNCE("body_bounce", BionicBodyTraitScope.TORSO, BionicTraitRule.scaled()),
	BODY_SLOW_FALL("body_slow_fall", BionicBodyTraitScope.TORSO, BionicTraitRule.scaled()),
	PROJECTILE_DEFLECTION("projectile_deflection", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.threshold(0.5d)),
	CONTACT_RETALIATION("contact_retaliation", BionicBodyTraitScope.WHOLE_BODY, BionicTraitRule.scaled());

	private final String serializedName;
	private final BionicBodyTraitScope scope;
	private final BionicTraitRule rule;

	BionicBodyTrait(String serializedName, BionicBodyTraitScope scope, BionicTraitRule rule) {
		this.serializedName = serializedName;
		this.scope = scope;
		this.rule = rule;
	}

	public BionicBodyTraitScope scope() { return scope; }
	public BionicTraitRule rule() { return rule; }

	public String serializedName() {
		return serializedName;
	}

	public String descriptionId() {
		return "create_biotech.trait." + serializedName;
	}
}
