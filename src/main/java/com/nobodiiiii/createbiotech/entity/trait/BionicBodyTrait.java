package com.nobodiiiii.createbiotech.entity.trait;

/** Whole-tissue donor facts that every retained source cube can contribute. */
public enum BionicBodyTrait {
	FIRE_IMMUNE("fire_immune"),
	WATER_SENSITIVE("water_sensitive"),
	FREEZE_IMMUNE("freeze_immune"),
	FREEZE_VULNERABLE("freeze_vulnerable"),
	SUN_SENSITIVE("sun_sensitive"),
	MOISTURE_DEPENDENT("moisture_dependent"),
	HEAT_SENSITIVE("heat_sensitive"),
	INVERTED_HEALING("inverted_healing"),
	FALL_DAMAGE_IMMUNE("fall_damage_immune"),
	WEB_ADAPTED("web_adapted"),
	NO_BREATHING("no_breathing"),
	WINGLESS_FLIGHT("wingless_flight"),
	BODY_BOUNCE("body_bounce"),
	BODY_SLOW_FALL("body_slow_fall"),
	PROJECTILE_DEFLECTION("projectile_deflection"),
	CONTACT_RETALIATION("contact_retaliation");

	private final String serializedName;

	BionicBodyTrait(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public String descriptionId() {
		return "create_biotech.trait." + serializedName;
	}
}
